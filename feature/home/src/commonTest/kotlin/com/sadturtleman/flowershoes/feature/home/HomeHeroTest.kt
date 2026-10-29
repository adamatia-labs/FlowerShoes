package com.sadturtleman.flowershoes.feature.home

import com.sadturtleman.flowershoes.core.military.ServiceCalculator
import com.sadturtleman.flowershoes.core.military.ServiceRulesTable
import com.sadturtleman.flowershoes.core.model.Branch
import com.sadturtleman.flowershoes.core.model.Role
import com.sadturtleman.flowershoes.core.model.Service
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 홈 위쪽 큰 D-day(Figma 2 · 홈 350:686). 예시: 육군 2026.04.20 입대 · 오늘 2026.10.28. */
class HomeHeroTest {
    private val today = LocalDate(2026, 10, 28)
    private val army = ServiceCalculator().timeline(Service("s", Branch.ARMY, LocalDate(2026, 4, 20), ServiceRulesTable.BUNDLED.version))

    @Test fun gomsinSeesPartnerNameWithSubjectParticle() {
        val hero = homeHero(army, Branch.ARMY, today, Role.GOMSIN, myName = "지수", partnerName = "민준")!!
        assertEquals("민준이 전역까지", hero.caption)
        assertEquals("D-356", hero.value)
        assertEquals(HomePhase.SERVING, hero.phase)
        assertEquals("입대 2026.04.20", hero.start)
        assertEquals("전역 2027.10.19", hero.end)
        assertEquals("35.0%", hero.percent)
        assertEquals(18, hero.months)
    }

    @Test fun nameWithoutFinalConsonantTakesNoParticle() {
        assertEquals("현우 전역까지", homeHero(army, Branch.ARMY, today, Role.GOMSIN, "지수", "현우")!!.caption)
    }

    @Test fun soldierSeesOwnCountdownWithoutName() {
        assertEquals("전역까지", homeHero(army, Branch.ARMY, today, Role.SOLDIER, "민준", "지수")!!.caption)
    }

    @Test fun soloSoldierSeesOwnName() {
        assertEquals("민준 전역까지", homeHero(army, Branch.ARMY, today, Role.SOLDIER, "민준", partnerName = null)!!.caption)
    }

    @Test fun soloGomsinWithoutPartnerNameSeesPlainCaption() {
        assertEquals("전역까지", homeHero(army, Branch.ARMY, today, Role.GOMSIN, "지수", partnerName = null)!!.caption)
    }

    @Test fun beforeEnlistmentCountsToEnlistmentWithBranchLine() {
        val air = ServiceCalculator().timeline(Service("s", Branch.AIR_FORCE, LocalDate(2026, 11, 9), ServiceRulesTable.BUNDLED.version))
        val hero = homeHero(air, Branch.AIR_FORCE, today, Role.GOMSIN, "지수", "민준")!!
        assertEquals(HomePhase.BEFORE_ENLIST, hero.phase)
        assertEquals("민준이 입대까지", hero.caption)
        assertEquals("D-12", hero.value)
        assertEquals("공군 · 입대 2026.11.09 (월)", hero.detail)
    }

    @Test fun dischargeDayGreets() {
        val hero = homeHero(army, Branch.ARMY, LocalDate(2027, 10, 19), Role.GOMSIN, "지수", "민준")!!
        assertEquals(HomePhase.DISCHARGE_DAY, hero.phase)
        assertEquals("민준아, 고생했어", hero.caption)
        assertEquals("D-DAY", hero.value)
    }

    @Test fun afterDischargeShowsDaysEnduredTogether() {
        val hero = homeHero(army, Branch.ARMY, LocalDate(2027, 11, 1), Role.GOMSIN, "지수", "민준")!!
        assertEquals(HomePhase.ARCHIVE, hero.phase)
        assertEquals("함께 견딘", hero.caption)
        assertEquals("548일", hero.value)
        assertEquals("입대 2026.04.20 – 전역 2027.10.19 · 보관 모드", hero.detail)
    }

    @Test fun noEnlistDateMeansNoHero() {
        assertNull(homeHero(null, null, today, Role.GOMSIN, "지수", null))
    }
}
