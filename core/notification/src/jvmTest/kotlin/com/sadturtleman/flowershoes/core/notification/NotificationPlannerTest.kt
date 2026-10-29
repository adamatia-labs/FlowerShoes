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

    private fun event(id: String, date: String, alarm: Boolean = true, type: EventType = EventType.VISIT, title: String = "", repeatYearly: Boolean = true) = Event(
        id, type, title, LocalDateTime.parse("${date}T00:00"), LocalDateTime.parse("${date}T23:59"),
        alarm, now, 1, "dev", repeatYearly = repeatYearly,
    )

    @Test
    fun `일정 전날 서울 20시에 알린다`() {
        val plan = NotificationPlanner().plan(now, "민준", emptyList(), listOf(event("e1", "2026-11-01")), null)
        val n = plan.single()
        assertEquals(Instant.parse("2026-10-31T11:00:00Z"), n.at) // 20:00 KST
        assertEquals("내일은 면회", n.title)
    }

    @Test
    fun `지난 일정 · 알림 끈 일정은 예약하지 않는다`() {
        val plan = NotificationPlanner().plan(
            now, "민준", emptyList(),
            listOf(event("past", "2026-10-22"), event("off", "2026-12-01", alarm = false)),
            null,
        )
        assertTrue(plan.isEmpty())
    }

    @Test
    fun `예약 편지는 공개 시각에 알린다`() {
        val at = Instant.parse("2027-04-30T00:00:00Z")
        val letter = Letter("l1", "minjun", null, listOf(LetterBlock.Text("축하해")), "plain", at)
        val n = NotificationPlanner().plan(now, "민준", listOf(letter), emptyList(), null).single()
        assertEquals(at, n.at)
        assertEquals("kkotsin://letter/l1", n.deepLink)
    }

    @Test
    fun `시계 차이로 조금 미래인 일반 편지는 예약 알림을 걸지 않는다`() {
        val letter = Letter("l2", "minjun", null, listOf(LetterBlock.Text("안녕")), "plain", Instant.parse("2026-10-22T03:00:20Z"))
        assertTrue(NotificationPlanner().plan(now, "민준", listOf(letter), emptyList(), null).isEmpty())
    }

    @Test
    fun `전역일과 복무 100일 단위를 알린다`() {
        val t = ServiceCalculator().timeline(Service("minjun", Branch.ARMY, LocalDate.parse("2026-04-16"), 1))
        val ids = NotificationPlanner().plan(now, "민준", emptyList(), emptyList(), t).map { it.id }
        // 10.22 은 190일째 → 다음은 200 · 300 · 400일
        assertEquals(listOf("dday-200", "dday-300", "dday-400", "dday-discharge"), ids)
    }

    @Test
    fun `예산을 넘으면 가까운 것만 남긴다`() {
        val events = (1..80).map { event("e$it", LocalDate(2026, 11, 1).let { d -> LocalDate.fromEpochDays(d.toEpochDays() + it) }.toString()) }
        val plan = NotificationPlanner(budget = 50).plan(now, "민준", emptyList(), events, null)
        assertEquals(50, plan.size)
        assertEquals("event-e1", plan.first().id)
        assertTrue(plan.zipWithNext().all { (a, b) -> a.at <= b.at })
    }

    @Test
    fun `커플 기념일 일정으로 100일 단위와 주년을 알린다`() {
        val couple = event(Event.COUPLE_ANNIVERSARY_ID, "2024-05-18", alarm = false, type = EventType.ANNIVERSARY, title = "사귄 날")
        val plan = NotificationPlanner().plan(now, "민준", emptyList(), listOf(couple), null)
        // 10.22 은 888일째 → 900 · 1000 · 1100일, 2027.5.18 은 3주년
        assertEquals(listOf("anniv-d900", "anniv-d1000", "anniv-2027", "anniv-d1100"), plan.map { it.id })
        assertEquals("3주년이에요", plan.first { it.id == "anniv-2027" }.title)
        assertEquals(Instant.parse("2027-05-18T00:00:00Z"), plan.first { it.id == "anniv-2027" }.at) // 09:00 KST
    }

    @Test
    fun `매년 반복 기념일은 해마다 그날 아침에 알린다`() {
        val birthday = event("b1", "2001-11-03", alarm = false, type = EventType.ANNIVERSARY, title = "민준 생일")
        val plan = NotificationPlanner().plan(now, "민준", emptyList(), listOf(birthday), null)
        assertEquals(listOf("anniv-b1-2026", "anniv-b1-2027"), plan.map { it.id })
        assertEquals("오늘은 민준 생일", plan.first().title)
        assertEquals(Instant.parse("2026-11-03T00:00:00Z"), plan.first().at)
    }

    @Test
    fun `반복을 끈 기념일은 해마다 알리지 않는다`() {
        val once = event("b1", "2026-11-03", alarm = false, type = EventType.ANNIVERSARY, repeatYearly = false)
        assertTrue(NotificationPlanner().plan(now, "민준", emptyList(), listOf(once), null).isEmpty())
    }

    @Test
    fun `알림 켠 반복 기념일은 다가오는 해의 전날에 알린다`() {
        val birthday = event("b1", "2001-11-03", type = EventType.ANNIVERSARY, title = "민준 생일")
        val eve = NotificationPlanner().plan(now, "민준", emptyList(), listOf(birthday), null, anniversaryYears = false)
        assertEquals(listOf("event-b1-2026", "event-b1-2027"), eve.map { it.id })
        assertEquals(Instant.parse("2026-11-02T11:00:00Z"), eve.first().at)
    }

    @Test
    fun `기념일 알림을 끄면 100일 · 주년을 건너뛴다`() {
        val couple = event(Event.COUPLE_ANNIVERSARY_ID, "2024-05-18", alarm = false, type = EventType.ANNIVERSARY)
        val plan = NotificationPlanner().plan(now, "민준", emptyList(), listOf(couple), null, anniversaryHundreds = false, anniversaryYears = false)
        assertTrue(plan.isEmpty())
    }
}
