package com.sadturtleman.flowershoes.core.firebase

import com.sadturtleman.flowershoes.core.crypto.Pairing
import com.sadturtleman.flowershoes.core.data.sync.DeviceChangeApproval
import com.sadturtleman.flowershoes.core.data.sync.DeviceChangeRequest
import com.sadturtleman.flowershoes.core.data.sync.DeviceChangeStatus
import com.sadturtleman.flowershoes.core.data.sync.DeviceChangeTransport
import com.sadturtleman.flowershoes.core.data.sync.LinkedDevice
import com.sadturtleman.flowershoes.core.data.sync.TransferCounts
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.BaseTimestamp
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.functions.functions
import kotlin.io.encoding.Base64
import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

// firestore.rules 의 hasOnly 목록과 1:1.

@Serializable
private data class PendingDeviceDoc(
    val uid: String,
    val signPub: String,
    val boxPub: String,
    val state: String = "pending",
    val active: Boolean = false,
    val createdAt: BaseTimestamp = Timestamp.ServerTimestamp,
)

@Serializable
private data class RequestDoc(
    val uid: String,
    val newDeviceId: String,
    val oldDeviceId: String,
    val deviceName: String,
    val provider: String,
    val status: String = "pending",
    /** 규칙이 request.time 과 같아야 한다고 본다 — 24시간 판단을 기기 시계에 맡기지 않는다. */
    val createdAt: BaseTimestamp = Timestamp.ServerTimestamp,
    val expireAt: Timestamp,
)

@Serializable
private data class ApproveDoc(
    val result: String,
    val approverDeviceId: String,
    val cutoff: Map<String, Long>,
    val sig: String,
    val counts: Map<String, Int>,
    val at: BaseTimestamp = Timestamp.ServerTimestamp,
)

@Serializable
private data class ResultDoc(val result: String, val at: BaseTimestamp = Timestamp.ServerTimestamp)

@Serializable
private data class KeyEnvelopeDoc(val wrapped: String, val epoch: Int)

@Serializable
private data class FinalizeResult(val token: String)

private fun Timestamp.ms(): Long = seconds * 1000 + nanoseconds / 1_000_000

private fun Long.toTimestamp(): Timestamp = Timestamp(this / 1000, ((this % 1000) * 1_000_000).toInt())

/**
 * 기기 교체(#20)의 서버 문서: couples/{c}/deviceChanges/{id} · 그 아래 answer/{partner|owner} · devices/{새 기기} · keys/{새 기기}.
 * 활성화 · 옛 기기 폐기 · 상태는 답을 확인한 함수(onDeviceChangeAnswered)가 바꾼다.
 */
class FirestoreDeviceChange(
    private val db: FirebaseFirestore = Firebase.firestore,
    private val region: String = "asia-northeast3",
) : DeviceChangeTransport {

    private fun couple(id: String) = db.collection("couples").document(id)

    private fun change(coupleId: String, id: String) = couple(coupleId).collection("deviceChanges").document(id)

    override fun changes(coupleId: String): kotlinx.coroutines.flow.Flow<Unit> = merge(
        couple(coupleId).collection("deviceChanges").snapshots.map { },
        couple(coupleId).collection("devices").snapshots.map { },
    )

    override suspend fun serverCoupleId(): String? {
        val uid = Firebase.auth.currentUser?.uid ?: return null
        val snap = db.collection("users").document(uid).get()
        return if (snap.exists) snap.get<String?>("coupleId") else null
    }

    override suspend fun currentDevice(coupleId: String, uid: String): String? =
        couple(coupleId).get().get<Map<String, String>?>("members")?.get(uid)

    override suspend fun request(coupleId: String, request: DeviceChangeRequest, keys: Pairing.PublicKeys) {
        val device = couple(coupleId).collection("devices").document(request.newDeviceId)
        // 다시 요청: 대기 기기 문서가 남아 있으면 그대로 쓴다(규칙이 덮어쓰기를 막는다).
        if (!device.get().exists) device.set(PendingDeviceDoc(request.uid, Base64.encode(keys.signPub), Base64.encode(keys.boxPub)))
        change(coupleId, request.id).set(
            RequestDoc(request.uid, request.newDeviceId, request.oldDeviceId, request.deviceName, request.provider, expireAt = request.expireAtMs.toTimestamp()),
        )
    }

    override suspend fun requests(coupleId: String): List<DeviceChangeRequest> =
        couple(coupleId).collection("deviceChanges").get().documents.mapNotNull { runCatching { it.toRequest() }.getOrNull() }

    private fun DocumentSnapshot.toRequest(): DeviceChangeRequest {
        val counts = get<Map<String, Long>?>("counts")
        return DeviceChangeRequest(
            id = id,
            uid = get("uid"),
            newDeviceId = get("newDeviceId"),
            oldDeviceId = get<String?>("oldDeviceId").orEmpty(),
            deviceName = get<String?>("deviceName").orEmpty(),
            provider = get<String?>("provider").orEmpty(),
            // 서버 시각이 아직 안 채워진 내 쓰기(로컬 캐시)는 지금으로 본다.
            createdAtMs = runCatching { get<Timestamp?>("createdAt")?.ms() }.getOrNull() ?: kotlin.time.Clock.System.now().toEpochMilliseconds(),
            expireAtMs = get<Timestamp>("expireAt").ms(),
            status = DeviceChangeStatus.entries.firstOrNull { it.name.equals(get<String?>("status"), ignoreCase = true) } ?: DeviceChangeStatus.PENDING,
            answeredAtMs = runCatching { get<Timestamp?>("answeredAt")?.ms() }.getOrNull(),
            second = get<Boolean?>("second") == true,
            counts = counts?.let {
                TransferCounts(
                    letters = it["letters"]?.toInt() ?: 0, events = it["events"]?.toInt() ?: 0,
                    photos = it["photos"]?.toInt() ?: 0, drawings = it["drawings"]?.toInt() ?: 0,
                )
            },
        )
    }

    override suspend fun deviceKeys(coupleId: String, deviceId: String): Pairing.PublicKeys? {
        val snap = couple(coupleId).collection("devices").document(deviceId).get()
        if (!snap.exists) return null
        return Pairing.PublicKeys(Base64.decode(snap.get<String>("signPub")), Base64.decode(snap.get<String>("boxPub")))
    }

    override suspend fun approve(coupleId: String, requestId: String, approval: DeviceChangeApproval) {
        val newDeviceId = change(coupleId, requestId).get().get<String>("newDeviceId")
        // 봉투를 먼저 — 함수가 활성화하자마자 새 폰이 읽는다.
        couple(coupleId).collection("keys").document(newDeviceId).set(KeyEnvelopeDoc(Base64.encode(approval.wrappedKey), approval.epoch))
        val c = approval.counts
        change(coupleId, requestId).collection("answer").document("partner").set(
            ApproveDoc(
                "approve", approval.approverDeviceId, approval.cutoff, Base64.encode(approval.sig),
                mapOf("letters" to c.letters, "events" to c.events, "photos" to c.photos, "drawings" to c.drawings),
            ),
        )
    }

    override suspend fun reject(coupleId: String, requestId: String) {
        change(coupleId, requestId).collection("answer").document("partner").set(ResultDoc("reject"))
    }

    override suspend fun cancel(coupleId: String, requestId: String, block: Boolean) {
        change(coupleId, requestId).collection("answer").document("owner").set(ResultDoc(if (block) "block" else "cancel"))
    }

    override suspend fun wrappedKey(coupleId: String, deviceId: String): ByteArray? {
        val snap = runCatching { couple(coupleId).collection("keys").document(deviceId).get() }.getOrNull() ?: return null
        return if (snap.exists) Base64.decode(snap.data(KeyEnvelopeDoc.serializer()).wrapped) else null
    }

    override suspend fun partnerDevice(coupleId: String, myUid: String): LinkedDevice? {
        val docs = couple(coupleId).collection("devices").get().documents
        val d = docs.firstOrNull { it.get<String?>("uid") != myUid && it.get<Boolean?>("active") == true } ?: return null
        return LinkedDevice(d.get("uid"), d.id, Pairing.PublicKeys(Base64.decode(d.get<String>("signPub")), Base64.decode(d.get<String>("boxPub"))))
    }

    override suspend fun finalize(coupleId: String, requestId: String, challengeSig: ByteArray): String =
        Firebase.functions(region).httpsCallable("finalizeDevice")
            .invoke(mapOf("coupleId" to coupleId, "requestId" to requestId, "challengeSig" to Base64.encode(challengeSig)))
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
