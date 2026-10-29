package com.sadturtleman.flowershoes.core.designsystem

import com.sadturtleman.flowershoes.core.model.Role
import kotlin.test.Test
import kotlin.test.assertEquals

/** 조합 컴포넌트의 규칙: 소인 문구(design.md 화면 규칙) · 복무 진행 개월 칸. */
class PaperComponentsTest {

    @Test
    fun `군인이 보낸 편지 소인은 군사우편이다`() {
        val ink = KkPostmarkInk.of(sender = Role.SOLDIER)
        assertEquals(KkPostmarkInk.Military, ink)
        assertEquals("군사우편", ink.label)
    }

    @Test
    fun `곰신이 보낸 편지 소인은 꽃신 우편이다`() {
        val ink = KkPostmarkInk.of(sender = Role.GOMSIN)
        assertEquals(KkPostmarkInk.Flower, ink)
        assertEquals("꽃신 우편", ink.label)
    }

    @Test
    fun `개월 칸은 지금 지나고 있는 달까지 채운다`() {
        // Figma Service Progress · Months: 21개월 중 42.3% → 9칸.
        assertEquals(9, filledMonths(progress = 0.423f, months = 21))
        assertEquals(0, filledMonths(progress = 0f, months = 18))
        assertEquals(1, filledMonths(progress = 0.01f, months = 18))
        assertEquals(18, filledMonths(progress = 1f, months = 18))
    }

    @Test
    fun `진행률이 범위를 벗어나도 칸 수를 넘지 않는다`() {
        assertEquals(18, filledMonths(progress = 1.4f, months = 18))
        assertEquals(0, filledMonths(progress = -0.2f, months = 18))
        assertEquals(0, filledMonths(progress = 0.5f, months = 0))
    }
}
