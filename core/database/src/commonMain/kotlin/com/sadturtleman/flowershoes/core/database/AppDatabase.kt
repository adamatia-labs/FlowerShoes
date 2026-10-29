package com.sadturtleman.flowershoes.core.database

import androidx.room.AutoMigration
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
 */
@Database(
    entities = [
        CoupleEntity::class, UserEntity::class, DeviceEntity::class, ServiceEntity::class,
        StatusEntity::class, MissYouEntity::class, EventEntity::class, LetterEntity::class,
        DrawingEntity::class, ReceiptEntity::class, AttachmentEntity::class, MemoryEntity::class,
        SyncOpEntity::class, StreamCounterEntity::class, RecvCursorEntity::class, RecvPendingEntity::class,
        PeerCursorEntity::class, RevokedDeviceEntity::class, MetaEntity::class, LeaveGrantEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
    // 2: 휴가 관리(leave_grant 표 · event.leaveGrantId). 3: 편지 글꼴(letter.font). 4: 만남 사진(event.photoIds). 5: 일정 메모(event.memo) · 폰 기종(user.deviceName).
    // 6: 만남 한 줄 메모(event.meetNotes) · 산 것(user.perks — 연결된 상대와 함께 쓰는 꽃신 평생 · 추억 책).
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5), AutoMigration(from = 5, to = 6)],
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

    companion object {
        const val VERSION = 6
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
