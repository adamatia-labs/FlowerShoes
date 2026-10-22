package com.sadturtleman.flowershoes.tti.data.room

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.sadturtleman.flowershoes.tti.domain.Tti
import com.sadturtleman.flowershoes.tti.domain.TtiRecord
import com.sadturtleman.flowershoes.tti.domain.TtiSpan
import com.sadturtleman.flowershoes.tti.domain.TtiTimeline

/** 측정 한 건(= 화면 인스턴스 하나). */
@Entity(tableName = "tti_record")
internal data class TtiRecordEntity(
    @PrimaryKey val id: String,
    val pageName: String,
    val createdAt: Long,
)

/**
 * 그 측정의 구간 하나.
 *
 * 구간을 열(column) 로 펴지 않고 행으로 둔다 — [TtiTimeline] 이 늘어도 스키마가 그대로다.
 * 기록이 지워지면 구간도 함께 지워지도록 CASCADE 를 건다.
 */
@Entity(
    tableName = "tti_span",
    primaryKeys = ["recordId", "timeline"],
    foreignKeys = [
        ForeignKey(
            entity = TtiRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["recordId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("recordId")],
)
internal data class TtiSpanEntity(
    val recordId: String,
    /** [TtiTimeline] 의 이름. enum 을 그대로 넣지 않는 것은 값이 바뀌어도 DB 가 깨지지 않게 하기 위해서다. */
    val timeline: String,
    val startedAt: Long,
    val endedAt: Long?,
)

/** 기록 + 그 구간들. 조회는 언제나 이 짝으로 한다. */
internal data class TtiRecordWithSpans(
    @Embedded val record: TtiRecordEntity,
    @Relation(parentColumn = "id", entityColumn = "recordId")
    val spans: List<TtiSpanEntity>,
)

/**
 * 저장 모양 → 도메인 모양.
 *
 * 이름이 [TtiTimeline] 에 없는 구간(예전 버전이 남긴 것)은 버린다.
 * 그 기록은 구간 수가 모자라 완성으로 잡히지 않고, 오래되면 정리된다.
 */
internal fun TtiRecordWithSpans.toTtiRecord(): TtiRecord = TtiRecord(
    tti = Tti(record.id),
    pageName = record.pageName,
    createdAt = record.createdAt,
    spans = spans.mapNotNull { span ->
        val timeline = TtiTimeline.entries.firstOrNull { it.name == span.timeline } ?: return@mapNotNull null
        timeline to TtiSpan(startedAt = span.startedAt, endedAt = span.endedAt)
    }.toMap(),
)
