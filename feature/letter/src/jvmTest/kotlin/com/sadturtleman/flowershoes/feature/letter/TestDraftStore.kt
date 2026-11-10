package com.sadturtleman.flowershoes.feature.letter

import com.sadturtleman.flowershoes.core.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.uuid.Uuid

/** UI scheduling fixture. Storage isolation itself is tested against Room in core:data. */
internal class TestDraftStore(owner: String = "a") : LetterDraftStore {
    var session = DraftSession(owner, "device", "session-1")
        private set
    val rows = MutableStateFlow<List<StoredDraft>>(emptyList())
    private val snapshots = MutableStateFlow(DraftSnapshot(session))
    private val owners = mutableMapOf<String, String>()
    private val tokens = mutableMapOf<String, String>()
    private val allRows = mutableMapOf<String, StoredDraft>()
    var failNextSave = false
    fun switchAccount(owner: String) {
        session = DraftSession(owner, "device", Uuid.random().toString())
        publish()
    }
    fun owned(owner: String) = allRows.values.filter { owners[it.id] == owner && it.json.isNotEmpty() }.sortedByDescending { it.updatedAtMs }
    private fun publish() {
        rows.value = owned(session.ownerUid)
        snapshots.value = DraftSnapshot(session, rows.value)
    }
    override fun observe() = snapshots
    override suspend fun open(session: DraftSession, id: String): DraftEditor? {
        if (session != this.session || owners[id]?.let { it != session.ownerUid } == true) return null
        val token = Uuid.random().toString()
        owners[id] = session.ownerUid; tokens[id] = token
        return DraftEditor(id, session, token, allRows[id]?.takeIf { it.json.isNotEmpty() })
    }
    private fun valid(editor: DraftEditor) = tokens[editor.id] == editor.token && owners[editor.id] == editor.session.ownerUid &&
        (session == editor.session || session.ownerUid != editor.session.ownerUid)
    override suspend fun save(editor: DraftEditor, json: String, atMs: Long): Boolean {
        if (failNextSave) { failNextSave = false; error("storage unavailable") }
        if (!valid(editor)) return false
        allRows[editor.id] = StoredDraft(editor.id, json, atMs); publish(); return true
    }
    override suspend fun retire(editor: DraftEditor): Boolean {
        if (!valid(editor)) return false
        allRows.remove(editor.id); owners.remove(editor.id); tokens.remove(editor.id); publish(); return true
    }
    override suspend fun delete(session: DraftSession, id: String): Boolean {
        if (session != this.session || owners[id] != session.ownerUid) return false
        allRows.remove(id); owners.remove(id); tokens.remove(id); publish(); return true
    }
    override suspend fun recoverUnclaimed(session: DraftSession, confirmedOwner: Boolean) = session == this.session && confirmedOwner
}
