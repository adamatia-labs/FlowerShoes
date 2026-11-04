package com.sadturtleman.flowershoes.feature.onboarding

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import com.sadturtleman.flowershoes.core.auth.AuthProvider
import com.sadturtleman.flowershoes.core.designsystem.KkotsinTheme
import com.sadturtleman.flowershoes.core.model.AppTheme
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class LoginInteractionTest {
    @Test
    fun `좁은 화면에서 200퍼센트 글자를 확대해도 슬로건과 로그인 버튼을 볼 수 있다`() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                KkotsinTheme(theme = AppTheme.GOMSIN, dark = false) {
                    Box(Modifier.size(320.dp, 640.dp)) {
                        OnboardingScreen(OnboardingState(today = LocalDate(2026, 10, 29))) {}
                    }
                }
            }
        }
        onNodeWithText("편지로 이어지는\n우리 둘만의 달력").performScrollTo().assertIsDisplayed()
        val textBounds = onNodeWithText("편지로 이어지는\n우리 둘만의 달력").getUnclippedBoundsInRoot()
        val viewport = onNodeWithTag("login-content").getUnclippedBoundsInRoot()
        val paper = onNodeWithTag("login-paper").getUnclippedBoundsInRoot()
        assertTrue(textBounds.left >= viewport.left && textBounds.right <= viewport.right && textBounds.top >= viewport.top && textBounds.bottom <= viewport.bottom)
        assertTrue(textBounds.left >= paper.left && textBounds.right <= paper.right && textBounds.top >= paper.top && textBounds.bottom <= paper.bottom)
        onNodeWithText("카카오\n로그인").assertIsDisplayed()
        onNodeWithText("Apple로\n계속하기").assertIsDisplayed()
        onNodeWithText("Google로\n계속하기").assertIsDisplayed()
        val heights = listOf("카카오\n로그인", "Apple로\n계속하기", "Google로\n계속하기")
            .map { onNodeWithText(it).getUnclippedBoundsInRoot().let { bounds -> bounds.bottom - bounds.top } }
        assertEquals(1, heights.distinct().size)
    }

    @Test
    fun `세 로그인 버튼은 각각 해당 제공자를 선택한다`() = runComposeUiTest {
        val selected = mutableListOf<AuthProvider>()
        setContent {
            KkotsinTheme(theme = AppTheme.GOMSIN, dark = false) {
                OnboardingScreen(OnboardingState(today = LocalDate(2026, 10, 29))) { intent ->
                    if (intent is OnboardingIntent.SignIn) selected += intent.provider
                }
            }
        }
        onNodeWithText("카카오 로그인").performClick()
        onNodeWithText("Apple로 계속하기").performClick()
        onNodeWithText("Google로 계속하기").performClick()
        assertEquals(listOf(AuthProvider.KAKAO, AuthProvider.APPLE, AuthProvider.GOOGLE), selected)
    }
}
