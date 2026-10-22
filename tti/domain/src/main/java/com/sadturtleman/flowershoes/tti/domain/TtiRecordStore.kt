package com.sadturtleman.flowershoes.tti.domain

/**
 * 측정 기록 저장 포트.
 *
 * 프로세스가 죽어도 남아 있어야 하므로 메모리가 아니라 영속 저장소를 전제로 한다.
 * 어디에 어떻게 담는지는 [com.sadturtleman.flowershoes.tti.data] 가 정한다.
 *
 * 호출은 [TtiRecorder] 가 한 줄로 세워서 넣는다. 구현이 스스로 동시성을 막을 필요는 없다.
 */
interface TtiRecordStore {

    /**
     * 기록과 그 구간을 연다.
     *
     * 기록이 이미 있으면 [createdAt] 을 덮어쓰지 않고, 구간이 이미 열려 있으면 [startedAt] 도 지킨다 —
     * 컴포지션이 다시 돌아 start 가 또 와도 처음 시각이 기준이어야 하기 때문이다.
     */
    suspend fun openSpan(
        tti: Tti,
        pageName: String,
        createdAt: Long,
        timeline: TtiTimeline,
        startedAt: Long,
    )

    /** 아직 안 닫힌 구간만 닫는다. 늦게 도착한 end 가 앞의 측정을 덮어쓰지 못한다. */
    suspend fun closeSpan(tti: Tti, timeline: TtiTimeline, endedAt: Long)

    /** 없으면 null. 이미 쏘고 지운 뒤일 수 있다. */
    suspend fun find(tti: Tti): TtiRecord?

    suspend fun findAll(): List<TtiRecord>

    suspend fun delete(ttis: List<Tti>)

    /** 끝내 완성되지 않고 남은 기록 청소. */
    suspend fun deleteCreatedBefore(threshold: Long)
}
