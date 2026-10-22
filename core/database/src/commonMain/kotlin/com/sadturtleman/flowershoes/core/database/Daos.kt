package com.sadturtleman.flowershoes.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// DAO 는 suspend 와 Flow 만 쓴다(LiveData · Cursor 같은 안드로이드 전용 API 금지).

@Dao
interface CoupleDao {
    @Query("SELECT * FROM couple LIMIT 1")
    fun observe(): Flow<CoupleEntity?>

    @Query("SELECT * FROM couple LIMIT 1")
    suspend fun get(): CoupleEntity?

    @Upsert
    suspend fun upsert(couple: CoupleEntity)

    @Query("DELETE FROM couple")
    suspend fun clear()
}

@Dao
interface UserDao {
    @Query("SELECT * FROM user")
    fun observeAll(): Flow<List<UserEntity>>

    @Query("SELECT * FROM user WHERE id = :id")
    suspend fun get(id: String): UserEntity?

    @Upsert
    suspend fun upsert(user: UserEntity)
}

@Dao
interface DeviceDao {
    @Query("SELECT * FROM device WHERE id = :id")
    suspend fun get(id: String): DeviceEntity?

    @Query("SELECT * FROM device WHERE userId = :userId")
    suspend fun forUser(userId: String): List<DeviceEntity>

    @Upsert
    suspend fun upsert(device: DeviceEntity)
}

@Dao
interface ServiceDao {
    @Query("SELECT * FROM service WHERE userId = :userId")
    fun observe(userId: String): Flow<ServiceEntity?>

    @Query("SELECT * FROM service")
    fun observeAll(): Flow<List<ServiceEntity>>

    @Query("SELECT * FROM service WHERE userId = :userId")
    suspend fun get(userId: String): ServiceEntity?

    @Upsert
    suspend fun upsert(service: ServiceEntity)
}

@Dao
interface StatusDao {
    /** 작성자의 최신 상태 하나. 만료된 것은 뺀다. */
    @Query(
        """
        SELECT * FROM status WHERE authorId = :authorId AND (expiresAtMs IS NULL OR expiresAtMs > :nowMs)
        ORDER BY createdAtMs DESC LIMIT 1
        """,
    )
    fun observeLatest(authorId: String, nowMs: Long): Flow<StatusEntity?>

    @Upsert
    suspend fun upsert(status: StatusEntity)
}

@Dao
interface MissYouDao {
    @Query("SELECT COUNT(*) FROM miss_you WHERE authorId = :authorId AND sentAtMs >= :sinceMs")
    fun observeCountSince(authorId: String, sinceMs: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(missYou: MissYouEntity)
}

@Dao
interface EventDao {
    @Query("SELECT * FROM event WHERE deletedAtMs IS NULL AND `end` >= :from AND start <= :to ORDER BY start")
    fun observeRange(from: String, to: String): Flow<List<EventEntity>>

    @Query("SELECT * FROM event WHERE deletedAtMs IS NULL AND start >= :from ORDER BY start LIMIT 1")
    fun observeNext(from: String): Flow<EventEntity?>

    @Query("SELECT * FROM event WHERE id = :id")
    suspend fun get(id: String): EventEntity?

    /** 일정 전날 알림처럼 앞으로 올 일정. */
    @Query("SELECT * FROM event WHERE deletedAtMs IS NULL AND start >= :from ORDER BY start LIMIT :limit")
    suspend fun upcoming(from: String, limit: Int): List<EventEntity>

    @Upsert
    suspend fun upsert(event: EventEntity)

    /** 상대 커서가 지난 뒤 90일 지난 tombstone 정리. */
    @Query("DELETE FROM event WHERE deletedAtMs IS NOT NULL AND deletedAtMs < :beforeMs")
    suspend fun purgeTombstones(beforeMs: Long)
}

@Dao
interface LetterDao {
    /** 편지함. 공개 시각이 지난 것만 — 예약 편지는 공개 전에 어디에도 보이지 않는다. */
    @Query("SELECT * FROM letter WHERE deletedAtMs IS NULL AND visibleAtMs <= :nowMs ORDER BY visibleAtMs DESC")
    fun observeVisible(nowMs: Long): Flow<List<LetterEntity>>

    /** 지우지 않은 편지 전부. 공개 여부는 시각이 흐르면 바뀌므로 Repository 가 지금 시각으로 거른다. */
    @Query("SELECT * FROM letter WHERE deletedAtMs IS NULL ORDER BY visibleAtMs DESC")
    fun observeAll(): Flow<List<LetterEntity>>

    /** 내가 보낸 예약 편지(작성자에게는 보인다). */
    @Query("SELECT * FROM letter WHERE deletedAtMs IS NULL AND authorId = :authorId AND visibleAtMs > :nowMs ORDER BY visibleAtMs")
    fun observeScheduledBy(authorId: String, nowMs: Long): Flow<List<LetterEntity>>

    /** 받는 폰이 로컬 알림으로 공개할 예약 편지. */
    @Query("SELECT * FROM letter WHERE deletedAtMs IS NULL AND authorId != :meId AND visibleAtMs > :nowMs ORDER BY visibleAtMs LIMIT :limit")
    suspend fun upcomingReveals(meId: String, nowMs: Long, limit: Int): List<LetterEntity>

    @Query("SELECT * FROM letter WHERE id = :id")
    suspend fun get(id: String): LetterEntity?

    @Upsert
    suspend fun upsert(letter: LetterEntity)
}

@Dao
interface DrawingDao {
    @Query("SELECT * FROM drawing WHERE letterId = :letterId AND deletedAtMs IS NULL ORDER BY orderInLetter")
    suspend fun forLetter(letterId: String): List<DrawingEntity>

    /** 위젯: 그림이 들어 있는 가장 최근 "공개된" 편지의 첫 그림. */
    @Query(
        """
        SELECT d.* FROM drawing d JOIN letter l ON l.id = d.letterId
        WHERE d.deletedAtMs IS NULL AND l.deletedAtMs IS NULL AND l.visibleAtMs <= :nowMs
          AND d.showOnWidget = 1 AND d.authorId != :meId
        ORDER BY l.visibleAtMs DESC, d.orderInLetter LIMIT 1
        """,
    )
    fun observeForWidget(meId: String, nowMs: Long): Flow<DrawingEntity?>

    @Upsert
    suspend fun upsert(drawing: DrawingEntity)

    @Query("UPDATE drawing SET deletedAtMs = :atMs WHERE letterId = :letterId")
    suspend fun tombstoneForLetter(letterId: String, atMs: Long)
}

@Dao
interface ReceiptDao {
    /** 먼저 온 값을 남긴다. 같은 키가 있으면 무시. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFirst(receipt: ReceiptEntity): Long

    @Query("SELECT * FROM receipt WHERE entity = :entity AND entityId = :entityId AND readerId = :readerId")
    suspend fun get(entity: String, entityId: String, readerId: String): ReceiptEntity?
}

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachment WHERE id = :id")
    suspend fun get(id: String): AttachmentEntity?

    @Upsert
    suspend fun upsert(attachment: AttachmentEntity)
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memory ORDER BY date DESC")
    fun observeAll(): Flow<List<MemoryEntity>>

    @Upsert
    suspend fun upsert(memory: MemoryEntity)

    @Query("DELETE FROM memory WHERE refId = :refId")
    suspend fun deleteForRef(refId: String)
}

@Dao
interface SyncDao {
    // 보낸 Op 로그

    @Insert
    suspend fun appendOp(op: SyncOpEntity)

    @Query("SELECT * FROM sync_op WHERE stream = :stream ORDER BY seq")
    suspend fun log(stream: String): List<SyncOpEntity>

    @Query("SELECT * FROM sync_op WHERE stream = :stream AND seq > :afterSeq ORDER BY seq")
    suspend fun opsAfter(stream: String, afterSeq: Long): List<SyncOpEntity>

    @Query("SELECT DISTINCT stream FROM sync_op")
    suspend fun streamsWithLog(): List<String>

    @Query("DELETE FROM sync_op WHERE stream = :stream AND seq <= :seq")
    suspend fun deleteThrough(stream: String, seq: Long)

    // 내 스트림 번호

    @Query("SELECT nextSeq FROM stream_counter WHERE stream = :stream")
    suspend fun nextSeq(stream: String): Long?

    @Upsert
    suspend fun setNextSeq(counter: StreamCounterEntity)

    // 받는 쪽 커서

    @Query("SELECT * FROM recv_cursor")
    suspend fun cursors(): List<RecvCursorEntity>

    @Query("SELECT contiguousSeq FROM recv_cursor WHERE stream = :stream")
    suspend fun cursor(stream: String): Long?

    @Upsert
    suspend fun setCursor(cursor: RecvCursorEntity)

    @Query("SELECT seq FROM recv_pending WHERE stream = :stream")
    suspend fun pending(stream: String): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addPending(pending: RecvPendingEntity)

    @Query("DELETE FROM recv_pending WHERE stream = :stream AND seq <= :seq")
    suspend fun deletePendingThrough(stream: String, seq: Long)

    // 상대가 확인해 준 내 번호

    @Query("SELECT ackedSeq FROM peer_cursor WHERE stream = :stream")
    suspend fun peerCursor(stream: String): Long?

    @Upsert
    suspend fun setPeerCursor(cursor: PeerCursorEntity)

    // 폐기 기기

    @Query("SELECT * FROM revoked_device")
    suspend fun revoked(): List<RevokedDeviceEntity>

    @Upsert
    suspend fun addRevoked(device: RevokedDeviceEntity)

    // 단일 값

    @Query("SELECT value FROM meta WHERE `key` = :key")
    suspend fun meta(key: String): Long?

    @Upsert
    suspend fun setMeta(meta: MetaEntity)
}
