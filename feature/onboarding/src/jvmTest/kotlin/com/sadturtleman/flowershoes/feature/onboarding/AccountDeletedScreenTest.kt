package com.sadturtleman.flowershoes.feature.onboarding

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performScrollTo
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
    fun `작은 화면에서 글자를 200퍼센트 확대해도 Apple 도움말과 완료 버튼을 사용할 수 있다`() = runComposeUiTest {
        var opened = false
        var closed = false
        setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f, 2f),
                LocalUriHandler provides object : UriHandler {
                    override fun openUri(uri: String) { opened = uri == "https://support.apple.com/ko-kr/102571" }
                },
            ) {
                KkotsinTheme(theme = AppTheme.GOMSIN, dark = false) {
                    Box(Modifier.size(640.dp, 320.dp)) {
                        AccountDeletedScreen(true) { closed = true }
                    }
                }
            }
        }
        onNodeWithText("Apple 계정 연결 해제 방법").performScrollTo().assertIsDisplayed().performClick()
        onNodeWithText("처음 화면으로").assertIsDisplayed().performClick()
        assertTrue(opened)
        assertTrue(closed)
    }

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
