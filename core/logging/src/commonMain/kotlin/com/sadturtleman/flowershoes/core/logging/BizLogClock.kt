package com.sadturtleman.flowershoes.core.logging

import kotlin.time.Clock

/**
 * 시각을 읽는 포트.
 *
 * TTI 와 달리 단조 시계가 필요 없다 — 구간 길이를 재는 것이 아니라 "언제 일어난 일인가" 만 남기므로
 * 사람이 읽는 벽시계 시각이어야 한다.
 */
fun interface BizLogClock {
    fun nowMillis(): Long
}

/** 기본 시계. 플랫폼마다 따로 꽂을 것이 없어 이 모듈 안에 둔다. */
class SystemBizLogClock : BizLogClock {
    override fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
}
