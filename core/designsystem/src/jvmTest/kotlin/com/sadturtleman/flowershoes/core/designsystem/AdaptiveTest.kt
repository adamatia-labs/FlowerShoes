package com.sadturtleman.flowershoes.core.designsystem

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/** 큰 화면(Figma 11 · 큰 화면): 600dp 부터 Nav Rail, 840dp 부터 2단. */
class AdaptiveTest {

    @Test
    fun `폭으로 창 크기를 나눈다`() {
        assertEquals(KkWindowWidth.Compact, KkWindowWidth.of(360.dp))
        assertEquals(KkWindowWidth.Compact, KkWindowWidth.of(599.dp))
        assertEquals(KkWindowWidth.Medium, KkWindowWidth.of(600.dp))
        assertEquals(KkWindowWidth.Medium, KkWindowWidth.of(839.dp))
        assertEquals(KkWindowWidth.Expanded, KkWindowWidth.of(840.dp))
        assertEquals(KkWindowWidth.Expanded, KkWindowWidth.of(1280.dp))
    }

    @Test
    fun `Nav Rail 은 600dp 부터, 2단은 840dp 부터`() {
        assertEquals(false, KkWindowWidth.Compact.navRail)
        assertEquals(true, KkWindowWidth.Medium.navRail)
        assertEquals(false, KkWindowWidth.Medium.twoPane)
        assertEquals(true, KkWindowWidth.Expanded.twoPane)
    }
}
