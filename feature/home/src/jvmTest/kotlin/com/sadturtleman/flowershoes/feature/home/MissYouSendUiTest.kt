package com.sadturtleman.flowershoes.feature.home

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.sadturtleman.flowershoes.core.designsystem.KkotsinTheme
import com.sadturtleman.flowershoes.core.model.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class MissYouSendUiTest {
    @Test
    fun `저장 결과를 받기 전에는 보냈다고 표시하지 않는다`() = runComposeUiTest {
        var requested = 0
        setContent {
            KkotsinTheme(theme = AppTheme.GOMSIN) {
                HomeScreen(HomeState(loading = false, linked = true), { if (it == HomeIntent.SendMissYou) requested++ }, {})
            }
        }
        onNodeWithText("보고싶어").performClick()
        runOnIdle { assertEquals(1, requested) }
        onAllNodesWithText("보냈어요").assertCountEquals(0)
        onAllNodesWithText("전할게요").assertCountEquals(0)
    }
    @Test
    fun `저장 성공과 실패를 실제 홈 화면에 구분해 표시한다`() = runComposeUiTest {
        var state by mutableStateOf(HomeState(loading = false, linked = true, partnerName = "연인"))
        var requests = 0
        setContent { KkotsinTheme(theme = AppTheme.GOMSIN) { HomeScreen(state, { if (it == HomeIntent.SendMissYou) requests++ }, {}) } }
        onNodeWithText("보고싶어").assertIsEnabled().performClick()
        runOnIdle { assertEquals(1, requests); state = state.copy(missYouStatus = MissYouSendStatus.SAVING) }
        onNodeWithText("보고싶어").assertIsNotEnabled()
        onAllNodesWithText("보냈어요").assertCountEquals(0)
        runOnIdle { state = state.copy(missYouStatus = MissYouSendStatus.FAILED) }
        assertEquals(LiveRegionMode.Polite, onNodeWithText("보내지 못했어요. 잠시 후 다시 시도해 주세요").fetchSemanticsNode().config[SemanticsProperties.LiveRegion])
        onAllNodesWithText("보냈어요").assertCountEquals(0)
        runOnIdle { state = state.copy(missYouStatus = MissYouSendStatus.IDLE) }
        onNodeWithText("보고싶어").assertIsEnabled().performClick()
        runOnIdle { assertEquals(2, requests); state = state.copy(missYouStatus = MissYouSendStatus.SAVED) }
        onAllNodesWithText("보냈어요").assertCountEquals(0)
        onNodeWithText("전할게요").assertIsNotEnabled()
        assertEquals(LiveRegionMode.Polite, onNodeWithText("연인에게 보고싶다고 전할게요").fetchSemanticsNode().config[SemanticsProperties.LiveRegion])
    }

}
