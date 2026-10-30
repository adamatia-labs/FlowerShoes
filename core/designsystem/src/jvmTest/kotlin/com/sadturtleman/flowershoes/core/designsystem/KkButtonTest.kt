package com.sadturtleman.flowershoes.core.designsystem

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.sadturtleman.flowershoes.core.model.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** 버튼(Figma Button `340:154`) 규칙: 위험한 동작은 빨강, 터치 영역은 48dp 이상(design.md 접근성). */
@OptIn(ExperimentalTestApi::class)
class KkButtonTest {
    private val c = kkColors(AppTheme.GOMSIN, dark = false)

    @Test
    fun `위험한 Secondary 버튼은 글자와 테두리가 빨강이다`() {
        val look = kkButtonLook(c, KkButtonStyle.Secondary, danger = true, enabled = true, pressed = false)
        assertEquals(c.danger, look.fg)
        assertEquals(c.danger, look.border)
    }

    @Test
    fun `보통 Secondary 버튼은 ink 글자에 line 테두리다`() {
        val look = kkButtonLook(c, KkButtonStyle.Secondary, danger = false, enabled = true, pressed = false)
        assertEquals(c.ink, look.fg)
        assertEquals(c.line, look.border)
    }

    @Test
    fun `위험한 Primary 버튼은 바탕이 빨강이다`() {
        val look = kkButtonLook(c, KkButtonStyle.Primary, danger = true, enabled = true, pressed = false)
        assertEquals(c.danger, look.bg)
        assertEquals(c.canvas, look.fg)
    }

    @Test
    fun `Text 버튼은 테두리 없이 accent 글자다`() {
        val look = kkButtonLook(c, KkButtonStyle.Text, danger = false, enabled = true, pressed = false)
        assertEquals(c.accent, look.fg)
        assertEquals(null, look.border)
    }

    @Test
    fun `M 크기 버튼도 터치 영역은 48dp 이상이다`() = runComposeUiTest {
        setContent {
            KkotsinTheme(theme = AppTheme.GOMSIN, dark = false) {
                KkButton("취소", {}, style = KkButtonStyle.Secondary, size = KkButtonSize.M)
            }
        }
        onNodeWithText("취소").assertHeightIsAtLeast(48.dp)
    }
}
