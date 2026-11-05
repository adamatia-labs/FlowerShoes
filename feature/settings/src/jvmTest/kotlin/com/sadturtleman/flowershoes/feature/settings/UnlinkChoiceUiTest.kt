package com.sadturtleman.flowershoes.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.sadturtleman.flowershoes.core.designsystem.KkotsinTheme
import com.sadturtleman.flowershoes.core.model.AppTheme
import kotlin.test.Test
import kotlin.test.assertFalse

@OptIn(ExperimentalTestApi::class)
class UnlinkChoiceUiTest {
    @Test
    fun `처리 중에는 기록 보관과 삭제 선택지를 모두 비활성화한다`() = runComposeUiTest {
        var keep by mutableStateOf(true)
        var enabled by mutableStateOf(true)
        setContent { KkotsinTheme(theme = AppTheme.GOMSIN) { KeepChoice(keep, enabled) { keep = it } } }
        onNodeWithText("모두 지우기").assertIsEnabled().performClick()
        runOnIdle { assertFalse(keep); enabled = false }
        onNodeWithText("추억으로 보관").assertIsNotEnabled()
        onNodeWithText("모두 지우기").assertIsNotEnabled()
        runOnIdle { assertFalse(keep) }
    }
}
