package com.sadturtleman.flowershoes.core.military

import com.sadturtleman.flowershoes.core.model.Branch
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 군종 하나의 복무 규칙. [effectiveFrom] 이후 입대한 사람에게 적용된다.
 *
 * [rankMonths] 는 이병 · 일병 · 상병으로 머무는 개월 수다(병장은 나머지 전부).
 * 계급이 없는 복무(사회복무요원)는 null 이다.
 */
@Serializable
data class ServiceRule(
    val branch: Branch,
    val effectiveFrom: LocalDate,
    val serviceMonths: Int,
    val rankMonths: List<Int>? = null,
) {
    init {
        require(serviceMonths > 0)
        require(rankMonths == null || (rankMonths.size == 3 && rankMonths.sum() < serviceMonths)) {
            "이병·일병·상병 기간의 합은 복무 기간보다 짧아야 한다"
        }
    }
}

/**
 * 복무 규칙 표. 앱에 번들된 값으로 시작하고, 서명된 원격 표가 더 높은 [version] 이면 그것으로 바꾼다.
 *
 * 복무 기간과 진급 기준은 정책으로 바뀌므로 코드가 아니라 데이터로 둔다.
 */
@Serializable
data class ServiceRulesTable(
    val version: Int,
    val rules: List<ServiceRule>,
) {

    /**
     * [enlistDate] 에 입대한 사람에게 적용할 규칙. 그날까지 시행된 규칙 중 가장 최근 것이다.
     * 표보다 이른 입대일이면 가장 오래된 규칙을 쓴다(이미 전역했을 가능성이 높아 근사로 충분하다).
     */
    fun ruleFor(branch: Branch, enlistDate: LocalDate): ServiceRule {
        val candidates = rules.filter { it.branch == branch }.sortedBy { it.effectiveFrom }
        require(candidates.isNotEmpty()) { "$branch 규칙이 없다" }
        return candidates.lastOrNull { it.effectiveFrom <= enlistDate } ?: candidates.first()
    }

    /** 원격 표가 더 새것일 때만 바꾼다. 서명 검증은 받는 쪽(:core:crypto)이 먼저 끝낸 뒤 부른다. */
    fun newer(other: ServiceRulesTable): ServiceRulesTable = if (other.version > version) other else this

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): ServiceRulesTable = json.decodeFromString(serializer(), text)

        /**
         * 앱에 들어가는 기본 표.
         *
         * 2018년 복무기간 단축이 끝난 뒤의 기간이다. 출시 전에 병무청 공지와 다시 대조할 것 —
         * 틀려도 사용자가 전역일을 직접 고칠 수 있지만(dischargeOverride), 기본값이 맞아야 한다.
         */
        val BUNDLED: ServiceRulesTable = parse(
            """
            {
              "version": 1,
              "rules": [
                { "branch": "ARMY",           "effectiveFrom": "2020-06-16", "serviceMonths": 18, "rankMonths": [2, 6, 6] },
                { "branch": "MARINE",         "effectiveFrom": "2020-06-16", "serviceMonths": 18, "rankMonths": [2, 6, 6] },
                { "branch": "NAVY",           "effectiveFrom": "2020-06-16", "serviceMonths": 20, "rankMonths": [2, 6, 6] },
                { "branch": "AIR_FORCE",      "effectiveFrom": "2020-06-16", "serviceMonths": 22, "rankMonths": [2, 6, 6] },
                { "branch": "SOCIAL_SERVICE", "effectiveFrom": "2020-06-16", "serviceMonths": 21 }
              ]
            }
            """.trimIndent(),
        )
    }
}
