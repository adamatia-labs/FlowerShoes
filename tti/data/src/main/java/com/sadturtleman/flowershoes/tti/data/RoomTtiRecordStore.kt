package com.sadturtleman.flowershoes.tti.data

import com.sadturtleman.flowershoes.tti.data.room.TtiDao
import com.sadturtleman.flowershoes.tti.data.room.TtiRecordEntity
import com.sadturtleman.flowershoes.tti.data.room.TtiSpanEntity
import com.sadturtleman.flowershoes.tti.data.room.toTtiRecord
import com.sadturtleman.flowershoes.tti.domain.Tti
import com.sadturtleman.flowershoes.tti.domain.TtiRecord
import com.sadturtleman.flowershoes.tti.domain.TtiRecordStore
import com.sadturtleman.flowershoes.tti.domain.TtiTimeline

/**
 * Room 으로 받은 [TtiRecordStore].
 *
 * "처음 값을 지킨다" 는 규칙은 SQL 이 맡는다 — 두 insert 모두 IGNORE 이고,
 * 닫기는 `endedAt IS NULL` 인 행만 건드린다. 그래서 같은 호출이 몇 번 와도 결과가 같다.
 */
internal class RoomTtiRecordStore constructor(
    private val dao: TtiDao,
) : TtiRecordStore {

    /**
     * 기록 행과 구간 행을 잇달아 넣는다.
     *
     * 트랜잭션으로 묶지 않는 것은 중간에 끊겨도 해가 없기 때문이다 —
     * 구간이 없는 기록은 완성으로 잡히지 않고 [deleteCreatedBefore] 가 데려간다.
     */
    override suspend fun openSpan(
        tti: Tti,
        pageName: String,
        createdAt: Long,
        timeline: TtiTimeline,
        startedAt: Long,
    ) {
        dao.insertRecord(TtiRecordEntity(id = tti.id, pageName = pageName, createdAt = createdAt))
        dao.insertSpan(
            TtiSpanEntity(
                recordId = tti.id,
                timeline = timeline.name,
                startedAt = startedAt,
                endedAt = null,
            )
        )
    }

    override suspend fun closeSpan(tti: Tti, timeline: TtiTimeline, endedAt: Long) {
        dao.endSpan(recordId = tti.id, timeline = timeline.name, endedAt = endedAt)
    }

    override suspend fun find(tti: Tti): TtiRecord? = dao.findRecord(tti.id)?.toTtiRecord()

    override suspend fun findAll(): List<TtiRecord> = dao.findAllRecords().map { it.toTtiRecord() }

    override suspend fun delete(ttis: List<Tti>) {
        dao.deleteRecords(ttis.map { it.id })
    }

    override suspend fun deleteCreatedBefore(threshold: Long) {
        dao.deleteCreatedBefore(threshold)
    }
}
