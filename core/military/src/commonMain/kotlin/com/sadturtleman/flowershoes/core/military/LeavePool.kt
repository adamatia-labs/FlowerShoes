package com.sadturtleman.flowershoes.core.military

import com.sadturtleman.flowershoes.core.model.Branch
import com.sadturtleman.flowershoes.core.model.Event
import com.sadturtleman.flowershoes.core.model.EventType
import com.sadturtleman.flowershoes.core.model.LeaveGrant
import com.sadturtleman.flowershoes.core.model.koreanName
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/** 휴가 풀에 더해지는 출처 한 줄(휴가 관리 › 출처 내역). */
sealed interface LeaveSource {
    val days: Int

    /** 정기휴가: 군종으로 계산하는 값이라 저장하지 않는다. 출처 내역 맨 위에 고정. */
    data class Regular(val branch: Branch, override val days: Int) : LeaveSource {
        /** "정기휴가 28일(공군)". */
        val label: String get() = "정기휴가 ${days}일(${branch.koreanName})"
    }

    data class Granted(val grant: LeaveGrant) : LeaveSource {
        override val days: Int get() = grant.days
    }
}

/**
 * 휴가 하나의 풀("남은 휴가 18일 / 전체 32일").
 *
 * 전체 = 정기휴가(군종 계산값) + 받은 휴가 합, 사용 = 휴가 일정 일수 합(시작 · 끝 · 주말 포함, 반일 없음).
 * 초과 사용을 막지 않는다 — [remaining] 이 음수가 된다.
 */
data class LeavePool(val sources: List<LeaveSource>, val used: Int) {
    val total: Int get() = sources.sumOf { it.days }
    val remaining: Int get() = total - used
    val overdrawn: Boolean get() = remaining < 0

    /**
     * [from]..[to] 를 휴가로 쓰면 남을 날(일정 추가 › 휴가 · 휴가 쓰기의 "5일 사용 · 남은 휴가 13일").
     * [replacing] 은 고치는 중인 일정 — 이미 휴가로 센 날은 돌려받고 새 기간만큼 쓴다.
     */
    fun remainingAfter(from: LocalDate, to: LocalDate, replacing: Event? = null): Int {
        val back = replacing?.takeIf { it.type == EventType.VACATION && it.deletedAt == null }?.dayCount ?: 0
        return remaining + back - (from.daysUntil(to) + 1)
    }

    fun grant(id: String): LeaveGrant? = sources.firstNotNullOfOrNull { (it as? LeaveSource.Granted)?.grant?.takeIf { g -> g.id == id } }

    companion object {
        /** [branch] 가 없으면(복무 정보 전) 정기휴가 없이 받은 휴가만. 지운 것(tombstone)은 세지 않는다. */
        fun of(branch: Branch?, grants: List<LeaveGrant>, events: List<Event>): LeavePool {
            val regular = branch?.let { LeaveSource.Regular(it, it.annualLeaveDays) }
            val granted = grants.filter { it.deletedAt == null }.sortedByDescending { it.grantedOn }.map { LeaveSource.Granted(it) }
            val used = events.filter { it.type == EventType.VACATION && it.deletedAt == null }.sumOf { it.dayCount }
            return LeavePool(listOfNotNull(regular) + granted, used)
        }
    }
}
