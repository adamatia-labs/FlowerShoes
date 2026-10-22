package com.sadturtleman.flowershoes.home.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sadturtleman.flowershoes.common.presentation.ui.theme.FlowerTheme
import com.sadturtleman.flowershoes.jank.presentation.JankScrollWatcher

/**
 * 홈 본문.
 *
 * 상태 분기 없이 성공 상태만 그리는 순수 UI 다. 분기는 [HomeView] 가 담당한다.
 */
@Composable
internal fun HomeContent(
    state: HomeUiState.Success,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    // 스크롤이 도는 구간만 따로 센다. 멈추는 순간 그 구간의 프레임 통계가 나간다.
    JankScrollWatcher(scrollState)

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(horizontal = FlowerTheme.spacing.lg, vertical = FlowerTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(FlowerTheme.spacing.lg),
    ) {
        state.bannerTitle?.let { title -> HomeBanner(title = title) }

        Text(
            text = state.greeting,
            style = FlowerTheme.typography.titleSerifL,
            color = FlowerTheme.colors.textPrimary,
        )
    }
}

/** 상단 배너. 문구는 피처 플래그(`home_banner`)에서 온다. */
@Composable
private fun HomeBanner(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        modifier = modifier
            .fillMaxWidth()
            .background(FlowerTheme.colors.bgBrandSubtle, FlowerTheme.shapes.md)
            .padding(FlowerTheme.spacing.lg),
        style = FlowerTheme.typography.bodyM,
        color = FlowerTheme.colors.textBrand,
    )
}
