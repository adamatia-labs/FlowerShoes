package com.sadturtleman.flowershoes.feature.calendar

import com.sadturtleman.flowershoes.core.military.LeavePool
import com.sadturtleman.flowershoes.core.model.Branch
import com.sadturtleman.flowershoes.core.model.Event
import com.sadturtleman.flowershoes.core.model.EventType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

class EventDraftTest {

    private val t0 = Instant.fromEpochMilliseconds(0)
    private fun d(s: String) = LocalDate.parse(s)

    private fun vacation(id: String, from: String, to: String) = Event(
        id, EventType.VACATION, "", LocalDateTime.parse("${from}T00:00"), LocalDateTime.parse("${to}T23:59"), false, t0, 1, "dev",
    )

    @Test
    fun `휴가를 고르면 기간만큼 쓴 뒤 남은 휴가를 보여 준다`() {
        val pool = LeavePool.of(Branch.ARMY, emptyList(), emptyList()) // 24일
        val draft = EventDraft(null, start = d("2026-11-14"), end = d("2026-11-18")).withType(EventType.VACATION)
        val use = draft.leaveUse(pool)!!
        assertEquals("5일 사용 · 남은 휴가 19일", use.label)
        assertFalse(use.overdrawn)
    }

    @Test
    fun `휴가 일정을 고칠 때는 원래 일수를 돌려받고 넘치면 빨간색`() {
        val old = vacation("v1", "2026-11-14", "2026-11-18")
        val pool = LeavePool.of(Branch.ARMY, emptyList(), listOf(old)) // 24 - 5 = 19
        val draft = EventDraft("v1", EventType.VACATION, start = d("2026-11-14"), end = d("2026-12-12"), allDay = true, original = old)
        val use = draft.leaveUse(pool)!!
        assertEquals("29일 사용 · 남은 휴가 -5일", use.label)
        assertTrue(use.overdrawn)
    }

    @Test
    fun `휴가가 아니면 휴가 사용을 보이지 않는다`() {
        val pool = LeavePool.of(Branch.ARMY, emptyList(), emptyList())
        assertNull(EventDraft(null, EventType.VISIT, start = d("2026-11-14"), end = d("2026-11-14")).leaveUse(pool))
    }

    @Test
    fun `휴가는 하루 종일로 시작한다`() {
        val draft = EventDraft(null, start = d("2026-11-14"), end = d("2026-11-14")).withType(EventType.VACATION)
        assertTrue(draft.allDay)
    }

    @Test
    fun `기념일은 하루짜리 종일 일정이고 매년 반복이 기본으로 켜져 있다`() {
        val draft = EventDraft(null, start = d("2025-09-13"), end = d("2025-09-15")).withType(EventType.ANNIVERSARY)
        assertTrue(draft.repeatYearly)
        assertEquals(LocalDateTime.parse("2025-09-13T00:00"), draft.startAt)
        assertEquals(LocalDateTime.parse("2025-09-13T23:59"), draft.endAt)
    }

    @Test
    fun `커플 기념일은 캘린더에서 지울 수 없다`() {
        val couple = EventDraft(Event.COUPLE_ANNIVERSARY_ID, EventType.ANNIVERSARY, start = d("2024-05-18"), end = d("2024-05-18"))
        assertFalse(couple.deletable)
        assertTrue(EventDraft("b1", EventType.ANNIVERSARY, start = d("2001-11-03"), end = d("2001-11-03")).deletable)
        assertFalse(EventDraft(null, start = d("2001-11-03"), end = d("2001-11-03")).deletable)
    }
}
