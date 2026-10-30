package com.sadturtleman.flowershoes.feature.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate

class AnniversaryLabelsTest {

    @Test
    fun `자동으로 챙겨요는 지난 날 둘과 다가올 날 둘`() {
        // Figma 8 · 기념일 · 목록: 사귄 날 2025.09.13, 오늘 2026.10.28.
        val list = anniversaryMilestones(LocalDate(2025, 9, 13), LocalDate(2026, 10, 28))
        assertEquals(
            listOf(
                "100일" to LocalDate(2025, 12, 21),
                "1주년" to LocalDate(2026, 9, 13),
                "500일" to LocalDate(2027, 1, 25),
                "2주년" to LocalDate(2027, 9, 13),
            ),
            list.map { it.label to it.date },
        )
    }

    @Test
    fun `사귄 지 얼마 안 됐으면 다가올 날만`() {
        val list = anniversaryMilestones(LocalDate(2026, 10, 1), LocalDate(2026, 10, 28))
        assertEquals(listOf("100일", "1주년"), list.map { it.label })
    }

    @Test
    fun `1000일 단위도 챙긴다`() {
        val list = anniversaryMilestones(LocalDate(2023, 1, 1), LocalDate(2025, 9, 1))
        assertEquals(listOf("500일", "2주년", "1000일", "3주년"), list.map { it.label })
    }

    @Test
    fun `오늘인 날은 다가올 날로 센다`() {
        val list = anniversaryMilestones(LocalDate(2025, 9, 13), LocalDate(2026, 9, 13))
        assertEquals(listOf("100일", "1주년", "500일"), list.map { it.label }.take(3))
    }

    @Test
    fun `기념일 줄은 사귄 날과 직접 추가한 날 개수`() {
        assertEquals("3개", anniversaryCountLabel(coupleSet = true, custom = 2))
        assertEquals("1개", anniversaryCountLabel(coupleSet = false, custom = 1))
        assertEquals("정하기", anniversaryCountLabel(coupleSet = false, custom = 0))
    }

    @Test
    fun `직접 추가한 날은 매년이면 매년 M월 D일 아니면 날짜`() {
        assertEquals("매년 1월 7일", customAnniversaryLabel(LocalDate(2001, 1, 7), repeats = true))
        assertEquals("2025.08.30", customAnniversaryLabel(LocalDate(2025, 8, 30), repeats = false))
    }
}
