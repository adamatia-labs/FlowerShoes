package com.sadturtleman.flowershoes.core.data

import com.sadturtleman.flowershoes.core.database.EventEntity
import com.sadturtleman.flowershoes.core.database.LeaveGrantEntity
import com.sadturtleman.flowershoes.core.model.LeaveGrant
import com.sadturtleman.flowershoes.core.model.LeaveKind
import com.sadturtleman.flowershoes.core.database.LetterEntity
import com.sadturtleman.flowershoes.core.database.MissYouEntity
import com.sadturtleman.flowershoes.core.database.ReceiptEntity
import com.sadturtleman.flowershoes.core.database.ServiceEntity
import com.sadturtleman.flowershoes.core.database.StatusEntity
import com.sadturtleman.flowershoes.core.model.Branch
import com.sadturtleman.flowershoes.core.model.Event
import com.sadturtleman.flowershoes.core.model.EventType
import com.sadturtleman.flowershoes.core.model.Letter
import com.sadturtleman.flowershoes.core.model.LetterBlock
import com.sadturtleman.flowershoes.core.model.MissYou
import com.sadturtleman.flowershoes.core.model.Receipt
import com.sadturtleman.flowershoes.core.model.Service
import com.sadturtleman.flowershoes.core.model.Status
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

// 엔티티(저장 모양) ↔ 도메인 모델. 시각은 epoch ms, 날짜는 ISO 문자열.

private val letterJson = Json { ignoreUnknownKeys = true }
private val blocksSerializer = ListSerializer(LetterBlock.serializer())

internal fun Instant.ms(): Long = toEpochMilliseconds()
internal fun Long.instant(): Instant = Instant.fromEpochMilliseconds(this)

fun Service.toEntity() = ServiceEntity(userId, branch.name, enlistDate.toString(), rulesVersion, dischargeOverride?.toString(), lamport)
fun ServiceEntity.toModel() = Service(
    userId, Branch.valueOf(branch), LocalDate.parse(enlistDate), rulesVersion, dischargeOverride?.let(LocalDate::parse), lamport,
)

fun Event.toEntity() = EventEntity(
    id, type.name, title, start.toString(), end.toString(), alarm, updatedAt.ms(), lamport, lastWriterDevice, deletedAt?.ms(), leaveGrantId,
    photoIds.takeIf { it.isNotEmpty() }?.joinToString(","),
)
fun EventEntity.toModel() = Event(
    id, EventType.valueOf(type), title, LocalDateTime.parse(start), LocalDateTime.parse(end), alarm,
    updatedAtMs.instant(), lamport, lastWriterDevice, deletedAtMs?.instant(), leaveGrantId,
    photoIds?.split(',')?.filter { it.isNotBlank() }.orEmpty(),
)

fun LeaveGrant.toEntity() = LeaveGrantEntity(
    id, kind.name, name, days, grantedOn.toString(), updatedAt.ms(), lamport, lastWriterDevice, deletedAt?.ms(),
)
fun LeaveGrantEntity.toModel() = LeaveGrant(
    id, runCatching { LeaveKind.valueOf(kind) }.getOrDefault(LeaveKind.OTHER), name, days, LocalDate.parse(grantedOn),
    updatedAtMs.instant(), lamport, lastWriterDevice, deletedAtMs?.instant(),
)

fun Letter.toEntity(lamport: Long) = LetterEntity(
    id, authorId, title, letterJson.encodeToString(blocksSerializer, blocks), paper, visibleAt.ms(), deletedAt?.ms(), lamport, font,
)
fun LetterEntity.toModel() = Letter(
    id, authorId, title, letterJson.decodeFromString(blocksSerializer, blocksJson), paper,
    visibleAtMs.instant(), deletedAtMs?.instant(), font,
)

fun Status.toEntity() = StatusEntity(id, authorId, label, color, createdAt.ms(), expiresAt?.ms(), lamport)
fun StatusEntity.toModel() = Status(id, authorId, label, color, createdAtMs.instant(), expiresAtMs?.instant(), lamport)

fun MissYou.toEntity() = MissYouEntity(id, authorId, sentAt.ms())

fun Receipt.toEntity() = ReceiptEntity(entity.name, entityId, readerId, at.ms())
