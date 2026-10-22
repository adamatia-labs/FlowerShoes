package com.sadturtleman.flowershoes.core.model

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

/** 군종. 사회복무요원은 계급이 없다. */
@Serializable
enum class Branch { ARMY, NAVY, AIR_FORCE, MARINE, SOCIAL_SERVICE }

/**
 * 복무 정보. 둘 다 고칠 수 있는 공유 데이터(LWW).
 *
 * [dischargeOverride] 가 있으면 계산값 대신 그 날짜를 쓴다 — 조기 전역·연장처럼 규칙으로 알 수 없는 경우다.
 * [rulesVersion] 은 계산에 쓴 복무 규칙 표의 버전이다. 규칙이 바뀌어도 왜 그 날짜가 나왔는지 추적할 수 있다.
 */
@Serializable
data class Service(
    val userId: String,
    val branch: Branch,
    val enlistDate: LocalDate,
    val rulesVersion: Int,
    val dischargeOverride: LocalDate? = null,
    val lamport: Long = 0,
)

/** 상대에게 보여 주는 지금 상태. 작성자만 쓰고, 최신 것 하나만 표시한다. */
@Serializable
data class Status(
    val id: String,
    val authorId: String,
    val label: String,
    val color: Long,
    val createdAt: Instant,
    val expiresAt: Instant? = null,
    val lamport: Long = 0,
)

/** 보고싶어 한 번. 추가 전용. */
@Serializable
data class MissYou(
    val id: String,
    val authorId: String,
    val sentAt: Instant,
)

@Serializable
enum class EventType { VACATION, OVERNIGHT, VISIT, TRAINING, DUTY }

/**
 * 일정. 둘 다 고치는 공유 데이터라 행 단위 LWW(lamport, lastWriterDevice)로 맞춘다.
 * 삭제는 행을 지우지 않고 [deletedAt] 을 남긴다(tombstone).
 */
@Serializable
data class Event(
    val id: String,
    val type: EventType,
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val alarm: Boolean,
    val updatedAt: Instant,
    val lamport: Long,
    val lastWriterDevice: String,
    val deletedAt: Instant? = null,
)

/** 편지 안의 한 덩어리. 목록 순서가 곧 화면 순서다. */
@Serializable
sealed interface LetterBlock {
    @Serializable
    data class Text(val text: String) : LetterBlock

    @Serializable
    data class DrawingRef(val drawingId: String) : LetterBlock

    @Serializable
    data class Photo(val attachmentId: String) : LetterBlock
}

/**
 * 편지. 그림은 편지 안에 들어간다 — "그림만 보내기"는 글 없는 편지다.
 *
 * [visibleAt] 이 미래면 예약 편지다. 미리 받아 두지만 그 시각 전에는 위젯·편지함·추억 어디에도 보이지 않는다.
 */
@Serializable
data class Letter(
    val id: String,
    val authorId: String,
    val title: String? = null,
    val blocks: List<LetterBlock>,
    val paper: String,
    val visibleAt: Instant,
    val deletedAt: Instant? = null,
) {
    val hasText: Boolean get() = blocks.any { it is LetterBlock.Text && it.text.isNotBlank() }
    val drawingCount: Int get() = blocks.count { it is LetterBlock.DrawingRef }

    /** [now] 기준으로 상대에게 보여도 되는지. 예약 편지 공개 전이거나 지운 편지면 false. */
    fun isVisible(now: Instant): Boolean = deletedAt == null && visibleAt <= now

    init {
        require(drawingCount <= MAX_DRAWINGS) { "편지 1통에 그림은 최대 $MAX_DRAWINGS 장" }
    }

    companion object {
        const val MAX_DRAWINGS = 3
    }
}

@Serializable
enum class ReceiptEntity { LETTER, DRAWING }

/** 읽음 · 처음 봄. 받는 사람만 쓰고, 한 번 기록되면 덮어쓰지 않는다(먼저 온 값 유지). */
@Serializable
data class Receipt(
    val entity: ReceiptEntity,
    val entityId: String,
    val readerId: String,
    val at: Instant,
)

@Serializable
enum class AttachmentKind { PHOTO, DRAWING_DOC, DRAWING_PNG }

@Serializable
data class Attachment(
    val id: String,
    val kind: AttachmentKind,
    val localPath: String,
    val sha256: String,
    val sizeBytes: Long,
)

@Serializable
enum class MemoryKind { LETTER, MEETING, ANNIVERSARY }

/** 추억 타임라인의 한 줄. 원본은 [refId] 가 가리키는 행이고, 이것은 보기용 색인이다. */
@Serializable
data class MemoryItem(
    val id: String,
    val kind: MemoryKind,
    val refId: String,
    val date: LocalDate,
)
