package com.sadturtleman.flowershoes.core.data.sync

import com.sadturtleman.flowershoes.core.crypto.Sodium
import com.sadturtleman.flowershoes.core.data.AttachmentStore
import com.sadturtleman.flowershoes.core.data.Identity
import com.sadturtleman.flowershoes.core.data.InboxApplier
import com.sadturtleman.flowershoes.core.data.LetterRepository
import com.sadturtleman.flowershoes.core.data.SyncWriter
import com.sadturtleman.flowershoes.core.database.AppDatabase
import com.sadturtleman.flowershoes.core.database.buildKkotsin
import com.sadturtleman.flowershoes.core.database.inMemoryDatabaseBuilder
import com.sadturtleman.flowershoes.core.model.LetterBlock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

/**
 * 기기 교체 통합 흐름(#20): 지수가 새 폰으로 로그인 → 민준 폰에 "새 폰 맞나요?" → 승인하면 새 커플 키와
 * 공유 데이터 전체가 새 폰으로 간다. 지수의 옛 폰은 승인 전이면 "내가 아니에요"로 막을 수 있다.
 */
class DeviceChangeTest {

    private val cloud = InMemoryCloud()
    private val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "kkotsin-change-${Uuid.random()}"

    private inner class Phone(val userId: String, val deviceId: String, val db: AppDatabase = inMemoryDatabaseBuilder().buildKkotsin()) {
        val vault = InMemoryKeyVault()
        val identity = object : Identity {
            override val userId = this@Phone.userId
            override val deviceId = this@Phone.deviceId
            override val keyEpoch = 1
        }
        val writer = SyncWriter(db, identity)
        val letters = LetterRepository(db, writer, identity)
        val pairing = PairingSession(db, identity, vault, cloud)
        val sync = SyncClient(db, identity, vault, cloud, cloud, AttachmentStore(db, root / deviceId), InboxApplier(db), writer)
        val tokens = mutableListOf<String>()
        val change = DeviceChangeSession(db, identity, vault, cloud.deviceChange(userId), deviceInfo = { "Galaxy S25" to "KAKAO" }, signIn = { tokens += it })
        val approver = DeviceChangeApprover(db, identity, vault, cloud.deviceChange(userId), sync)
    }

    private lateinit var jisu: Phone
    private lateinit var minjun: Phone

    @BeforeTest
    fun setUp() = kotlinx.coroutines.runBlocking {
        Sodium.init()
        jisu = Phone("jisu", "jisu-old")
        minjun = Phone("minjun", "minjun-1")
        val token = assertIs<PairingState.Waiting>(jisu.pairing.createLink("지수")).token
        assertIs<PairingState.Linked>(minjun.pairing.accept(token))
        assertIs<PairingState.Linked>(jisu.pairing.poll())
        Unit
    }

    @AfterTest
    fun tearDown() = FileSystem.SYSTEM.deleteRecursively(root)

    private suspend fun settle() = repeat(2) {
        jisu.sync.syncOnce()
        minjun.sync.syncOnce()
    }

    private fun newPhone() = Phone("jisu", "jisu-new")

    @Test
    fun `새 폰 요청을 상대가 승인하면 새 폰이 연결되고 기록이 온다`() = runTest {
        val letter = minjun.letters.newLetter(listOf(LetterBlock.Text("보고 싶어"))).also { minjun.letters.send(it, emptyList()) }
        val mine = jisu.letters.newLetter(listOf(LetterBlock.Text("나도"))).also { jisu.letters.send(it, emptyList()) }
        settle()

        val b = newPhone()
        assertEquals(cloud.serverCoupleIdOf("jisu"), b.change.linkedElsewhere())
        val waiting = assertIs<DeviceChangeState.Waiting>(b.change.request())
        assertEquals("Galaxy S25", waiting.request.deviceName)
        assertTrue(("minjun" to "새 폰으로 바꿨대요. 맞나요?") in cloud.pushes)
        assertTrue(("jisu" to "새 폰에서 로그인했어요") in cloud.pushes)

        // 옛 폰: 아직 승인 전이라 막을 수 있다. 상대 폰: 승인 요청.
        val old = assertIs<DeviceChangePrompt.SignedInElsewhere>(jisu.approver.prompts().single())
        assertTrue(old.canBlock)
        val ask = assertIs<DeviceChangePrompt.Approve>(minjun.approver.prompts().single())
        assertEquals(false, ask.request.second)
        assertIs<DeviceChangeState.Waiting>(b.change.poll())

        assertEquals(ApproveResult.Approved(skipped = 0), minjun.approver.approve(ask.request))
        val linked = assertIs<DeviceChangeState.Linked>(b.change.poll())
        assertTrue(linked.fresh)
        assertEquals(TransferCounts(letters = 2), linked.expected)
        assertEquals(listOf("custom-token-for-jisu"), b.tokens)
        assertTrue(minjun.approver.prompts().isEmpty())
        // 승인 뒤 옛 폰은 알림만(막기 없음).
        assertEquals(false, assertIs<DeviceChangePrompt.SignedInElsewhere>(jisu.approver.prompts().single()).canBlock)

        b.sync.syncOnce()
        assertEquals(setOf(letter.id, mine.id), b.letters.observeInbox().first().map { it.id }.toSet())
        assertEquals(2, b.db.coupleDao().get()?.keyEpoch)

        // 새 폰이 쓴 것은 상대에게, 상대가 쓴 것은 새 폰에 간다(옛 폰이 아니라).
        val fromNew = b.letters.newLetter(listOf(LetterBlock.Text("새 폰이야"))).also { b.letters.send(it, emptyList()) }
        b.sync.syncOnce()
        minjun.sync.syncOnce()
        assertTrue(minjun.letters.observeInbox().first().any { it.id == fromNew.id })
        val reply = minjun.letters.newLetter(listOf(LetterBlock.Text("반가워"))).also { minjun.letters.send(it, emptyList()) }
        minjun.sync.syncOnce()
        b.sync.syncOnce()
        assertTrue(b.letters.observeInbox().first().any { it.id == reply.id })
        assertEquals(0, cloud.inboxSize(cloud.serverCoupleIdOf("jisu")!!, "jisu-old"))
    }

    @Test
    fun `상대가 거절하면 새 폰은 거절 화면이고 새 기기는 쓸 수 없다`() = runTest {
        val b = newPhone()
        b.change.request()
        val ask = assertIs<DeviceChangePrompt.Approve>(minjun.approver.prompts().single())
        minjun.approver.reject(ask.request)

        assertIs<DeviceChangeState.Rejected>(b.change.poll())
        assertNull(b.db.coupleDao().get())
        assertTrue(minjun.approver.prompts().isEmpty())
        assertTrue(jisu.approver.prompts().isEmpty())
        // 다시 요청할 수 있다.
        assertIs<DeviceChangeState.Waiting>(b.change.request())
    }

    @Test
    fun `옛 폰이 내가 아니에요를 누르면 요청이 취소되고 상대에게 알린다`() = runTest {
        val b = newPhone()
        b.change.request()
        val old = assertIs<DeviceChangePrompt.SignedInElsewhere>(jisu.approver.prompts().single())
        jisu.approver.block(old.request)

        assertIs<DeviceChangeState.Blocked>(b.change.poll())
        assertTrue(minjun.approver.prompts().isEmpty())
        assertTrue(("minjun" to "새 폰 요청을 막았어요") in cloud.pushes)
    }

    @Test
    fun `옛 폰의 막기가 승인보다 먼저 처리되면 상대 폰은 키도 기기도 바꾸지 않는다`() = runTest {
        val b = newPhone()
        b.change.request()
        val ask = assertIs<DeviceChangePrompt.Approve>(minjun.approver.prompts().single())
        val old = assertIs<DeviceChangePrompt.SignedInElsewhere>(jisu.approver.prompts().single())
        // 두 답이 함수보다 먼저 써진다(규칙은 둘 다 받아 준다). 함수는 먼저 온 막기를 처리한다.
        cloud.holdFunctions = true
        jisu.approver.block(old.request)
        val racing = DeviceChangeApprover(minjun.db, minjun.identity, minjun.vault, cloud.deviceChange("minjun"), minjun.sync, confirmDelay = { cloud.releaseFunctions() })

        assertEquals(ApproveResult.Stopped, racing.approve(ask.request))
        assertEquals(1, minjun.db.coupleDao().get()?.keyEpoch)
        assertEquals("jisu-old", minjun.db.deviceDao().activeFor("jisu")?.id)
        assertIs<DeviceChangeState.Blocked>(b.change.poll())
        // 옛 폰과는 그대로 주고받는다.
        val letter = minjun.letters.newLetter(listOf(LetterBlock.Text("괜찮아?"))).also { minjun.letters.send(it, emptyList()) }
        settle()
        assertTrue(jisu.letters.observeInbox().first().any { it.id == letter.id })
    }

    @Test
    fun `승인 확인이 늦으면 다음 동기화가 마무리한다`() = runTest {
        val b = newPhone()
        b.change.request()
        val ask = assertIs<DeviceChangePrompt.Approve>(minjun.approver.prompts().single())
        cloud.holdFunctions = true
        val slow = DeviceChangeApprover(minjun.db, minjun.identity, minjun.vault, cloud.deviceChange("minjun"), minjun.sync, confirmDelay = {})

        assertEquals(ApproveResult.Sent, slow.approve(ask.request))
        assertEquals(1, minjun.db.coupleDao().get()?.keyEpoch)
        cloud.releaseFunctions()
        assertTrue(slow.prompts().isEmpty())
        assertEquals(2, minjun.db.coupleDao().get()?.keyEpoch)
        assertIs<DeviceChangeState.Linked>(b.change.poll())
    }

    @Test
    fun `승인 뒤 온보딩을 끝내기 전에 앱이 꺼져도 새 폰은 이어서 받는다`() = runTest {
        val b = newPhone()
        b.change.request()
        minjun.approver.approve(assertIs<DeviceChangePrompt.Approve>(minjun.approver.prompts().single()).request)
        assertIs<DeviceChangeState.Linked>(b.change.poll())

        // 다시 켠 앱: 같은 DB · 보관함, 새 세션.
        val again = DeviceChangeSession(b.db, b.identity, b.vault, cloud.deviceChange("jisu"))
        assertEquals(cloud.serverCoupleIdOf("jisu"), again.linkedElsewhere())
        val resumed = assertIs<DeviceChangeState.Linked>(again.poll())
        assertEquals(false, resumed.fresh)
        assertEquals(2, resumed.epoch)
    }

    @Test
    fun `새 폰이 요청을 취소하면 상대 화면에서도 사라진다`() = runTest {
        val b = newPhone()
        b.change.request()
        assertIs<DeviceChangeState.Idle>(b.change.cancel())
        assertTrue(minjun.approver.prompts().isEmpty())
    }

    @Test
    fun `24시간 안 두 번째 요청은 상대 화면에 경고와 최근 기록이 함께 뜬다`() = runTest {
        val b = newPhone()
        b.change.request()
        minjun.approver.approve(assertIs<DeviceChangePrompt.Approve>(minjun.approver.prompts().single()).request)
        assertIs<DeviceChangeState.Linked>(b.change.poll())

        val c = Phone("jisu", "jisu-third")
        cloud.nowMs = { kotlin.time.Clock.System.now().toEpochMilliseconds() + 3.hours.inWholeMilliseconds }
        c.change.request()
        val ask = assertIs<DeviceChangePrompt.Approve>(minjun.approver.prompts().single())
        assertTrue(ask.request.second)
        assertEquals(listOf(DeviceChangeStatus.APPROVED, DeviceChangeStatus.PENDING), ask.recent.map { it.status })
    }

    @Test
    fun `요청은 7일 뒤 만료되고 다시 요청할 수 있다`() = runTest {
        val clock = MovableClock()
        val b = newPhone()
        val session = DeviceChangeSession(b.db, b.identity, b.vault, cloud.deviceChange("jisu"), clock)
        session.request()
        clock.advance(8.days)
        cloud.nowMs = { clock.now().toEpochMilliseconds() }

        assertIs<DeviceChangeState.Expired>(session.poll())
        assertTrue(DeviceChangeApprover(minjun.db, minjun.identity, minjun.vault, cloud.deviceChange("minjun"), null, clock).prompts().isEmpty())
        assertIs<DeviceChangeState.Waiting>(session.request())
    }

    @Test
    fun `이 폰에 연결이 있으면 교체 대상이 아니다`() = runTest {
        assertNull(jisu.change.linkedElsewhere())
    }

    private class MovableClock : kotlin.time.Clock {
        private var at = kotlin.time.Clock.System.now()
        override fun now() = at
        fun advance(d: kotlin.time.Duration) {
            at += d
        }
    }
}
