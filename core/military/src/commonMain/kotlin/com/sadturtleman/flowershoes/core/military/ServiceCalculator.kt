package com.sadturtleman.flowershoes.core.military

import com.sadturtleman.flowershoes.core.model.Service
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus

/** 병 계급. 사회복무요원은 계급이 없어 [ServiceTimeline.ranks] 가 비어 있다. */
enum class Rank { PRIVATE, PRIVATE_FIRST_CLASS, CORPORAL, SERGEANT }

/** 계급 하나와 그 계급이 시작되는 날. */
data class RankStep(val rank: Rank, val from: LocalDate)

/**
 * 한 사람의 복무 일정. 화면은 이것만 보고 D-day · 진행률 · 다음 진급을 그린다.
 *
 * [dischargeDate] 는 사용자 수정값이 있으면 그 값, 없으면 규칙으로 계산한 값이다.
 */
data class ServiceTimeline(
    val enlistDate: LocalDate,
    val dischargeDate: LocalDate,
    val isOverridden: Boolean,
    val ranks: List<RankStep>,
    val rulesVersion: Int,
) {
    /** 전체 복무 일수(입대일과 전역일 모두 포함). */
    val totalDays: Int get() = enlistDate.daysUntil(dischargeDate) + 1

    /** 전역까지 남은 날. 전역일 당일이 0, 지나면 음수. */
    fun daysLeft(today: LocalDate): Int = today.daysUntil(dischargeDate)

    /** 복무한 날(입대일이 1일째). 입대 전이면 0, 전역 뒤에는 [totalDays] 에서 멈춘다. */
    fun servedDays(today: LocalDate): Int =
        (enlistDate.daysUntil(today) + 1).coerceIn(0, totalDays)

    /** 0.0 ~ 1.0. */
    fun progress(today: LocalDate): Double = servedDays(today).toDouble() / totalDays

    fun rankAt(today: LocalDate): RankStep? = ranks.lastOrNull { it.from <= today }

    fun nextRank(today: LocalDate): RankStep? = ranks.firstOrNull { it.from > today }

    /**
     * 호봉. 지금 계급에서 몇 번째 달인지(첫 달이 1호봉). 계급이 없으면 null.
     */
    fun payStep(today: LocalDate): Int? {
        val current = rankAt(today) ?: return null
        val months = (today.year - current.from.year) * 12 + (today.month.number - current.from.month.number)
        return months + 1
    }
}

/**
 * 규칙 표로 복무 일정을 계산한다.
 *
 * 전역일은 민법 기간 계산을 따른다: 입대일에서 N개월 뒤 "같은 날"의 전날이 끝나는 날이고,
 * 그 달에 같은 날이 없으면(예: 8월 31일 입대 → 2월 31일 없음) 그 달의 말일이 끝나는 날이다.
 *
 * 진급일은 매달 1일이다. 입대한 달을 1개월로 치고, 이병 · 일병 · 상병 기간을 차례로 더한다.
 */
class ServiceCalculator(private val table: ServiceRulesTable = ServiceRulesTable.BUNDLED) {

    fun timeline(service: Service): ServiceTimeline {
        val rule = table.ruleFor(service.branch, service.enlistDate)
        val computed = dischargeDate(service.enlistDate, rule.serviceMonths)
        val discharge = service.dischargeOverride ?: computed
        return ServiceTimeline(
            enlistDate = service.enlistDate,
            dischargeDate = discharge,
            isOverridden = service.dischargeOverride != null,
            ranks = rule.rankMonths?.let { rankSteps(service.enlistDate, it, discharge) }.orEmpty(),
            rulesVersion = table.version,
        )
    }

    internal fun dischargeDate(enlist: LocalDate, months: Int): LocalDate {
        val target = enlist.plus(months, DateTimeUnit.MONTH)
        // plus 는 없는 날을 말일로 당긴다. 날이 당겨졌다면 "같은 날"이 없는 달이므로 말일이 곧 끝나는 날이다.
        return if (target.day != enlist.day) target else target.minus(DatePeriod(days = 1))
    }

    private fun rankSteps(enlist: LocalDate, rankMonths: List<Int>, discharge: LocalDate): List<RankStep> {
        val firstOfEnlistMonth = LocalDate(enlist.year, enlist.month, 1)
        val steps = mutableListOf(RankStep(Rank.PRIVATE, enlist))
        var offset = 0
        val promoted = listOf(Rank.PRIVATE_FIRST_CLASS, Rank.CORPORAL, Rank.SERGEANT)
        rankMonths.forEachIndexed { i, months ->
            offset += months
            val from = firstOfEnlistMonth.plus(offset, DateTimeUnit.MONTH)
            // 수정된 전역일이 진급일보다 이르면 그 계급은 오지 않는다.
            if (from <= discharge) steps += RankStep(promoted[i], from)
        }
        return steps
    }
}
