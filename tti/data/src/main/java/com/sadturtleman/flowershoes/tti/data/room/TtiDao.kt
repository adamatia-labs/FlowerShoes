package com.sadturtleman.flowershoes.tti.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
internal interface TtiDao {

    /** 같은 화면 인스턴스의 두 번째 진입은 무시한다(첫 구간이 기준 시각이다). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecord(record: TtiRecordEntity)

    /**
     * 구간을 연다. 이미 열려 있으면 무시한다 —
     * 컴포지션이 다시 돌아 start 가 또 와도 처음 시각을 지키기 위해서다.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSpan(span: TtiSpanEntity)

    /** 아직 안 닫힌 구간만 닫는다. 늦게 도착한 end 가 앞의 측정을 덮어쓰지 못한다. */
    @Query(
        "UPDATE tti_span SET endedAt = :endedAt " +
            "WHERE recordId = :recordId AND timeline = :timeline AND endedAt IS NULL"
    )
    suspend fun endSpan(recordId: String, timeline: String, endedAt: Long)

    @Transaction
    @Query("SELECT * FROM tti_record WHERE id = :recordId")
    suspend fun findRecord(recordId: String): TtiRecordWithSpans?

    @Transaction
    @Query("SELECT * FROM tti_record ORDER BY createdAt")
    suspend fun findAllRecords(): List<TtiRecordWithSpans>

    /** 구간은 CASCADE 로 함께 지워진다. */
    @Query("DELETE FROM tti_record WHERE id IN (:recordIds)")
    suspend fun deleteRecords(recordIds: List<String>)

    /** 끝내 완성되지 않고 남은 기록 청소. */
    @Query("DELETE FROM tti_record WHERE createdAt < :threshold")
    suspend fun deleteCreatedBefore(threshold: Long)
}
