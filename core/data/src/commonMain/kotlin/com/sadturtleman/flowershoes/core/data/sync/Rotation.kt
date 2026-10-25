package com.sadturtleman.flowershoes.core.data.sync

import com.sadturtleman.flowershoes.core.crypto.CoupleKey
import com.sadturtleman.flowershoes.core.crypto.DeviceKeys
import com.sadturtleman.flowershoes.core.crypto.Hash
import com.sadturtleman.flowershoes.core.crypto.Signatures
import com.sadturtleman.flowershoes.core.data.Identity
import com.sadturtleman.flowershoes.core.database.AppDatabase
import com.sadturtleman.flowershoes.core.database.DeviceEntity
import com.sadturtleman.flowershoes.core.database.RevokedDeviceEntity
import com.sadturtleman.flowershoes.core.model.DeviceState
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** 기기 교체 요청(couples/{c}/rotations/{id}). */
class RotationRequest(
    val id: String,
    val uid: String,
    val newDeviceId: String,
    val oldDeviceId: String,
    val epoch: Int,
    val newSignPub: ByteArray,
    val newBoxPub: ByteArray,
    val wrappedKey: ByteArray,
    val mac: ByteArray,
    val sig: ByteArray,
    val createdAtMs: Long,
    /** 상대 폰 안내용(서명 밖 표시 정보): "Galaxy S25" · 로그인 제공자(KAKAO · APPLE · GOOGLE). */
    val deviceName: String = "",
    val provider: String = "",
)

/** 상대 활성 기기의 서명된 승인. */
class RotationApproval(val approverDeviceId: String, val cutoff: Map<String, Long>, val sig: ByteArray)

/** 기기 교체(7-3-A)에 필요한 서버 문서. 활성화 자체는 승인을 확인한 함수가 한다. */
interface RotationTransport {
    /** Wake both phones when a request or server activation changes, without requiring APNs. */
    fun changes(coupleId: String): kotlinx.coroutines.flow.Flow<Unit> = kotlinx.coroutines.flow.emptyFlow()

    suspend fun putPendingDevice(coupleId: String, deviceId: String, uid: String, signPub: ByteArray, boxPub: ByteArray)

    suspend fun putRotation(coupleId: String, request: RotationRequest)

    suspend fun rotations(coupleId: String): List<RotationRequest>

    suspend fun putApproval(coupleId: String, rotationId: String, approval: RotationApproval)

    suspend fun approval(coupleId: String, rotationId: String): RotationApproval?

    /** callable finalizeDevice: 새 기기 키로 서명한 챌린지를 확인하고 커스텀 토큰을 돌려준다. */
    suspend fun finalize(coupleId: String, rotationId: String, challengeSig: ByteArray): String
}

object RotationMessages {
    /** 요청 서명 · MAC 대상. 서버 · 상대가 같은 바이트를 만든다. */
    fun request(coupleId: String, r: RotationRequest): ByteArray = Hash.of(
        "kkotsin-rotation-req-v1".encodeToByteArray(), coupleId.encodeToByteArray(), r.id.encodeToByteArray(),
        r.newDeviceId.encodeToByteArray(), r.oldDeviceId.encodeToByteArray(), r.epoch.toString().encodeToByteArray(),
        r.newSignPub, r.newBoxPub, r.wrappedKey, length = 64,
    )

    /** firebase/functions/src/sig.ts approvalMessage 와 같다. */
    fun approval(coupleId: String, rotationId: String, newDeviceId: String, cutoff: Map<String, Long>): ByteArray {
        val sorted = cutoff.keys.sorted().joinToString(",") { "$it=${cutoff.getValue(it)}" }
        return "kkotsin-approval-v1|$coupleId|$rotationId|$newDeviceId|$sorted".encodeToByteArray()
    }

    /** sig.ts finalizeMessage 와 같다. */
    fun finalize(coupleId: String, rotationId: String, uid: String): ByteArray =
        "kkotsin-finalize-v1|$coupleId|$rotationId|$uid".encodeToByteArray()
}

/**
 * 새 폰 쪽(백업 복원 직후). 백업에 있던 커플 키(현재 epoch)로 MAC 을 만들어 "커플 키를 가진 본인"임을 증명한다.
 * 계정만 털린 사람은 MAC 을 못 만들어 대기 상태에 머문다 → 진짜 폰이 끊기는 공격이 안 된다.
 */
class RotationRequester(
    private val db: AppDatabase,
    private val identity: Identity,
    private val vault: KeyVault,
    private val transport: RotationTransport,
    private val clock: Clock = Clock.System,
    /** 새 폰 이름 · 로그인 제공자(상대 폰의 "새 폰으로 바꿨어요" 카드). */
    private val deviceInfo: suspend () -> Pair<String, String> = { "" to "" },
) {
    suspend fun request(oldDeviceId: String): RotationRequest {
        val couple = checkNotNull(db.coupleDao().get()) { "복원된 커플이 없다" }
        val oldKey = checkNotNull(vault.currentCoupleKey()) { "백업에 커플 키가 없다" }
        val keys = vault.deviceKeys() ?: DeviceKeys.generate().also { vault.putDeviceKeys(it) }
        val partner = checkNotNull(db.deviceDao().activeFor(couple.partnerUserId)) { "상대 기기를 모른다" }
        transport.putPendingDevice(couple.id, identity.deviceId, identity.userId, keys.signPub, keys.boxPub)

        val newKey = CoupleKey.generate(oldKey.epoch + 1)
        vault.putCoupleKey(newKey)
        val draft = RotationRequest(
            id = Uuid.random().toString(), uid = identity.userId, newDeviceId = identity.deviceId, oldDeviceId = oldDeviceId,
            epoch = newKey.epoch, newSignPub = keys.signPub, newBoxPub = keys.boxPub, wrappedKey = newKey.wrapFor(partner.boxPub),
            mac = ByteArray(0), sig = ByteArray(0), createdAtMs = clock.now().toEpochMilliseconds(),
        )
        val msg = RotationMessages.request(couple.id, draft)
        val info = deviceInfo()
        val request = RotationRequest(
            draft.id, draft.uid, draft.newDeviceId, draft.oldDeviceId, draft.epoch, draft.newSignPub, draft.newBoxPub,
            draft.wrappedKey, mac = oldKey.mac(msg), sig = keys.sign(msg), createdAtMs = draft.createdAtMs,
            deviceName = info.first, provider = info.second,
        )
        transport.putRotation(couple.id, request)
        return request
    }

    /** 상대 폰이 승인하면 세션을 이어 받고 로컬 기기 정보를 바꾼다. 아직이면 null(복원한 데이터는 읽기 전용으로 보여 준다). */
    suspend fun finishIfApproved(request: RotationRequest): String? {
        val couple = checkNotNull(db.coupleDao().get())
        transport.approval(couple.id, request.id) ?: return null
        val keys = checkNotNull(vault.deviceKeys())
        val token = transport.finalize(couple.id, request.id, keys.sign(RotationMessages.finalize(couple.id, request.id, identity.userId)))
        val now = clock.now().toEpochMilliseconds()
        db.coupleDao().upsert(couple.copy(keyEpoch = request.epoch))
        db.deviceDao().upsert(DeviceEntity(identity.deviceId, identity.userId, identity.userId, keys.signPub, keys.boxPub, DeviceState.ACTIVE.name, now, null))
        db.deviceDao().get(request.oldDeviceId)?.let { db.deviceDao().upsert(it.copy(state = DeviceState.REVOKED.name, revokedAtMs = now)) }
        return token
    }
}

/** 상대 폰이 받은 교체 요청을 어떻게 처리했는지. */
sealed interface RotationDecision {
    data object Approved : RotationDecision

    /** 자동 승인하지 않았다 — 화면에서 SAS 로 직접 확인(7-3-B)하도록 안내한다. */
    data class NeedsManual(val reason: String) : RotationDecision

    data object Rejected : RotationDecision
}

/**
 * 상대 폰 쪽: 동기화 때마다 교체 요청을 확인해 자동 승인한다(사용자 조작 없음).
 *
 * - MAC(지금 커플 키) · 새 기기 서명이 맞아야 한다. 백업 속 키가 너무 오래돼 MAC 을 검증할 수 없으면 7-3-B 로.
 * - 24시간 안에 두 번째 요청이면 자동 승인하지 않는다(도난 폰과 진짜 주인이 번갈아 교체하는 것을 막는다).
 * - 승인 직전에 우편함을 끝까지 받아 반영하고, 그 시점 커서를 옛 기기 cutoff 로 적는다.
 */
class RotationApprover(
    private val db: AppDatabase,
    private val identity: Identity,
    private val vault: KeyVault,
    private val transport: RotationTransport,
    private val drainInbox: suspend () -> Unit,
    private val clock: Clock = Clock.System,
) {
    private val handled = mutableSetOf<String>()

    suspend fun check(): List<Pair<RotationRequest, RotationDecision>> {
        val couple = db.coupleDao().get() ?: return emptyList()
        val all = transport.rotations(couple.id).filter { it.uid == couple.partnerUserId }.sortedBy { it.createdAtMs }
        return all.filter { it.id !in handled && transport.approval(couple.id, it.id) == null }.map { r ->
            val decision = decide(couple.id, r, all)
            handled += r.id
            r to decision
        }
    }

    private suspend fun decide(coupleId: String, r: RotationRequest, all: List<RotationRequest>): RotationDecision {
        val msg = RotationMessages.request(coupleId, r)
        if (!Signatures.verify(msg, r.sig, r.newSignPub)) return RotationDecision.Rejected
        val current = vault.coupleKey(r.epoch - 1)
            ?: return RotationDecision.NeedsManual("새 폰의 백업이 오래돼 자동으로 확인할 수 없어요. 상대 폰 넘겨주기로 연결해 주세요")
        if (!current.verifyMac(msg, r.mac)) return RotationDecision.Rejected
        val recent = all.any { it.id != r.id && it.createdAtMs < r.createdAtMs && r.createdAtMs - it.createdAtMs < 24.hours.inWholeMilliseconds }
        if (recent) return RotationDecision.NeedsManual("하루 안에 기기 교체가 두 번 요청됐어요. 확인 코드로 직접 확인해 주세요")

        drainInbox()
        val cutoff = db.syncDao().cursors().filter { it.stream.startsWith("${r.oldDeviceId}:") }.associate { it.stream to it.contiguousSeq }
        val keys = checkNotNull(vault.deviceKeys())
        val newKey = CoupleKey.unwrap(keys, r.wrappedKey)
        vault.putCoupleKey(newKey)
        transport.putApproval(coupleId, r.id, RotationApproval(identity.deviceId, cutoff, keys.sign(RotationMessages.approval(coupleId, r.id, r.newDeviceId, cutoff))))

        val now = clock.now().toEpochMilliseconds()
        db.syncDao().addRevoked(RevokedDeviceEntity(r.oldDeviceId, now, Json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), buildJsonObject { cutoff.forEach { (k, v) -> put(k, JsonPrimitive(v)) } })))
        db.deviceDao().get(r.oldDeviceId)?.let { db.deviceDao().upsert(it.copy(state = DeviceState.REVOKED.name, revokedAtMs = now)) }
        db.deviceDao().upsert(DeviceEntity(r.newDeviceId, r.uid, r.uid, r.newSignPub, r.newBoxPub, DeviceState.ACTIVE.name, now, null))
        db.coupleDao().get()?.let { db.coupleDao().upsert(it.copy(keyEpoch = newKey.epoch)) }
        return RotationDecision.Approved
    }
}

/** 복원 뒤 기기 교체가 어디까지 왔는지. 화면이 안내 문구를 고른다. */
enum class RestoreRotationState {
    /** 교체할 일이 없다(복원하지 않았거나 이미 끝났다). */
    NotNeeded,

    /** 요청을 올렸고 상대 폰의 자동 확인을 기다린다. 그동안 보낸 것은 확인 뒤에 전달된다. */
    WaitingForPartner,

    /** 방금 끝났다. */
    Done,
}

/**
 * 백업 복원 직후 자동 기기 교체(7-3-A). 동기화할 때마다 한 걸음씩 진행한다 — 앱이 재시작돼도 이어진다.
 *
 * 복원된 DB 에는 옛 폰이 내 활성 기기로 남아 있다. 그 id 가 지금 기기와 다르면 교체가 필요하다.
 * 이미 올린 요청은 서버에서 다시 찾으므로(newDeviceId) 같은 요청을 두 번 만들지 않는다.
 */
class PostRestoreRotation(
    private val db: AppDatabase,
    private val identity: Identity,
    private val requester: RotationRequester,
    private val transport: RotationTransport,
    /** 끝났을 때: 커스텀 토큰으로 다시 로그인 · 키 epoch 저장 등 플랫폼 일. */
    private val onFinished: suspend (token: String, epoch: Int) -> Unit = { _, _ -> },
) {
    suspend fun step(): RestoreRotationState {
        val couple = db.coupleDao().get() ?: return RestoreRotationState.NotNeeded
        val active = db.deviceDao().activeFor(identity.userId) ?: return RestoreRotationState.NotNeeded
        if (active.id == identity.deviceId) return RestoreRotationState.NotNeeded
        val request = transport.rotations(couple.id).lastOrNull { it.newDeviceId == identity.deviceId && it.uid == identity.userId }
            ?: requester.request(oldDeviceId = active.id)
        val token = requester.finishIfApproved(request) ?: return RestoreRotationState.WaitingForPartner
        onFinished(token, request.epoch)
        return RestoreRotationState.Done
    }
}

