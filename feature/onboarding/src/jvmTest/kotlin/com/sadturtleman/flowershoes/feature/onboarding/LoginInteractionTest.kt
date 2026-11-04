package com.sadturtleman.flowershoes.feature.onboarding

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.sadturtleman.flowershoes.core.auth.AuthProvider
import com.sadturtleman.flowershoes.core.designsystem.KkotsinTheme
import com.sadturtleman.flowershoes.core.model.AppTheme
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class LoginInteractionTest {
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
