package com.sadturtleman.flowershoes.core.designsystem

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.sadturtleman.flowershoes.core.model.AppTheme
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class PageStateTest {
    @Test
    fun `동작 줄이기에서도 화면이 바뀌고 이전 상태를 복원한다`() = runComposeUiTest(
        effectContext = object : MotionDurationScale { override val scaleFactor = 0f },
    ) {
        val page = mutableIntStateOf(0)
        setContent {
            KkotsinTheme(theme = AppTheme.GOMSIN, dark = false) {
                KkPageFlip(page.intValue, pageKey = { it }, isForward = { a, b -> b > a }) { current ->
                    val count = rememberSaveable { mutableIntStateOf(0) }
                    KkButton("$current-${count.intValue}", { count.intValue++ })
                }
            }
        }
        onNodeWithText("0-0").performClick()
        runOnIdle { page.intValue = 1 }
        onNodeWithText("1-0").assertExists()
        onNodeWithText("0-1").assertDoesNotExist()
        runOnIdle { page.intValue = 0 }
        onNodeWithText("0-1").assertExists()
        onNodeWithText("1-0").assertDoesNotExist()
    }

    @Test
    fun `다른 화면에 갔다가 돌아와도 각 화면의 상태를 보존한다`() = runComposeUiTest {
        val page = mutableIntStateOf(0)
        setContent {
            KkotsinTheme(theme = AppTheme.GOMSIN, dark = false) {
                KkPageFlip(page.intValue, pageKey = { it }, isForward = { a, b -> b > a }) { current ->
                    val count = rememberSaveable { mutableIntStateOf(0) }
                    KkButton("$current-${count.intValue}", { count.intValue++ })
                }
            }
        }
        onNodeWithText("0-0").performClick()
        onNodeWithText("0-1").assertExists()
        mainClock.autoAdvance = false
        runOnIdle { page.intValue = 1 }
        mainClock.advanceTimeBy(500)
        onNodeWithText("1-0").performClick()
        runOnIdle { page.intValue = 0 }
        mainClock.advanceTimeBy(500)
        onNodeWithText("0-1").assertExists()
        runOnIdle { page.intValue = 1 }
        mainClock.advanceTimeBy(500)
        onNodeWithText("1-1").assertExists()
    }

    @Test
    fun `전환 도중 바로 돌아와도 이전 화면의 상태를 보존한다`() = runComposeUiTest {
        val page = mutableIntStateOf(0)
        setContent {
            KkotsinTheme(theme = AppTheme.GOMSIN, dark = false) {
                KkPageFlip(page.intValue, pageKey = { it }, isForward = { a, b -> b > a }) { current ->
                    val count = rememberSaveable { mutableIntStateOf(0) }
                    KkButton("$current-${count.intValue}", { count.intValue++ })
                }
            }
        }
        onNodeWithText("0-0").performClick()
        mainClock.autoAdvance = false
        runOnIdle { page.intValue = 1 }
        mainClock.advanceTimeBy(60)
        runOnIdle { page.intValue = 0 }
        mainClock.advanceTimeBy(500)
        onNodeWithText("0-1").assertExists()
    }
}
