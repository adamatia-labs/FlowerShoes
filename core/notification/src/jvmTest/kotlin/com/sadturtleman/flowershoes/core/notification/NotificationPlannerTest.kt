package com.sadturtleman.flowershoes.core.notification

import com.sadturtleman.flowershoes.core.military.ServiceCalculator
import com.sadturtleman.flowershoes.core.model.Branch
import com.sadturtleman.flowershoes.core.model.Event
import com.sadturtleman.flowershoes.core.model.EventType
import com.sadturtleman.flowershoes.core.model.Letter
import com.sadturtleman.flowershoes.core.model.LetterBlock
import com.sadturtleman.flowershoes.core.model.Service
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class NotificationPlannerTest {

    // 서울 2026-10-22 12:00
    private val now = Instant.parse("2026-10-22T03:00:00Z")

    private fun event(id: String, date: String, alarm: Boolean = true, type: EventType = EventType.VISIT) = Event(
        id, type, "", LocalDateTime.parse("${date}T00:00"), LocalDateTime.parse("${date}T23:59"),
        alarm, now, 1, "dev",
    )

    @Test
    fun `일정 전날 서울 20시에 알린다`() {
        val plan = NotificationPlanner().plan(now, "민준", emptyList(), listOf(event("e1", "2026-11-01")), null, null)
        val n = plan.single()
        assertEquals(Instant.parse("2026-10-31T11:00:00Z"), n.at) // 20:00 KST
        assertEquals("내일은 면회", n.title)
    }

    @Test
    fun `지난 일정 · 알림 끈 일정은 예약하지 않는다`() {
        val plan = NotificationPlanner().plan(
            now, "민준", emptyList(),
            listOf(event("past", "2026-10-22"), event("off", "2026-12-01", alarm = false)),
            null, null,
        )
        assertTrue(plan.isEmpty())
    }

    @Test
    fun `예약 편지는 공개 시각에 알린다`() {
        val at = Instant.parse("2027-04-30T00:00:00Z")
        val letter = Letter("l1", "minjun", null, listOf(LetterBlock.Text("축하해")), "plain", at)
        val n = NotificationPlanner().plan(now, "민준", listOf(letter), emptyList(), null, null).single()
        assertEquals(at, n.at)
        assertEquals("kkotsin://letter/l1", n.deepLink)
    }

    @Test
    fun `전역일과 복무 100일 단위를 알린다`() {
        val t = ServiceCalculator().timeline(Service("minjun", Branch.ARMY, LocalDate.parse("2026-04-16"), 1))
        val ids = NotificationPlanner().plan(now, "민준", emptyList(), emptyList(), t, null).map { it.id }
        // 10.22 은 190일째 → 다음은 200 · 300 · 400일
        assertEquals(listOf("dday-200", "dday-300", "dday-400", "dday-discharge"), ids)
    }

    @Test
    fun `예산을 넘으면 가까운 것만 남긴다`() {
        val events = (1..80).map { event("e$it", LocalDate(2026, 11, 1).let { d -> LocalDate.fromEpochDays(d.toEpochDays() + it) }.toString()) }
        val plan = NotificationPlanner(budget = 50).plan(now, "민준", emptyList(), events, null, null)
        assertEquals(50, plan.size)
        assertEquals("event-e1", plan.first().id)
        assertTrue(plan.zipWithNext().all { (a, b) -> a.at <= b.at })
    }
}
