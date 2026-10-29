package com.sadturtleman.flowershoes.core.tti.room

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/**
 * 측정 기록 저장소.
 *
 * 계측 데이터라 잃어도 앱 동작에는 영향이 없다. 그래서 마이그레이션을 쌓지 않고
 * 스키마가 바뀌면 버리는 쪽을 택한다(모듈 안에서만 쓰는 테이블이다).
 * 앱 DB(kkotsin.db)와 파일을 나누는 것도 같은 이유다 — 계측 스키마가 앱 DB 의 마이그레이션에 섞이지 않는다.
 */
@Database(
    entities = [TtiRecordEntity::class, TtiSpanEntity::class],
    version = 1,
    exportSchema = false,
)
@ConstructedBy(TtiDatabaseConstructor::class)
abstract class TtiDatabase : RoomDatabase() {
    internal abstract fun ttiDao(): TtiDao

    companion object {
        const val FILE_NAME = "tti.db"
    }
}

// Room 컴파일러가 플랫폼마다 actual 을 만든다.
@Suppress("NO_ACTUAL_FOR_EXPECT", "KotlinNoActualForExpect")
expect object TtiDatabaseConstructor : RoomDatabaseConstructor<TtiDatabase> {
    override fun initialize(): TtiDatabase
}

/** 플랫폼별 빌더에 공통 설정을 얹는다. Android · iOS 가 같은 번들 SQLite 를 쓴다. */
internal fun RoomDatabase.Builder<TtiDatabase>.buildTti(): TtiDatabase = this
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.IO)
    .fallbackToDestructiveMigration(dropAllTables = true)
    .build()
