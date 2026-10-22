package com.sadturtleman.flowershoes.core.firebase

import com.sadturtleman.flowershoes.core.data.sync.RotationApproval
import com.sadturtleman.flowershoes.core.data.sync.RotationRequest
import com.sadturtleman.flowershoes.core.data.sync.RotationTransport
import dev.gitlive.firebase.Firebase
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
}
