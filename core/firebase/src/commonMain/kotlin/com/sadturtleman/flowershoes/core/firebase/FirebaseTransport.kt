package com.sadturtleman.flowershoes.core.firebase

import com.sadturtleman.flowershoes.core.data.sync.BlobStore
import com.sadturtleman.flowershoes.core.data.sync.ControlDoc
import com.sadturtleman.flowershoes.core.data.sync.MailItem
import com.sadturtleman.flowershoes.core.data.sync.Mailbox
import com.sadturtleman.flowershoes.core.data.sync.PairingTransport
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.firestore
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
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
private data class InviteDoc(val uid: String, val expireAt: Timestamp)

@Serializable
private data class KeyDoc(val wrapped: String, val epoch: Int)

private fun ByteArray.b64() = Base64.encode(this)
private fun String.unb64() = Base64.decode(this)
private fun Clock.after(d: kotlin.time.Duration): Timestamp {
    val ms = (now() + d).toEpochMilliseconds()
    return Timestamp(ms / 1000, ((ms % 1000) * 1_000_000).toInt())
}

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
        blob(coupleId, blobId).set(BlobDoc(bytes.b64(), clock.after(32.days)))
    }

    override suspend fun get(coupleId: String, blobId: String): ByteArray? {
        val snap = blob(coupleId, blobId).get()
        return if (snap.exists) snap.data(BlobDoc.serializer()).data.unb64() else null
    }

    override suspend fun delete(coupleId: String, blobId: String) {
        blob(coupleId, blobId).delete()
    }

    // ── PairingTransport ──

    private fun pairing(code: String) = db.collection("pairings").document(code)

    override suspend fun createInvite(code: String, inviterUid: String) {
        pairing(code).set(InviteDoc(inviterUid, clock.after(24.hours)))
    }

    override suspend fun inviterUid(code: String): String? {
        val snap = pairing(code).get()
        return if (snap.exists) snap.data(InviteDoc.serializer()).uid else null
    }

    override suspend fun putStep(code: String, name: String, fields: Map<String, String>) {
        pairing(code).collection("steps").document(name).set(fields)
    }

    override suspend fun getStep(code: String, name: String): Map<String, String>? {
        val snap = pairing(code).collection("steps").document(name).get()
        return if (snap.exists) snap.data<Map<String, String>>() else null
    }

    /**
     * 함수가 커플을 만들면 초대 문서를 지우고 users/{uid}.coupleId 를 채운다. 여기서는 내 사용자 문서로 확인한다.
     */
    override suspend fun linkedCoupleId(code: String): String? {
        val uid = Firebase.auth.currentUser?.uid ?: return null
        val snap = db.collection("users").document(uid).get()
        if (!snap.exists) return null
        // 이 코드로 생긴 커플일 때만(다시 연결하면 coupleId 가 잠깐 옛 커플을 가리킨다).
        if (snap.get<String?>("linkedVia") != code) return null
        return snap.get<String?>("coupleId")
    }

    override suspend fun wrappedKeyFor(coupleId: String, deviceId: String): ByteArray? {
        val snap = db.collection("couples").document(coupleId).collection("keys").document(deviceId).get()
        return if (snap.exists) snap.data(KeyDoc.serializer()).wrapped.unb64() else null
    }
}

/** 푸시 토큰을 users/{uid}.fcmTokens 에 둔다. 서버(onInboxCreated)가 여기로 보낸다. 만료된 토큰은 서버가 지운다. */
class PushTokenRegistry(private val db: FirebaseFirestore = Firebase.firestore) {
    suspend fun register(token: String) {
        val uid = Firebase.auth.currentUser?.uid ?: return
        db.collection("users").document(uid).set(mapOf("fcmTokens" to dev.gitlive.firebase.firestore.FieldValue.arrayUnion(token)), merge = true)
    }
}
