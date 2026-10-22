package com.sadturtleman.flowershoes.tti.data.room

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * 측정 기록 저장소.
 *
 * 계측 데이터라 잃어도 앱 동작에는 영향이 없다. 그래서 마이그레이션을 쌓지 않고
 * 스키마가 바뀌면 버리는 쪽을 택한다(모듈 안에서만 쓰는 테이블이다).
 */
@Database(
    entities = [TtiRecordEntity::class, TtiSpanEntity::class],
    version = 1,
    exportSchema = false,
)
internal abstract class TtiDatabase : RoomDatabase() {
    abstract fun ttiDao(): TtiDao
}
