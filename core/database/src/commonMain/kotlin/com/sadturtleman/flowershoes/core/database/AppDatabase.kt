package com.sadturtleman.flowershoes.core.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/**
 * 꽃신 로컬 DB. 각자 폰의 이 DB 가 원본이다(Local-first).
 *
 * 파일은 OS 백업에서 뺀다 — 기기 키는 백업되지 않으므로 DB 만 복원되면 "데이터는 있는데 열쇠가 없는" 상태가 된다.
 * 복원은 앱 자체 암호화 백업(:core:backup)으로만 한다.
 */
@Database(
    entities = [
        CoupleEntity::class, UserEntity::class, DeviceEntity::class, ServiceEntity::class,
        StatusEntity::class, MissYouEntity::class, EventEntity::class, LetterEntity::class,
        DrawingEntity::class, ReceiptEntity::class, AttachmentEntity::class, MemoryEntity::class,
        SyncOpEntity::class, StreamCounterEntity::class, RecvCursorEntity::class, RecvPendingEntity::class,
        PeerCursorEntity::class, RevokedDeviceEntity::class, MetaEntity::class,
    ],
    version = 1,
    exportSchema = true,
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
    abstract fun letterDao(): LetterDao
    abstract fun drawingDao(): DrawingDao
    abstract fun receiptDao(): ReceiptDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun memoryDao(): MemoryDao
    abstract fun syncDao(): SyncDao

    companion object {
        const val FILE_NAME = "kkotsin.db"
    }
}

// Room 컴파일러가 플랫폼마다 actual 을 만든다.
@Suppress("NO_ACTUAL_FOR_EXPECT", "KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

/** 플랫폼별 빌더에 공통 설정을 얹는다. Android · iOS 가 같은 번들 SQLite 를 쓴다. */
fun RoomDatabase.Builder<AppDatabase>.buildKkotsin(): AppDatabase = this
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.IO)
    .build()
