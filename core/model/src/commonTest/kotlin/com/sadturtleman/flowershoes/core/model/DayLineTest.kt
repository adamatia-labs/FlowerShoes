package com.sadturtleman.flowershoes.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

class DayLineTest {

    // 오늘 2026-11-09(월)
    private val today = LocalDate(2026, 11, 9)

    private fun event(
        id: String,
        end: String,
        type: EventType = EventType.VISIT,
        start: String = end,
        lines: Map<String, DayLine> = emptyMap(),
        deleted: Boolean = false,
    ) = Event(
        id = id, type = type, title = "", start = LocalDateTime.parse("${start}T13:00"), end = LocalDateTime.parse("${end}T16:00"),
        alarm = false, updatedAt = Instant.fromEpochMilliseconds(0), lamport = 1, lastWriterDevice = "dev",
        deletedAt = if (deleted) Instant.fromEpochMilliseconds(1) else null, dayLines = lines,
    )

    @Test
    fun `줄마다 늦게 쓴 쪽이 남는다`() {
        val mine = mapOf("me" to DayLine("드디어 얼굴 봤다", 200), "you" to DayLine("옛 줄", 100))
        val theirs = mapOf("you" to DayLine("면회실 짜장면 최고", 300))
        assertEquals(
            mapOf("me" to DayLine("드디어 얼굴 봤다", 200), "you" to DayLine("면회실 짜장면 최고", 300)),
            mergeDayLines(mine, theirs),
        )
    }

    @Test
    fun `면회 외박 휴가만 그날의 한 줄 대상이다`() {
        val targets = EventType.entries.filter { event("e", "2026-11-08", it).hasDayLine }
        assertEquals(listOf(EventType.VACATION, EventType.OVERNIGHT, EventType.VISIT), targets)
    }

    @Test
    fun `어제 끝난 면회는 홈 카드에 뜬다`() {
        val visit = event("v", "2026-11-08")
        assertEquals("v", dayLineCard(listOf(visit), "me", today, emptySet())?.id)
    }

    @Test
    fun `끝난 날 당일에는 아직 홈 카드가 없다`() {
        assertNull(dayLineCard(listOf(event("v", "2026-11-09")), "me", today, emptySet()))
    }

    @Test
    fun `끝나고 사흘이 지나면 홈 카드가 사라진다`() {
        assertEquals("v", dayLineCard(listOf(event("v", "2026-11-06")), "me", today, emptySet())?.id)
        assertNull(dayLineCard(listOf(event("v", "2026-11-05")), "me", today, emptySet()))
    }

    @Test
    fun `내가 쓰면 홈 카드가 사라지고 상대만 썼으면 남는다`() {
        val mine = event("a", "2026-11-08", lines = mapOf("me" to DayLine("또 와 줘", 1)))
        val theirs = event("b", "2026-11-08", lines = mapOf("you" to DayLine("벌써 보고 싶다", 1)))
        assertNull(dayLineCard(listOf(mine), "me", today, emptySet()))
        assertEquals("b", dayLineCard(listOf(theirs), "me", today, emptySet())?.id)
    }

    @Test
    fun `지운 줄은 쓰지 않은 것으로 본다`() {
        val cleared = event("a", "2026-11-08", lines = mapOf("me" to DayLine("", 5)))
        assertEquals("a", dayLineCard(listOf(cleared), "me", today, emptySet())?.id)
    }

    @Test
    fun `닫은 카드와 지운 일정과 대상이 아닌 일정은 뜨지 않는다`() {
        val events = listOf(
            event("closed", "2026-11-08"),
            event("gone", "2026-11-08", deleted = true),
            event("duty", "2026-11-08", EventType.DUTY),
        )
        assertNull(dayLineCard(events, "me", today, setOf("closed")))
    }

    @Test
    fun `여러 개면 가장 최근에 끝난 일정 하나`() {
        val vacation = event("vac", "2026-11-07", EventType.VACATION, start = "2026-11-03")
        val visit = event("vis", "2026-11-08")
        assertEquals("vis", dayLineCard(listOf(vacation, visit), "me", today, emptySet())?.id)
    }

    @Test
    fun `내 줄과 상대 줄을 나눠 본다`() {
        val e = event("a", "2026-11-08", lines = mapOf("me" to DayLine("또 와 줘", 1), "you" to DayLine("벌써 보고 싶다", 2), "old" to DayLine("", 3)))
        assertEquals("또 와 줘", e.myDayLine("me"))
        assertEquals("벌써 보고 싶다", e.partnerDayLine("me"))
        assertNull(event("b", "2026-11-08").partnerDayLine("me"))
    }

    @Test
    fun `홈 카드 문구는 끝난 날을 어제 그저께 날짜로 말한다`() {
        assertEquals("어제 면회, 그날의 한 줄 남길까요?", event("a", "2026-11-08").dayLineAsk(today))
        assertEquals("그저께 휴가, 그날의 한 줄 남길까요?", event("b", "2026-11-07", EventType.VACATION, start = "2026-11-03").dayLineAsk(today))
        assertEquals("11월 6일 외박, 그날의 한 줄 남길까요?", event("c", "2026-11-06", EventType.OVERNIGHT).dayLineAsk(today))
    }
}
