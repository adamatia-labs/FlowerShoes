package com.sadturtleman.flowershoes.core.military

import com.sadturtleman.flowershoes.core.model.Branch
import com.sadturtleman.flowershoes.core.model.Service
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServiceCalculatorTest {

    private val calculator = ServiceCalculator()

    private fun service(branch: Branch, enlist: String, override: String? = null) = Service(
        userId = "u",
        branch = branch,
        enlistDate = LocalDate.parse(enlist),
        rulesVersion = 1,
        dischargeOverride = override?.let(LocalDate::parse),
    )

    @Test
    fun `육군 18개월은 같은 날의 전날에 끝난다`() {
        val t = calculator.timeline(service(Branch.ARMY, "2024-01-15"))
        assertEquals(LocalDate.parse("2025-07-14"), t.dischargeDate)
    }

    @Test
    fun `같은 날이 없는 달이면 그 달 말일에 끝난다`() {
        // 2024-08-31 + 18개월 = 2026-02-31 은 없다 → 2026-02-28
        val t = calculator.timeline(service(Branch.ARMY, "2024-08-31"))
        assertEquals(LocalDate.parse("2026-02-28"), t.dischargeDate)
    }

    @Test
    fun `군종마다 복무 기간이 다르다`() {
        assertEquals(LocalDate.parse("2025-09-14"), calculator.timeline(service(Branch.NAVY, "2024-01-15")).dischargeDate)
        assertEquals(LocalDate.parse("2025-10-14"), calculator.timeline(service(Branch.AIR_FORCE, "2024-01-15")).dischargeDate)
    }

    @Test
    fun `공군은 21개월 복무하고 병장으로 7개월째에 전역한다`() {
        // 국방부 조견표: 공군 21개월(2021-12-14 단축 완료). 병장 기간 = 21 − (2 + 6 + 6) = 7개월.
        val t = calculator.timeline(service(Branch.AIR_FORCE, "2024-01-15"))
        assertEquals(LocalDate.parse("2025-10-14"), t.dischargeDate)
        assertEquals(
            listOf("2024-01-15", "2024-03-01", "2024-09-01", "2025-03-01").map(LocalDate::parse),
            t.ranks.map { it.from },
        )
        assertEquals(Rank.SERGEANT, t.rankAt(t.dischargeDate)?.rank)
        // 진급한 달을 1호봉으로 치므로 병장 3월 ~ 10월 = 8호봉에서 전역한다(22개월이면 9호봉).
        assertEquals(8, t.payStep(t.dischargeDate))
    }

    @Test
    fun `진급일은 입대한 달을 1개월로 치고 매달 1일이다`() {
        val t = calculator.timeline(service(Branch.ARMY, "2024-01-15"))
        assertEquals(
            listOf("2024-01-15", "2024-03-01", "2024-09-01", "2025-03-01").map(LocalDate::parse),
            t.ranks.map { it.from },
        )
        assertEquals(Rank.CORPORAL, t.rankAt(LocalDate.parse("2024-12-25"))?.rank)
        assertEquals(Rank.SERGEANT, t.nextRank(LocalDate.parse("2024-12-25"))?.rank)
    }

    @Test
    fun `계급은 진급 누락을 모르니 예정으로 보여 준다`() {
        val t = calculator.timeline(service(Branch.ARMY, "2024-01-15"))
        val today = LocalDate.parse("2024-12-25")
        assertEquals("상병(예정)", t.rankAt(today)?.rank?.expectedName)
        assertEquals("병장(예정)", t.nextRank(today)?.rank?.expectedName)
        assertEquals("병장 진급 예정", t.nextRank(today)?.rank?.promotionName)
    }

    @Test
    fun `호봉은 지금 계급의 몇 번째 달인지다`() {
        val t = calculator.timeline(service(Branch.ARMY, "2024-01-15"))
        assertEquals(1, t.payStep(LocalDate.parse("2024-09-01")))
        assertEquals(4, t.payStep(LocalDate.parse("2024-12-31")))
    }

    @Test
    fun `사용자가 고친 전역일이 계산값보다 우선한다`() {
        val t = calculator.timeline(service(Branch.ARMY, "2024-01-15", override = "2025-02-20"))
        assertEquals(LocalDate.parse("2025-02-20"), t.dischargeDate)
        assertTrue(t.isOverridden)
        // 병장 진급일(2025-03-01)이 전역일보다 늦으니 병장은 없다.
        assertEquals(Rank.CORPORAL, t.ranks.last().rank)
    }

    @Test
    fun `사회복무요원은 계급이 없다`() {
        val t = calculator.timeline(service(Branch.SOCIAL_SERVICE, "2024-01-15"))
        assertTrue(t.ranks.isEmpty())
        assertNull(t.payStep(LocalDate.parse("2024-06-01")))
    }

    @Test
    fun `D-day 와 진행률`() {
        val t = calculator.timeline(service(Branch.ARMY, "2024-01-15"))
        assertEquals(0, t.daysLeft(t.dischargeDate))
        assertEquals(1, t.servedDays(t.enlistDate))
        assertEquals(0, t.servedDays(LocalDate.parse("2024-01-01")))
        assertEquals(1.0, t.progress(LocalDate.parse("2030-01-01")))
    }

    @Test
    fun `시행일 이후 규칙 중 가장 최근 것을 쓴다`() {
        val table = ServiceRulesTable(
            version = 3,
            rules = listOf(
                ServiceRule(Branch.ARMY, LocalDate.parse("2020-06-16"), 18, listOf(2, 6, 6)),
                ServiceRule(Branch.ARMY, LocalDate.parse("2027-01-01"), 15, listOf(2, 5, 5)),
            ),
        )
        assertEquals(18, table.ruleFor(Branch.ARMY, LocalDate.parse("2026-12-31")).serviceMonths)
        assertEquals(15, table.ruleFor(Branch.ARMY, LocalDate.parse("2027-01-01")).serviceMonths)
        assertEquals(3, ServiceRulesTable.BUNDLED.newer(table).version)
        assertEquals(2, ServiceRulesTable.BUNDLED.newer(ServiceRulesTable.BUNDLED.copy(version = 0)).version)
    }
}
