package com.sadturtleman.flowershoes.feature.letter

import com.sadturtleman.flowershoes.core.data.LetterDraftStore
import com.sadturtleman.flowershoes.core.data.SEOUL
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json

/** 쓰던 편지 한 통(편지함 "쓰던 편지 N통" · 쓰던 편지 목록 · "이어 쓸까요?" 시트). */
data class DraftSummary(
    val id: String,
    val firstLine: String,
    val paper: Paper,
    /** "어제 오후 11:40" · "10월 25일". */
    val whenLabel: String,
)

/**
 * 쓰던 편지 보관(임시저장 여러 통, 이 기기에만). 메인 스레드(viewModelScope)에서만 부른다 — 저장 · 지우기만 잠근다. 편지 쓰기 화면이 고칠 때마다 [edited] 를 부르면
 * 입력이 멈추고 2초 뒤에 저장하고, 화면을 나갈 때 [flush] 로 바로 저장한다. 빈 편지는 남기지 않는다.
 *
 * 지금 쓰는 편지는 [currentId] 한 통이다. [startNew] · [resume] 은 다른 편지로 옮겨 가기 전에 하던 저장을 마저 한다.
 */
internal class DraftKeeper(
    private val store: LetterDraftStore,
    private val scope: CoroutineScope,
    private val clock: Clock,
    private val newId: () -> String = { Uuid.random().toString() },
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()
    private var pending: Job? = null
    private var pendingDraft: ComposeDraft? = null

    /** id → 마지막으로 저장한 JSON. 같은 글을 다시 쓰지 않고, 빈 편지가 되면 지울 것을 안다. */
    private val saved = mutableMapOf<String, String>()

    var currentId: String = newId()
        private set

    val drafts: Flow<List<DraftSummary>> = store.observe().map { rows ->
        rows.mapNotNull { row ->
            val d = decode(row.json) ?: return@mapNotNull null
            val at = Instant.fromEpochMilliseconds(row.updatedAtMs)
            DraftSummary(row.id, d.firstLine(), Paper.of(d.paper), draftWhen(at, clock.now()))
        }
    }

    /** 글 · 편지지 · 첨부 · 예약이 바뀌었다. 2초 동안 더 고치지 않으면 저장한다. */
    fun edited(draft: ComposeDraft) {
        pending?.cancel()
        pendingDraft = draft
        val id = currentId
        pending = scope.launch {
            delay(AUTOSAVE_MS)
            pendingDraft = null
            persist(id, draft)
        }
    }

    /** 화면을 나간다 — 기다리지 않고 저장한다. */
    fun flush(draft: ComposeDraft) {
        pending?.cancel()
        pendingDraft = null
        val id = currentId
        scope.launch { persist(id, draft) }
    }

    /** 새 편지를 쓴다. 하던 저장은 마저 한다. */
    fun startNew() {
        pendingDraft?.let { flush(it) }
        currentId = newId()
    }

    /** 쓰던 편지 [id] 를 이어 쓴다. 없으면 null. */
    suspend fun resume(id: String): ComposeDraft? {
        val row = store.observe().first().firstOrNull { it.id == id } ?: return null
        val draft = decode(row.json) ?: return null
        pendingDraft?.let { flush(it) }
        currentId = id
        saved[id] = encode(draft)
        return draft
    }

    /**
     * [id] 를 보냈다 — 목록에서 빠진다. 보내기가 끝난 뒤에 부른다(실패하면 쓰던 편지가 남아야 한다).
     * 그 사이 새 편지를 열었으면 [currentId] 는 이미 다른 편지다.
     */
    fun sent(id: String) = delete(id)

    /** 쓰던 편지 목록 › 지우기. 지금 쓰는 편지면 다음 글은 새 편지로 남는다. */
    fun delete(id: String) {
        if (id == currentId) {
            pending?.cancel()
            pendingDraft = null
            currentId = newId()
        }
        scope.launch { lock.withLock { saved.remove(id); store.delete(id) } }
    }

    private suspend fun persist(id: String, draft: ComposeDraft) = lock.withLock {
        // 보내는 중인 편지는 곧 편지함으로 간다 — 다시 남기지 않는다.
        if (draft.sending) return@withLock
        if (draft.isEmpty) {
            if (saved.remove(id) != null) store.delete(id)
            return@withLock
        }
        val body = encode(draft)
        if (saved[id] == body) return@withLock
        saved[id] = body
        store.save(id, body, clock.now().toEpochMilliseconds())
    }

    private fun encode(d: ComposeDraft) = json.encodeToString(ComposeDraft.serializer(), d)

    private fun decode(s: String) = runCatching { json.decodeFromString(ComposeDraft.serializer(), s) }.getOrNull()

    private companion object {
        const val AUTOSAVE_MS = 2_000L
    }
}

/** 목록에 보이는 첫 줄: 제목 → 본문 첫 줄 → "그림 n장" · "사진 n장". */
private fun ComposeDraft.firstLine(): String =
    title.takeIf { it.isNotBlank() }?.trim()
        ?: plainText.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
        ?: if (drawings.isNotEmpty()) "그림 ${drawings.size}장" else "사진 ${photos.size}장"

/** 쓰던 편지를 고친 때: "오늘 오후 9:12" · "어제 오후 11:40" · "10월 25일". */
internal fun draftWhen(at: LocalDateTime, now: LocalDateTime): String {
    val days = now.date.toEpochDays() - at.date.toEpochDays()
    if (days != 0L && days != 1L) return "${at.month.ordinal + 1}월 ${at.day}일"
    val ampm = if (at.hour < 12) "오전" else "오후"
    val h = (at.hour % 12).let { if (it == 0) 12 else it }
    return (if (days == 0L) "오늘" else "어제") + " $ampm $h:${at.minute.toString().padStart(2, '0')}"
}

internal fun draftWhen(at: Instant, now: Instant): String = draftWhen(at.toLocalDateTime(SEOUL), now.toLocalDateTime(SEOUL))
