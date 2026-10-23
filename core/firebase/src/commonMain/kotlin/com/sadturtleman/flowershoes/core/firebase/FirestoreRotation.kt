package com.sadturtleman.flowershoes.core.firebase

import com.sadturtleman.flowershoes.core.data.sync.RotationApproval
import com.sadturtleman.flowershoes.core.data.sync.RotationRequest
import com.sadturtleman.flowershoes.core.data.sync.RotationTransport
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.functions.functions
import kotlin.io.encoding.Base64
import kotlinx.serialization.Serializable

// firestore.rules 의 hasOnly 목록과 1:1.

@Serializable
private data class PendingDeviceDoc(
    val uid: String,
    val signPub: String,
    val boxPub: String,
    val state: String = "pending",
    val active: Boolean = false,
    val createdAt: Timestamp,
)

@Serializable
private data class RotationDoc(
    val uid: String,
    val newDeviceId: String,
    val oldDeviceId: String,
    val epoch: Int,
    val wrappedKey: String,
    val mac: String,
    val sig: String,
    val createdAt: Long,
    val deviceName: String = "",
    val provider: String = "",
)

@Serializable
private data class ApprovalDoc(val approverDeviceId: String, val cutoff: Map<String, Long>, val sig: String, val at: Timestamp)

@Serializable
private data class FinalizeResult(val token: String)

/**
 * 기기 교체(7-3-A)의 서버 문서. 대기 기기의 공개키는 devices/{id} 문서에서 읽는다.
 * 활성화 · 옛 기기 폐기는 승인 서명을 확인한 함수(onRotationApproved)가 한다.
 */
class FirestoreRotation(
    private val db: FirebaseFirestore = Firebase.firestore,
    private val region: String = "asia-northeast3",
) : RotationTransport {

    private fun couple(id: String) = db.collection("couples").document(id)

    override suspend fun putPendingDevice(coupleId: String, deviceId: String, uid: String, signPub: ByteArray, boxPub: ByteArray) {
        couple(coupleId).collection("devices").document(deviceId)
            .set(PendingDeviceDoc(uid, Base64.encode(signPub), Base64.encode(boxPub), createdAt = Timestamp.now()))
    }

    override suspend fun putRotation(coupleId: String, request: RotationRequest) {
        couple(coupleId).collection("rotations").document(request.id).set(
            RotationDoc(
                request.uid, request.newDeviceId, request.oldDeviceId, request.epoch,
                Base64.encode(request.wrappedKey), Base64.encode(request.mac), Base64.encode(request.sig), request.createdAtMs,
                request.deviceName, request.provider,
            ),
        )
    }

    override suspend fun rotations(coupleId: String): List<RotationRequest> =
        couple(coupleId).collection("rotations").get().documents.mapNotNull { snap ->
            val d = runCatching { snap.data(RotationDoc.serializer()) }.getOrNull() ?: return@mapNotNull null
            val device = couple(coupleId).collection("devices").document(d.newDeviceId).get()
            if (!device.exists) return@mapNotNull null
            val dev = device.data(PendingDeviceDoc.serializer())
            RotationRequest(
                snap.id, d.uid, d.newDeviceId, d.oldDeviceId, d.epoch,
                Base64.decode(dev.signPub), Base64.decode(dev.boxPub),
                Base64.decode(d.wrappedKey), Base64.decode(d.mac), Base64.decode(d.sig), d.createdAt,
                deviceName = d.deviceName, provider = d.provider,
            )
        }

    override suspend fun putApproval(coupleId: String, rotationId: String, approval: RotationApproval) {
        couple(coupleId).collection("rotations").document(rotationId).collection("approval").document("main")
            .set(ApprovalDoc(approval.approverDeviceId, approval.cutoff, Base64.encode(approval.sig), Timestamp.now()))
    }

    override suspend fun approval(coupleId: String, rotationId: String): RotationApproval? {
        val snap = couple(coupleId).collection("rotations").document(rotationId).collection("approval").document("main").get()
        if (!snap.exists) return null
        val a = snap.data(ApprovalDoc.serializer())
        return RotationApproval(a.approverDeviceId, a.cutoff, Base64.decode(a.sig))
    }

    override suspend fun finalize(coupleId: String, rotationId: String, challengeSig: ByteArray): String =
        Firebase.functions(region).httpsCallable("finalizeDevice")
            .invoke(mapOf("coupleId" to coupleId, "rotationId" to rotationId, "challengeSig" to Base64.encode(challengeSig)))
            .data(FinalizeResult.serializer()).token
}

/** 연결 해제 · 탈퇴 callable. 서버 데이터 삭제 · 상대 알림은 함수가 한다. */
class FunctionsAccount(private val region: String = "asia-northeast3") : com.sadturtleman.flowershoes.core.data.sync.AccountTransport {
    override suspend fun unlinkCouple(coupleId: String) {
        Firebase.functions(region).httpsCallable("unlinkCouple").invoke(mapOf("coupleId" to coupleId))
    }

    override suspend fun deleteAccount() {
        Firebase.functions(region).httpsCallable("deleteAccount").invoke()
    }

    override suspend fun signInWithCustomToken(token: String) {
        Firebase.auth.signInWithCustomToken(token)
    }
}

@Serializable
private data class RelinkDoc(val uid: String, val coupleId: String, val expireAt: Timestamp)

@Serializable
private data class DeviceStateDoc(val active: Boolean = false)

@Serializable
private data class KeyEnvelopeDoc(val wrapped: String, val epoch: Int)

/** relinks/{code} — 상대 폰이 넘겨주기(7-3-B). 활성화는 함수 onRelinkStep 이 한다. */
class FirestoreRelink(private val db: FirebaseFirestore = Firebase.firestore) : com.sadturtleman.flowershoes.core.data.sync.RelinkTransport {
    private fun doc(code: String) = db.collection("relinks").document(code)

    override suspend fun create(code: String, inviterUid: String, coupleId: String) {
        val expire = kotlin.time.Clock.System.now().toEpochMilliseconds() + 24 * 3600 * 1000L
        doc(code).set(RelinkDoc(inviterUid, coupleId, Timestamp(expire / 1000, 0)))
    }

    override suspend fun coupleId(code: String): String? = doc(code).get().let { if (it.exists) it.data(RelinkDoc.serializer()).coupleId else null }

    override suspend fun giverUid(code: String): String? = doc(code).get().let { if (it.exists) it.data(RelinkDoc.serializer()).uid else null }

    override suspend fun putStep(code: String, name: String, fields: Map<String, String>) {
        doc(code).collection("steps").document(name).set(fields)
    }

    override suspend fun getStep(code: String, name: String): Map<String, String>? =
        doc(code).collection("steps").document(name).get().let { if (it.exists) it.data<Map<String, String>>() else null }

    override suspend fun activated(coupleId: String, deviceId: String): Boolean {
        val snap = db.collection("couples").document(coupleId).collection("devices").document(deviceId).get()
        return snap.exists && snap.data(DeviceStateDoc.serializer()).active
    }

    override suspend fun wrappedKeyFor(coupleId: String, deviceId: String): ByteArray? {
        val snap = db.collection("couples").document(coupleId).collection("keys").document(deviceId).get()
        return if (snap.exists) Base64.decode(snap.data(KeyEnvelopeDoc.serializer()).wrapped) else null
    }
}

/** 알림 정책을 서버에(users/{uid}/notifyPrefs/main). 서버가 보낼지 · 묶을지 정할 때 읽는다(functions/src/policy.ts). */
class FirestoreNotifyPrefs(private val db: FirebaseFirestore = Firebase.firestore) : com.sadturtleman.flowershoes.core.data.NotifyPrefsTransport {
    @kotlinx.serialization.Serializable
    private data class Doc(val quietStart: Int, val quietEnd: Int, val tz: String, val missYouBundle: Int, val kinds: Map<String, Boolean>)

    override suspend fun upload(prefs: com.sadturtleman.flowershoes.core.data.NotificationPrefs) {
        val uid = Firebase.auth.currentUser?.uid ?: return
        val kinds = mapOf(
            "letter" to prefs.letters, "letter-drawing" to prefs.letters, "scheduled" to prefs.letters,
            "missyou" to prefs.missYou, "status" to prefs.status,
        )
        db.collection("users").document(uid).collection("notifyPrefs").document("main")
            .set(Doc.serializer(), Doc(prefs.quietStart, prefs.quietEnd, "Asia/Seoul", prefs.missYouBundle.coerceAtLeast(1), kinds))
    }
}
