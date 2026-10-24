package com.sadturtleman.flowershoes.core.data.sync

import com.sadturtleman.flowershoes.core.crypto.Aead
import com.sadturtleman.flowershoes.core.crypto.CryptoException
import com.sadturtleman.flowershoes.core.crypto.OpEnvelope
import com.sadturtleman.flowershoes.core.crypto.OpHeader
import com.sadturtleman.flowershoes.core.crypto.Signatures
import com.sadturtleman.flowershoes.core.crypto.Sodium
import com.sadturtleman.flowershoes.core.data.AttachmentStore
import com.sadturtleman.flowershoes.core.data.Identity
import com.sadturtleman.flowershoes.core.data.InboxApplier
import com.sadturtleman.flowershoes.core.data.OpCodec
import com.sadturtleman.flowershoes.core.data.OpPayload
import com.sadturtleman.flowershoes.core.data.ReceivedOp
import com.sadturtleman.flowershoes.core.data.SyncWriter
import com.sadturtleman.flowershoes.core.data.toModel
import com.sadturtleman.flowershoes.core.database.AppDatabase
import com.sadturtleman.flowershoes.core.database.CoupleEntity
import com.sadturtleman.flowershoes.core.database.MetaEntity
import com.sadturtleman.flowershoes.core.database.PeerCursorEntity
import com.sadturtleman.flowershoes.core.database.SyncOpEntity
import com.sadturtleman.flowershoes.core.model.AttachmentKind
import com.sadturtleman.flowershoes.core.sync.OpKind
import com.sadturtleman.flowershoes.core.sync.ReceiveState
import com.sadturtleman.flowershoes.core.sync.ResendPlan
import com.sadturtleman.flowershoes.core.sync.ResendPlanner
import com.sadturtleman.flowershoes.core.sync.SendLog
import com.sadturtleman.flowershoes.core.sync.StreamId
import com.sadturtleman.flowershoes.core.sync.SyncOp
import kotlin.time.Clock
import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 한 번 돌린 결과. 화면 · 로그용. */
data class SyncReport(val sent: Int = 0, val received: Int = 0, val resent: Int = 0, val snapshot: Boolean = false, val rejected: Int = 0)

/**
 * 동기화 한 바퀴(설계 6-2 · 6-3).
 *
 * 1. 보내기: 내 스트림의 Op 로그 중 아직 올리지 않은 것을 커플 키로 암호화 · 서명해 상대 기기 우편함에 올린다.
 *    편지 안 그림 문서는 무작위 키로 암호화해 blob 으로 따로 올리고, 그 키는 Op 본문(역시 암호문)에 싣는다.
 * 2. 받기: 내 우편함을 읽어 서명 확인 → 복호화 → 반영(중복 · LWW · cutoff 는 [InboxApplier]) → 우편함에서 지운다.
 * 3. 커서: 내 커서를 control 문서로 올리고, 상대 control 을 읽어 확인받은 로그를 지우고 재전송 · 스냅샷을 정한다.
 *
 * 푸시를 받았을 때 · 앱이 앞으로 나올 때 · 쓰기 직후 부른다. 여러 번 불러도 안전하다.
 */
class SyncClient(
    private val db: AppDatabase,
    private val identity: Identity,
    private val vault: KeyVault,
    private val mailbox: Mailbox,
    private val blobs: BlobStore,
    private val attachments: AttachmentStore,
    private val inbox: InboxApplier,
    private val writer: SyncWriter,
    private val clock: Clock = Clock.System,
    private val planner: ResendPlanner = ResendPlanner(),
) {
    private val myStream get() = StreamId(identity.deviceId, StreamId.Channel.APP)

    suspend fun syncOnce(): SyncReport {
        Sodium.init()
        val couple = db.coupleDao().get() ?: return SyncReport()
        if (couple.partnerUserId.isEmpty()) return SyncReport() // 혼자 모드
        val partnerDevice = db.deviceDao().activeFor(couple.partnerUserId) ?: return SyncReport()

        val received = pull(couple)
        val plan = handlePeerControl(couple, partnerDevice.id, partnerDevice.signPub)
        val sent = push(couple, partnerDevice.id)
        publishControl(couple)
        return SyncReport(sent = sent, received = received.first, rejected = received.second, resent = plan.first, snapshot = plan.second)
    }

    // ── 보내기 ────────────────────────────────────────────────────────────

    private suspend fun push(couple: CoupleEntity, partnerDevice: String): Int {
        val stream = myStream.wire
        val uploaded = db.syncDao().meta(uploadedKey(stream)) ?: 0L
        val ops = db.syncDao().opsAfter(stream, uploaded)
        ops.forEach { op ->
            mailbox.put(couple.id, partnerDevice, seal(couple, op, resend = false))
            db.syncDao().setMeta(MetaEntity(uploadedKey(stream), op.seq))
        }
        return ops.size
    }

    private suspend fun seal(couple: CoupleEntity, op: SyncOpEntity, resend: Boolean): MailItem {
        val key = checkNotNull(vault.currentCoupleKey()) { "커플 키가 없다" }
        val signer = checkNotNull(vault.deviceKeys()) { "기기 키가 없다" }
        val payload = withBlobs(couple, op.payload)
        val header = OpHeader(op.opId, op.stream, op.seq, key.epoch)
        val env = OpEnvelope.seal(header, Plain(op.lamport, payload).encode(), key, signer)
        return MailItem(op.opId, op.kind, identity.deviceId, header.toBytes(), env.ciphertext, env.signature, resend)
    }

    /** 편지 안 그림 문서를 blob 으로 올리고, blob id · 키를 Op 본문에 적는다(스냅샷 안의 편지도). */
    private suspend fun withBlobs(couple: CoupleEntity, payload: ByteArray): ByteArray {
        val p = OpCodec.decode(payload) ?: return payload
        val transformed = uploadBlobs(couple, p)
        return if (transformed === p) payload else OpCodec.encode(transformed)
    }

    private suspend fun uploadBlobs(couple: CoupleEntity, p: OpPayload): OpPayload = when (p) {
        is OpPayload.Snapshot -> p.copy(items = p.items.map { uploadBlobs(couple, it) })
        is OpPayload.EventPut -> if (p.event.photoIds.isEmpty() || p.event.deletedAt != null) p else p.copy(
            photos = p.event.photoIds.mapNotNull { id -> upload(couple, id)?.let { (blobId, key) -> com.sadturtleman.flowershoes.core.data.PhotoRef(id, blobId, key) } },
        )
        is OpPayload.LetterPut -> if (p.drawings.isEmpty() && p.letter.photoCount == 0) p else p.copy(
            photos = p.letter.blocks.filterIsInstance<com.sadturtleman.flowershoes.core.model.LetterBlock.Photo>().mapNotNull { b ->
                upload(couple, b.attachmentId)?.let { (blobId, key) -> com.sadturtleman.flowershoes.core.data.PhotoRef(b.attachmentId, blobId, key) }
            },
            drawings = p.drawings.map { d ->
                var out = d
                d.docAttachmentId?.let { id -> upload(couple, id)?.let { (blobId, key) -> out = out.copy(docBlobId = blobId, docBlobKey = key) } }
                if (d.pngAttachmentId != d.docAttachmentId) {
                    upload(couple, d.pngAttachmentId)?.let { (blobId, key) -> out = out.copy(pngBlobId = blobId, pngBlobKey = key) }
                }
                d.photoAttachmentId?.let { id -> upload(couple, id)?.let { (blobId, key) -> out = out.copy(photoBlobId = blobId, photoBlobKey = key) } }
                out
            },
        )
        else -> p
    }

    // ── 받기 ──────────────────────────────────────────────────────────────

    private suspend fun pull(couple: CoupleEntity): Pair<Int, Int> {
        var applied = 0
        var rejected = 0
        for (item in mailbox.fetch(couple.id, identity.deviceId)) {
            val ok = runCatching { receive(couple, item) }.getOrElse { false }
            if (ok) applied++ else rejected++
            // 풀 수 없는 것(서명 불일치 · 키 없음)도 지운다. 남겨 두면 매번 다시 실패한다. 빠진 번호는 재요청으로 메운다.
            mailbox.delete(couple.id, identity.deviceId, item.opId)
        }
        return applied to rejected
    }

    /** iOS: NSE 가 App Group 에 받아 둔 Op 을 반영한다(우편함과 같은 확인 · 중복 제거를 거친다). */
    suspend fun receiveQueued(items: List<MailItem>): Int {
        val couple = db.coupleDao().get() ?: return 0
        return items.count { runCatching { receive(couple, it) }.getOrDefault(false) }
    }

    suspend fun receiveQueued(item: MailItem): Boolean = receiveQueued(listOf(item)) == 1

    private suspend fun receive(couple: CoupleEntity, item: MailItem): Boolean {
        val sender = db.deviceDao().get(item.from) ?: return false
        val header = OpHeader.fromBytes(item.header)
        if (header.opId != item.opId) return false
        val key = vault.coupleKey(header.keyEpoch) ?: return false
        val plain = try {
            Plain.decode(OpEnvelope.open(OpEnvelope(header, item.ciphertext, item.signature), sender.signPub) { if (it == key.epoch) key else null })
        } catch (_: CryptoException) {
            return false
        }
        downloadBlobs(couple, plain.payload)
        inbox.apply(ReceivedOp(StreamId.parse(header.stream), header.seq, header.opId, plain.lamport, plain.payload))
        return true
    }

    /** 첨부 하나를 무작위 키로 암호화해 blob 으로. 돌려주는 값은 (blob id, base64 키). */
    private suspend fun upload(couple: CoupleEntity, attachmentId: String): Pair<String, String>? {
        val bytes = attachments.read(attachmentId) ?: return null
        val blobKey = Sodium.random(Aead.KEY_BYTES)
        val blobId = Uuid.random().toString()
        blobs.put(couple.id, blobId, Aead.seal(blobKey, bytes))
        return blobId to blobKey.b64()
    }

    private suspend fun download(couple: CoupleEntity, blobId: String?, key: String?, attachmentId: String?, kind: AttachmentKind) {
        if (blobId == null || key == null || attachmentId == null) return
        val sealed = blobs.get(couple.id, blobId) ?: return
        attachments.putWithId(attachmentId, kind, Aead.open(key.unb64(), sealed))
        blobs.delete(couple.id, blobId)
    }

    /** 편지 안 그림 문서를 받아 보낸 쪽과 같은 첨부 id 로 둔다. 다 받은 뒤에 편지를 반영한다(반쯤 받은 편지를 보여 주지 않게). */
    private suspend fun downloadBlobs(couple: CoupleEntity, payload: ByteArray) {
        OpCodec.decode(payload)?.let { fetchBlobs(couple, it) }
    }

    private suspend fun fetchBlobs(couple: CoupleEntity, p: OpPayload) {
        when (p) {
            is OpPayload.Snapshot -> p.items.forEach { fetchBlobs(couple, it) }
            // 이미 받은 사진은 다시 받지 않는다(일정을 고칠 때마다 사진이 함께 다시 온다).
            is OpPayload.EventPut -> p.photos.filter { attachments.read(it.attachmentId) == null }
                .forEach { ph -> download(couple, ph.blobId, ph.blobKey, ph.attachmentId, AttachmentKind.PHOTO) }
            is OpPayload.LetterPut -> {
                p.drawings.forEach { d ->
                    download(couple, d.docBlobId, d.docBlobKey, d.docAttachmentId, AttachmentKind.DRAWING_DOC)
                    download(couple, d.pngBlobId, d.pngBlobKey, d.pngAttachmentId, AttachmentKind.DRAWING_PNG)
                    download(couple, d.photoBlobId, d.photoBlobKey, d.photoAttachmentId, AttachmentKind.PHOTO)
                }
                p.photos.forEach { ph -> download(couple, ph.blobId, ph.blobKey, ph.attachmentId, AttachmentKind.PHOTO) }
            }
            else -> Unit
        }
    }

    // ── 커서 · 재전송 ─────────────────────────────────────────────────────

    @Serializable
    private data class ControlPayload(val cursors: Map<String, Long>, val missing: Map<String, List<Long>>, val atMs: Long)

    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun publishControl(couple: CoupleEntity) {
        val signer = vault.deviceKeys() ?: return
        val cursors = db.syncDao().cursors()
        val payload = ControlPayload(
            cursors = cursors.associate { it.stream to it.contiguousSeq },
            missing = cursors.associate { c -> c.stream to ReceiveState(c.contiguousSeq, db.syncDao().pending(c.stream).toSet()).missing() }
                .filterValues { it.isNotEmpty() },
            atMs = clock.now().toEpochMilliseconds(),
        )
        val bytes = json.encodeToString(ControlPayload.serializer(), payload).encodeToByteArray()
        mailbox.putControl(couple.id, identity.deviceId, ControlDoc(bytes, signer.sign(bytes)))
    }

    /** 상대 control: 확인받은 로그를 지우고, 재전송 또는 스냅샷. 돌려주는 값은 (재전송 개수, 스냅샷 여부). */
    private suspend fun handlePeerControl(couple: CoupleEntity, partnerDevice: String, partnerSignPub: ByteArray): Pair<Int, Boolean> {
        val doc = mailbox.getControl(couple.id, partnerDevice) ?: return 0 to false
        if (!Signatures.verify(doc.payload, doc.signature, partnerSignPub)) return 0 to false
        val control = runCatching { json.decodeFromString(ControlPayload.serializer(), doc.payload.decodeToString()) }.getOrNull() ?: return 0 to false
        val stream = myStream.wire
        val peerCursor = control.cursors[stream] ?: 0L
        db.syncDao().setPeerCursor(PeerCursorEntity(stream, peerCursor))
        db.syncDao().deleteThrough(stream, peerCursor)

        val uploaded = db.syncDao().meta(uploadedKey(stream)) ?: 0L
        val log = sendLog(stream)
        // 아직 한 번도 올리지 않은 Op 은 재전송 대상이 아니다(push 가 보낸다).
        val logUploaded = log.copy(ops = log.ops.filter { it.seq <= uploaded }, nextSeq = minOf(log.nextSeq, uploaded + 1))
        return when (val plan = planner.plan(logUploaded, peerCursor, control.missing[stream].orEmpty(), clock.now())) {
            ResendPlan.Nothing -> 0 to false
            is ResendPlan.Resend -> {
                val rows = db.syncDao().log(stream).associateBy { it.seq }
                plan.ops.forEach { op -> rows[op.seq]?.let { mailbox.put(couple.id, partnerDevice, seal(couple, it, resend = true)) } }
                plan.ops.size to false
            }
            is ResendPlan.Snapshot -> {
                writeSnapshot(plan.coversThrough)
                0 to true
            }
        }
    }

    private suspend fun sendLog(stream: String): SendLog {
        val rows = db.syncDao().log(stream)
        val next = db.syncDao().nextSeq(stream) ?: 1L
        val sid = StreamId.parse(stream)
        return SendLog(
            sid,
            rows.map { SyncOp(sid, it.seq, it.opId, OpKind.fromWire(it.kind) ?: OpKind.EVENT, it.entity, it.entityId, it.payload, it.lamport, it.keyEpoch, kotlin.time.Instant.fromEpochMilliseconds(it.createdAtMs)) },
            next,
        )
    }

    /**
     * 스냅샷: 지금 DB 상태로 내가 쓴 것 전부(내 정보 · 복무 · 내가 마지막으로 고친 일정 · 내가 쓴 편지).
     * 받는 쪽은 lamport 로 병합하고, 이 스트림을 [coversThrough] 까지 반영한 것으로 처리한다.
     */
    suspend fun writeSnapshot(coversThrough: Long) {
        val couple = db.coupleDao().get()
        val items = buildList<OpPayload> {
            db.userDao().get(identity.userId)?.let { add(OpPayload.UserPut(it.id, it.role, it.nickname, it.nicknameLamport, it.deviceName.orEmpty(), it.perks?.split(',')?.filter { p -> p.isNotBlank() }.orEmpty())) }
            db.serviceDao().get(identity.userId)?.let { add(OpPayload.ServicePut(it.toModel())) }
            db.serviceDao().get(com.sadturtleman.flowershoes.core.data.PENDING_SOLDIER)?.let { add(OpPayload.ServicePut(it.toModel())) }
            couple?.anniversary?.let { add(OpPayload.AnniversaryPut(it, couple.anniversaryLamport)) }
            db.eventDao().lastWrittenBy(identity.deviceId).forEach { add(OpPayload.EventPut(it.toModel())) }
            db.leaveDao().lastWrittenBy(identity.deviceId).forEach { add(OpPayload.LeaveGrantPut(it.toModel())) }
            db.letterDao().byAuthor(identity.userId).forEach { l ->
                val drawings = db.drawingDao().forLetter(l.id).map {
                    com.sadturtleman.flowershoes.core.data.DrawingMeta(it.id, it.orderInLetter, it.shape, it.docAttachmentId, it.pngAttachmentId, it.caption, it.replayEnabled, it.replayDurationMs, showOnWidget = it.showOnWidget, photoAttachmentId = photoOf(it.docAttachmentId))
                }
                add(OpPayload.LetterPut(l.toModel(), drawings))
            }
        }
        writer.write(
            OpKind.SNAPSHOT, "snapshot", "snapshot",
            localWrite = {},
            payload = { OpPayload.Snapshot(coversThrough, items) },
        )
    }

    /**
     * 넘겨주기(7-3-B) 스냅샷: 내가 가진 공유 데이터 전체(양쪽이 쓴 편지 · 그림 · 일정 · 읽음 · 보고싶어 · 정보).
     * 새 기기는 내 스트림 커서가 없으므로 지금까지의 번호를 모두 반영한 것으로 처리하게 한다.
     */
    suspend fun writeFullSnapshot() {
        val stream = myStream.wire
        val couple = db.coupleDao().get()
        val items = buildList<OpPayload> {
            db.userDao().all().forEach { add(OpPayload.UserPut(it.id, it.role, it.nickname, it.nicknameLamport, it.deviceName.orEmpty(), it.perks?.split(',')?.filter { p -> p.isNotBlank() }.orEmpty())) }
            db.serviceDao().all().forEach { add(OpPayload.ServicePut(it.toModel())) }
            couple?.anniversary?.let { add(OpPayload.AnniversaryPut(it, couple.anniversaryLamport)) }
            db.eventDao().all().forEach { add(OpPayload.EventPut(it.toModel())) }
            db.leaveDao().all().forEach { add(OpPayload.LeaveGrantPut(it.toModel())) }
            db.letterDao().allActive().forEach { l -> add(OpPayload.LetterPut(l.toModel(), drawingMetas(l.id))) }
            db.missYouDao().all().forEach { add(OpPayload.MissYouPut(com.sadturtleman.flowershoes.core.model.MissYou(it.id, it.authorId, kotlin.time.Instant.fromEpochMilliseconds(it.sentAtMs)))) }
            db.receiptDao().all().forEach {
                add(OpPayload.ReceiptPut(com.sadturtleman.flowershoes.core.model.Receipt(com.sadturtleman.flowershoes.core.model.ReceiptEntity.valueOf(it.entity), it.entityId, it.readerId, kotlin.time.Instant.fromEpochMilliseconds(it.atMs))))
            }
        }
        val covers = (db.syncDao().nextSeq(stream) ?: 1L) - 1
        writer.write(OpKind.SNAPSHOT, "snapshot", "full", localWrite = {}, payload = { OpPayload.Snapshot(covers, items) })
    }

    /** 그림 문서가 가리키는 배경 사진 첨부(스냅샷에서 사진도 함께 보내려고). DB 에 따로 두지 않고 문서에서 읽는다. */
    private suspend fun photoOf(docAttachmentId: String?): String? {
        val bytes = docAttachmentId?.let { attachments.read(it) } ?: return null
        // 깨진 문서는 사진 없이 보낸다(스냅샷 전체를 막지 않는다).
        val doc = (runCatching { com.sadturtleman.flowershoes.core.drawing.DrawingCodec.decode(bytes) }.getOrNull() as? com.sadturtleman.flowershoes.core.drawing.DrawingCodec.Result.Ok)?.document
        return doc?.elements?.filterIsInstance<com.sadturtleman.flowershoes.core.drawing.PhotoBackground>()?.firstOrNull()?.attachmentId
    }

    private suspend fun drawingMetas(letterId: String) = db.drawingDao().forLetter(letterId).map {
        com.sadturtleman.flowershoes.core.data.DrawingMeta(it.id, it.orderInLetter, it.shape, it.docAttachmentId, it.pngAttachmentId, it.caption, it.replayEnabled, it.replayDurationMs, showOnWidget = it.showOnWidget, photoAttachmentId = photoOf(it.docAttachmentId))
    }

    private fun uploadedKey(stream: String) = "uploaded:$stream"
}

/** Op 평문 = lamport(8바이트) ‖ 본문. lamport 는 받는 쪽 시계를 맞추는 데 쓴다. */
internal class Plain(val lamport: Long, val payload: ByteArray) {
    fun encode(): ByteArray = ByteArray(8) { (lamport ushr (56 - 8 * it)).toByte() } + payload

    companion object {
        fun decode(bytes: ByteArray): Plain {
            if (bytes.size < 8) throw CryptoException("평문이 짧다")
            var l = 0L
            for (i in 0 until 8) l = (l shl 8) or (bytes[i].toLong() and 0xFF)
            return Plain(l, bytes.copyOfRange(8, bytes.size))
        }
    }
}
