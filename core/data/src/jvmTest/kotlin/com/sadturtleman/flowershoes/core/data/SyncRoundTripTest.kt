package com.sadturtleman.flowershoes.core.data

import com.sadturtleman.flowershoes.core.database.AppDatabase
import com.sadturtleman.flowershoes.core.database.RevokedDeviceEntity
import com.sadturtleman.flowershoes.core.database.SyncOpEntity
import com.sadturtleman.flowershoes.core.database.buildKkotsin
import com.sadturtleman.flowershoes.core.database.inMemoryDatabaseBuilder
import com.sadturtleman.flowershoes.core.model.Event
import com.sadturtleman.flowershoes.core.model.EventType
import com.sadturtleman.flowershoes.core.model.LetterBlock
import com.sadturtleman.flowershoes.core.model.ReceiptEntity
import com.sadturtleman.flowershoes.core.sync.StreamId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * 두 폰(A · B)의 DB 를 띄워, A 가 쓴 Op 을 B 가 반영하는 과정을 끝까지 돌린다.
 * 우편함 · 암호화는 빼고 SyncOp 로그 → InboxApplier 만 잇는다.
 */
class SyncRoundTripTest {

    private class FixedClock(var now: Instant) : Clock {
        override fun now() = now
    }

    private class Phone(val userId: String, val deviceId: String, clock: Clock) {
        val db: AppDatabase = inMemoryDatabaseBuilder().buildKkotsin()
        val identity = object : Identity {
            override val userId = this@Phone.userId
            override val deviceId = this@Phone.deviceId
            override val keyEpoch = 1
        }
        val writer = SyncWriter(db, identity, clock)
        val events = EventRepository(db, writer, identity, clock)
        val letters = LetterRepository(db, writer, identity, clock)
        val missYou = MissYouRepository(db, writer, identity, clock)
        val leave = LeaveRepository(db, writer, identity, clock)
        val profile = ProfileRepository(db, writer, identity, clock = clock)
        val inbox = InboxApplier(db, clock)
        val stream = StreamId(deviceId, StreamId.Channel.APP)

        suspend fun log(): List<SyncOpEntity> = db.syncDao().log(stream.wire)
    }

    private val clock = FixedClock(Instant.parse("2026-10-22T03:00:00Z"))
    private val a = Phone("jisu", "dev-a", clock)
    private val b = Phone("minjun", "dev-b", clock)

    @AfterTest
    fun close() {
        a.db.close()
        b.db.close()
    }

    private suspend fun deliver(from: Phone, to: Phone) = deliver(from, to, from.log())

    private suspend fun deliver(from: Phone, to: Phone, ops: List<SyncOpEntity>) =
        ops.map { to.inbox.apply(ReceivedOp(from.stream, it.seq, it.opId, it.lamport, it.payload)) }

    private fun visit(id: String, title: String) = Event(
        id, EventType.VISIT, title,
        LocalDateTime.parse("2026-11-01T10:00"), LocalDateTime.parse("2026-11-01T15:00"),
        alarm = true, updatedAt = clock.now, lamport = 0, lastWriterDevice = "",
    )

    @Test
    fun `쓰기와 Op 발행이 함께 남고 상대에게 그대로 반영된다`() = runTest {
        a.events.save(visit("e1", "첫 면회"))
        a.missYou.send()
        assertEquals(listOf(1L, 2L), a.log().map { it.seq })

        assertEquals(listOf(ApplyResult.APPLIED, ApplyResult.APPLIED), deliver(a, b))
        assertEquals("첫 면회", b.db.eventDao().get("e1")?.title)
        assertEquals(1, b.missYou.observeTodayFrom("jisu").first())
        // 같은 Op 이 다시 와도 한 번만 반영한다.
        assertEquals(listOf(ApplyResult.DUPLICATE, ApplyResult.DUPLICATE), deliver(a, b))
    }

    @Test
    fun `순서가 뒤바뀌어 와도 커서는 빈 번호가 채워질 때 오른다`() = runTest {
        repeat(3) { a.missYou.send() }
        val ops = a.log()
        deliver(a, b, listOf(ops[2]))
        assertEquals(0L, b.db.syncDao().cursor(a.stream.wire))
        assertEquals(listOf(3L), b.db.syncDao().pending(a.stream.wire))
        deliver(a, b, listOf(ops[0], ops[1]))
        assertEquals(3L, b.db.syncDao().cursor(a.stream.wire))
        assertEquals(emptyList(), b.db.syncDao().pending(a.stream.wire))
    }

    @Test
    fun `같은 일정을 둘이 고치면 lamport 가 큰 쪽이 이긴다`() = runTest {
        a.events.save(visit("e1", "면회"))
        deliver(a, b)
        b.events.save(b.db.eventDao().get("e1")!!.toModel().copy(title = "면회(민준 수정)"))
        a.events.save(a.db.eventDao().get("e1")!!.toModel().copy(title = "면회(지수 수정)")) // A 는 lamport 2

        deliver(b, a)
        deliver(a, b, a.log().takeLast(1))
        // B 의 수정은 lamport 2(A 의 1 을 본 뒤), A 의 두 번째 수정도 lamport 2 → 기기 id 가 큰 dev-b 가 이긴다.
        assertEquals(a.db.eventDao().get("e1")?.title, b.db.eventDao().get("e1")?.title)
        assertEquals("면회(민준 수정)", a.db.eventDao().get("e1")?.title)
    }

    @Test
    fun `일정 삭제는 tombstone 으로 전달된다`() = runTest {
        a.events.save(visit("e1", "훈련"))
        a.events.delete("e1")
        deliver(a, b)
        assertEquals(emptyList(), b.events.observeRange(kotlinx.datetime.LocalDate.parse("2026-11-01"), kotlinx.datetime.LocalDate.parse("2026-11-30")).first())
    }

    @Test
    fun `예약 편지는 공개 시각 전에는 받는 쪽 편지함에 없다`() = runTest {
        val later = a.letters.newLetter(listOf(LetterBlock.Text("전역 축하해")), visibleAt = Instant.parse("2027-04-30T00:00:00Z"))
        a.letters.send(later, emptyList())
        assertEquals("scheduled", a.log().single().kind)
        deliver(a, b)
        assertEquals(emptyList(), b.letters.observeInbox().first())
    }

    @Test
    fun `읽음은 한 번만 기록되고 상대에게 간다`() = runTest {
        val letter = a.letters.newLetter(listOf(LetterBlock.Text("안녕")))
        a.letters.send(letter, emptyList())
        deliver(a, b)
        b.letters.markRead(ReceiptEntity.LETTER, letter.id)
        b.letters.markRead(ReceiptEntity.LETTER, letter.id)
        assertEquals(1, b.log().size)
        deliver(b, a)
        assertEquals("minjun", a.db.receiptDao().get("LETTER", letter.id, "minjun")?.readerId)
    }

    @Test
    fun `폐기된 기기의 cutoff 뒤 Op 은 버린다`() = runTest {
        repeat(2) { a.missYou.send() }
        b.db.syncDao().addRevoked(RevokedDeviceEntity("dev-a", 0, """{"dev-a:app":1}"""))
        assertEquals(listOf(ApplyResult.APPLIED, ApplyResult.REJECTED_REVOKED), deliver(a, b))
    }

    @Test
    fun `모르는 종류의 Op 은 건너뛰되 커서는 옮긴다`() = runTest {
        val unknown = """{"t":"future-thing","x":1}""".encodeToByteArray()
        assertEquals(ApplyResult.UNKNOWN_SKIPPED, b.inbox.apply(ReceivedOp(a.stream, 1, "op-x", 1, unknown)))
        assertEquals(1L, b.db.syncDao().cursor(a.stream.wire))
    }

    @Test
    fun `받은 휴가와 휴가 일정이 상대에게 가고 상대 폰의 휴가 풀이 같아진다`() = runTest {
        val grant = com.sadturtleman.flowershoes.core.model.LeaveGrant(
            "g1", com.sadturtleman.flowershoes.core.model.LeaveKind.REWARD, "포상휴가 · 사격 만발", 4,
            kotlinx.datetime.LocalDate.parse("2026-10-02"), clock.now, 0, "",
        )
        a.profile.saveService(com.sadturtleman.flowershoes.core.model.Service("minjun", com.sadturtleman.flowershoes.core.model.Branch.AIR_FORCE, kotlinx.datetime.LocalDate.parse("2026-03-02"), 2))
        a.leave.save(grant)
        a.leave.use(kotlinx.datetime.LocalDate.parse("2026-11-14"), kotlinx.datetime.LocalDate.parse("2026-11-15"), notify = false)
        // 복무 · 받은 휴가 · 알리지 않은 사용 등록은 조용한 종류로 나간다.
        assertEquals(listOf("profile", "profile", "profile"), a.log().map { it.kind })
        deliver(a, b)

        val mirrored = b.leave.observeGrants().first().single()
        assertEquals("포상휴가", mirrored.title)
        assertEquals("포상휴가 · 사격 만발 · 2026.10.02에 받음", mirrored.subtitle)
        val use = b.leave.observeUses().first().single()
        assertEquals(EventType.VACATION, use.type)
        assertEquals(2, use.dayCount)
        val pool = b.leave.observePool().first()
        assertEquals(32, pool.total) // 공군 28 + 포상 4
        assertEquals(30, pool.remaining)

        a.leave.delete("g1")
        deliver(a, b, a.log().takeLast(1))
        assertEquals(emptyList(), b.leave.observeGrants().first())
        assertEquals(26, b.leave.observePool().first().remaining)
    }

    @Test
    fun `커플 기념일은 매년 반복 기념일 일정 하나로 상대에게 가고 그 해 캘린더에 놓인다`() = runTest {
        a.profile.setAnniversary(kotlinx.datetime.LocalDate.parse("2024-05-18"))
        deliver(a, b)

        assertEquals(kotlinx.datetime.LocalDate.parse("2024-05-18"), b.profile.observeAnniversary().first())
        val inMay = b.events.observeRange(kotlinx.datetime.LocalDate.parse("2026-05-01"), kotlinx.datetime.LocalDate.parse("2026-05-31")).first()
        val anniv = inMay.single()
        assertEquals(EventType.ANNIVERSARY, anniv.type)
        assertEquals(kotlinx.datetime.LocalDate.parse("2026-05-18"), anniv.start.date)

        // 둘이 고치면 늦게 쓴 쪽 하나만 남는다(같은 일정 id · LWW).
        b.profile.setAnniversary(kotlinx.datetime.LocalDate.parse("2024-05-19"))
        deliver(b, a)
        assertEquals(kotlinx.datetime.LocalDate.parse("2024-05-19"), a.profile.observeAnniversary().first())

        a.profile.setAnniversary(null)
        deliver(a, b, a.log().takeLast(1))
        assertNull(b.profile.observeAnniversary().first())
    }

    @Test
    fun `스냅샷은 담긴 항목을 반영하고 커서를 한 번에 옮긴다`() = runTest {
        a.events.save(visit("e1", "휴가"))
        val item = OpCodec.decode(a.log().single().payload)!!
        val snapshot = OpCodec.encode(OpPayload.Snapshot(coversThrough = 40, items = listOf(item)))
        b.inbox.apply(ReceivedOp(a.stream, 41, "snap", 5, snapshot))
        assertEquals("휴가", b.db.eventDao().get("e1")?.title)
        assertEquals(41L, b.db.syncDao().cursor(a.stream.wire))
        assertNull(b.db.syncDao().pending(a.stream.wire).firstOrNull())
    }
}
