package com.sadturtleman.flowershoes.core.jank

import androidx.metrics.performance.FrameData

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

private const val NANOS_PER_MILLI = 1_000_000L
