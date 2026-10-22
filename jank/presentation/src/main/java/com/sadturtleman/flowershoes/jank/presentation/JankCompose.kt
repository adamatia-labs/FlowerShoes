package com.sadturtleman.flowershoes.jank.presentation

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.metrics.performance.FrameData
import androidx.metrics.performance.PerformanceMetricsState
import com.sadturtleman.flowershoes.jank.domain.JankFrame
import com.sadturtleman.flowershoes.jank.domain.JankReporter
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 화면 트리에 내려주는 집계기. Activity 가 주입받은 것을 그대로 꽂는다.
 * 꽂히지 않았으면 아래 helper 들이 모두 아무 일도 하지 않는다(계측이 화면을 막지 않는다).
 */
val LocalJankReporter = staticCompositionLocalOf<JankReporter?> { null }

/**
 * 지금 보고 있는 화면의 이름을 JankStats 에 등록하고, 떠날 때 그 화면의 통계를 내보낸다.
 *
 * 라우팅 테이블에서 화면을 감싸는 것이 기본 자리다 — TTI 의 TtiPage 와 같은 지점이라
 * 두 지표가 같은 화면 이름으로 묶인다.
 */
@Composable
fun JankPage(pageName: String) {
    val view = LocalView.current
    val reporter = LocalJankReporter.current ?: return
    DisposableEffect(view, pageName) {
        // 프레임마다 딸려 오는 꼬리표. 어느 화면에서 끊겼는지는 이 값으로 갈린다.
        val holder = PerformanceMetricsState.getHolderForHierarchy(view)
        holder.state?.putState(STATE_PAGE, pageName)
        reporter.onPageEnter(pageName)
        onDispose {
            holder.state?.removeState(STATE_PAGE)
            reporter.onPageExit(pageName)
        }
    }
}

/**
 * 스크롤이 도는 동안만 따로 센다. 멈추는 순간 그 구간의 통계가 나간다.
 *
 * 목록 상태를 그대로 넘기면 된다. 스크롤 구간을 나눠 두는 이유는 [JankReporter] 에 적어 두었다 —
 * 머문 시간이 섞이면 목록이 무거운 것이 평균에 묻힌다.
 */
@Composable
fun JankScrollWatcher(scrollableState: ScrollableState) {
    val view = LocalView.current
    val reporter = LocalJankReporter.current ?: return
    LaunchedEffect(scrollableState, view, reporter) {
        snapshotFlow { scrollableState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { isScrolling ->
                val state = PerformanceMetricsState.getHolderForHierarchy(view).state
                if (isScrolling) {
                    state?.putState(STATE_SCROLLING, "true")
                    reporter.onScrollStart()
                } else {
                    state?.removeState(STATE_SCROLLING)
                    reporter.onScrollEnd()
                }
            }
    }
}

/**
 * JankStats 의 프레임을 도메인 모양으로 옮긴다.
 *
 * 이 한 줄 때문에 :jank:domain 이 androidx 를 모르고도 버킷과 임계치를 들고 있을 수 있다.
 */
fun FrameData.toJankFrame(): JankFrame = JankFrame(
    isJank = isJank,
    durationMillis = frameDurationUiNanos / NANOS_PER_MILLI,
    states = states.associate { it.key to it.value },
)

private const val NANOS_PER_MILLI = 1_000_000L
private const val STATE_PAGE = "page"
private const val STATE_SCROLLING = "scrolling"
