package com.sadturtleman.flowershoes.logging.domain

/**
 * 시각을 읽는 포트.
 *
 * TTI 와 달리 단조 시계가 필요 없다 — 구간 길이를 재는 것이 아니라 "언제 일어난 일인가" 만 남기므로
 * 사람이 읽는 벽시계 시각이어야 한다.
 */
fun interface BizLogClock {
    fun nowMillis(): Long
}

/** 기본 시계. 순수 JVM 이라 이 모듈 안에 둔다 — 안드로이드 구현을 따로 꽂을 것이 없다. */
class SystemBizLogClock : BizLogClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
