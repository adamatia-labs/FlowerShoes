package com.sadturtleman.flowershoes.core.jank

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 화면 트리에 내려주는 집계기. Android 는 Activity 가, iOS 는 MainViewController 가 Koin 에서 꺼내 꽂는다.
 * 꽂히지 않았으면 아래 helper 들이 모두 아무 일도 하지 않는다(계측이 화면을 막지 않는다).
 */
val LocalJankReporter = staticCompositionLocalOf<JankReporter?> { null }

/**
 * 지금 보고 있는 화면의 이름을 집계기와 프레임 꼬리표에 등록하고, 떠날 때 그 화면의 통계를 내보낸다.
 *
 * 라우팅 테이블에서 화면을 감싸는 것이 기본 자리다 — TTI 의 TtiPage 와 같은 지점이라
 * 두 지표가 같은 화면 이름으로 묶인다.
 */
@Composable
fun JankPage(pageName: String) {
    val reporter = LocalJankReporter.current ?: return
    val tags = rememberFrameTags()
    DisposableEffect(tags, pageName) {
        // 프레임마다 딸려 오는 꼬리표. 어느 화면에서 끊겼는지는 이 값으로 갈린다.
        tags?.put(STATE_PAGE, pageName)
        reporter.onPageEnter(pageName)
        onDispose {
            tags?.remove(STATE_PAGE)
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
    val reporter = LocalJankReporter.current ?: return
    val tags = rememberFrameTags()
    LaunchedEffect(scrollableState, tags, reporter) {
        snapshotFlow { scrollableState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { isScrolling ->
                if (isScrolling) {
                    tags?.put(STATE_SCROLLING, "true")
                    reporter.onScrollStart()
                } else {
                    tags?.remove(STATE_SCROLLING)
                    reporter.onScrollEnd()
                }
            }
    }
}

/**
 * 프레임에 꼬리표(page · scrolling)를 다는 곳.
 *
 * Android 는 JankStats 의 PerformanceMetricsState 에 단다 — 프레임과 함께 돌아와 [JankFrame.states] 가 된다.
 * iOS 는 null 이다. CADisplayLink 의 프레임에는 실을 자리가 없고, 화면 이름은 [JankReporter] 가 이미 들고 있다.
 */
internal interface FrameTags {
    fun put(key: String, value: String)
    fun remove(key: String)
}

@Composable
internal expect fun rememberFrameTags(): FrameTags?

private const val STATE_PAGE = "page"
private const val STATE_SCROLLING = "scrolling"
