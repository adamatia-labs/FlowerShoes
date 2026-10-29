package com.sadturtleman.flowershoes.core.data

import com.sadturtleman.flowershoes.core.database.AppDatabase
import com.sadturtleman.flowershoes.core.database.LetterDraftEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 쓰던 편지 한 통. [json] 의 모양은 편지 쓰기 화면(:feature:letter)이 정한다. */
data class StoredDraft(val id: String, val json: String, val updatedAtMs: Long)

/**
 * 쓰던 편지(임시저장) 보관함. 이 기기에만 — 동기화하지 않고 복구하지 않는다(planning §8). 개수 제한 없음.
 * 연결 해제 · 탈퇴 때는 [com.sadturtleman.flowershoes.core.data.sync.UnlinkService] 가 표째 비운다.
 */
interface LetterDraftStore {
    /** 마지막으로 고친 것부터. */
    fun observe(): Flow<List<StoredDraft>>

    suspend fun save(id: String, json: String, atMs: Long)

    suspend fun delete(id: String)
}

class RoomLetterDraftStore(private val db: AppDatabase) : LetterDraftStore {
    override fun observe(): Flow<List<StoredDraft>> =
        db.letterDraftDao().observeAll().map { rows -> rows.map { StoredDraft(it.id, it.draftJson, it.updatedAtMs) } }

    override suspend fun save(id: String, json: String, atMs: Long) = db.letterDraftDao().upsert(LetterDraftEntity(id, json, atMs))

    override suspend fun delete(id: String) = db.letterDraftDao().delete(id)
}
