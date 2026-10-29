package com.sadturtleman.flowershoes.feature.home

import com.sadturtleman.flowershoes.core.military.Rank
import com.sadturtleman.flowershoes.core.military.RankStep
import com.sadturtleman.flowershoes.core.military.ServiceTimeline
import com.sadturtleman.flowershoes.core.model.Role
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** 홈 머리말(날짜 · 제목)과 혼자 모드 군인의 진급 일정. */
class HomeHeaderTest {
    private val today = LocalDate(2026, 10, 28)

    @Test fun dateLineHasMonthDayAndWeekday() {
        assertEquals("10월 28일 수요일", dateLine(today))
    }

    @Test fun linkedTitlePutsMeFirst() {
        assertEquals("지수 & 민준", homeTitle(linked = true, Role.GOMSIN, "지수", "민준"))
        assertEquals("민준 & 지수", homeTitle(linked = true, Role.SOLDIER, "민준", "지수"))
    }

    @Test fun soloTitleFollowsRole() {
        assertEquals("지수의 꽃신", homeTitle(linked = false, Role.GOMSIN, "지수", "연인"))
        assertEquals("전역일 계산기", homeTitle(linked = false, Role.SOLDIER, "민준", "연인"))
    }

    @Test fun soloSoldierSeesPromotionScheduleWithExpectedRanks() {
        val t = ServiceTimeline(
            LocalDate(2026, 4, 20), LocalDate(2028, 2, 19), false,
            listOf(
                RankStep(Rank.PRIVATE, LocalDate(2026, 4, 20)),
                RankStep(Rank.PRIVATE_FIRST_CLASS, LocalDate(2026, 6, 20)),
                RankStep(Rank.CORPORAL, LocalDate(2026, 12, 20)),
                RankStep(Rank.SERGEANT, LocalDate(2027, 6, 20)),
            ),
            2,
        )
        assertEquals(
            listOf(
                RankRow("일병", "2026.06.20 · 지남"),
                RankRow("상병(예정)", "2026.12.20 · D-53"),
                RankRow("병장(예정)", "2027.06.20 · D-235"),
            ),
            rankRows(t, today),
        )
    }
}
