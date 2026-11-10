package com.sadturtleman.flowershoes.core.data

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.sadturtleman.flowershoes.core.database.AppDatabase
import com.sadturtleman.flowershoes.core.database.LetterDraftEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlin.uuid.Uuid

data class StoredDraft(val id: String, val json: String, val updatedAtMs: Long)
data class DraftSession(val ownerUid: String, val deviceId: String, val generation: String)
data class DraftSnapshot(val session: DraftSession?, val drafts: List<StoredDraft> = emptyList(), val unclaimedCount: Int = 0, val loaded: Boolean = true)
/** 편집을 열 때만 발급한다. 지우기/다른 편집/재로그인 뒤에는 이 권한으로 행을 되살릴 수 없다. */
data class DraftEditor(val id: String, val session: DraftSession, val token: String, val initial: StoredDraft? = null)

/** 이 기기에만 남는 편지. 미확인 구버전 초안은 내용 공개 전에 본인 확인으로 복구한다. */
interface LetterDraftStore {
    fun observe(): Flow<DraftSnapshot>
    suspend fun open(session: DraftSession, id: String): DraftEditor?
    suspend fun save(editor: DraftEditor, json: String, atMs: Long): Boolean
    suspend fun retire(editor: DraftEditor): Boolean
    suspend fun delete(session: DraftSession, id: String): Boolean
    suspend fun recoverUnclaimed(session: DraftSession, confirmedOwner: Boolean): Boolean
    /** block은 같은 DB 트랜잭션에서 실행한다. 설정을 다시 읽거나 쓰면 안 된다. */
    suspend fun withCurrentEditor(editor: DraftEditor, block: suspend () -> Unit): Boolean
}

class RoomLetterDraftStore(private val db: AppDatabase, private val settings: DeviceSettings) : LetterDraftStore {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(): Flow<DraftSnapshot> = settings.draftSessions.distinctUntilChanged().flatMapLatest { session ->
        if (session == null) flowOf(DraftSnapshot(null)) else flow {
            // 이미 수집 중인 화면의 이전 계정 목록을 먼저 무효화한다.
            emit(DraftSnapshot(session, loaded = false))
            emitAll(combine(db.letterDraftDao().observeOwned(session.ownerUid), db.letterDraftDao().observeUnclaimedCount()) { rows, count ->
                DraftSnapshot(session, rows.map { StoredDraft(it.id, it.draftJson, it.updatedAtMs) }, if (session.ownerUid.startsWith("local:")) 0 else count)
            })
        }
    }

    override suspend fun open(session: DraftSession, id: String): DraftEditor? {
        var result: DraftEditor? = null
        settings.withDraftSession(session) {
            db.useWriterConnection { tx -> tx.immediateTransaction {
                val old = db.letterDraftDao().get(id)
                if (old != null && old.ownerUid != session.ownerUid) return@immediateTransaction
                val token = Uuid.random().toString()
                db.letterDraftDao().upsert((old ?: LetterDraftEntity(id, "", 0, session.ownerUid)).copy(editorToken = token))
                result = DraftEditor(id, session, token, old?.takeIf { it.draftJson.isNotEmpty() }?.let { StoredDraft(it.id, it.draftJson, it.updatedAtMs) })
            } }
        }
        return result
    }

    override suspend fun save(editor: DraftEditor, json: String, atMs: Long): Boolean {
        var saved = false
        // 전환 전에 이미 열린 편집의 마지막 입력만 원래 계정에 마저 저장한다.
        // 동일 계정으로 다시 로그인했으면 generation 불일치로 거부한다.
        settings.withDraftSession(editor.session, finishCapturedEdit = true) {
            saved = db.letterDraftDao().saveEdited(editor.id, editor.session.ownerUid, editor.token, json, atMs) == 1
        }
        return saved
    }

    override suspend fun retire(editor: DraftEditor): Boolean {
        var deleted = false
        settings.withDraftSession(editor.session, finishCapturedEdit = true) {
            deleted = db.letterDraftDao().retire(editor.id, editor.session.ownerUid, editor.token) == 1
        }
        return deleted
    }

    override suspend fun delete(session: DraftSession, id: String): Boolean {
        var deleted = false
        settings.withDraftSession(session) { deleted = db.letterDraftDao().deleteOwned(id, session.ownerUid) == 1 }
        return deleted
    }

    override suspend fun recoverUnclaimed(session: DraftSession, confirmedOwner: Boolean): Boolean {
        if (!confirmedOwner || session.ownerUid.startsWith("local:")) return false
        return settings.withDraftSession(session) { db.letterDraftDao().claimUnowned(session.ownerUid) }
    }

    override suspend fun withCurrentEditor(editor: DraftEditor, block: suspend () -> Unit): Boolean {
        var applied = false
        settings.withDraftSession(editor.session) {
            db.useWriterConnection { tx -> tx.immediateTransaction {
                val row = db.letterDraftDao().get(editor.id)
                if (row?.ownerUid == editor.session.ownerUid && row.editorToken == editor.token) {
                    block(); applied = true
                }
            } }
        }
        return applied
    }
}
