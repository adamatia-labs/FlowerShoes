package com.sadturtleman.flowershoes.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import com.sadturtleman.flowershoes.core.model.AppTheme
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class PageSlideTest {
    @Test
    fun `앞으로는 왼쪽으로 뒤로는 오른쪽으로 이동한다`() = runComposeUiTest {
        val page = mutableIntStateOf(0)
        setContent {
            KkotsinTheme(theme = AppTheme.GOMSIN, dark = false) {
                KkPageFlip(page.intValue, pageKey = { it }, isForward = { a, b -> b > a }) {
                    Box(Modifier.fillMaxSize().testTag("page-$it"))
                }
            }
        }
        mainClock.autoAdvance = false
        runOnIdle { page.intValue = 1 }
        mainClock.advanceTimeBy(100)
        assertTrue(onNodeWithTag("page-0").getUnclippedBoundsInRoot().left.value < 0f)
        assertTrue(onNodeWithTag("page-1").getUnclippedBoundsInRoot().left.value > 0f)
        mainClock.advanceTimeBy(500)
        onNodeWithTag("page-0").assertDoesNotExist()
        runOnIdle { page.intValue = 0 }
        mainClock.advanceTimeBy(100)
        assertTrue(onNodeWithTag("page-1").getUnclippedBoundsInRoot().left.value > 0f)
        assertTrue(onNodeWithTag("page-0").getUnclippedBoundsInRoot().left.value < 0f)
        mainClock.advanceTimeBy(500)
        onNodeWithTag("page-1").assertDoesNotExist()
    }

}
