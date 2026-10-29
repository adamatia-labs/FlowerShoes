package com.sadturtleman.flowershoes.core.tti.room

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/**
 * 측정 기록 저장소.
 *
 * 계측 데이터지만 앱 DB 와 같은 규칙을 따른다 — 스키마를 바꾸면 버전을 올리고 이전 경로를 넣는다(TtiMigrationTest 가 확인한다).
 * 앱 DB(kkotsin.db)와 파일을 나누는 것은 계측 스키마가 앱 DB 의 버전 · 이전에 섞이지 않게 하려는 것이다.
 */
@Database(
    entities = [TtiRecordEntity::class, TtiSpanEntity::class],
    version = TtiDatabase.VERSION,
    exportSchema = true,
)
@ConstructedBy(TtiDatabaseConstructor::class)
abstract class TtiDatabase : RoomDatabase() {
    internal abstract fun ttiDao(): TtiDao

    companion object {
        const val VERSION = 1
        const val FILE_NAME = "tti.db"

        /** 표를 고치는 수동 이전. 자동으로 못 하는 변경만 여기 둔다. */
        val MIGRATIONS: List<Migration> = emptyList()
    }
}

// Room 컴파일러가 플랫폼마다 actual 을 만든다.
@Suppress("NO_ACTUAL_FOR_EXPECT", "KotlinNoActualForExpect")
expect object TtiDatabaseConstructor : RoomDatabaseConstructor<TtiDatabase> {
    override fun initialize(): TtiDatabase
}

/** 플랫폼별 빌더에 공통 설정을 얹는다. Android · iOS 가 같은 번들 SQLite 를 쓴다. 파괴적 폴백은 두지 않는다. */
internal fun RoomDatabase.Builder<TtiDatabase>.buildTti(): TtiDatabase = this
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.IO)
    .addMigrations(*TtiDatabase.MIGRATIONS.toTypedArray())
    .build()
