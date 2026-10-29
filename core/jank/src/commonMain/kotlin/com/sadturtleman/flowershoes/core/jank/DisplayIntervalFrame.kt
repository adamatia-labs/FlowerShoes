package com.sadturtleman.flowershoes.core.jank

import kotlin.math.roundToLong

/**
 * 표시 간격 하나를 프레임 한 장으로 옮긴다. iOS 의 CADisplayLink 처럼 "언제 화면에 실렸는가" 만 아는 소스가 쓴다.
 *
 * 제때 실린 프레임은 직전 콜백이 예고한 시각(targetTimestamp)에 실린다. 그보다 반 프레임 넘게 늦었다면
 * 그 사이 수직 동기를 한 번 이상 놓친 것이다 — Apple 이 드롭 판정식을 문서로 주지 않아 통상의 추론을 따른다.
 * 기준을 16ms 로 박지 않고 예고된 간격으로 보는 것은 120Hz 기기(ProMotion)에서 한 장씩 놓치는 것도 잡기 위해서다.
 *
 * @param intervalMillis 직전 프레임이 실린 뒤 이번 프레임이 실리기까지.
 * @param expectedIntervalMillis 직전 콜백이 예고한 간격(targetTimestamp - timestamp).
 */
fun displayIntervalFrame(
    intervalMillis: Double,
    expectedIntervalMillis: Double,
    states: Map<String, String> = emptyMap(),
): JankFrame = JankFrame(
    isJank = intervalMillis > expectedIntervalMillis * LATE_FACTOR,
    durationMillis = intervalMillis.roundToLong(),
    states = states,
)

/** 예고된 간격의 1.5 배. 콜백 시각의 흔들림은 넘기고, 수직 동기 한 번을 놓친 것(2 배)은 잡는다. */
private const val LATE_FACTOR = 1.5
