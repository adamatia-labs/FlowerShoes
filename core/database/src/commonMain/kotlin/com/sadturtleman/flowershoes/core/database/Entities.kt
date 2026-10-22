package com.sadturtleman.flowershoes.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// 시각은 epoch ms(Long), 날짜는 ISO 문자열로 둔다. TypeConverter 없이 플랫폼 간 같은 값이 저장된다.
// 도메인 모델과의 변환은 :core:data 가 맡는다.

@Entity(tableName = "couple")
data class CoupleEntity(
    @PrimaryKey val id: String,
    val myUserId: String,
    val partnerUserId: String,
    val keyEpoch: Int,
    val linkedAtMs: Long,
    val anniversary: String?,
    val anniversaryLamport: Long,
)

@Entity(tableName = "user")
data class UserEntity(
    @PrimaryKey val id: String,
    val role: String,
    val nickname: String,
    val nicknameLamport: Long,
)

@Entity(tableName = "device", indices = [Index("userId")])
data class DeviceEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val uid: String,
    val signPub: ByteArray,
    val boxPub: ByteArray,
    val state: String,
    val registeredAtMs: Long,
    val revokedAtMs: Long?,
)

@Entity(tableName = "service")
data class ServiceEntity(
    @PrimaryKey val userId: String,
    val branch: String,
    val enlistDate: String,
    val rulesVersion: Int,
    val dischargeOverride: String?,
    val lamport: Long,
)

@Entity(tableName = "status", indices = [Index("authorId", "createdAtMs")])
data class StatusEntity(
    @PrimaryKey val id: String,
    val authorId: String,
    val label: String,
    val color: Long,
    val createdAtMs: Long,
    val expiresAtMs: Long?,
    val lamport: Long,
)

@Entity(tableName = "miss_you", indices = [Index("sentAtMs")])
data class MissYouEntity(
    @PrimaryKey val id: String,
    val authorId: String,
    val sentAtMs: Long,
)

@Entity(tableName = "event", indices = [Index("start")])
data class EventEntity(
    @PrimaryKey val id: String,
    val type: String,
    val title: String,
    val start: String,
    val end: String,
    val alarm: Boolean,
    val updatedAtMs: Long,
    val lamport: Long,
    val lastWriterDevice: String,
    val deletedAtMs: Long?,
)

/** 편지. [blocksJson] 은 LetterBlock 목록(kotlinx-serialization JSON). */
@Entity(tableName = "letter", indices = [Index("visibleAtMs")])
data class LetterEntity(
    @PrimaryKey val id: String,
    val authorId: String,
    val title: String?,
    val blocksJson: String,
    val paper: String,
    val visibleAtMs: Long,
    val deletedAtMs: Long?,
    val lamport: Long,
)

@Entity(tableName = "drawing", indices = [Index("letterId")])
data class DrawingEntity(
    @PrimaryKey val id: String,
    val coupleId: String,
    val letterId: String,
    val orderInLetter: Int,
    val authorId: String,
    val shape: String,
    val docAttachmentId: String?,
    val pngAttachmentId: String,
    val thumbPath: String,
    val caption: String?,
    val replayEnabled: Boolean,
    val replayDurationMs: Long,
    val showOnWidget: Boolean,
    val sentAtMs: Long,
    val receivedAtMs: Long?,
    val deletedAtMs: Long?,
)

/** 읽음 · 처음 봄. 기본키가 (entity, entityId, readerId) 라 먼저 들어온 값이 남는다. */
@Entity(tableName = "receipt", primaryKeys = ["entity", "entityId", "readerId"])
data class ReceiptEntity(
    val entity: String,
    val entityId: String,
    val readerId: String,
    val atMs: Long,
)

@Entity(tableName = "attachment")
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val localPath: String,
    val sha256: String,
    val sizeBytes: Long,
)

@Entity(tableName = "memory", indices = [Index("date")])
data class MemoryEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val refId: String,
    val date: String,
)

/** 보낸 Op 로그. 상대 control 커서가 넘어선 것만 지운다. */
@Entity(tableName = "sync_op", primaryKeys = ["stream", "seq"], indices = [Index(value = ["opId"], unique = true)])
data class SyncOpEntity(
    val stream: String,
    val seq: Long,
    val opId: String,
    val kind: String,
    val entity: String,
    val entityId: String,
    val payload: ByteArray,
    val lamport: Long,
    val keyEpoch: Int,
    val createdAtMs: Long,
)

/** 내가 쓰는 스트림의 다음 seq. */
@Entity(tableName = "stream_counter")
data class StreamCounterEntity(
    @PrimaryKey val stream: String,
    val nextSeq: Long,
)

@Entity(tableName = "recv_cursor")
data class RecvCursorEntity(
    @PrimaryKey val stream: String,
    val contiguousSeq: Long,
)

@Entity(tableName = "recv_pending", primaryKeys = ["stream", "seq"])
data class RecvPendingEntity(
    val stream: String,
    val seq: Long,
    val opId: String,
)

@Entity(tableName = "peer_cursor")
data class PeerCursorEntity(
    @PrimaryKey val stream: String,
    val ackedSeq: Long,
)

/** 폐기된 기기. [cutoffJson] 은 { stream: 마지막 인정 seq }. */
@Entity(tableName = "revoked_device")
data class RevokedDeviceEntity(
    @PrimaryKey val deviceId: String,
    val revokedAtMs: Long,
    val cutoffJson: String,
)

/** Lamport 시계 같은 단일 값. */
@Entity(tableName = "meta")
data class MetaEntity(
    @PrimaryKey val key: String,
    val value: Long,
)
