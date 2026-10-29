package com.sadturtleman.flowershoes.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

class EventOccurrenceTest {

    private fun event(type: EventType, start: String, end: String = start, repeatYearly: Boolean = true) = Event(
        id = "e", type = type, title = "", start = LocalDateTime.parse("${start}T00:00"), end = LocalDateTime.parse("${end}T23:59"),
        alarm = false, updatedAt = Instant.fromEpochMilliseconds(0), lamport = 1, lastWriterDevice = "dev", repeatYearly = repeatYearly,
    )

    @Test
    fun `매년 반복 기념일은 해마다 같은 날에 놓인다`() {
        val a = event(EventType.ANNIVERSARY, "2024-05-18")
        val got = a.occurrencesIn(LocalDate(2026, 1, 1), LocalDate(2027, 12, 31))
        assertEquals(listOf(LocalDate(2026, 5, 18), LocalDate(2027, 5, 18)), got.map { it.start.date })
        assertTrue(got.all { it.id == "e" })
    }

    @Test
    fun `반복 기념일은 원래 날 이전 해에는 없다`() {
        val a = event(EventType.ANNIVERSARY, "2026-05-18")
        assertEquals(emptyList(), a.occurrencesIn(LocalDate(2025, 1, 1), LocalDate(2025, 12, 31)))
    }

    @Test
    fun `2월 29일 기념일은 평년에 2월 28일`() {
        val a = event(EventType.ANNIVERSARY, "2024-02-29")
        assertEquals(listOf(LocalDate(2026, 2, 28)), a.occurrencesIn(LocalDate(2026, 2, 1), LocalDate(2026, 3, 31)).map { it.start.date })
    }

    @Test
    fun `반복을 끈 기념일과 다른 일정은 원래 날에만 있다`() {
        val once = event(EventType.ANNIVERSARY, "2024-05-18", repeatYearly = false)
        val visit = event(EventType.VISIT, "2024-05-18")
        val range = LocalDate(2024, 5, 1) to LocalDate(2026, 12, 31)
        assertEquals(listOf(once), once.occurrencesIn(range.first, range.second))
        assertEquals(listOf(visit), visit.occurrencesIn(range.first, range.second))
        assertEquals(emptyList(), visit.occurrencesIn(LocalDate(2025, 1, 1), LocalDate(2025, 12, 31)))
    }

    @Test
    fun `일정 일수는 시작 · 끝 · 주말을 모두 센다`() {
        // 2026-11-13(금) ~ 11-16(월)
        assertEquals(4, event(EventType.VACATION, "2026-11-13", "2026-11-16").dayCount)
        assertEquals(1, event(EventType.VACATION, "2026-11-13").dayCount)
    }
}
