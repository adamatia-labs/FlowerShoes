package com.sadturtleman.flowershoes.core.military

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

class LeavePoolTest {

    private val t0 = Instant.fromEpochMilliseconds(0)

    private fun grant(id: String, kind: LeaveKind, days: Int, on: String, deleted: Boolean = false) = LeaveGrant(
        id, kind, kind.label, days, LocalDate.parse(on), t0, 1, "dev", if (deleted) t0 else null,
    )

    private fun event(id: String, type: EventType, from: String, to: String = from, deleted: Boolean = false) = Event(
        id, type, "", LocalDateTime.parse("${from}T00:00"), LocalDateTime.parse("${to}T23:59"), false, t0, 1, "dev",
        deletedAt = if (deleted) t0 else null,
    )

    @Test
    fun `전체는 정기휴가와 받은 휴가의 합이고 사용은 휴가 일정 일수의 합`() {
        val pool = LeavePool.of(
            Branch.AIR_FORCE,
            grants = listOf(grant("g1", LeaveKind.REWARD, 4, "2026-09-01"), grant("g2", LeaveKind.CONSOLATION, 2, "2026-10-01")),
            events = listOf(
                event("v1", EventType.VACATION, "2026-11-13", "2026-11-16"), // 금 ~ 월, 주말 포함 4일
                event("v2", EventType.VACATION, "2026-12-24"),
                event("o1", EventType.OVERNIGHT, "2026-12-01", "2026-12-02"),
            ),
        )
        assertEquals(34, pool.total) // 28 + 4 + 2
        assertEquals(5, pool.used)
        assertEquals(29, pool.remaining)
    }

    @Test
    fun `지운 받은 휴가와 지운 휴가 일정은 세지 않는다`() {
        val pool = LeavePool.of(
            Branch.ARMY,
            grants = listOf(grant("g1", LeaveKind.REWARD, 4, "2026-09-01", deleted = true)),
            events = listOf(event("v1", EventType.VACATION, "2026-11-13", "2026-11-16", deleted = true)),
        )
        assertEquals(24, pool.total)
        assertEquals(0, pool.used)
    }

    @Test
    fun `초과 사용이면 남은 휴가가 음수다`() {
        val pool = LeavePool.of(Branch.ARMY, emptyList(), listOf(event("v1", EventType.VACATION, "2026-01-01", "2026-01-26")))
        assertEquals(-2, pool.remaining)
        assertEquals(true, pool.overdrawn)
    }

    @Test
    fun `출처 내역 맨 위는 정기휴가이고 받은 휴가는 받은 날 최근 순`() {
        val pool = LeavePool.of(
            Branch.AIR_FORCE,
            grants = listOf(grant("old", LeaveKind.REWARD, 4, "2026-09-01"), grant("new", LeaveKind.PETITION, 1, "2026-10-01")),
            events = emptyList(),
        )
        assertEquals(LeaveSource.Regular(Branch.AIR_FORCE, 28), pool.sources.first())
        assertEquals("정기휴가 28일(공군)", (pool.sources.first() as LeaveSource.Regular).label)
        assertEquals(listOf("new", "old"), pool.sources.drop(1).map { (it as LeaveSource.Granted).grant.id })
    }

    @Test
    fun `복무 정보가 없으면 정기휴가 없이 받은 휴가만`() {
        val pool = LeavePool.of(null, listOf(grant("g1", LeaveKind.REWARD, 3, "2026-09-01")), emptyList())
        assertEquals(3, pool.total)
        assertEquals(listOf("g1"), pool.sources.map { (it as LeaveSource.Granted).grant.id })
    }

    @Test
    fun `기간을 고르면 그만큼 쓴 뒤 남은 휴가를 미리 센다`() {
        val v1 = event("v1", EventType.VACATION, "2026-11-13", "2026-11-16")
        val pool = LeavePool.of(Branch.AIR_FORCE, emptyList(), listOf(v1)) // 28 - 4 = 24
        assertEquals(19, pool.remainingAfter(LocalDate.parse("2026-12-01"), LocalDate.parse("2026-12-05")))
        // 이미 잡은 휴가 일정을 고치면 원래 일수는 돌려받고 새 기간만큼 쓴다.
        assertEquals(22, pool.remainingAfter(LocalDate.parse("2026-11-13"), LocalDate.parse("2026-11-18"), replacing = v1))
        // 휴가가 아니던 일정을 휴가로 바꾸면 돌려받을 일수가 없다.
        val o1 = event("o1", EventType.OVERNIGHT, "2026-12-01", "2026-12-02")
        assertEquals(22, pool.remainingAfter(LocalDate.parse("2026-12-01"), LocalDate.parse("2026-12-02"), replacing = o1))
        // 넘쳐도 막지 않는다 — 음수.
        assertEquals(-6, pool.remainingAfter(LocalDate.parse("2027-01-01"), LocalDate.parse("2027-01-30")))
    }
}
