package com.sadturtleman.flowershoes.core.data

import com.sadturtleman.flowershoes.core.database.AppDatabase
import com.sadturtleman.flowershoes.core.database.buildKkotsin
import com.sadturtleman.flowershoes.core.database.inMemoryDatabaseBuilder
import com.sadturtleman.flowershoes.core.model.DayLine
import com.sadturtleman.flowershoes.core.model.Event
import com.sadturtleman.flowershoes.core.model.EventType
import com.sadturtleman.flowershoes.core.sync.StreamId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/** 그날의 한 줄: 쓰기 · 두 폰 합치기 · 한 달 모아 보기(추억 책이 쓰는 쿼리). */
class DayLineRepositoryTest {

    private class FixedClock(var now: Instant) : Clock {
        override fun now() = now
    }

    private class Phone(user: String, device: String, clock: Clock) {
        val db: AppDatabase = inMemoryDatabaseBuilder().buildKkotsin()
        val identity = object : Identity {
            override val userId = user
            override val deviceId = device
            override val keyEpoch = 1
        }
        val events = EventRepository(db, SyncWriter(db, identity, clock), identity, clock)
        val inbox = InboxApplier(db, clock)
        val stream = StreamId(device, StreamId.Channel.APP)
    }

    private val clock = FixedClock(Instant.parse("2026-10-20T01:00:00Z"))
    private val jisu = Phone("jisu", "dev-a", clock)
    private val minjun = Phone("minjun", "dev-b", clock)

    @AfterTest
    fun close() {
        jisu.db.close()
        minjun.db.close()
    }

    private suspend fun deliver(from: Phone, to: Phone) =
        from.db.syncDao().log(from.stream.wire).forEach { to.inbox.apply(ReceivedOp(from.stream, it.seq, it.opId, it.lamport, it.payload)) }

    private fun event(id: String, type: EventType, start: String, end: String = start) = Event(
        id, type, "", LocalDateTime.parse("${start}T13:00"), LocalDateTime.parse("${end}T16:00"),
        alarm = false, updatedAt = clock.now, lamport = 0, lastWriterDevice = "",
    )

    @Test
    fun `내 줄은 40자로 자르고 앞뒤 공백을 뗀다`() = runTest {
        jisu.events.save(event("v", EventType.VISIT, "2026-10-19"))
        jisu.events.writeDayLine("v", "  " + "가".repeat(45) + "  ")
        assertEquals(DayLine("가".repeat(40), clock.now.toEpochMilliseconds()), jisu.events.get("v")?.dayLines?.get("jisu"))
    }

    @Test
    fun `두 사람이 따로 쓴 한 줄이 양쪽 폰에 모두 남는다`() = runTest {
        jisu.events.save(event("v", EventType.VISIT, "2026-10-19"))
        deliver(jisu, minjun)
        jisu.events.writeDayLine("v", "자판기 커피가 이렇게 맛있을 줄")
        clock.now = Instant.parse("2026-10-20T02:00:00Z")
        minjun.events.writeDayLine("v", "다음엔 내가 웃길게")
        deliver(jisu, minjun)
        deliver(minjun, jisu)
        val expected = mapOf("jisu" to "자판기 커피가 이렇게 맛있을 줄", "minjun" to "다음엔 내가 웃길게")
        assertEquals(expected, jisu.events.get("v")?.dayLines?.mapValues { it.value.text })
        assertEquals(expected, minjun.events.get("v")?.dayLines?.mapValues { it.value.text })
    }

    @Test
    fun `일정 Op 본문은 dayLines 필드로 한 줄을 싣는다`() = runTest {
        jisu.events.save(event("v", EventType.VISIT, "2026-10-19"))
        jisu.events.writeDayLine("v", "또 와 줘")
        val payload = jisu.db.syncDao().log(jisu.stream.wire).last().payload.decodeToString()
        assertTrue("\"dayLines\"" in payload, payload)
        assertTrue("meetNotes" !in payload, payload)
    }

    @Test
    fun `한 달의 그날의 한 줄은 그 달에 시작한 만남 중 한 줄이 있는 것`() = runTest {
        jisu.events.save(event("oct", EventType.VISIT, "2026-10-19"))
        jisu.events.save(event("blank", EventType.OVERNIGHT, "2026-10-03", "2026-10-04"))
        jisu.events.save(event("duty", EventType.DUTY, "2026-10-10"))
        jisu.events.save(event("vac", EventType.VACATION, "2026-10-30", "2026-11-03"))
        jisu.events.save(event("gone", EventType.VISIT, "2026-10-25"))
        jisu.events.writeDayLine("oct", "첫 면회")
        jisu.events.writeDayLine("duty", "당직")
        jisu.events.writeDayLine("vac", "바다")
        jisu.events.writeDayLine("gone", "지울 일정")
        jisu.events.delete("gone")
        jisu.events.writeDayLine("blank", "")

        assertEquals(listOf("oct", "vac"), jisu.events.observeDayLinesIn(2026, 10).first().map { it.id })
        assertEquals(emptyList(), jisu.events.observeDayLinesIn(2026, 11).first().map { it.id })
    }
}
