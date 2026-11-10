package com.sadturtleman.flowershoes.core.data.sync

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.sadturtleman.flowershoes.core.crypto.Aead
import com.sadturtleman.flowershoes.core.crypto.CryptoException
import com.sadturtleman.flowershoes.core.crypto.OpEnvelope
import com.sadturtleman.flowershoes.core.crypto.OpHeader
import com.sadturtleman.flowershoes.core.crypto.Signatures
import com.sadturtleman.flowershoes.core.crypto.Sodium
import com.sadturtleman.flowershoes.core.data.AttachmentStore
import com.sadturtleman.flowershoes.core.data.AttachmentConflictException
import okio.ByteString.Companion.toByteString
import com.sadturtleman.flowershoes.core.data.Identity
import com.sadturtleman.flowershoes.core.data.InboxApplier
import com.sadturtleman.flowershoes.core.data.OpCodec
import com.sadturtleman.flowershoes.core.data.OpPayload
import com.sadturtleman.flowershoes.core.data.ReceivedOp
import com.sadturtleman.flowershoes.core.data.RelationshipArchive
import com.sadturtleman.flowershoes.core.data.SyncWriter
import com.sadturtleman.flowershoes.core.data.toModel
import com.sadturtleman.flowershoes.core.database.AppDatabase
import com.sadturtleman.flowershoes.core.database.CoupleEntity
import com.sadturtleman.flowershoes.core.database.DeviceEntity
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 아직 상대에게 올리지 않은 내 기록: 편지 · 그림 수(연결 직후 "편지 N통 · 그림 M장 보내는 중")와 Op 수. */
data class Outbox(val letters: Int, val drawings: Int, val ops: Int) {
    val isEmpty: Boolean get() = ops == 0
}

/**
 * 한 번 돌린 결과. 화면 · 로그용.
 * [skipped]: 크기 한도(Op 암호문 67.5KB · blob 10MB)를 넘어 건너뛴 항목 수(#20 결정 5). 그 뒤 스트림은 멈추지 않는다.
 */
data class SyncReport(val sent: Int = 0, val received: Int = 0, val resent: Int = 0, val snapshot: Boolean = false, val rejected: Int = 0, val skipped: Int = 0)

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
    vault: KeyVault,
    private val mailbox: Mailbox,
    private val blobs: BlobStore,
    private val attachments: AttachmentStore,
    private val inbox: InboxApplier,
    private val writer: SyncWriter,
    private val clock: Clock = Clock.System,
    private val planner: ResendPlanner = ResendPlanner(),
) {
    private val vault = vault.forDatabase(db, identity)
    private val myStream get() = StreamId(identity.deviceId, StreamId.Channel.APP)
    private val widgetStream get() = StreamId(identity.deviceId, StreamId.Channel.WIDGET).wire
    private val archive = RelationshipArchive(db)

    suspend fun reconnect() = mailbox.reconnect()

    /** 동기화가 실패한 까닭을 서버 오류 화면에 보일 코드로. 우편함 구현이 안다. */
    fun errorCode(error: Throwable): String? = mailbox.errorCode(error)

    /**
     * 이번 바퀴에서 한도를 넘어 건너뛴 항목 수. 동기화 루프와 승인 화면이 함께 부를 수 있어
     * 인스턴스가 아니라 그 호출의 코루틴 문맥에 센다.
     */
    private class Skipped : kotlin.coroutines.AbstractCoroutineContextElement(Key) {
        var count = 0

        companion object Key : kotlin.coroutines.CoroutineContext.Key<Skipped>
    }

    private suspend fun skip() {
        kotlin.coroutines.coroutineContext[Skipped]?.let { it.count++ }
    }

    suspend fun syncOnce(): SyncReport = db.relationshipMutex.withLock {
        val skipped = Skipped()
        kotlinx.coroutines.withContext(skipped) { syncRound() }.copy(skipped = skipped.count)
    }

    private suspend fun syncRound(): SyncReport {
        Sodium.init()
        archive.prepare()
        val couple = db.coupleDao().get() ?: return SyncReport()
        if (couple.partnerUserId.isEmpty()) return SyncReport() // 혼자 모드
        val partnerDevice = db.deviceDao().activeFor(couple.partnerUserId) ?: return SyncReport()

        val received = pull(couple)
        val plan = handlePeerControl(couple, partnerDevice.id, partnerDevice.signPub)
        val widgetResent = handleWidgetControl(couple, partnerDevice.id, partnerDevice.signPub)
        val sent = push(couple, partnerDevice.id, myStream.wire) + push(couple, partnerDevice.id, widgetStream)
        publishControl(couple)
        return SyncReport(sent = sent, received = received.first, rejected = received.second, resent = plan.first + widgetResent, snapshot = plan.second)
    }

    /** 내 앱 스트림에서 아직 올리지 않은 것. 혼자 모드에서 쓴 것은 연결 뒤 첫 동기화까지 여기 남는다. */
    suspend fun outbox(): Outbox {
        val stream = myStream.wire
        val ops = db.syncDao().opsAfter(stream, db.syncDao().meta(uploadedKey(stream)) ?: 0L)
        val letters = ops.filter { it.entity == "letter" }.map { it.entityId }.distinct()
        val drawings = letters.sumOf { id -> db.drawingDao().forLetter(id).count { it.authorId == identity.userId } }
        return Outbox(letters.size, drawings, ops.size)
    }

    // ── 보내기 ────────────────────────────────────────────────────────────

    private suspend fun push(couple: CoupleEntity, partnerDevice: String, stream: String): Int {
        val uploaded = db.syncDao().meta(uploadedKey(stream)) ?: 0L
        val ops = db.syncDao().opsAfter(stream, uploaded)
        ops.forEach { op ->
            mailbox.put(couple.id, partnerDevice, seal(couple, op, resend = false))
            db.syncDao().setMeta(MetaEntity(uploadedKey(stream), op.seq))
        }
        return ops.size
    }

    /** 위젯이 보낸 사본을 같은 stream/seq/opId로 보관한다. 오프라인 전송과 재실행도 중복되지 않는다. */
    suspend fun importWidgetOp(coupleId: String, item: MailItem): Boolean = db.relationshipMutex.withLock {
        archive.prepare()
        val couple = db.coupleDao().get() ?: return@withLock false
        if (couple.id != coupleId || item.from != identity.deviceId || item.kind != "missyou") return@withLock false
        val signer = vault.deviceKeys() ?: return@withLock false
        val header = OpHeader.fromBytes(item.header)
        if (header.stream != widgetStream || header.seq < 1 || header.opId != item.opId) return@withLock false
        val key = vault.coupleKey(header.keyEpoch) ?: return@withLock false
        val plain = Plain.decode(OpEnvelope.open(OpEnvelope(header, item.ciphertext, item.signature), signer.signPub) { if (it == key.epoch) key else null })
        val payload = OpCodec.decode(plain.payload) as? OpPayload.MissYouPut ?: return@withLock false
        if (payload.missYou.authorId != identity.userId || payload.missYou.id != item.opId) return@withLock false
        db.useWriterConnection { connection ->
            connection.immediateTransaction {
                val result = inbox.apply(ReceivedOp(StreamId.parse(header.stream), header.seq, header.opId, plain.lamport, plain.payload))
                if (result == com.sadturtleman.flowershoes.core.data.ApplyResult.APPLIED) {
                    db.syncDao().appendOp(SyncOpEntity(header.stream, header.seq, header.opId, "missyou", "missyou", payload.missYou.id, plain.payload, plain.lamport, header.keyEpoch, clock.now().toEpochMilliseconds()))
                    val next = maxOf(db.syncDao().nextSeq(header.stream) ?: 1L, header.seq + 1)
                    db.syncDao().setNextSeq(com.sadturtleman.flowershoes.core.database.StreamCounterEntity(header.stream, next))
                }
            }
        }
        return@withLock true
    }

    /** 위젯은 별도 번호를 사용한다. 앱 스트림 스냅샷으로 덮지 않고 확인될 때까지 원본을 재전송한다. */
    private suspend fun handleWidgetControl(couple: CoupleEntity, partnerDevice: String, signPub: ByteArray): Int {
        val doc = mailbox.getControl(couple.id, partnerDevice) ?: return 0
        if (!Signatures.verify(doc.payload, doc.signature, signPub)) return 0
        val control = runCatching { json.decodeFromString(ControlPayload.serializer(), doc.payload.decodeToString()) }.getOrNull() ?: return 0
        val cursor = control.cursors[widgetStream] ?: 0L
        db.syncDao().deleteThrough(widgetStream, cursor)
        val uploaded = db.syncDao().meta(uploadedKey(widgetStream)) ?: 0L
        val pending = db.syncDao().opsAfter(widgetStream, cursor).filter { it.seq <= uploaded }
        pending.forEach { mailbox.put(couple.id, partnerDevice, seal(couple, it, resend = true)) }
        return pending.size
    }

    /**
     * 우편함 한 칸(암호문 [MAX_OP_CIPHERTEXT])을 넘는 Op 은 그 번호를 빈 스냅샷으로 채워 보낸다 —
     * 올리지 못해 그 뒤 스트림 전체가 멈추는 대신 그 항목만 빠진다(#20 결정 5). 상대 커서도 그 번호를 넘어간다.
     */
    private suspend fun seal(couple: CoupleEntity, op: SyncOpEntity, resend: Boolean): MailItem {
        val key = checkNotNull(vault.currentCoupleKey()) { "커플 키가 없다" }
        val signer = checkNotNull(vault.deviceKeys()) { "기기 키가 없다" }
        val header = OpHeader(op.opId, op.stream, op.seq, key.epoch)
        val original = OpCodec.decode(op.payload)
        val shared = original?.let { archive.shared(it, currentOnly = true) }
        // 번호는 빈 스냅샷으로 이어 준다. 보관본 한 건 때문에 뒤의 새 편지가 막히지 않는다.
        val payload = OpCodec.encode(shared ?: OpPayload.Snapshot(0, emptyList()))
        val kind = if (shared == null) OpKind.SNAPSHOT.wire else op.kind
        val env = OpEnvelope.seal(header, Plain(op.lamport, withBlobs(couple, payload)).encode(), key, signer)
        if (env.ciphertext.size <= MAX_OP_CIPHERTEXT) return MailItem(op.opId, kind, identity.deviceId, header.toBytes(), env.ciphertext, env.signature, resend)
        skip()
        // #20 결정 5의 과대 항목 건너뛰기도 이 조각의 완료 표식은 보존한다.
        val skippedPayload = (OpCodec.decode(payload) as? OpPayload.Snapshot)?.copy(items = emptyList())
            ?: OpPayload.Snapshot(0, emptyList())
        val empty = OpEnvelope.seal(header, Plain(op.lamport, OpCodec.encode(skippedPayload)).encode(), key, signer)
        return MailItem(op.opId, OpKind.SNAPSHOT.wire, identity.deviceId, header.toBytes(), empty.ciphertext, empty.signature, resend)
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
            val ok = receiveOrReject(couple, item)
            if (ok) applied++ else rejected++
            // 풀 수 없는 것(서명 불일치 · 키 없음)도 지운다. 남겨 두면 매번 다시 실패한다. 빠진 번호는 재요청으로 메운다.
            mailbox.delete(couple.id, identity.deviceId, item.opId)
        }
        return applied to rejected
    }

    /** iOS: NSE 가 App Group 에 받아 둔 Op 을 반영한다(우편함과 같은 확인 · 중복 제거를 거친다). */
    suspend fun receiveQueued(items: List<MailItem>): Int = db.relationshipMutex.withLock {
        archive.prepare()
        val couple = db.coupleDao().get() ?: return@withLock 0
        return@withLock items.count { receiveOrReject(couple, it) }
    }

    suspend fun receiveQueued(item: MailItem): Boolean = receiveQueued(listOf(item)) == 1

    private suspend fun receiveOrReject(couple: CoupleEntity, item: MailItem): Boolean = try {
        receive(couple, item)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }

    private suspend fun receive(couple: CoupleEntity, item: MailItem): Boolean {
        val sender = db.deviceDao().get(item.from) ?: return false
        if (couple.partnerUserId.isBlank() || sender.userId != couple.partnerUserId) return false
        val header = OpHeader.fromBytes(item.header)
        if (header.opId != item.opId || header.seq < 1) return false
        val stream = StreamId.parse(header.stream)
        if (stream.deviceId != item.from) return false
        val key = vault.coupleKey(header.keyEpoch) ?: return false
        val plain = try {
            Plain.decode(OpEnvelope.open(OpEnvelope(header, item.ciphertext, item.signature), sender.signPub) { if (it == key.epoch) key else null })
        } catch (_: CryptoException) {
            return false
        }
        val authorization = ReceiveAuthorization(couple, sender, stream, header.seq)
        val payload = try { downloadBlobs(authorization, plain.payload) } catch (_: AttachmentConflictException) {
            // 서명과 복호화를 확인한 확정 충돌은 소비한다. 뒤의 정상 기록 ACK를 영구히 막지 않는다.
            OpCodec.encode(OpPayload.Snapshot(0, emptyList()))
        }
        authorization.write { inbox.apply(ReceivedOp(stream, header.seq, header.opId, plain.lamport, payload)) }
        return true
    }

    /**
     * 다운로드는 DB 밖에서 기다리되, 검증한 신원과 실제 쓰기는 같은 SQLite 경계에 둔다.
     * relationshipMutex를 공유하지 않는 앱/백그라운드 DB 인스턴스의 변경도 여기서 보인다.
     */
    private inner class ReceiveAuthorization(
        val couple: CoupleEntity,
        private val sender: DeviceEntity,
        private val stream: StreamId,
        private val seq: Long,
    ) {
        private val receiverDevice = identity.deviceId

        suspend fun <T> write(block: suspend () -> T): T = db.useWriterConnection { connection ->
            connection.immediateTransaction {
                val currentSender = db.deviceDao().get(sender.id)
                check(identity.userId == couple.myUserId && identity.deviceId == receiverDevice &&
                    db.coupleDao().get() == couple && currentSender != null &&
                    currentSender.userId == sender.userId && currentSender.uid == sender.uid &&
                    currentSender.signPub.contentEquals(sender.signPub) && currentSender.boxPub.contentEquals(sender.boxPub) &&
                    currentSender.state == sender.state && currentSender.registeredAtMs == sender.registeredAtMs &&
                    currentSender.revokedAtMs == sender.revokedAtMs && inbox.accepts(stream, seq)) {
                    "연결 또는 송신 기기가 바뀌어 수신을 중단했어요"
                }
                block()
            }
        }
    }

    /** 첨부 하나를 무작위 키로 암호화해 blob 으로. 돌려주는 값은 (blob id, base64 키). 한도([MAX_BLOB_BYTES])를 넘으면 빼고 보낸다. */
    private suspend fun upload(couple: CoupleEntity, attachmentId: String): Pair<String, String>? {
        if (archive.contains("attachment", attachmentId)) return null
        val bytes = attachments.read(attachmentId) ?: return null
        val blobKey = Sodium.random(Aead.KEY_BYTES)
        val blobId = Uuid.random().toString()
        val sealed = Aead.seal(blobKey, bytes)
        if (sealed.size > MAX_BLOB_BYTES) {
            skip()
            return null
        }
        blobs.put(couple.id, blobId, sealed)
        return blobId to blobKey.b64()
    }

    private suspend fun download(authorization: ReceiveAuthorization, blobId: String?, key: String?, attachmentId: String?, kind: AttachmentKind) {
        val couple = authorization.couple
        if (blobId == null || key == null || attachmentId == null) return
        // 잘못된 첨부 참조는 그 항목만 건너뛰어 나머지 기록과 스트림은 계속 처리한다.
        if (!AttachmentStore.isSafeId(blobId) || !AttachmentStore.isSafeId(attachmentId)) return
        if (archive.contains("attachment", attachmentId)) return
        val binding = attachmentMetaKey("attachment-binding", couple.id, blobId, attachmentId, kind.name, key)
        val sealed = blobs.get(couple.id, blobId)
        if (sealed == null) {
            // 실제로 내용이 같았던 수신만 기존 파일에 연결한다. 확인되지 않은 누락은 재전송으로 복구한다.
            authorization.write {
                val local = db.attachmentDao().get(attachmentId)
                val bytes = if (db.syncDao().meta(binding) != null) attachments.read(attachmentId) else null
                check(local != null && bytes != null && bytes.size.toLong() == local.sizeBytes &&
                    bytes.toByteString().sha256().hex() == local.sha256) { "첨부 연결을 아직 확인할 수 없어요" }
            }
            return
        }
        val bytes = Aead.open(key.unb64(), sealed)
        authorization.write {
            attachments.putWithId(attachmentId, kind, bytes)
            db.syncDao().setMeta(MetaEntity(binding, 1))
        }
        blobs.delete(couple.id, blobId)
    }

    private fun attachmentMetaKey(prefix: String, vararg parts: String): String =
        "$prefix:" + json.encodeToString(parts.toList()).encodeToByteArray().toByteString().sha256().hex()

    /** 편지 안 그림 문서를 받아 보낸 쪽과 같은 첨부 id 로 둔다. 다 받은 뒤에 편지를 반영한다(반쯤 받은 편지를 보여 주지 않게). */
    private suspend fun downloadBlobs(authorization: ReceiveAuthorization, payload: ByteArray): ByteArray =
        OpCodec.decode(payload)?.let { OpCodec.encode(fetchBlobs(authorization, it)) } ?: payload

    private suspend fun fetchBlobs(authorization: ReceiveAuthorization, p: OpPayload): OpPayload {
        val couple = authorization.couple
        if (archive.shared(p) == null) return p
        // 편지는 불변 ID, 일정은 LWW 버전에 묶는다. 재전송마다 바뀌는 blob ID에 격리를 의존하지 않는다.
        val rejected = when (p) {
            is OpPayload.LetterPut -> attachmentMetaKey("attachment-rejected", couple.id, "letter", p.letter.id)
            is OpPayload.EventPut -> attachmentMetaKey("attachment-rejected", couple.id, "event", p.event.id, p.event.lamport.toString(), p.event.lastWriterDevice)
            else -> null
        }
        if (rejected != null && db.syncDao().meta(rejected) != null) throw AttachmentConflictException()
        try {
            when (p) {
                // 충돌한 참조를 다른 원본 사진으로 표시하지 않는다. 같은 스냅샷의 정상 기록은 계속 반영한다.
                is OpPayload.Snapshot -> return p.copy(items = p.items.mapNotNull {
                    try { fetchBlobs(authorization, it) } catch (_: AttachmentConflictException) { null }
                })
                // 파일이 있어도 새 blob의 내용을 비교해야 다른 사진을 기존 ID로 연결하지 않는다.
                is OpPayload.EventPut -> p.photos.forEach { ph -> download(authorization, ph.blobId, ph.blobKey, ph.attachmentId, AttachmentKind.PHOTO) }
                is OpPayload.LetterPut -> {
                    p.drawings.forEach { d ->
                        download(authorization, d.docBlobId, d.docBlobKey, d.docAttachmentId, AttachmentKind.DRAWING_DOC)
                        download(authorization, d.pngBlobId, d.pngBlobKey, d.pngAttachmentId, AttachmentKind.DRAWING_PNG)
                        download(authorization, d.photoBlobId, d.photoBlobKey, d.photoAttachmentId, AttachmentKind.PHOTO)
                    }
                    p.photos.forEach { ph -> download(authorization, ph.blobId, ph.blobKey, ph.attachmentId, AttachmentKind.PHOTO) }
                }
                else -> Unit
            }
        } catch (collision: AttachmentConflictException) {
            if (rejected != null) authorization.write { db.syncDao().setMeta(MetaEntity(rejected, 1)) }
            throw collision
        }
        return p
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
        val beforeAck = db.syncDao().log(stream)
        // 구버전 수신자는 첫 조각만으로 coversThrough 를 ACK한다. 마지막 조각까지
        // 연속 수신한 ACK가 오기 전에는 원본과 모든 조각을 함께 보존한다.
        val incompleteBatch = beforeAck.any { row ->
            val snapshot = OpCodec.decode(row.payload) as? OpPayload.Snapshot
            val batch = snapshot?.batch
            batch != null && batch.validFor(row.seq, snapshot.coversThrough) && peerCursor < batch.lastSeq
        }
        if (!incompleteBatch) db.syncDao().deleteThrough(stream, peerCursor)

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
                // 원본이 이미 floor 아래인 복구에서도 미완료 묶음을 계속 재발행하지 않는다.
                // 동일 opId/seq로 재전송하므로 TTL·앱 재시작 뒤에도 같은 묶음을 마칠 수 있다.
                val rows = db.syncDao().log(stream)
                val pending = rows.firstNotNullOfOrNull { row ->
                    val snapshot = OpCodec.decode(row.payload) as? OpPayload.Snapshot
                    snapshot?.batch?.takeIf {
                        it.validFor(row.seq, snapshot.coversThrough) && it.lastSeq > peerCursor &&
                            snapshot.coversThrough >= log.floor - 1
                    }
                }
                if (pending != null) {
                    val parts = rows.filter { it.seq in pending.firstSeq..pending.lastSeq }
                    parts.forEach { mailbox.put(couple.id, partnerDevice, seal(couple, it, resend = true)) }
                    parts.size to false
                } else {
                    writeSnapshot(plan.coversThrough)
                    0 to true
                }
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

    private enum class SnapshotPurpose(val entityId: String) { REPAIR("snapshot"), DEVICE_CHANGE("full") }

    /** 현재 관계의 공유 기록을 같은 DB 시점에서 전송한다. 기기 교체는 현재 상태를 제외한다(기획 §5). */
    suspend fun writeSnapshot(coversThrough: Long) = writeSnapshotTransaction(SnapshotPurpose.REPAIR, coversThrough)

    suspend fun writeFullSnapshot() = writeSnapshotTransaction(SnapshotPurpose.DEVICE_CHANGE, null)

    private suspend fun writeSnapshotTransaction(purpose: SnapshotPurpose, coversThrough: Long?) {
        db.useWriterConnection { tx -> tx.immediateTransaction {
            archive.prepare()
            val firstSeq = db.syncDao().nextSeq(myStream.wire) ?: 1L
            val covers = coversThrough ?: (firstSeq - 1)
            require(covers in 0 until firstSeq)
            val items = buildList<OpPayload> {
                db.userDao().all().forEach { add(OpPayload.UserPut(it.id, it.role, it.nickname, it.nicknameLamport, it.deviceName.orEmpty(), it.perks?.split(',')?.filter { p -> p.isNotBlank() }.orEmpty())) }
                db.serviceDao().all().forEach { add(OpPayload.ServicePut(it.toModel())) }
                db.eventDao().all().forEach { add(OpPayload.EventPut(it.toModel())) }
                db.leaveDao().all().forEach { add(OpPayload.LeaveGrantPut(it.toModel())) }
                db.bookCoverDao().get()?.let { add(it.toPayload()) }
                db.letterDao().all().forEach { l ->
                    val deletedAt = l.deletedAtMs
                    if (deletedAt == null) {
                        add(OpPayload.LetterPut(l.toModel(), drawingMetas(l.id)))
                    } else {
                        // 구버전도 아는 Delete를 함께 보낸다. 두 항목을 한 조각으로 묶어
                        // 삭제가 insert보다 먼저 도착해 무시되거나 첨부를 다시 받지 않게 한다.
                        add(OpPayload.Snapshot(0, listOf(
                            OpPayload.LetterPut(l.toModel().copy(blocks = emptyList())),
                            OpPayload.Delete(com.sadturtleman.flowershoes.core.data.DeletableEntity.LETTER,
                                l.id, l.lamport, deletedAt, identity.deviceId),
                        )))
                    }
                }
                db.missYouDao().all().forEach { add(OpPayload.MissYouPut(com.sadturtleman.flowershoes.core.model.MissYou(it.id, it.authorId, kotlin.time.Instant.fromEpochMilliseconds(it.sentAtMs)))) }
                if (purpose == SnapshotPurpose.REPAIR) db.statusDao().all().forEach { add(OpPayload.StatusPut(it.toModel())) }
                db.receiptDao().all().forEach {
                    add(OpPayload.ReceiptPut(com.sadturtleman.flowershoes.core.model.Receipt(com.sadturtleman.flowershoes.core.model.ReceiptEntity.valueOf(it.entity), it.entityId, it.readerId, kotlin.time.Instant.fromEpochMilliseconds(it.atMs))))
                }
            }
            writeSnapshotOps(purpose.entityId, covers, firstSeq, items)
        } }
    }

    /** 모든 조각을 한 트랜잭션에서 예약한다. 어느 조각 하나만으로는 coversThrough 를 확인하지 않는다. */
    private suspend fun writeSnapshotOps(entityId: String, coversThrough: Long, firstSeq: Long, items: List<OpPayload>) {
        val chunks = mutableListOf<MutableList<OpPayload>>()
        var size = 0
        items.mapNotNull { archive.shared(it, currentOnly = true) }.forEach { item ->
            val itemSize = wireSize(item)
            if (chunks.isEmpty() || size + itemSize > SNAPSHOT_CHUNK_BYTES) {
                chunks += mutableListOf<OpPayload>()
                size = 0
            }
            chunks.last() += item
            size += itemSize
        }
        if (chunks.isEmpty()) chunks += mutableListOf<OpPayload>()
        require(chunks.size <= 100_000)
        chunks.forEachIndexed { index, chunk ->
            val batch = com.sadturtleman.flowershoes.core.data.SnapshotBatch(firstSeq, chunks.size, index)
            writer.write(OpKind.SNAPSHOT, "snapshot", entityId, localWrite = {}, payload = { OpPayload.Snapshot(coversThrough, chunk, batch) })
        }
    }

    private fun com.sadturtleman.flowershoes.core.database.BookCoverEntity.toPayload() =
        OpPayload.BookCoverPut(cover, lamport, lastWriterDevice)

    /** 보낼 때 [uploadBlobs] 가 붙이는 blob 참조(id · 키)까지 셈한 본문 크기. */
    private fun wireSize(item: OpPayload): Int = OpCodec.encode(item).size + BLOB_REF_BYTES * when (item) {
        is OpPayload.EventPut -> item.event.photoIds.size
        is OpPayload.LetterPut -> item.letter.photoCount + item.drawings.sumOf { listOfNotNull(it.docAttachmentId, it.pngAttachmentId, it.photoAttachmentId).size }
        else -> 0
    }

    /** 그림 문서가 가리키는 배경 사진 첨부(스냅샷에서 사진도 함께 보내려고). DB 에 따로 두지 않고 문서에서 읽는다. */
    private suspend fun photoOf(docAttachmentId: String?): String? {
        val bytes = docAttachmentId?.let { attachments.read(it, com.sadturtleman.flowershoes.core.drawing.DrawingCodec.MAX_DOCUMENT_BYTES) } ?: return null
        // 깨진 문서는 사진 없이 보낸다(스냅샷 전체를 막지 않는다).
        val doc = (runCatching { com.sadturtleman.flowershoes.core.drawing.DrawingCodec.decode(bytes) }.getOrNull() as? com.sadturtleman.flowershoes.core.drawing.DrawingCodec.Result.Ok)?.document
        return doc?.elements?.filterIsInstance<com.sadturtleman.flowershoes.core.drawing.PhotoBackground>()?.firstOrNull()?.attachmentId
    }

    private suspend fun drawingMetas(letterId: String) = db.drawingDao().forLetter(letterId).map {
        com.sadturtleman.flowershoes.core.data.DrawingMeta(it.id, it.orderInLetter, it.shape, it.docAttachmentId, it.pngAttachmentId, it.caption, it.replayEnabled, it.replayDurationMs, showOnWidget = it.showOnWidget, photoAttachmentId = photoOf(it.docAttachmentId))
    }

    private fun uploadedKey(stream: String) = "uploaded:$stream"

    private companion object {
        /** 스냅샷 조각 하나의 본문 상한. 우편함 한도(암호문 67,500바이트)에서 lamport · AEAD 덧붙임과 여유를 뺐다. */
        const val SNAPSHOT_CHUNK_BYTES = 48_000

        /** blob 참조 하나: blob id(36) · base64 키(44) · JSON 이름과 따옴표. */
        const val BLOB_REF_BYTES = 160

        /** firestore.rules 우편함: ct(base64) ≤ 90,000자 = 암호문 67,500바이트. */
        const val MAX_OP_CIPHERTEXT = 90_000 / 4 * 3

        /** storage.rules couples/{c}/blobs: 암호문 10MB. */
        const val MAX_BLOB_BYTES = 10 * 1024 * 1024
    }
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
