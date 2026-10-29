package com.sadturtleman.flowershoes.feature.calendar

import com.sadturtleman.flowershoes.core.military.LeavePool
import com.sadturtleman.flowershoes.core.model.Branch
import com.sadturtleman.flowershoes.core.model.Event
import com.sadturtleman.flowershoes.core.model.EventType
import com.sadturtleman.flowershoes.core.model.LeaveGrant
import com.sadturtleman.flowershoes.core.model.LeaveKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

class LeaveStateTest {

    private val t0 = Instant.fromEpochMilliseconds(0)

    private fun vacation(id: String, from: String, to: String = from) = Event(
        id, EventType.VACATION, "", LocalDateTime.parse("${from}T00:00"), LocalDateTime.parse("${to}T23:59"), false, t0, 1, "dev",
    )

    private val reward = LeaveGrant("g1", LeaveKind.REWARD, "포상휴가", 4, LocalDate.parse("2026-09-25"), t0, 1, "dev")

    private fun state(uses: List<Event>) = LeaveState(
        pool = LeavePool.of(Branch.AIR_FORCE, listOf(reward), uses),
        uses = uses,
        today = LocalDate.parse("2026-10-28"),
        enlistDate = LocalDate.parse("2026-04-20"),
    )

    @Test
    fun `받은 휴가는 정기휴가가 맨 위이고 받은 휴가는 종류와 받은 날`() {
        val rows = state(emptyList()).received
        assertEquals(listOf("정기휴가 28일(공군)", "포상휴가 · 2026.09.25"), rows.map { it.title })
        assertEquals(listOf(null, "4일"), rows.map { it.value })
    }

    @Test
    fun `쓴 휴가는 기간과 일수이고 남은 휴가 막대는 전체에 대한 남은 비율`() {
        val s = state(listOf(vacation("v2", "2026-09-04", "2026-09-07"), vacation("v1", "2026-07-10", "2026-07-14"), vacation("v3", "2026-10-09")))
        assertEquals(listOf("7월 10일 – 7월 14일" to "5일", "9월 4일 – 9월 7일" to "4일", "10월 9일" to "1일"), s.used.map { it.title to it.value })
        assertEquals(22, s.remaining) // 32 - 10
        assertEquals(22f / 32f, s.remainingFraction)
    }

    @Test
    fun `넘쳐 쓴 휴가는 막대가 비고 남은 휴가가 음수`() {
        val s = state(listOf(vacation("v1", "2026-11-01", "2026-12-10")))
        assertEquals(-8, s.remaining)
        assertEquals(0f, s.remainingFraction)
    }
}
