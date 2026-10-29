package com.sadturtleman.flowershoes.core.tti

import kotlin.uuid.Uuid

/**
 * 측정 한 건을 가리키는 손잡이.
 *
 * 같은 화면(pageName)이라도 백스택에 두 번 쌓이면 서로 다른 측정이므로 id 로 가른다.
 * 화면은 이 값을 remember 로 들고 다니고, 구간의 원본은 [TtiRecordStore] 에 있다.
 */
data class Tti(val id: String = Uuid.random().toString())

/** 구간 하나. 아직 닫히지 않았으면 [endedAt] 이 null 이다. */
data class TtiSpan(val startedAt: Long, val endedAt: Long? = null) {
    /** 아직 안 닫혔으면 null. */
    val durationMillis: Long?
        get() = endedAt?.let { it - startedAt }
}

/**
 * 저장돼 있는 측정 한 건. [TtiShooter] 로 나가는 것이 이 모양이다.
 *
 * @param createdAt 첫 구간이 열린 벽시계 시각. 오래된 미완성 기록을 지우는 데만 쓴다.
 */
data class TtiRecord(
    val tti: Tti,
    val pageName: String,
    val createdAt: Long,
    val spans: Map<TtiTimeline, TtiSpan>,
) {
    /**
     * 네 구간의 길이 합. 하나라도 안 닫혔으면 null 이다.
     *
     * 처음과 끝의 차이가 아니라 합인 이유: 구간 사이에는 측정하지 않는 빈 시간(사용자 입력 대기 등)이
     * 끼어들 수 있고, 그것까지 TTI 로 세면 화면이 느려진 것처럼 보인다.
     */
    val totalTimeMillis: Long?
        get() = TtiTimeline.entries.fold(0L) { sum, timeline ->
            sum + (spans[timeline]?.durationMillis ?: return null)
        }

    /** start · end 가 네 구간 모두 찍혀 있어 쏠 수 있는 상태인가. */
    val isComplete: Boolean
        get() = totalTimeMillis != null
}
