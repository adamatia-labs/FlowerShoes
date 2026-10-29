package com.sadturtleman.flowershoes.core.data

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.sadturtleman.flowershoes.core.database.AppDatabase
import com.sadturtleman.flowershoes.core.database.DrawingEntity
import com.sadturtleman.flowershoes.core.database.MemoryEntity
import com.sadturtleman.flowershoes.core.database.MetaEntity
import com.sadturtleman.flowershoes.core.database.UserEntity
import com.sadturtleman.flowershoes.core.database.RecvCursorEntity
import com.sadturtleman.flowershoes.core.database.RecvPendingEntity
import com.sadturtleman.flowershoes.core.sync.Lww
import com.sadturtleman.flowershoes.core.sync.ReceiveState
import com.sadturtleman.flowershoes.core.sync.RevokedDevice
import com.sadturtleman.flowershoes.core.sync.RevocationFilter
import com.sadturtleman.flowershoes.core.sync.StreamId
import com.sadturtleman.flowershoes.core.sync.Versioned
import kotlin.time.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.long
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 복호화 · 서명 확인을 마친 Op 하나. */
class ReceivedOp(
    val stream: StreamId,
    val seq: Long,
    val opId: String,
    val lamport: Long,
    val payload: ByteArray,
)

enum class ApplyResult { APPLIED, DUPLICATE, REJECTED_REVOKED, UNKNOWN_SKIPPED }

/** 연결 전 곰신이 입력한 군인 정보의 자리표시 id. 연결하면 상대(군인) userId 로 바뀐다. */
const val PENDING_SOLDIER = "pending:soldier"

/**
 * 받은 Op 을 로컬 DB 에 반영한다. 한 Op 이 한 트랜잭션이다.
 *
 * 1. 폐기된 기기의 cutoff 를 넘으면 버린다.
 * 2. (stream, seq) 로 중복을 거른다.
 * 3. 본문을 반영한다 — 수정 가능한 데이터는 LWW, 추가 전용은 없을 때만 넣고, Receipt 는 먼저 온 값을 남긴다.
 * 4. 커서 · pending 을 옮긴다. 모르는 종류여도 커서는 옮긴다(뒤가 막히지 않게).
 */
class InboxApplier(
    private val db: AppDatabase,
    private val clock: Clock = Clock.System,
) {
    suspend fun apply(op: ReceivedOp): ApplyResult = db.useWriterConnection { tx ->
        tx.immediateTransaction {
            val sync = db.syncDao()
            if (!RevocationFilter(revoked()).accepts(op.stream, op.seq)) return@immediateTransaction ApplyResult.REJECTED_REVOKED

            val state = ReceiveState(sync.cursor(op.stream.wire) ?: 0L, sync.pending(op.stream.wire).toSet())
            if (state.isDuplicate(op.seq)) return@immediateTransaction ApplyResult.DUPLICATE

            val lamport = (sync.meta(SyncWriter.LAMPORT) ?: 0L)
            if (op.lamport > lamport) sync.setMeta(MetaEntity(SyncWriter.LAMPORT, op.lamport))

            val payload = OpCodec.decode(op.payload)
            var next = state.receive(op.seq)
            if (payload != null) {
                applyPayload(payload)
                if (payload is OpPayload.Snapshot) next = next.applySnapshot(payload.coversThrough)
            }
            save(op.stream.wire, op.opId, state, next)
            if (payload == null) ApplyResult.UNKNOWN_SKIPPED else ApplyResult.APPLIED
        }
    }

    private suspend fun revoked(): List<RevokedDevice> = db.syncDao().revoked().map { r ->
        val cutoff = Json.parseToJsonElement(r.cutoffJson).jsonObject.mapKeys { StreamId.parse(it.key) }
            .mapValues { it.value.jsonPrimitive.long }
        RevokedDevice(r.deviceId, r.revokedAtMs.instant(), cutoff)
    }

    private suspend fun save(stream: String, opId: String, before: ReceiveState, after: ReceiveState) {
        val sync = db.syncDao()
        sync.setCursor(RecvCursorEntity(stream, after.contiguousSeq))
        sync.deletePendingThrough(stream, after.contiguousSeq)
        (after.pending - before.pending).forEach { sync.addPending(RecvPendingEntity(stream, it, opId)) }
    }

    private suspend fun applyPayload(p: OpPayload) {
        when (p) {
            is OpPayload.MissYouPut -> db.missYouDao().insert(p.missYou.toEntity())
            is OpPayload.StatusPut -> db.statusDao().upsert(p.status.toEntity())
            is OpPayload.EventPut -> {
                val local = db.eventDao().get(p.event.id)
                val incoming = Versioned(p.event.lamport, p.event.lastWriterDevice, p.event.deletedAt != null)
                val localModel = local?.toModel()
                val notes = com.sadturtleman.flowershoes.core.model.mergeMeetNotes(localModel?.meetNotes.orEmpty(), p.event.meetNotes)
                if (Lww.incomingWins(local?.let { Versioned(it.lamport, it.lastWriterDevice, it.deletedAtMs != null) }, incoming)) {
                    db.eventDao().upsert(p.event.copy(meetNotes = notes).toEntity())
                } else if (localModel != null && notes != localModel.meetNotes) {
                    // 일정은 이 폰 것이 이겨도 만남 메모는 줄마다 합친다(상대가 자기 줄만 고친 경우).
                    db.eventDao().upsert(localModel.copy(meetNotes = notes).toEntity())
                }
            }
            is OpPayload.LeaveGrantPut -> {
                val local = db.leaveDao().get(p.grant.id)
                val incoming = Versioned(p.grant.lamport, p.grant.lastWriterDevice, p.grant.deletedAt != null)
                if (Lww.incomingWins(local?.let { Versioned(it.lamport, it.lastWriterDevice, it.deletedAtMs != null) }, incoming)) {
                    db.leaveDao().upsert(p.grant.toEntity())
                }
            }
            is OpPayload.LetterPut -> {
                if (db.letterDao().get(p.letter.id) == null) {
                    db.letterDao().upsert(p.letter.toEntity(lamport = 0))
                    p.drawings.forEach { d ->
                        db.drawingDao().upsert(
                            DrawingEntity(
                                id = d.id, coupleId = db.coupleDao().get()?.id.orEmpty(), letterId = p.letter.id,
                                orderInLetter = d.orderInLetter, authorId = p.letter.authorId, shape = d.shape,
                                docAttachmentId = d.docAttachmentId, pngAttachmentId = d.pngAttachmentId, thumbPath = "",
                                caption = d.caption, replayEnabled = d.replayEnabled, replayDurationMs = d.replayDurationMs,
                                showOnWidget = d.showOnWidget, sentAtMs = p.letter.visibleAt.ms(),
                                receivedAtMs = clock.now().toEpochMilliseconds(), deletedAtMs = null,
                            ),
                        )
                    }
                    db.memoryDao().upsert(
                        MemoryEntity("letter-${p.letter.id}", "LETTER", p.letter.id, p.letter.visibleAt.toString().take(10)),
                    )
                }
            }
            is OpPayload.ReceiptPut -> db.receiptDao().insertFirst(p.receipt.toEntity())
            is OpPayload.ServicePut -> applyService(p)
            is OpPayload.UserPut -> {
                val local = db.userDao().get(p.userId)
                if (local == null || p.lamport >= local.nicknameLamport) {
                    db.userDao().upsert(UserEntity(p.userId, p.role, p.nickname, p.lamport, p.deviceName.ifBlank { local?.deviceName }, p.perks.joinToString(",").ifBlank { null }))
                }
            }
            is OpPayload.Delete -> delete(p)
            is OpPayload.Snapshot -> p.items.forEach { applyPayload(it) }
        }
    }

    /**
     * 복무 정보. 평소에는 LWW 지만, 연결 직후 혼자 모드 값이 엇갈리면 "군인이 입력한 값"이 이긴다(7-1-1).
     * 곰신이 연결 전에 넣은 값은 [PENDING_SOLDIER] 자리에 있다.
     */
    private suspend fun applyService(p: OpPayload.ServicePut) {
        val s = p.service
        val pending = db.serviceDao().get(PENDING_SOLDIER)
        if (s.userId == PENDING_SOLDIER) {
            // 상대(곰신)가 대신 넣어 둔 내 정보. 내가 직접 넣은 값이 있으면 그것이 이긴다.
            val couple = db.coupleDao().get() ?: return
            if (db.serviceDao().get(couple.myUserId) == null) db.serviceDao().upsert(s.copy(userId = couple.myUserId).toEntity())
            return
        }
        if (pending != null) db.serviceDao().deleteFor(PENDING_SOLDIER)
        val local = db.serviceDao().get(s.userId)
        if (local == null || s.lamport > local.lamport) db.serviceDao().upsert(s.toEntity())
    }

    private suspend fun delete(p: OpPayload.Delete) {
        when (p.entity) {
            DeletableEntity.EVENT -> {
                val local = db.eventDao().get(p.id) ?: return
                val wins = Lww.incomingWins(
                    Versioned(local.lamport, local.lastWriterDevice, local.deletedAtMs != null),
                    Versioned(p.lamport, p.writerDevice, deleted = true),
                )
                if (wins) db.eventDao().upsert(local.copy(deletedAtMs = p.atMs, lamport = p.lamport, lastWriterDevice = p.writerDevice))
            }
            DeletableEntity.LEAVE_GRANT -> {
                val local = db.leaveDao().get(p.id) ?: return
                val wins = Lww.incomingWins(
                    Versioned(local.lamport, local.lastWriterDevice, local.deletedAtMs != null),
                    Versioned(p.lamport, p.writerDevice, deleted = true),
                )
                if (wins) db.leaveDao().upsert(local.copy(deletedAtMs = p.atMs, lamport = p.lamport, lastWriterDevice = p.writerDevice))
            }
            DeletableEntity.LETTER -> {
                val local = db.letterDao().get(p.id) ?: return
                db.letterDao().upsert(local.copy(deletedAtMs = p.atMs))
                db.drawingDao().tombstoneForLetter(p.id, p.atMs)
                db.memoryDao().deleteForRef(p.id)
            }
        }
    }
}
