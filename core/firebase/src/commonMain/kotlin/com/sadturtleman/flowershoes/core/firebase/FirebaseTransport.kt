package com.sadturtleman.flowershoes.core.firebase

import com.sadturtleman.flowershoes.core.crypto.Pairing
import com.sadturtleman.flowershoes.core.data.sync.BlobStore
import com.sadturtleman.flowershoes.core.data.sync.ControlDoc
import com.sadturtleman.flowershoes.core.data.sync.Invite
import com.sadturtleman.flowershoes.core.data.sync.LinkedDevice
import com.sadturtleman.flowershoes.core.data.sync.MailItem
import com.sadturtleman.flowershoes.core.data.sync.Mailbox
import com.sadturtleman.flowershoes.core.data.sync.PairingTransport
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.storage.storage
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

// Firestore 문서 모양. firestore.rules 의 hasOnly 목록과 1:1 이다.

@Serializable
private data class OpDoc(
    val kind: String,
    val from: String,
    val createdAt: Timestamp,
    val expireAt: Timestamp,
    val size: Int,
    val ct: String,
    val sig: String,
    val header: String,
    val resend: Boolean = false,
)

@Serializable
private data class ControlFields(val payload: String, val sig: String, val at: Timestamp)

@Serializable
private data class BlobDoc(val data: String, val expireAt: Timestamp)

@Serializable
private data class InviteDoc(val uid: String, val deviceId: String, val pubKey: String, val name: String, val expireAt: Timestamp)

@Serializable
private data class AcceptDoc(val uid: String, val deviceId: String, val pubKey: String)

private fun ByteArray.b64() = Base64.encode(this)
private fun String.unb64() = Base64.decode(this)
private fun Clock.after(d: kotlin.time.Duration): Timestamp = (now() + d).toEpochMilliseconds().toTimestamp()

private fun Long.toTimestamp(): Timestamp = Timestamp(this / 1000, ((this % 1000) * 1_000_000).toInt())

private fun Timestamp.ms(): Long = seconds * 1000 + nanoseconds / 1_000_000

/**
 * 우편함 · 커서 · blob · 연결 단계를 Firestore 에.
 *
 * 우편함 TTL 31일(expireAt 필드, Firestore TTL 정책). TTL 은 정확한 시각에 지우지 않으므로 읽을 때도 거른다.
 * 그림 문서처럼 작은 첨부는 Firestore 문서(1MB 한도)에 base64 로 둔다. 큰 사진은 Storage 로 옮긴다.
 */
class FirestoreTransport(
    private val db: FirebaseFirestore = Firebase.firestore,
    private val clock: Clock = Clock.System,
) : Mailbox, BlobStore, PairingTransport {

    private fun ops(coupleId: String, device: String) = db.collection("couples").document(coupleId)
        .collection("inbox").document(device).collection("ops")

    // ── Mailbox ──

    override suspend fun put(coupleId: String, toDevice: String, item: MailItem) {
        ops(coupleId, toDevice).document(item.opId).set(
            OpDoc(
                kind = item.kind, from = item.from, createdAt = Timestamp.now(), expireAt = clock.after(31.days),
                size = item.ciphertext.size, ct = item.ciphertext.b64(), sig = item.signature.b64(),
                header = item.header.b64(), resend = item.resend,
            ),
        )
    }

    override suspend fun reconnect() {
        try { db.disableNetwork() } finally {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { db.enableNetwork() }
        }
    }

    /**
     * 서버 쪽 Firestore 상태만 서버 오류 화면에 올린다(상태 이름이 곧 코드).
     * PERMISSION_DENIED 같은 것은 기기 승인 대기 창에서 늘 나오는 실패라 빼 둔다.
     */
    override fun errorCode(error: Throwable): String? =
        (error as? dev.gitlive.firebase.firestore.FirebaseFirestoreException)?.code?.name?.takeIf { it in SERVER_FAULTS }

    override suspend fun fetch(coupleId: String, myDevice: String): List<MailItem> {
        val now = clock.now().toEpochMilliseconds()
        return ops(coupleId, myDevice).get().documents.mapNotNull { snap ->
            val d = runCatching { snap.data(OpDoc.serializer()) }.getOrNull() ?: return@mapNotNull null
            if (d.expireAt.ms() < now) return@mapNotNull null
            MailItem(snap.id, d.kind, d.from, d.header.unb64(), d.ct.unb64(), d.sig.unb64(), d.resend)
        }
    }

    override fun rings(coupleId: String, myDevice: String): kotlinx.coroutines.flow.Flow<Unit> =
        ops(coupleId, myDevice).snapshots.filter { it.documents.isNotEmpty() }.map { }

    override suspend fun delete(coupleId: String, myDevice: String, opId: String) {
        ops(coupleId, myDevice).document(opId).delete()
    }

    override suspend fun putControl(coupleId: String, myDevice: String, doc: ControlDoc) {
        db.collection("couples").document(coupleId).collection("control").document(myDevice)
            .set(ControlFields(doc.payload.b64(), doc.signature.b64(), Timestamp.now()))
    }

    override suspend fun getControl(coupleId: String, device: String): ControlDoc? {
        val snap = db.collection("couples").document(coupleId).collection("control").document(device).get()
        if (!snap.exists) return null
        val f = snap.data(ControlFields.serializer())
        return ControlDoc(f.payload.unb64(), f.sig.unb64())
    }

    // ── BlobStore ──

    private fun blob(coupleId: String, id: String) = db.collection("couples").document(coupleId).collection("blobs").document(id)

    override suspend fun put(coupleId: String, blobId: String, bytes: ByteArray) {
        // 규칙의 문자열 900,000자 제한에는 Base64의 4/3 팽창도 포함된다.
        val inline = bytes.size <= 675_000
        if (!inline) putStorageBytes(storageBlob(coupleId, blobId), bytes)
        blob(coupleId, blobId).set(BlobDoc(if (inline) bytes.b64() else STORAGE_BLOB, clock.after(32.days)))
    }

    override suspend fun get(coupleId: String, blobId: String): ByteArray? {
        val snap = blob(coupleId, blobId).get()
        if (!snap.exists) return null
        val data = snap.data(BlobDoc.serializer()).data
        return if (data == STORAGE_BLOB) getStorageBytes(storageBlob(coupleId, blobId)) else data.unb64()
    }

    override suspend fun delete(coupleId: String, blobId: String) {
        val ref = blob(coupleId, blobId)
        val snap = ref.get()
        if (snap.exists && snap.data(BlobDoc.serializer()).data == STORAGE_BLOB) {
            storageBlob(coupleId, blobId).delete()
        }
        ref.delete()
    }

    private fun storageBlob(coupleId: String, blobId: String) = Firebase.storage.reference.child("couples/$coupleId/blobs/$blobId")

    private companion object {
        const val STORAGE_BLOB = "storage:v1"

        /** 서버가 답하지 않거나 서버 안에서 잘못된 상태(gRPC 상태 이름). */
        val SERVER_FAULTS = setOf("UNAVAILABLE", "DEADLINE_EXCEEDED", "INTERNAL", "UNKNOWN", "RESOURCE_EXHAUSTED", "DATA_LOSS")
    }

    // ── PairingTransport ──

    private fun pairing(token: String) = db.collection("pairings").document(token)

    override suspend fun createInvite(token: String, invite: Invite) {
        pairing(token).set(InviteDoc(invite.inviterUid, invite.deviceId, invite.keys.encode(), invite.name, invite.expireAtMs.toTimestamp()))
    }

    override suspend fun invite(token: String): Invite? {
        val snap = pairing(token).get()
        if (!snap.exists) return null
        val d = runCatching { snap.data(InviteDoc.serializer()) }.getOrNull() ?: return null
        val keys = Pairing.PublicKeys.decode(d.pubKey) ?: return null
        return Invite(d.uid, d.deviceId, keys, d.name, d.expireAt.ms())
    }

    override suspend fun accept(token: String, uid: String, deviceId: String, keys: Pairing.PublicKeys) {
        pairing(token).collection("steps").document("accept").set(AcceptDoc(uid, deviceId, keys.encode()))
    }

    /**
     * 함수가 커플을 만들면 초대 문서를 지우고 users/{uid}.coupleId 를 채운다. 여기서는 내 사용자 문서로 확인한다.
     */
    override suspend fun serverCoupleId(): Result<String?> = runCatching {
        val uid = checkNotNull(Firebase.auth.currentUser?.uid) { "로그인 전" }
        val snap = db.collection("users").document(uid).get()
        if (snap.exists) snap.get<String?>("coupleId") else null
    }

    override suspend fun linkedCoupleId(token: String, myUid: String): String? {
        val snap = db.collection("users").document(myUid).get()
        if (!snap.exists) return null
        // 이 토큰으로 생긴 커플일 때만(다시 연결하면 coupleId 가 잠깐 옛 커플을 가리킨다).
        if (snap.get<String?>("linkedVia") != token) return null
        return snap.get<String?>("coupleId")
    }

    override suspend fun partnerDevice(coupleId: String, myUid: String): LinkedDevice? {
        val docs = db.collection("couples").document(coupleId).collection("devices").get().documents
        val d = docs.firstOrNull { it.get<String?>("uid") != myUid && it.get<Boolean?>("active") == true } ?: return null
        return LinkedDevice(d.get("uid"), d.id, Pairing.PublicKeys(d.get<String>("signPub").unb64(), d.get<String>("boxPub").unb64()))
    }
}

/**
 * 푸시 토큰을 users/{uid}.fcmTokens 에 둔다. 서버(onInboxCreated)가 여기로 보낸다. 만료된 토큰은 서버가 지운다.
 */
class PushTokenRegistry(private val db: FirebaseFirestore = Firebase.firestore) {
    suspend fun register(token: String) {
        val uid = Firebase.auth.currentUser?.uid ?: return
        db.collection("users").document(uid).set(
            mapOf("fcmTokens" to dev.gitlive.firebase.firestore.FieldValue.arrayUnion(token)),
            merge = true,
        )
    }
}
