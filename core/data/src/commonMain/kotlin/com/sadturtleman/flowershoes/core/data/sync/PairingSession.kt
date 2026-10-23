package com.sadturtleman.flowershoes.core.data.sync

import com.sadturtleman.flowershoes.core.crypto.CoupleKey
import com.sadturtleman.flowershoes.core.crypto.CrockfordCode
import com.sadturtleman.flowershoes.core.crypto.DeviceKeys
import com.sadturtleman.flowershoes.core.crypto.Pairing
import com.sadturtleman.flowershoes.core.data.Identity
import com.sadturtleman.flowershoes.core.database.AppDatabase
import com.sadturtleman.flowershoes.core.database.CoupleEntity
import com.sadturtleman.flowershoes.core.database.DeviceEntity
import com.sadturtleman.flowershoes.core.model.DeviceState
import kotlin.io.encoding.Base64
import kotlin.time.Clock

/** 연결 화면이 그리는 상태. */
sealed interface PairingState {
    data object Idle : PairingState

    /** 초대한 쪽: 코드를 보여 주고 상대가 입력하기를 기다린다. */
    data class ShowCode(val code: String) : PairingState

    /** 초대받은 쪽: 커밋을 올리고 상대 공개키를 기다린다. */
    data object WaitingPartner : PairingState

    /** 두 화면에 같은 숫자가 떠야 한다. 전화 · 면회 · 메시지로 서로 확인한다. */
    /** [device] · [provider]: 상대 폰 교체 받기에서 새 폰 이름 · 로그인 제공자(새 폰이 알려 준 표시 정보, 서명 밖). */
    data class CompareSas(val sas: String, val device: String? = null, val provider: String? = null) : PairingState

    /** 내 쪽은 [맞아요] 를 눌렀고 상대 확인 · 서버의 커플 생성을 기다린다. */
    data object WaitingConfirm : PairingState

    data class Linked(val coupleId: String) : PairingState

    data class Failed(val reason: String) : PairingState
}

/**
 * 첫 연결(설계 7-1). 커밋 → 상대 공개 → 공개 → SAS 순서를 지킨다.
 *
 * 화면은 [start] · [join] 으로 시작하고, 몇 초마다(또는 문서 변경 알림마다) [poll] 을 부른다.
 * 숫자가 맞으면 [confirm], 다르면 화면을 닫는다(서버가 두 SAS 가 다르면 초대를 폐기한다).
 */
class PairingSession(
    private val db: AppDatabase,
    private val identity: Identity,
    private val vault: KeyVault,
    private val transport: PairingTransport,
    private val clock: Clock = Clock.System,
) {
    var state: PairingState = PairingState.Idle
        private set

    private var role: Role? = null
    private var code = ""
    private var nonce = ByteArray(0)
    private var partner: Partner? = null
    private var sas = ""

    private enum class Role { INVITER, JOINER }

    private class Partner(val uid: String, val deviceId: String, val keys: Pairing.PublicKeys)

    private suspend fun myKeys(): DeviceKeys = vault.deviceKeys() ?: DeviceKeys.generate().also { vault.putDeviceKeys(it) }

    suspend fun start(): PairingState {
        myKeys()
        role = Role.INVITER
        code = CrockfordCode.random(CODE_LENGTH)
        transport.createInvite(code, identity.userId)
        return PairingState.ShowCode(code).also { state = it }
    }

    suspend fun join(input: String): PairingState {
        val normalized = CrockfordCode.normalize(input)?.takeIf { it.length == CODE_LENGTH }
            ?: return PairingState.Failed("코드 8자리를 확인해 주세요").also { state = it }
        val keys = myKeys()
        role = Role.JOINER
        code = normalized
        nonce = Pairing.newNonce()
        val commitment = Pairing.commit(Pairing.PublicKeys(keys.signPub, keys.boxPub), nonce)
        transport.putStep(code, Steps.JOIN, mapOf("uid" to identity.userId, "commit" to commitment.b64()))
        return PairingState.WaitingPartner.also { state = it }
    }

    suspend fun poll(): PairingState {
        val keys = myKeys()
        val mine = Pairing.PublicKeys(keys.signPub, keys.boxPub)
        state = when (role) {
            Role.INVITER -> pollInviter(mine)
            Role.JOINER -> pollJoiner(mine)
            null -> state
        }
        return state
    }

    private suspend fun pollInviter(mine: Pairing.PublicKeys): PairingState {
        if (state is PairingState.WaitingConfirm) return finishIfLinked()
        if (state is PairingState.CompareSas) return state
        val join = transport.getStep(code, Steps.JOIN) ?: return state
        // 상대 커밋이 올라온 뒤에야 내 공개키를 올린다.
        if (transport.getStep(code, Steps.INVITER) == null) {
            transport.putStep(code, Steps.INVITER, mapOf("deviceId" to identity.deviceId, "signPub" to mine.signPub.b64(), "boxPub" to mine.boxPub.b64()))
        }
        val reveal = transport.getStep(code, Steps.REVEAL) ?: return state
        val theirs = Pairing.PublicKeys(reveal.getValue("signPub").unb64(), reveal.getValue("boxPub").unb64())
        val theirNonce = reveal.getValue("nonce").unb64()
        if (!Pairing.verifyCommitment(join.getValue("commit").unb64(), theirs, theirNonce)) {
            return PairingState.Failed("상대 키가 처음 약속과 달라요. 새 코드로 다시 시도해 주세요")
        }
        partner = Partner(join.getValue("uid"), reveal.getValue("deviceId"), theirs)
        sas = Pairing.sas(code, inviter = mine, joiner = theirs, joinerNonce = theirNonce)
        return PairingState.CompareSas(sas)
    }

    private suspend fun pollJoiner(mine: Pairing.PublicKeys): PairingState {
        if (state is PairingState.WaitingConfirm) return finishIfLinked()
        if (state is PairingState.CompareSas) return state
        val inviter = transport.getStep(code, Steps.INVITER) ?: return state
        // 상대 공개키를 본 뒤에야 내 키와 nonce 를 공개한다.
        if (transport.getStep(code, Steps.REVEAL) == null) {
            transport.putStep(
                code, Steps.REVEAL,
                mapOf("deviceId" to identity.deviceId, "signPub" to mine.signPub.b64(), "boxPub" to mine.boxPub.b64(), "nonce" to nonce.b64()),
            )
        }
        val theirs = Pairing.PublicKeys(inviter.getValue("signPub").unb64(), inviter.getValue("boxPub").unb64())
        val inviterUid = transport.inviterUid(code) ?: return state
        partner = Partner(inviterUid, inviter.getValue("deviceId"), theirs)
        sas = Pairing.sas(code, inviter = theirs, joiner = mine, joinerNonce = nonce)
        return PairingState.CompareSas(sas)
    }

    /** 두 화면의 숫자가 같다. 초대한 쪽은 여기서 커플 키(epoch 1)를 만들어 상대 공개키로 봉인해 보낸다. */
    suspend fun confirm(): PairingState {
        val p = partner ?: return state
        when (role) {
            Role.INVITER -> {
                val key = CoupleKey.generate(epoch = 1)
                vault.putCoupleKey(key)
                transport.putStep(code, Steps.CONFIRM_INVITER, mapOf("wrappedKey" to key.wrapFor(p.keys.boxPub).b64(), "sas" to sas))
            }
            Role.JOINER -> transport.putStep(code, Steps.CONFIRM_JOINER, mapOf("sas" to sas))
            null -> return state
        }
        state = PairingState.WaitingConfirm
        return finishIfLinked()
    }

    private suspend fun finishIfLinked(): PairingState {
        val coupleId = transport.linkedCoupleId(code) ?: return PairingState.WaitingConfirm
        val p = checkNotNull(partner)
        if (role == Role.JOINER) {
            val wrapped = transport.wrappedKeyFor(coupleId, identity.deviceId) ?: return PairingState.WaitingConfirm
            vault.putCoupleKey(CoupleKey.unwrap(myKeys(), wrapped))
        }
        val keys = myKeys()
        val now = clock.now().toEpochMilliseconds()
        val solo = db.coupleDao().get()
        db.coupleDao().clear()
        db.coupleDao().upsert(
            CoupleEntity(coupleId, identity.userId, p.uid, keyEpoch = 1, linkedAtMs = now, anniversary = solo?.anniversary, anniversaryLamport = solo?.anniversaryLamport ?: 0),
        )
        db.deviceDao().upsert(DeviceEntity(identity.deviceId, identity.userId, identity.userId, keys.signPub, keys.boxPub, DeviceState.ACTIVE.name, now, null))
        db.deviceDao().upsert(DeviceEntity(p.deviceId, p.uid, p.uid, p.keys.signPub, p.keys.boxPub, DeviceState.ACTIVE.name, now, null))
        return PairingState.Linked(coupleId)
    }

    companion object {
        const val CODE_LENGTH = 8
    }
}

internal fun ByteArray.b64(): String = Base64.encode(this)

internal fun String.unb64(): ByteArray = Base64.decode(this)
