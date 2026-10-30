package com.sadturtleman.flowershoes.feature.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class LifetimeBenefitsTest {

    @Test
    fun `꽃신 평생 혜택은 planning 6 대로 글꼴 7종 · 편지지 5종`() {
        assertEquals(
            listOf("광고 제거", "손글씨 글꼴 7종", "편지지 5종", "펜 · 색 · 위젯 꾸미기", "연결된 민준도 함께"),
            lifetimeBenefits("민준"),
        )
    }
}
