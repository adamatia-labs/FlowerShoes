package com.sadturtleman.flowershoes.core.data.sync

import com.sadturtleman.flowershoes.core.crypto.CoupleKey
import com.sadturtleman.flowershoes.core.crypto.DeviceKeys
import com.sadturtleman.flowershoes.core.crypto.Pairing
import com.sadturtleman.flowershoes.core.data.Identity
import com.sadturtleman.flowershoes.core.data.toModel
import com.sadturtleman.flowershoes.core.database.AppDatabase
import com.sadturtleman.flowershoes.core.database.CoupleEntity
import com.sadturtleman.flowershoes.core.database.DeviceEntity
import com.sadturtleman.flowershoes.core.database.RevokedDeviceEntity
import com.sadturtleman.flowershoes.core.model.DeviceState
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * 기기 교체(#20). 새 폰이 같은 계정으로 로그인하면 요청을 남기고, 상대가 승인하면 상대 폰이
 * 새 커플 키(epoch+1)를 새 기기 공개키로 봉인해 전하고 공유 데이터 전체를 스냅샷으로 다시 보낸다.
 * 옛 폰은 "내가 아니에요"로 아직 승인 전인 요청을 막을 수 있다. 신뢰 모델은 초대 링크(#15)와 같다.
 */

/** 요청 상태. 승인 · 거절 · 취소 · 막기는 답을 확인한 함수(onDeviceChangeAnswered)만 쓴다. */
enum class DeviceChangeStatus {
    PENDING,
    APPROVED,

    /** 상대가 "내가 아는 일이 아니에요". */
    REJECTED,

    /** 새 폰이 요청을 취소했거나 새 요청으로 바뀌었다. */
    CANCELLED,

    /** 옛 폰이 "내가 한 게 아니에요 · 막기". */
    BLOCKED,
}

/** 새 폰이 받아 올 기록 수. 상대 폰이 승인할 때 세어 요청에 싣는다(받는 중 진행률의 분모). */
data class TransferCounts(val letters: Int = 0, val events: Int = 0, val photos: Int = 0, val drawings: Int = 0)

/** couples/{c}/deviceChanges/{id}. */
data class DeviceChangeRequest(
    val id: String,
    val uid: String,
    val newDeviceId: String,
    val oldDeviceId: String,
    /** 표시 정보(서명 밖): "Galaxy S25" · 로그인 제공자(KAKAO · APPLE · GOOGLE). */
    val deviceName: String,
    val provider: String,
    val createdAtMs: Long,
    val expireAtMs: Long,
    val status: DeviceChangeStatus = DeviceChangeStatus.PENDING,
    val answeredAtMs: Long? = null,
    /** 같은 사람의 24시간 안 두 번째 요청(함수가 정한다). 상대 화면에 경고 + 한 번 더 확인. */
    val second: Boolean = false,
    val counts: TransferCounts? = null,
) {
    fun expired(nowMs: Long) = status == DeviceChangeStatus.PENDING && expireAtMs <= nowMs
}

/** 상대 활성 기기의 승인. [wrappedKey] 는 새 커플 키를 새 기기 boxPub 으로 봉인한 것(keys/{newDeviceId}). */
class DeviceChangeApproval(
    val approverDeviceId: String,
    val cutoff: Map<String, Long>,
    val sig: ByteArray,
    val wrappedKey: ByteArray,
    val epoch: Int,
    val counts: TransferCounts,
)

/** 기기 교체의 서버 문서. 활성화 · 옛 기기 폐기 · 상태는 답을 확인한 함수가 바꾼다. */
interface DeviceChangeTransport {
    /** 요청 · 기기 문서가 바뀌면 울린다(앱이 살아 있는 동안 두 폰을 깨운다). */
    fun changes(coupleId: String): kotlinx.coroutines.flow.Flow<Unit> = kotlinx.coroutines.flow.emptyFlow()

    /** users/{uid}.coupleId — 새 폰은 이 값으로 이미 연결된 계정인지 안다. 확인하지 못하면 null. */
    suspend fun serverCoupleId(): String?

    /** couples/{c}.members[uid] — 지금 그 사람의 활성 기기. */
    suspend fun currentDevice(coupleId: String, uid: String): String?

    /** 대기 기기 문서(없을 때만) + 요청 문서. 요청의 createdAt 은 서버 시각이다. */
    suspend fun request(coupleId: String, request: DeviceChangeRequest, keys: Pairing.PublicKeys)

    suspend fun requests(coupleId: String): List<DeviceChangeRequest>

    /** 대기 기기 공개키(couples/{c}/devices/{id}). */
    suspend fun deviceKeys(coupleId: String, deviceId: String): Pairing.PublicKeys?

    /** 커플 키 봉투(keys/{newDeviceId}) + 상대 답(answer/partner, approve). */
    suspend fun approve(coupleId: String, requestId: String, approval: DeviceChangeApproval)

    /** 상대 답(answer/partner, reject). */
    suspend fun reject(coupleId: String, requestId: String)

    /** 요청한 사람의 답(answer/owner): 새 폰의 취소 또는 옛 폰의 막기. */
    suspend fun cancel(coupleId: String, requestId: String, block: Boolean)

    /** keys/{deviceId}: 활성화된 뒤에만 읽을 수 있다. */
    suspend fun wrappedKey(coupleId: String, deviceId: String): ByteArray?

    /** 커플의 상대 활성 기기. */
    suspend fun partnerDevice(coupleId: String, myUid: String): LinkedDevice?

    /** callable finalizeDevice: 새 기기 키로 서명한 챌린지를 확인하고 커스텀 토큰을 돌려준다(옛 세션 폐기). */
    suspend fun finalize(coupleId: String, requestId: String, challengeSig: ByteArray): String
}

object DeviceChangeMessages {
    /** firebase/functions/src/sig.ts approvalMessage 와 같다. */
    fun approval(coupleId: String, requestId: String, newDeviceId: String, cutoff: Map<String, Long>): ByteArray {
        val sorted = cutoff.keys.sorted().joinToString(",") { "$it=${cutoff.getValue(it)}" }
        return "kkotsin-approval-v1|$coupleId|$requestId|$newDeviceId|$sorted".encodeToByteArray()
    }

    /** sig.ts finalizeMessage 와 같다. */
    fun finalize(coupleId: String, requestId: String, uid: String): ByteArray =
        "kkotsin-finalize-v1|$coupleId|$requestId|$uid".encodeToByteArray()
}

/** 새 폰 화면이 그리는 상태. */
sealed interface DeviceChangeState {
    data object Idle : DeviceChangeState

    data class Waiting(val request: DeviceChangeRequest) : DeviceChangeState

    /**
     * 승인됐고 커플 키 · 커플 행을 만들었다. 기록은 이어지는 동기화로 들어온다.
     * [fresh]: 이번에 처음 연결했다(앱을 다시 켜 이어 본 것이 아니다) — 연결 기록을 한 번만 남기려고.
     */
    data class Linked(val coupleId: String, val expected: TransferCounts, val epoch: Int, val fresh: Boolean = false) : DeviceChangeState

    data object Rejected : DeviceChangeState

    /** 옛 폰이 막았다. */
    data object Blocked : DeviceChangeState

    data object Expired : DeviceChangeState

    data class Failed(val reason: String) : DeviceChangeState
}

/** 이 폰에 지금 있는 기록 수(받는 중 진행률의 분자, 승인할 때 보낼 수). */
suspend fun AppDatabase.transferCounts(): TransferCounts {
    val letters = letterDao().allActive()
    val events = eventDao().all().filter { it.deletedAtMs == null }
    return TransferCounts(
        letters = letters.size,
        events = events.size,
        photos = letters.sumOf { it.toModel().photoCount } + events.sumOf { it.toModel().photoIds.size },
        drawings = letters.sumOf { drawingDao().forLetter(it.id).size },
    )
}

/**
 * 새 폰 쪽. 로그인 뒤 서버가 이 계정의 커플을 알면 [request] → 상대 승인을 [poll] 로 기다린다.
 * 승인되면 커플 키를 풀고 커플 · 기기 행을 만든 뒤 finalizeDevice 토큰으로 다시 로그인한다.
 */
class DeviceChangeSession(
    private val db: AppDatabase,
    private val identity: Identity,
    private val vault: KeyVault,
    private val transport: DeviceChangeTransport,
    private val clock: Clock = Clock.System,
    /** 새 폰 이름 · 로그인 제공자 — 상대 · 옛 폰 카드에 보인다. */
    private val deviceInfo: suspend () -> Pair<String, String> = { "" to "" },
    /** finalizeDevice 가 준 커스텀 토큰으로 다시 로그인. */
    private val signIn: suspend (String) -> Unit = {},
) {
    var state: DeviceChangeState = DeviceChangeState.Idle
        private set

    private var coupleId: String? = null

    private suspend fun myKeys(): DeviceKeys = vault.deviceKeys() ?: DeviceKeys.generate().also { vault.putDeviceKeys(it) }

    /**
     * 이 계정이 이미 다른 폰에서 연결돼 있으면 그 커플 id. 이 폰에 커플이 있으면 null(교체가 아니다) —
     * 단, 이 기기의 교체가 승인돼 커플을 만든 뒤 온보딩을 끝내기 전에 앱이 꺼졌다면 그 커플(이어서 받는다).
     */
    suspend fun linkedElsewhere(): String? {
        val local = db.coupleDao().get()?.takeIf { it.partnerUserId.isNotBlank() }
        if (local != null) return local.id.takeIf { runCatching { mine(local.id)?.status == DeviceChangeStatus.APPROVED }.getOrDefault(false) }?.also { coupleId = it }
        return runCatching { transport.serverCoupleId() }.getOrNull()?.also { coupleId = it }
    }

    private suspend fun mine(c: String): DeviceChangeRequest? =
        transport.requests(c).filter { it.uid == identity.userId && it.newDeviceId == identity.deviceId }.maxByOrNull { it.createdAtMs }

    /** 상대에게 "새 폰 맞나요?"를 보낸다. 같은 기기의 이전 요청은 서버가 취소된 것으로 바꾼다. */
    suspend fun request(): DeviceChangeState {
        val c = coupleId ?: linkedElsewhere() ?: return set(DeviceChangeState.Failed("연결된 연인을 찾지 못했어요"))
        val keys = myKeys()
        val old = transport.currentDevice(c, identity.userId).orEmpty()
        val (name, provider) = runCatching { deviceInfo() }.getOrDefault("" to "")
        val now = clock.now().toEpochMilliseconds()
        val r = DeviceChangeRequest(
            id = Uuid.random().toString(), uid = identity.userId, newDeviceId = identity.deviceId, oldDeviceId = old,
            deviceName = name.take(MAX_NAME), provider = provider.take(MAX_PROVIDER),
            createdAtMs = now, expireAtMs = now + REQUEST_TTL.inWholeMilliseconds,
        )
        transport.request(c, r, Pairing.PublicKeys.of(keys))
        return set(DeviceChangeState.Waiting(r))
    }

    /** 앱을 다시 켰을 때도 이 기기의 마지막 요청으로 이어 본다. */
    suspend fun poll(): DeviceChangeState {
        if (state is DeviceChangeState.Linked) return state
        val c = coupleId ?: linkedElsewhere() ?: return state
        val mine = mine(c) ?: return state
        // 이미 커플을 만들었다(앱을 다시 켰다): 다시 풀지 않고 이어서 받는다.
        db.coupleDao().get()?.takeIf { it.id == c && mine.status == DeviceChangeStatus.APPROVED }?.let {
            return set(DeviceChangeState.Linked(c, mine.counts ?: TransferCounts(), it.keyEpoch))
        }
        val now = clock.now().toEpochMilliseconds()
        return set(
            when {
                mine.expired(now) -> DeviceChangeState.Expired
                mine.status == DeviceChangeStatus.PENDING -> DeviceChangeState.Waiting(mine)
                mine.status == DeviceChangeStatus.REJECTED -> DeviceChangeState.Rejected
                mine.status == DeviceChangeStatus.BLOCKED -> DeviceChangeState.Blocked
                mine.status == DeviceChangeStatus.CANCELLED -> DeviceChangeState.Idle
                else -> finish(c, mine) ?: DeviceChangeState.Waiting(mine)
            },
        )
    }

    /** "요청 취소". */
    suspend fun cancel(): DeviceChangeState {
        val waiting = state as? DeviceChangeState.Waiting ?: return set(DeviceChangeState.Idle)
        coupleId?.let { transport.cancel(it, waiting.request.id, block = false) }
        return set(DeviceChangeState.Idle)
    }

    /** 승인된 요청: 커플 키를 풀고 커플 · 기기 행을 만든 뒤 세션을 이어 받는다. 아직 활성화 전이면 null. */
    private suspend fun finish(c: String, r: DeviceChangeRequest): DeviceChangeState? {
        val wrapped = transport.wrappedKey(c, identity.deviceId) ?: return null
        val partner = transport.partnerDevice(c, identity.userId) ?: return null
        val keys = myKeys()
        val key = CoupleKey.unwrap(keys, wrapped)
        vault.putCoupleKey(key)
        val now = clock.now().toEpochMilliseconds()
        db.coupleDao().clear()
        db.coupleDao().upsert(CoupleEntity(c, identity.userId, partner.uid, key.epoch, now))
        db.deviceDao().upsert(DeviceEntity(identity.deviceId, identity.userId, identity.userId, keys.signPub, keys.boxPub, DeviceState.ACTIVE.name, now, null))
        db.deviceDao().upsert(DeviceEntity(partner.deviceId, partner.uid, partner.uid, partner.keys.signPub, partner.keys.boxPub, DeviceState.ACTIVE.name, now, null))
        // 옛 세션(같은 계정의 옛 폰)을 끊고 이 기기로 다시 로그인한다. 실패해도 연결은 끝났다(옛 세션이 토큰 만료까지 남을 뿐이다).
        runCatching { signIn(transport.finalize(c, r.id, keys.sign(DeviceChangeMessages.finalize(c, r.id, identity.userId)))) }
        return DeviceChangeState.Linked(c, r.counts ?: TransferCounts(), key.epoch, fresh = true)
    }

    /** 넘겨받은 내 역할(스냅샷의 내 사용자 행). 새 폰은 온보딩을 건너뛰므로 이 값으로 역할을 정한다. */
    suspend fun myRole(): String? = db.userDao().get(identity.userId)?.role

    suspend fun localCounts(): TransferCounts = db.transferCounts()

    private fun set(next: DeviceChangeState) = next.also { state = it }

    companion object {
        /** 요청 유효기간(#20 결정 2). firestore.rules 의 상한과 같다. */
        val REQUEST_TTL = 7.days

        /** firestore.rules 의 deviceName · provider 상한. */
        const val MAX_NAME = 60
        const val MAX_PROVIDER = 20
    }
}

/** 상대 폰의 승인 결과. */
sealed interface ApproveResult {
    /** 받아들여졌고 보냈다. [skipped]: 크기 한도를 넘어 보내지 못한 항목 수(#20 결정 5). */
    data class Approved(val skipped: Int) : ApproveResult

    /** 답은 갔지만 아직 확인되지 않았다 — 다음 동기화가 마무리한다. */
    data object Sent : ApproveResult

    /** 그사이 옛 폰이 막았거나 새 폰이 취소했다. 아무것도 바꾸지 않았다. */
    data object Stopped : ApproveResult
}

/** 상대 · 옛 폰 화면에 띄울 것. */
sealed interface DeviceChangePrompt {
    val request: DeviceChangeRequest

    /** 상대 폰: "새 폰 맞나요?". [recent] 는 그 사람의 최근 24시간 요청(최근 바뀐 기록). */
    data class Approve(override val request: DeviceChangeRequest, val recent: List<DeviceChangeRequest>) : DeviceChangePrompt

    /** 옛 폰: "새 폰에서 로그인했어요". 승인 전이면 막을 수 있다. */
    data class SignedInElsewhere(override val request: DeviceChangeRequest) : DeviceChangePrompt {
        val canBlock: Boolean get() = request.status == DeviceChangeStatus.PENDING
    }
}

/**
 * 연결된 폰 쪽(상대 폰 · 옛 폰). 동기화 때마다 [prompts] 를 읽어 화면에 띄우고 사용자의 답을 보낸다.
 * 승인은 우편함을 끝까지 받아 반영한 뒤 그 커서를 옛 기기 cutoff 로 적고, 새 커플 키를 새 기기 앞으로 봉인한다.
 */
class DeviceChangeApprover(
    private val db: AppDatabase,
    private val identity: Identity,
    private val vault: KeyVault,
    private val transport: DeviceChangeTransport,
    private val sync: SyncClient?,
    private val clock: Clock = Clock.System,
    /** 승인 뒤 함수의 활성화를 다시 확인하기 전 기다림. */
    private val confirmDelay: suspend () -> Unit = { kotlinx.coroutines.delay(1_000) },
) {
    suspend fun prompts(): List<DeviceChangePrompt> {
        val couple = db.coupleDao().get()?.takeIf { it.partnerUserId.isNotBlank() } ?: return emptyList()
        val now = clock.now().toEpochMilliseconds()
        val all = transport.requests(couple.id)
        // 확인이 늦었던(또는 앱이 꺼져 끊긴) 승인을 마무리한다.
        settle(couple.id, all)
        val forPartner = all.filter { it.uid == couple.partnerUserId && it.status == DeviceChangeStatus.PENDING && !it.expired(now) }.map { r ->
            DeviceChangePrompt.Approve(r, all.filter { it.uid == r.uid && r.createdAtMs - it.createdAtMs in 0 until RECENT.inWholeMilliseconds }.sortedBy { it.createdAtMs })
        }
        // 옛 폰: 내 계정의 다른 기기가 요청했다(승인 뒤에도 하루는 알린다).
        val forMe = all.filter {
            it.uid == identity.userId && it.newDeviceId != identity.deviceId && now - it.createdAtMs < RECENT.inWholeMilliseconds &&
                (it.status == DeviceChangeStatus.PENDING && !it.expired(now) || it.status == DeviceChangeStatus.APPROVED)
        }.map { DeviceChangePrompt.SignedInElsewhere(it) }
        return (forPartner + forMe).sortedBy { it.request.createdAtMs }
    }

    /** 동기화 루프와 승인 화면이 함께 마무리하려 할 수 있다 — 한 번에 하나만. */
    private val settling = kotlinx.coroutines.sync.Mutex()

    /**
     * "승인하기". 새 커플 키는 답을 쓰기 전에 보관함에 "승인 중"으로 먼저 둔다 — 답을 쓴 뒤 앱이 꺼져도
     * 함수가 새 폰을 활성화하면 그 키로 이어 갈 수 있게. 함수가 승인을 받아들였는지(옛 폰의 막기와 겨룰 수 있다)
     * 확인하고 나서야 현재 키 · 기기 행을 바꾸고 공유 데이터 전체를 보낸다. 확인이 늦으면 [prompts] 가 이어서 마무리한다.
     */
    suspend fun approve(request: DeviceChangeRequest): ApproveResult {
        val couple = checkNotNull(db.coupleDao().get()) { "연결된 커플이 없다" }
        check(request.uid == couple.partnerUserId) { "연인의 요청이 아니다" }
        val newKeys = checkNotNull(transport.deviceKeys(couple.id, request.newDeviceId)) { "새 기기 공개키가 없다" }
        val keys = checkNotNull(vault.deviceKeys()) { "기기 키가 없다" }
        // 같은 요청을 다시 누르면(답 쓰기가 실패했던 경우) 같은 키 · cutoff 로 다시 보낸다 — 봉투가 어긋나지 않게.
        val pending = vault.pendingApproval()?.also { check(it.requestId == request.id) { "다른 기기 교체를 마무리하는 중이다" } }
            ?: run {
                // 옛 기기가 보낸 정상 Op 을 먼저 받아 반영한 뒤 그 커서를 cutoff 로 적는다.
                runCatching { sync?.syncOnce() }
                val old = db.deviceDao().activeFor(couple.partnerUserId)
                val cutoff = old?.let { o -> db.syncDao().cursors().filter { it.stream.startsWith("${o.id}:") }.associate { it.stream to it.contiguousSeq } }.orEmpty()
                val current = checkNotNull(vault.currentCoupleKey()) { "커플 키가 없다" }
                PendingApproval(request.id, CoupleKey.generate(current.epoch + 1), old?.id, cutoff).also { vault.putPendingApproval(it) }
            }
        transport.approve(
            couple.id, request.id,
            DeviceChangeApproval(
                identity.deviceId, pending.cutoff, keys.sign(DeviceChangeMessages.approval(couple.id, request.id, request.newDeviceId, pending.cutoff)),
                pending.key.wrapFor(newKeys.boxPub), pending.key.epoch, db.transferCounts(),
            ),
        )
        repeat(CONFIRM_ATTEMPTS) {
            settle(couple.id)?.let { return it }
            confirmDelay()
        }
        return settle(couple.id) ?: ApproveResult.Sent
    }

    /** 보관함의 "승인 중"을 서버 상태로 마무리한다. 아직이면 null. */
    private suspend fun settle(coupleId: String, known: List<DeviceChangeRequest>? = null): ApproveResult? = settling.withLock {
        val pending = vault.pendingApproval() ?: return@withLock null
        val r = (known ?: transport.requests(coupleId)).firstOrNull { it.id == pending.requestId }
        when {
            r?.status == DeviceChangeStatus.APPROVED -> ApproveResult.Approved(apply(pending, r))
            // 옛 폰이 먼저 막았거나 새 폰이 취소했다(또는 요청이 없어졌다): 키 · 기기는 그대로.
            r == null || r.status != DeviceChangeStatus.PENDING || r.expired(clock.now().toEpochMilliseconds()) -> {
                vault.putPendingApproval(null)
                ApproveResult.Stopped
            }
            else -> null
        }
    }

    /** 승인이 받아들여졌다: 새 커플 키 · 옛 기기 폐기 · 새 기기 행, 그리고 전체 스냅샷. 돌려주는 값은 건너뛴 항목 수(#20 결정 5). */
    private suspend fun apply(p: PendingApproval, r: DeviceChangeRequest): Int {
        val couple = checkNotNull(db.coupleDao().get())
        val newKeys = checkNotNull(transport.deviceKeys(couple.id, r.newDeviceId)) { "새 기기 공개키가 없다" }
        vault.putCoupleKey(p.key)
        val now = clock.now().toEpochMilliseconds()
        p.oldDeviceId?.let { id ->
            db.syncDao().addRevoked(RevokedDeviceEntity(id, now, cutoffJson(p.cutoff)))
            db.deviceDao().get(id)?.let { db.deviceDao().upsert(it.copy(state = DeviceState.REVOKED.name, revokedAtMs = now)) }
        }
        db.deviceDao().upsert(DeviceEntity(r.newDeviceId, r.uid, r.uid, newKeys.signPub, newKeys.boxPub, DeviceState.ACTIVE.name, now, null))
        db.coupleDao().upsert(couple.copy(keyEpoch = p.key.epoch))
        vault.putPendingApproval(null)
        // 공유 데이터 전체를 새 기기로. 다음 push 가 새 키로 새 기기 우편함에 올린다.
        val s = sync ?: return 0
        s.writeFullSnapshot()
        return runCatching { s.syncOnce().skipped }.getOrDefault(0)
    }

    /** 상대 폰의 "내가 아는 일이 아니에요". */
    suspend fun reject(request: DeviceChangeRequest) {
        val couple = db.coupleDao().get() ?: return
        transport.reject(couple.id, request.id)
    }

    /** 옛 폰의 "내가 한 게 아니에요 · 막기": 요청 취소 · 새 기기 무효 · 상대에게 알림(함수). */
    suspend fun block(request: DeviceChangeRequest) {
        val couple = db.coupleDao().get() ?: return
        transport.cancel(couple.id, request.id, block = true)
    }

    private fun cutoffJson(cutoff: Map<String, Long>): String =
        Json.encodeToString(JsonObject.serializer(), buildJsonObject { cutoff.forEach { (k, v) -> put(k, JsonPrimitive(v)) } })

    private companion object {
        val RECENT = 24.hours

        /** 승인 확인을 몇 번 볼지(1초 간격). 그 뒤는 [prompts] 가 이어서 본다. */
        const val CONFIRM_ATTEMPTS = 10
    }
}
