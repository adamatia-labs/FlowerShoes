package com.sadturtleman.flowershoes.home.presentation.home

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sadturtleman.flowershoes.common.presentation.ui.component.FlowerErrorView
import com.sadturtleman.flowershoes.common.presentation.ui.component.FlowerHomeTopAppBar
import com.sadturtleman.flowershoes.common.presentation.ui.component.FlowerLoadingView
import com.sadturtleman.flowershoes.common.presentation.ui.preview.PREVIEW_DEVICE
import com.sadturtleman.flowershoes.common.presentation.ui.theme.FlowerShoesTheme
import com.sadturtleman.flowershoes.common.presentation.ui.theme.FlowerTheme
import com.sadturtleman.flowershoes.tti.domain.TtiTimeline
import com.sadturtleman.flowershoes.tti.presentation.TtiDrawnEffect
import com.sadturtleman.flowershoes.tti.presentation.TtiEmptySpanEffect
import com.sadturtleman.flowershoes.tti.presentation.TtiSpanEffect

/**
 * 홈 라우트의 진입점.
 *
 * ViewModel 을 붙이는 얇은 껍데기다. 상태 [HomeViewModel.uiState] 를 읽어 내려보내고
 * 인텐트를 [HomeViewModel.onIntent] 로 올려보내는 두 배선이 전부다.
 * 화면 그리기는 preview 가능한 [HomeView] 가 전담한다.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // TTI 계측. 구간을 여닫는 신호는 상태에서 읽는다 — 화면이 계측 코드를 따로 부르지 않는다.
    TtiSpanEffect(TtiTimeline.BACKEND, running = state is HomeUiState.Loading)
    TtiDrawnEffect(ready = state is HomeUiState.Success)
    // 지금 홈에는 사진처럼 뒤늦게 채워지는 큰 덩어리가 없다. 생기면 그 이미지가 이 구간을 잰다.
    if (state is HomeUiState.Success) {
        TtiEmptySpanEffect(TtiTimeline.BIG_PART_LOADING)
    }

    HomeView(
        state = state,
        onIntent = viewModel::onIntent,
        modifier = modifier,
    )
}

/**
 * 홈 화면.
 *
 * 화면 크롬(상단 앱바)과 [HomeUiState] 분기를 담당하고, 성공 상태의 본문은 [HomeContent] 에 맡긴다.
 * 하단 탭 바는 RootComposable 이 그린다 — 화면은 자기 본문만 그린다.
 *
 * MVI 규약대로 입력은 [HomeUiState] 하나, 출력은 [HomeIntent] 하나로 받는다.
 * 인텐트가 늘어도 이 시그니처는 그대로다.
 */
@Composable
internal fun HomeView(
    state: HomeUiState,
    onIntent: (HomeIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FlowerTheme.colors.bgCanvas),
    ) {
        FlowerHomeTopAppBar()

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                HomeUiState.Loading -> FlowerLoadingView()

                is HomeUiState.Error -> FlowerErrorView(
                    message = state.message,
                    onRetry = { onIntent(HomeIntent.Retry) },
                )

                is HomeUiState.Success -> HomeContent(
                    state = state,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Preview
// ---------------------------------------------------------------------------

@Composable
private fun HomeViewPreviewHost(state: HomeUiState) {
    FlowerShoesTheme {
        HomeView(state = state, onIntent = {})
    }
}

@Preview(name = "홈 · 성공 · Light", device = PREVIEW_DEVICE, showBackground = true)
@Composable
private fun HomeViewSuccessPreview() {
    HomeViewPreviewHost(HomePreviewData.success)
}

@Preview(
    name = "홈 · 성공 · Dark",
    device = PREVIEW_DEVICE,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun HomeViewSuccessDarkPreview() {
    HomeViewPreviewHost(HomePreviewData.success)
}

@Preview(name = "홈 · 배너 없음", device = PREVIEW_DEVICE, showBackground = true)
@Composable
private fun HomeViewNoBannerPreview() {
    HomeViewPreviewHost(HomePreviewData.success.copy(bannerTitle = null))
}

@Preview(name = "홈 · 로딩", device = PREVIEW_DEVICE, showBackground = true)
@Composable
private fun HomeViewLoadingPreview() {
    HomeViewPreviewHost(HomeUiState.Loading)
}

@Preview(name = "홈 · 에러", device = PREVIEW_DEVICE, showBackground = true)
@Composable
private fun HomeViewErrorPreview() {
    HomeViewPreviewHost(HomeUiState.Error("네트워크에 연결할 수 없습니다."))
}

private object HomePreviewData {

    val success = HomeUiState.Success(
        greeting = "꽃신에 오신 것을 환영합니다",
        bannerTitle = "개발 빌드입니다",
    )
}
