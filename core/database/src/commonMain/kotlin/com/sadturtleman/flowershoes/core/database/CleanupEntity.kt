package com.sadturtleman.flowershoes.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** 서버 요청 전부터 설정 정리가 끝날 때까지 남는 단일 로컬 작업. 프로필/동기화 정리로 지우지 않는다. */
@Entity(tableName = "relationship_cleanup")
data class CleanupEntity(
    @PrimaryKey val id: Int = 1,
    val token: String,
    val ownerUid: String,
    val deviceId: String,
    val accountSession: String,
    val source: String,
    val choice: String,
    val phase: String,
    val coupleId: String?,
    val coupleOwner: String?,
    val partnerUid: String?,
    val keyEpoch: Int?,
    val linkedAtMs: Long?,
    val sealedKeys: String?,
    val retainedDrafts: Boolean = false,
    val appleInstructions: Boolean = false,
)

@Dao
interface CleanupDao {
    @Query("SELECT * FROM relationship_cleanup WHERE id = 1")
    suspend fun get(): CleanupEntity?

    @Query("SELECT * FROM relationship_cleanup WHERE id = 1")
    fun observe(): Flow<CleanupEntity?>

    @Upsert
    suspend fun save(cleanup: CleanupEntity)

    @Query("DELETE FROM relationship_cleanup WHERE token = :token")
    suspend fun delete(token: String)
}
