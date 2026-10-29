package com.sadturtleman.flowershoes.core.tti

import kotlin.time.Clock

/**
 * 시각을 읽는 포트.
 *
 * 두 시계를 나눠 두는 이유는 쓰임이 다르기 때문이다 — 구간 길이는 뒤로 가지 않는 시계로 재야 하고,
 * "언제 만들어진 기록인가" 는 프로세스가 죽었다 살아나도 이어지는 시계로 봐야 한다.
 *
 * 포트로 두는 것은 테스트가 시계를 손으로 감기 위해서다. 실제 시계는 [SystemTtiClock] 하나다.
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

/**
 * 기본 시계. 구간은 부팅 기준 시계([bootElapsedMillis]), 기록 시각은 벽시계로 잰다.
 *
 * (부팅 기준 시계는 기기가 잠들어도 계속 흐르므로, 백그라운드로 내려간 화면의 구간은 길게 잡힌다.
 *  그런 기록은 대개 완성되지 않은 채 오래돼 정리된다)
 */
class SystemTtiClock : TtiClock {

    override fun elapsedMillis(): Long = bootElapsedMillis()

    override fun wallTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()
}

/**
 * 부팅 뒤 흐른 시간(ms). 기기가 잠든 시간도 포함한다.
 *
 * 단조 시계 중에서도 부팅 기준이어야 하는 것은 구간의 시작이 DB 에 남기 때문이다 — 프로세스가 죽었다 살아난 뒤
 * 그 구간을 닫아도 같은 기준의 값이어야 한다. kotlin.time 의 TimeSource.Monotonic 은 프로세스 밖으로
 * 들고 나갈 수 없어 이 자리에 맞지 않는다.
 */
internal expect fun bootElapsedMillis(): Long
