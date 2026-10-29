package com.sadturtleman.flowershoes.core.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/**
 * 꽃신 로컬 DB. 각자 폰의 이 DB 가 원본이다(Local-first).
 *
 * 파일은 OS 백업에서 뺀다 — 기기 키는 백업되지 않으므로 DB 만 복원되면 "데이터는 있는데 열쇠가 없는" 상태가 된다.
 */
@Database(
    entities = [
        CoupleEntity::class, UserEntity::class, DeviceEntity::class, ServiceEntity::class,
        StatusEntity::class, MissYouEntity::class, EventEntity::class, LetterEntity::class,
        DrawingEntity::class, ReceiptEntity::class, AttachmentEntity::class, MemoryEntity::class,
        SyncOpEntity::class, StreamCounterEntity::class, RecvCursorEntity::class, RecvPendingEntity::class,
        PeerCursorEntity::class, RevokedDeviceEntity::class, MetaEntity::class, LeaveGrantEntity::class, LetterDraftEntity::class,
        BookCoverEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
    // 1: 출시 기준. 출시 전 변경(옛 2~10)은 이 버전 하나로 합쳤다.
    // 이제부터 스키마를 바꾸면 버전을 올리고 autoMigrations 에 AutoMigration 을 넣거나 MIGRATIONS 에 Migration 을 넣는다(MigrationTest 가 확인한다).
    autoMigrations = [],
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun coupleDao(): CoupleDao
    abstract fun userDao(): UserDao
    abstract fun deviceDao(): DeviceDao
    abstract fun serviceDao(): ServiceDao
    abstract fun statusDao(): StatusDao
    abstract fun missYouDao(): MissYouDao
    abstract fun eventDao(): EventDao
    abstract fun leaveDao(): LeaveDao
    abstract fun letterDao(): LetterDao
    abstract fun drawingDao(): DrawingDao
    abstract fun receiptDao(): ReceiptDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun memoryDao(): MemoryDao
    abstract fun syncDao(): SyncDao
    abstract fun letterDraftDao(): LetterDraftDao
    abstract fun bookCoverDao(): BookCoverDao

    companion object {
        const val VERSION = 1
        const val FILE_NAME = "kkotsin.db"

        /** 표를 고치는 수동 이전. 자동으로 못 하는 변경(이름 바꾸기 · 값 옮기기)만 여기 둔다. */
        val MIGRATIONS: List<Migration> = emptyList()
    }
}

// Room 컴파일러가 플랫폼마다 actual 을 만든다.
@Suppress("NO_ACTUAL_FOR_EXPECT", "KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

/**
 * 플랫폼별 빌더에 공통 설정을 얹는다. Android · iOS 가 같은 번들 SQLite 를 쓴다.
 * 파괴적 폴백은 두지 않는다 — 이전 경로가 빠지면 사용자 데이터를 지우는 대신 열리지 않는다.
 */
fun RoomDatabase.Builder<AppDatabase>.buildKkotsin(): AppDatabase = this
    .setDriver(BundledSQLiteDriver())
    .addMigrations(*AppDatabase.MIGRATIONS.toTypedArray())
    .setQueryCoroutineContext(Dispatchers.IO)
    .build()
