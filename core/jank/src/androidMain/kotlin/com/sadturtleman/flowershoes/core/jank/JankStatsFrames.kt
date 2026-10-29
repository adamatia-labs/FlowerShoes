package com.sadturtleman.flowershoes.core.jank

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.metrics.performance.FrameData
import androidx.metrics.performance.PerformanceMetricsState

/**
 * JankStats 의 프레임을 측정 규칙의 모양으로 옮긴다.
 *
 * 이 한 줄 때문에 [JankReporter] 가 androidx 를 모르고도 버킷과 임계치를 들고 있을 수 있다.
 */
fun FrameData.toJankFrame(): JankFrame = JankFrame(
    isJank = isJank,
    durationMillis = frameDurationUiNanos / NANOS_PER_MILLI,
    states = states.associate { it.key to it.value },
)

/** 꼬리표는 이 화면 계층의 PerformanceMetricsState 에 단다. JankStats 가 다음 프레임부터 함께 돌려준다. */
@Composable
internal actual fun rememberFrameTags(): FrameTags? {
    val view = LocalView.current
    return remember(view) {
        object : FrameTags {
            override fun put(key: String, value: String) {
                PerformanceMetricsState.getHolderForHierarchy(view).state?.putState(key, value)
            }

            override fun remove(key: String) {
                PerformanceMetricsState.getHolderForHierarchy(view).state?.removeState(key)
            }
        }
    }
}

private const val NANOS_PER_MILLI = 1_000_000L
