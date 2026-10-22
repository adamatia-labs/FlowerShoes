package com.sadturtleman.flowershoes.tti.domain

/**
 * 시각을 읽는 포트.
 *
 * 두 시계를 나눠 두는 이유는 쓰임이 다르기 때문이다 — 구간 길이는 뒤로 가지 않는 시계로 재야 하고,
 * "언제 만들어진 기록인가" 는 프로세스가 죽었다 살아나도 이어지는 시계로 봐야 한다.
 *
 * 구현은 플랫폼이 정한다([androidx] 없는 이 모듈이 android.os.SystemClock 을 부를 수 없다).
 */
interface TtiClock {

    /**
     * 구간 길이를 재는 단조 시각(ms).
     *
     * 사용자가 시각을 바꾸거나 NTP 가 시계를 당겨도 영향을 받지 않아야 한다 —
     * 그러지 않으면 측정값이 음수로도 나온다.
     */
    fun elapsedMillis(): Long

    /** 오래 남은 미완성 기록을 가려내는 데만 쓰는 벽시계 시각(ms). */
    fun wallTimeMillis(): Long
}
