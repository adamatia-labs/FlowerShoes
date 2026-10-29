package com.sadturtleman.flowershoes.core.jank

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.metrics.performance.PerformanceMetricsState

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
