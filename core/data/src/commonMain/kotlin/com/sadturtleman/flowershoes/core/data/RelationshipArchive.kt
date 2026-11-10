package com.sadturtleman.flowershoes.core.data

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.sadturtleman.flowershoes.core.database.AppDatabase
import com.sadturtleman.flowershoes.core.database.CoupleEntity
import com.sadturtleman.flowershoes.core.database.MetaEntity
import com.sadturtleman.flowershoes.core.model.Event
import com.sadturtleman.flowershoes.core.model.LetterBlock
import com.sadturtleman.flowershoes.core.model.ReceiptEntity

/**
 * 보관한 관계의 행과 첨부는 이 폰에서만 읽는다. 표 구조를 바꾸지 않고 기존 meta에 영구 표식을 둔다.
 * 삭제한 행의 표식도 남겨 두어 오래된 전송 로그나 스냅샷이 다시 공유하지 못하게 한다.
 */
internal class RelationshipArchive(private val db: AppDatabase) {
    /**
     * 구버전에는 기록의 소속이 없다. 기존 연결이 있으면 혼합됐을 수 있는 모든 공유 기록을 먼저 보관한다.
     * 새 설치의 혼자 모드는 커플 행이 없으므로 그대로 유지한다. 새 연결은 커플 행을 쓰기 전에 초기화한다.
     */
    suspend fun prepare() = db.useWriterConnection { tx ->
        tx.immediateTransaction {
            val couple = db.coupleDao().get()
            if (db.syncDao().meta(INITIALIZED) == null) {
                if (couple != null) {
                    // active 상태에서도 구버전 재연결로 이전 연인의 기록이 섞였는지 구별할 수 없다.
                    // 이후 해제는 별도 경계에서 새로 쓴 기록까지 보관해야 한다.
                    if (couple.partnerUserId.isNotBlank()) archive(couple, "archive:legacy:${couple.id}")
                    else archive(couple)
                }
                db.syncDao().setMeta(MetaEntity(INITIALIZED, 1))
            }
            couple?.takeIf { it.partnerUserId.isBlank() && it.keyEpoch == 0 }?.let { archive(it) }
        }
    }

    /** 호출자는 연결 해제와 같은 트랜잭션 안에서 실행한다. 반복 호출은 새 혼자 모드 기록을 보관하지 않는다. */
    suspend fun archive(couple: CoupleEntity, boundary: String = "archive:relationship:${couple.id}") = db.useWriterConnection { tx ->
        tx.immediateTransaction {
            if (db.syncDao().meta(boundary) != null) return@immediateTransaction
            // 관계마다 다시 쓰는 고정 id는 보관용 id로 옮긴다. 새 관계의 기념일과 표지는 계속 쓸 수 있다.
            val oldAnniversary = db.eventDao().get(Event.COUPLE_ANNIVERSARY_ID)
            if (oldAnniversary != null) {
                db.eventDao().upsert(oldAnniversary.copy(id = "archived:${couple.id}:${oldAnniversary.id}"))
                usePrepared("DELETE FROM event WHERE id = ?") { it.bindText(1, oldAnniversary.id); it.step() }
            }
            db.bookCoverDao().get()?.let { cover ->
                db.bookCoverDao().upsert(cover.copy(id = "archived:${couple.id}:book"))
                usePrepared("DELETE FROM book_cover WHERE id = 'book'") { it.step() }
            }
            db.serviceDao().get(PENDING_SOLDIER)?.let { service ->
                db.serviceDao().upsert(service.copy(userId = "archived:${couple.id}:$PENDING_SOLDIER"))
                db.serviceDao().deleteFor(PENDING_SOLDIER)
            }
            for ((table, entity) in listOf(
                "letter" to "letter", "drawing" to "drawing", "event" to "event",
                "leave_grant" to "leave", "miss_you" to "missyou", "status" to "status",
                "attachment" to "attachment", "book_cover" to "book_cover",
            )) {
                usePrepared("INSERT OR IGNORE INTO meta (`key`, value) SELECT 'archive:$entity:' || id, 1 FROM $table") { it.step() }
            }
            // 원본이 이미 삭제된 읽음도 이전 관계의 정보다. 대상 id의 표식을 함께 남긴다.
            usePrepared("INSERT OR IGNORE INTO meta (`key`, value) SELECT 'archive:' || CASE WHEN entity = 'DRAWING' THEN 'drawing:' ELSE 'letter:' END || entityId, 1 FROM receipt") { it.step() }
            db.syncDao().setMeta(MetaEntity(boundary, 1))
        }
    }

    suspend fun contains(entity: String, id: String): Boolean = db.syncDao().meta("archive:$entity:$id") != null

    private suspend fun currentUser(id: String): Boolean {
        val couple = db.coupleDao().get() ?: return true
        return id == couple.myUserId || id == couple.partnerUserId || id == PENDING_SOLDIER
    }

    /** 로컬 로그, 일반/기기교체 스냅샷 모두 이 경계를 거친다. null은 전송하지 않는 항목이다. */
    suspend fun shared(payload: OpPayload, currentOnly: Boolean = false): OpPayload? {
        val blocked = when (payload) {
            is OpPayload.Snapshot -> return payload.copy(items = payload.items.mapNotNull { shared(it, currentOnly) })
            is OpPayload.LetterPut -> contains("letter", payload.letter.id) ||
                payload.letter.blocks.filterIsInstance<LetterBlock.Photo>().any { contains("attachment", it.attachmentId) } ||
                payload.letter.blocks.filterIsInstance<LetterBlock.DrawingRef>().any { contains("drawing", it.drawingId) } ||
                payload.drawings.any { d -> contains("drawing", d.id) || listOfNotNull(d.docAttachmentId, d.pngAttachmentId, d.photoAttachmentId).any { contains("attachment", it) } }
            is OpPayload.EventPut -> contains("event", payload.event.id) || payload.event.photoIds.any { contains("attachment", it) } ||
                (currentOnly && payload.event.id == Event.COUPLE_ANNIVERSARY_ID && db.eventDao().get(payload.event.id) != payload.event.toEntity())
            is OpPayload.LeaveGrantPut -> contains("leave", payload.grant.id)
            is OpPayload.MissYouPut -> contains("missyou", payload.missYou.id)
            is OpPayload.StatusPut -> contains("status", payload.status.id)
            is OpPayload.ReceiptPut -> contains(if (payload.receipt.entity == ReceiptEntity.DRAWING) "drawing" else "letter", payload.receipt.entityId)
            is OpPayload.UserPut -> !currentUser(payload.userId)
            is OpPayload.ServicePut -> !currentUser(payload.service.userId) ||
                (currentOnly && payload.service.userId == PENDING_SOLDIER && db.serviceDao().get(PENDING_SOLDIER)?.toModel() != payload.service)
            is OpPayload.BookCoverPut -> currentOnly && db.bookCoverDao().get()?.let { it.cover == payload.cover && it.lamport == payload.lamport && it.lastWriterDevice == payload.writerDevice } != true
            is OpPayload.Delete -> contains(when (payload.entity) {
                DeletableEntity.EVENT -> "event"
                DeletableEntity.LETTER -> "letter"
                DeletableEntity.LEAVE_GRANT -> "leave"
            }, payload.id)
        }
        return payload.takeUnless { blocked }
    }

    private companion object {
        const val INITIALIZED = "archive:initialized"
    }
}
