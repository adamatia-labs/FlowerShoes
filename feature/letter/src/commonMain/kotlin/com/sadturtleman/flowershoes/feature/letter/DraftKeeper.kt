package com.sadturtleman.flowershoes.feature.letter

import com.sadturtleman.flowershoes.core.data.*
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json

data class DraftSummary(val id: String, val firstLine: String, val paper: Paper, val whenLabel: String)
internal data class DraftKeeperState(
    val session: DraftSession? = null,
    val editorId: String,
    val ready: Boolean = false,
    val editorToken: String? = null,
    val drafts: List<DraftSummary> = emptyList(),
    val unclaimedCount: Int = 0,
)

/** 편집 권한과 입력을 함께 보관해 계정 전환 후 다른 소유자에게 쓰지 않는다. */
internal class DraftKeeper(
    private val store: LetterDraftStore,
    private val scope: CoroutineScope,
    private val clock: Clock,
    private val newId: () -> String = { Uuid.random().toString() },
    private val onFailure: () -> Unit = {},
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()
    private var pending: Job? = null
    private var pendingDraft: Pair<DraftEditor, ComposeDraft>? = null
    private val saved = mutableMapOf<String, String>()
    private var editor: DraftEditor? = null
    val currentEditor get() = editor
    private var request = 0L
    private val _state = MutableStateFlow(DraftKeeperState(editorId = newId()))
    val state: StateFlow<DraftKeeperState> = _state
    val currentId get() = state.value.editorId
    val ready get() = state.value.ready
    val drafts: Flow<List<DraftSummary>> = state.map { it.drafts }

    init {
        scope.launch {
            store.observe().collect { snapshot ->
                if (snapshot.session != state.value.session) {
                    flushPending()
                    editor = null
                    val id = newId()
                    _state.value = DraftKeeperState(snapshot.session, id)
                    val version = ++request
                    snapshot.session?.let { session -> scope.launch { openNew(session, id, version) } }
                }
                _state.update { it.copy(drafts = snapshot.drafts.mapNotNull(::summary), unclaimedCount = snapshot.unclaimedCount) }
            }
        }
    }

    private suspend fun openNew(session: DraftSession, id: String, version: Long) {
        val opened = try { store.open(session, id) } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { onFailure(); null }
        if (request != version || state.value.session != session) {
            opened?.takeIf { it.initial == null }?.let { store.retire(it) }
            return
        }
        editor = opened
        _state.update { it.copy(ready = opened != null, editorToken = opened?.token) }
    }

    fun edited(draft: ComposeDraft) {
        val captured = editor ?: return
        pending?.cancel()
        val input = captured to draft
        pendingDraft = input
        pending = scope.launch {
            delay(2_000)
            if (persist(captured, draft) && pendingDraft === input) pendingDraft = null
        }
    }

    fun flush(draft: ComposeDraft, completed: (Boolean) -> Unit = {}) {
        val captured = editor ?: return completed(false)
        pending?.cancel()
        val input = captured to draft
        pendingDraft = input
        pending = scope.launch {
            val success = persist(captured, draft)
            if (success && pendingDraft === input) pendingDraft = null
            completed(success)
        }
    }

    private fun flushPending(): Job? {
        pending?.cancel(); pending = null
        val captured = pendingDraft
        pendingDraft = null
        return if (captured != null) scope.launch { persist(captured.first, captured.second) } else null
    }

    fun startNew() {
        flushPending()
        editor = null
        val id = newId()
        _state.update { it.copy(editorId = id, ready = false, editorToken = null) }
        val version = ++request
        state.value.session?.let { session -> scope.launch { openNew(session, id, version) } }
    }

    suspend fun resume(id: String): ComposeDraft? {
        val session = state.value.session ?: return null
        val version = ++request
        flushPending()?.join()
        if (version != request || state.value.session != session) return null
        val opened = store.open(session, id) ?: return null
        if (version != request || state.value.session != session) return null
        val draft = opened.initial?.let { decode(it.json) }
        if (draft == null) return null
        editor = opened
        saved[opened.token] = encode(draft)
        _state.update { it.copy(editorId = id, ready = true, editorToken = opened.token) }
        return draft
    }

    fun sent(captured: DraftEditor) {
        if (editor?.token == captured.token) { pending?.cancel(); pendingDraft = null; startNew() }
        scope.launch { lock.withLock { store.retire(captured); saved.remove(captured.token) } }
    }

    fun delete(id: String) {
        val session = state.value.session ?: return
        if (id == currentId) { pending?.cancel(); pendingDraft = null; startNew() }
        scope.launch { lock.withLock { store.delete(session, id) } }
    }

    suspend fun recoverUnclaimed(confirmedOwner: Boolean): Boolean {
        val session = state.value.session ?: return false
        return store.recoverUnclaimed(session, confirmedOwner)
    }

    private suspend fun persist(captured: DraftEditor, draft: ComposeDraft): Boolean = lock.withLock {
        val body = if (draft.isEmpty) "" else encode(draft)
        if (saved[captured.token] == body) return@withLock true
        try {
            val success = store.save(captured, body, clock.now().toEpochMilliseconds())
            if (success) saved[captured.token] = body
            success
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { if (editor == captured) onFailure(); false }
    }

    private fun summary(row: StoredDraft): DraftSummary? {
        val d = decode(row.json) ?: return null
        return DraftSummary(row.id, d.firstLine(), Paper.of(d.paper), draftWhen(Instant.fromEpochMilliseconds(row.updatedAtMs), clock.now()))
    }
    private fun encode(d: ComposeDraft) = json.encodeToString(ComposeDraft.serializer(), d)
    private fun decode(s: String) = runCatching { json.decodeFromString(ComposeDraft.serializer(), s) }.getOrNull()
}

private fun ComposeDraft.firstLine(): String =
    title.takeIf { it.isNotBlank() }?.trim()
        ?: plainText.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
        ?: if (drawings.isNotEmpty()) "그림 ${drawings.size}장" else "사진 ${photos.size}장"

internal fun draftWhen(at: LocalDateTime, now: LocalDateTime): String {
    val days = now.date.toEpochDays() - at.date.toEpochDays()
    if (days != 0L && days != 1L) return "${at.month.ordinal + 1}월 ${at.day}일"
    val ampm = if (at.hour < 12) "오전" else "오후"
    val h = (at.hour % 12).let { if (it == 0) 12 else it }
    return (if (days == 0L) "오늘" else "어제") + " $ampm $h:${at.minute.toString().padStart(2, '0')}"
}
internal fun draftWhen(at: Instant, now: Instant): String = draftWhen(at.toLocalDateTime(SEOUL), now.toLocalDateTime(SEOUL))
