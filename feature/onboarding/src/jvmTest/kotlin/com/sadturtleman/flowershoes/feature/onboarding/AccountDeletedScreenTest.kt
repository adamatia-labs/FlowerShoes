package com.sadturtleman.flowershoes.feature.onboarding

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.sadturtleman.flowershoes.core.designsystem.KkotsinTheme
import com.sadturtleman.flowershoes.core.model.AppTheme
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AccountDeletedScreenTest {
    @Test
    fun `Apple 연결을 확인해야 할 때 탈퇴 완료 화면에 방법을 보여 준다`() = runComposeUiTest {
        var closed = false
        setContent {
            KkotsinTheme(theme = AppTheme.GOMSIN, dark = false) {
                AccountDeletedScreen(showAppleInstructions = true) { closed = true }
            }
        }
        onNodeWithText("기록을 모두 지웠어요").assertExists()
        onNodeWithText("Apple 계정 연결은 직접 확인해 주세요", substring = true).assertExists()
        onNodeWithText("Apple 계정 연결 해제 방법").assertExists()
        onNodeWithText("처음 화면으로").performClick()
        assertTrue(closed)
    }

    @Test
    fun `Apple 연결 확인이 필요하지 않으면 안내를 보이지 않는다`() = runComposeUiTest {
        setContent {
            KkotsinTheme(theme = AppTheme.GOMSIN, dark = false) {
                AccountDeletedScreen(showAppleInstructions = false) {}
            }
        }
        onNodeWithText("Apple 계정 연결은 직접 확인해 주세요", substring = true).assertDoesNotExist()
    }
}
