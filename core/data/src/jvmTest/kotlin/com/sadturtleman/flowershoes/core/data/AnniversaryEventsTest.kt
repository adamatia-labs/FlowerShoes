package com.sadturtleman.flowershoes.core.data

import com.sadturtleman.flowershoes.core.database.buildKkotsin
import com.sadturtleman.flowershoes.core.database.inMemoryDatabaseBuilder
import com.sadturtleman.flowershoes.core.model.Event
import com.sadturtleman.flowershoes.core.model.EventType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/** 설정 › 기념일 › 직접 추가한 날: 커플 기념일과 다른 일정은 빼고, 지운 것도 뺀다. */
class AnniversaryEventsTest {

    private val db = inMemoryDatabaseBuilder().buildKkotsin()
    private val identity = object : Identity {
        override val userId = "jisu"
        override val deviceId = "dev-a"
        override val keyEpoch = 1
    }
    private val events = EventRepository(db, SyncWriter(db, identity), identity)
    private val profile = ProfileRepository(db, SyncWriter(db, identity), identity)

    @AfterTest
    fun close() = db.close()

    private fun event(id: String, type: EventType, date: String, title: String = "") = Event(
        id, type, title, LocalDateTime.parse("${date}T00:00"), LocalDateTime.parse("${date}T23:59"),
        alarm = false, updatedAt = Instant.fromEpochMilliseconds(0), lamport = 0, lastWriterDevice = "",
    )

    @Test
    fun `직접 추가한 기념일만 날짜 순으로`() = runTest {
        profile.setAnniversary(LocalDate(2025, 9, 13))
        events.save(event("b", EventType.ANNIVERSARY, "2001-01-07", "민준 생일"))
        events.save(event("a", EventType.ANNIVERSARY, "2000-05-01", "지수 생일"))
        events.save(event("v", EventType.VISIT, "2026-10-19"))
        events.save(event("gone", EventType.ANNIVERSARY, "2024-03-01"))
        events.delete("gone")

        assertEquals(listOf("지수 생일", "민준 생일"), events.observeAnniversaries().first().map { it.title })
    }
}
