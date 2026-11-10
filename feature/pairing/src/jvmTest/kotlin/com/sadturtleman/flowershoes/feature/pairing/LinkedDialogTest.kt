package com.sadturtleman.flowershoes.feature.pairing

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.sadturtleman.flowershoes.core.designsystem.KkotsinTheme
import com.sadturtleman.flowershoes.core.model.AppTheme
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class LinkedDialogTest {
    @Test
    fun `연결 안내에는 기록 전송 문구가 없고 시작하기로 닫을 수 있다`() = runComposeUiTest {
        var closed = false
        setContent { KkotsinTheme(theme = AppTheme.GOMSIN, dark = false) { LinkedDialog("민준") { closed = true } } }
        onNodeWithText("민준이랑 연결됐어요").assertExists()
        onNodeWithText("보낼게요", substring = true).assertDoesNotExist()
        onNodeWithText("보내는 중", substring = true).assertDoesNotExist()
        onNodeWithText("시작하기").performClick()
        assertTrue(closed)
    }
}
