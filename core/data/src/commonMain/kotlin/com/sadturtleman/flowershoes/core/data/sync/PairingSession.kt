package com.sadturtleman.flowershoes.core.data.sync

import com.sadturtleman.flowershoes.core.crypto.DeviceKeys
import com.sadturtleman.flowershoes.core.crypto.InviteToken
import com.sadturtleman.flowershoes.core.crypto.Pairing
import com.sadturtleman.flowershoes.core.data.Identity
import com.sadturtleman.flowershoes.core.database.AppDatabase
import com.sadturtleman.flowershoes.core.database.CoupleEntity
import com.sadturtleman.flowershoes.core.database.DeviceEntity
import com.sadturtleman.flowershoes.core.model.DeviceState
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/** 연결 화면이 그리는 상태(#15 결정 6). */
sealed interface PairingState {
    data object Idle : PairingState

    /** 보낸 쪽: 링크를 만들었다. [token] 으로 링크를 만들어 공유하고 상대가 누르길 기다린다(앱은 홈으로 가도 된다). */
    data class Waiting(val token: String, val expireAtMs: Long) : PairingState

    /** 받는 쪽: 수락을 보냈고 서버가 커플을 만들길 기다린다. */
    data class Accepting(val inviterName: String) : PairingState

    data class Linked(val coupleId: String) : PairingState

    /** 만료 · 이미 쓰임 · 새 링크로 무효 · 없는 링크. 서버에서 구분되지 않아 한 상태다("이 초대 링크는 만료됐어요"). */
    data object Expired : PairingState

    /** 내가 보낸 링크를 내가 눌렀다("내가 보낸 초대 링크예요" + 다시 공유). */
    data class OwnLink(val token: String) : PairingState

    data object AlreadyLinked : PairingState

    data class Failed(val reason: String) : PairingState
}

/** 보낸 쪽이 만든 링크. 앱을 껐다 켜도 수락을 이어 받도록 이 기기에 둔다. */
data class PendingInvite(val token: String, val expireAtMs: Long)

interface PendingInviteStore {
    suspend fun load(): PendingInvite?

    suspend fun save(invite: PendingInvite?)
}

class InMemoryPendingInvite : PendingInviteStore {
    private var value: PendingInvite? = null

    override suspend fun load() = value

    override suspend fun save(invite: PendingInvite?) {
        value = invite
    }
}

/**
 * 첫 연결 — 초대 링크 한 번(#15). SAS · 코드 입력 없음.
 *
 * - 보낸 쪽: [createLink] → 링크 공유 → 푸시가 오거나 앱을 열 때 [poll] → [PairingState.Linked].
 * - 받는 쪽: 링크(카카오 앱 실행 · `/i/{token}`)로 들어와 [accept] → 대개 바로 [PairingState.Linked], 아니면 [poll].
 *
 * 커플은 서버 함수(onInviteAccepted)가 만들고, 커플 키는 양쪽이 상대 공개키로 각자 도출한다([Pairing.coupleKey]).
 */
class PairingSession(
    private val db: AppDatabase,
    private val identity: Identity,
    private val vault: KeyVault,
    private val transport: PairingTransport,
    private val clock: Clock = Clock.System,
    private val pending: PendingInviteStore = InMemoryPendingInvite(),
) {
    var state: PairingState = PairingState.Idle
        private set

    /** 지금 다루는 링크. 보낸 쪽이면 [inviter]. */
    private var token: String? = null
    private var inviter = false

    private suspend fun myKeys(): DeviceKeys = vault.deviceKeys() ?: DeviceKeys.generate().also { vault.putDeviceKeys(it) }

    private suspend fun linkedLocally(): Boolean = db.coupleDao().get()?.partnerUserId?.isNotBlank() == true

    /**
     * 새 링크를 만든다. 이전 링크는 서버가 지운다(#15 결정 3).
     * [name] 은 OG 미리보기 · 받는 쪽 화면에 보일 내 표시 이름 — 없으면 내 프로필 이름.
     */
    suspend fun createLink(name: String? = null): PairingState {
        resumePending()?.let { if (it is PairingState.Linked) return it }
        if (linkedLocally()) return set(PairingState.AlreadyLinked)
        val keys = myKeys()
        val shownName = (name ?: db.userDao().get(identity.userId)?.nickname.orEmpty()).trim().take(MAX_NAME)
        val newToken = InviteToken.random()
        val expireAtMs = (clock.now() + INVITE_TTL).toEpochMilliseconds()
        transport.createInvite(newToken, Invite(identity.userId, identity.deviceId, Pairing.PublicKeys.of(keys), shownName, expireAtMs))
        pending.save(PendingInvite(newToken, expireAtMs))
        token = newToken
        inviter = true
        return set(PairingState.Waiting(newToken, expireAtMs))
    }

    /** 링크로 들어왔다. 웹 GET 과 달리 여기서 초대를 소비한다(로그인한 앱의 쓰기). */
    suspend fun accept(input: String): PairingState {
        if (linkedLocally()) return set(PairingState.AlreadyLinked)
        if (!InviteToken.isValid(input)) return set(PairingState.Expired)
        val invite = transport.invite(input)
            // 문서가 없다: 쓰였거나 만료. 내가 수락하고 끝내지 못한 링크면 마저 끝낸다.
            ?: return set(finishIfLinkedVia(input) ?: PairingState.Expired)
        // 자기 링크: 보낸 쪽 상태(기다리는 링크)는 그대로 둔다.
        if (invite.inviterUid == identity.userId) return set(PairingState.OwnLink(input))
        if (invite.expireAtMs <= clock.now().toEpochMilliseconds()) return set(PairingState.Expired)
        token = input
        inviter = false
        // 이미 누가 수락했으면(규칙상 한 번만) 쓰기가 거절된다 — 내가 끝내지 못한 수락이 아니면 만료다.
        runCatching { transport.accept(input, identity.userId, identity.deviceId, Pairing.PublicKeys.of(myKeys())) }
            .onFailure { return set(finishIfLinkedVia(input) ?: PairingState.Expired) }
        set(PairingState.Accepting(invite.name))
        return poll()
    }

    /** 서버가 커플을 만들었는지 본다. 보낸 쪽은 앱을 다시 켠 뒤 처음 부를 때 저장해 둔 링크를 이어 받는다. */
    suspend fun poll(): PairingState {
        if (token == null) return resumePending() ?: state
        return set(refresh())
    }

    private suspend fun resumePending(): PairingState? {
        if (token != null) return null
        val saved = pending.load() ?: return null
        token = saved.token
        inviter = true
        state = PairingState.Waiting(saved.token, saved.expireAtMs)
        return set(refresh())
    }

    private suspend fun refresh(): PairingState {
        val current = token ?: return state
        val shown = state
        if (shown is PairingState.Linked) return shown
        finishIfLinkedVia(current)?.let { return it }
        val invite = if (inviter || shown is PairingState.Accepting) transport.invite(current) else return shown
        val gone = invite == null || invite.expireAtMs <= clock.now().toEpochMilliseconds()
        if (!gone) return shown
        // 함수는 커플을 만든 뒤 초대를 지운다 — 두 읽기 사이에 연결됐을 수 있어 한 번 더 본다.
        finishIfLinkedVia(current)?.let { return it }
        // 만료 · 새 링크로 무효 · 함수가 수락을 거절(그사이 다른 사람과 연결 등).
        if (inviter) pending.save(null)
        return PairingState.Expired
    }

    private suspend fun finishIfLinkedVia(inviteToken: String): PairingState? =
        transport.linkedCoupleId(inviteToken, identity.userId)?.let { finish(it, inviteToken) }

    /** 서버가 만든 커플에서 상대 기기 공개키를 읽어 커플 키(epoch 1)를 도출하고 커플 · 기기 행을 만든다. */
    private suspend fun finish(coupleId: String, inviteToken: String): PairingState? {
        val partner = transport.partnerDevice(coupleId, identity.userId) ?: return null
        val keys = myKeys()
        vault.putCoupleKey(Pairing.coupleKey(keys, partner.keys, inviteToken))
        val now = clock.now().toEpochMilliseconds()
        db.coupleDao().clear()
        db.coupleDao().upsert(CoupleEntity(coupleId, identity.userId, partner.uid, keyEpoch = 1, linkedAtMs = now))
        db.deviceDao().upsert(DeviceEntity(identity.deviceId, identity.userId, identity.userId, keys.signPub, keys.boxPub, DeviceState.ACTIVE.name, now, null))
        db.deviceDao().upsert(DeviceEntity(partner.deviceId, partner.uid, partner.uid, partner.keys.signPub, partner.keys.boxPub, DeviceState.ACTIVE.name, now, null))
        // 연결됐으면 내가 보낸 링크도 끝났다(서버가 두 사람의 초대를 지운다).
        pending.save(null)
        return PairingState.Linked(coupleId)
    }

    private fun set(next: PairingState): PairingState = next.also { state = it }

    companion object {
        /** 초대 유효기간(#15 결정 3). firestore.rules 의 상한과 같다. */
        val INVITE_TTL = 7.days

        /** firestore.rules 의 name 상한. */
        const val MAX_NAME = 40
    }
}

/** 초대 링크 모양. 경로 `/i/{token}` 은 고정이고 호스트만 바뀐다(커스텀 도메인 전환 대비). */
object InviteLinks {
    /** Firebase Hosting 사이트. 실제 값과 설정값 연결은 딥링크 티켓(#39)에서. */
    const val DEFAULT_HOST = "TODO_HOSTING_SITE.web.app"

    fun url(token: String, host: String = DEFAULT_HOST): String = "https://$host/i/$token"
}

internal fun ByteArray.b64(): String = Base64.encode(this)

internal fun String.unb64(): ByteArray = Base64.decode(this)
