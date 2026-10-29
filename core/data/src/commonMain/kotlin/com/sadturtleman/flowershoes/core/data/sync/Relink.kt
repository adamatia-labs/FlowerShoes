package com.sadturtleman.flowershoes.core.data.sync

import com.sadturtleman.flowershoes.core.crypto.CoupleKey
import com.sadturtleman.flowershoes.core.crypto.CrockfordCode
import com.sadturtleman.flowershoes.core.crypto.DeviceKeys
import com.sadturtleman.flowershoes.core.crypto.Pairing
import com.sadturtleman.flowershoes.core.data.Identity
import com.sadturtleman.flowershoes.core.database.AppDatabase
import com.sadturtleman.flowershoes.core.database.CoupleEntity
import com.sadturtleman.flowershoes.core.database.DeviceEntity
import com.sadturtleman.flowershoes.core.database.RevokedDeviceEntity
import com.sadturtleman.flowershoes.core.model.DeviceState
import kotlin.time.Clock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject

/** relinks/{code} 와 그 단계. 활성화 · 옛 기기 폐기는 confirm-inviter 의 서명을 확인한 함수(onRelinkConfirmed)가 한다. */
interface RelinkTransport {
    suspend fun create(code: String, inviterUid: String, coupleId: String)

    suspend fun coupleId(code: String): String?

    /** 넘겨주는 사람(지수) uid. relinks/{code}.uid */
    suspend fun giverUid(code: String): String?

    suspend fun putStep(code: String, name: String, fields: Map<String, String>)

    suspend fun getStep(code: String, name: String): Map<String, String>?

    /** 함수가 새 기기를 활성화했는지. */
    suspend fun activated(coupleId: String, deviceId: String): Boolean

    suspend fun wrappedKeyFor(coupleId: String, deviceId: String): ByteArray?
}

/**
 * 상대 폰이 넘겨주기(설계 7-3-B). 폰을 잃었을 때 상대 폰에서 공유 데이터를 다시 받는 경로.
 *
 * 새 폰에는 커플 키도 지수 키도 없어 먼저 요청을 보낼 곳이 없다 → **지수 폰이 코드를 만들고 민준이 입력**한다.
 * 순서는 첫 연결과 같다: 커밋 → 상대 공개 → 공개 → 6자리 SAS. 다른 점은 지수가 [맞아요, 넘겨줄게요] 를 누르면
 * 우편함을 끝까지 받고 옛 기기 cutoff 를 정한 뒤, 새 커플 키(epoch+1)를 새 기기 앞으로 봉인하고
 * 공유 데이터 전체를 스냅샷으로 보낸다는 것이다.
 */
class RelinkSession(
    private val db: AppDatabase,
    private val identity: Identity,
    private val vault: KeyVault,
    private val transport: RelinkTransport,
    private val sync: SyncClient?,
    private val clock: Clock = Clock.System,
    /** 새 폰 이름 · 로그인 제공자(KAKAO · APPLE · GOOGLE) — 넘겨주는 폰의 확인 카드에 보인다. */
    private val deviceInfo: suspend () -> Pair<String, String> = { "" to "" },
) {
    var state: PairingState = PairingState.Idle
        private set

    private var code = ""
    private var nonce = ByteArray(0)
    private var newDevice: Pair<String, Pairing.PublicKeys>? = null
    private var sas = ""
    private var giver = false

    /**
     * 받는 폰: 커플 id · 넘겨주는 사람 uid. 함수가 새 기기를 활성화하자마자 relinks/{code} 를 지우므로,
     * 문서가 있을 때(확인 코드 단계) 미리 읽어 둔다 — 활성화 뒤에 읽으면 없어서 영영 기다린다.
     */
    private var relinkCoupleId: String? = null
    private var relinkGiverUid: String? = null

    /** 받는 폰: 넘겨줄 기록 수(넘겨주는 폰이 inviter 단계에 적는다) — 넘겨받는 중 진행률의 분모. */
    var expected: RelinkCounts? = null
        private set

    /** 넘겨받은 내 역할(스냅샷의 내 사용자 행). 받는 폰은 온보딩을 건너뛰므로 이 값으로 역할을 정한다. */
    suspend fun myRole(): String? = db.userDao().get(identity.userId)?.role

    /** 이 폰에 지금 있는 기록 수(넘겨받는 중 진행률의 분자). */
    suspend fun localCounts(): RelinkCounts = RelinkCounts(
        letters = db.letterDao().allActive().size,
        drawings = db.letterDao().allActive().sumOf { l -> db.drawingDao().forLetter(l.id).size },
        events = db.eventDao().all().count { it.deletedAtMs == null },
    )

    private suspend fun myKeys(): DeviceKeys = vault.deviceKeys() ?: DeviceKeys.generate().also { vault.putDeviceKeys(it) }

    // ── 지수(기존 폰) ──

    suspend fun offer(): PairingState {
        val couple = checkNotNull(db.coupleDao().get()) { "연결된 커플이 없다" }
        myKeys()
        giver = true
        code = CrockfordCode.random(PairingSession.CODE_LENGTH)
        transport.create(code, identity.userId, couple.id)
        return PairingState.ShowCode(code).also { state = it }
    }

    // ── 민준(새 폰, 같은 계정으로 로그인) ──

    suspend fun accept(input: String): PairingState {
        val normalized = CrockfordCode.normalize(input)?.takeIf { it.length == PairingSession.CODE_LENGTH }
            ?: return PairingState.Failed("코드 8자리를 확인해 주세요").also { state = it }
        val keys = myKeys()
        code = normalized
        nonce = Pairing.newNonce()
        transport.putStep(code, Steps.JOIN, mapOf("uid" to identity.userId, "commit" to Pairing.commit(Pairing.PublicKeys(keys.signPub, keys.boxPub), nonce).b64()))
        return PairingState.WaitingPartner.also { state = it }
    }

    suspend fun poll(): PairingState {
        val keys = myKeys()
        val mine = Pairing.PublicKeys(keys.signPub, keys.boxPub)
        state = if (giver) pollGiver(mine) else pollReceiver(mine)
        return state
    }

    private suspend fun pollGiver(mine: Pairing.PublicKeys): PairingState {
        if (state is PairingState.CompareSas || state is PairingState.Linked) return state
        val join = transport.getStep(code, Steps.JOIN) ?: return state
        if (join["uid"] != db.coupleDao().get()?.partnerUserId) {
            return PairingState.Failed("연인의 계정이 아니에요. 같은 계정으로 로그인했는지 확인해 주세요")
        }
        if (transport.getStep(code, Steps.INVITER) == null) {
            val counts = localCounts()
            transport.putStep(
                code, Steps.INVITER,
                mapOf(
                    "deviceId" to identity.deviceId, "signPub" to mine.signPub.b64(), "boxPub" to mine.boxPub.b64(),
                    "letters" to counts.letters.toString(), "drawings" to counts.drawings.toString(), "events" to counts.events.toString(),
                ),
            )
        }
        val reveal = transport.getStep(code, Steps.REVEAL) ?: return state
        val theirs = Pairing.PublicKeys(reveal.getValue("signPub").unb64(), reveal.getValue("boxPub").unb64())
        val theirNonce = reveal.getValue("nonce").unb64()
        if (!Pairing.verifyCommitment(join.getValue("commit").unb64(), theirs, theirNonce)) {
            return PairingState.Failed("상대 키가 처음 약속과 달라요. 새 코드로 다시 시도해 주세요")
        }
        newDevice = reveal.getValue("deviceId") to theirs
        sas = Pairing.sas(code, inviter = mine, joiner = theirs, joinerNonce = theirNonce)
        return PairingState.CompareSas(sas, reveal["deviceName"]?.takeIf { it.isNotBlank() }, reveal["provider"]?.takeIf { it.isNotBlank() })
    }

    private suspend fun pollReceiver(mine: Pairing.PublicKeys): PairingState {
        if (state is PairingState.WaitingConfirm) return finishReceiver()
        if (state is PairingState.CompareSas) return state
        val inviter = transport.getStep(code, Steps.INVITER) ?: return state
        if (transport.getStep(code, Steps.REVEAL) == null) {
            val (deviceName, provider) = runCatching { deviceInfo() }.getOrDefault("" to "")
            transport.putStep(
                code, Steps.REVEAL,
                mapOf(
                    "deviceId" to identity.deviceId, "signPub" to mine.signPub.b64(), "boxPub" to mine.boxPub.b64(), "nonce" to nonce.b64(),
                    "deviceName" to deviceName, "provider" to provider,
                ),
            )
        }
        val theirs = Pairing.PublicKeys(inviter.getValue("signPub").unb64(), inviter.getValue("boxPub").unb64())
        newDevice = inviter.getValue("deviceId") to theirs // 받는 쪽에게는 "상대(지수) 기기"
        expected = expected ?: RelinkCounts(
            inviter["letters"]?.toIntOrNull() ?: 0, inviter["drawings"]?.toIntOrNull() ?: 0, inviter["events"]?.toIntOrNull() ?: 0,
        )
        relinkCoupleId = relinkCoupleId ?: transport.coupleId(code)
        relinkGiverUid = relinkGiverUid ?: transport.giverUid(code)
        sas = Pairing.sas(code, inviter = theirs, joiner = mine, joinerNonce = nonce)
        return PairingState.CompareSas(sas)
    }

    /**
     * 지수: [맞아요, 넘겨줄게요]. 화면이 기기 생체 · 암호 인증을 먼저 받는다.
     * 민준: [맞아요].
     */
    suspend fun confirm(): PairingState = if (giver) confirmGiver() else confirmReceiver()

    private suspend fun confirmGiver(): PairingState {
        val (newDeviceId, newKeys) = newDevice ?: return state
        val couple = checkNotNull(db.coupleDao().get())
        val keys = myKeys()
        // 옛 기기가 보낸 정상 Op 을 먼저 받아 반영한 뒤 그 커서를 cutoff 로 적는다.
        sync?.syncOnce()
        val old = db.deviceDao().activeFor(couple.partnerUserId)
        val cutoff = old?.let { o -> db.syncDao().cursors().filter { it.stream.startsWith("${o.id}:") }.associate { it.stream to it.contiguousSeq } }.orEmpty()

        val current = checkNotNull(vault.currentCoupleKey())
        val newKey = CoupleKey.generate(current.epoch + 1)
        vault.putCoupleKey(newKey)
        val wrapped = newKey.wrapFor(newKeys.boxPub)
        val sig = keys.sign(RotationMessages.approval(couple.id, code, newDeviceId, cutoff))
        transport.putStep(
            code, Steps.CONFIRM_INVITER,
            mapOf("wrappedKey" to wrapped.b64(), "sas" to sas, "cutoff" to cutoffJson(cutoff), "sig" to sig.b64(), "newDeviceId" to newDeviceId, "epoch" to newKey.epoch.toString()),
        )

        val now = clock.now().toEpochMilliseconds()
        old?.let {
            db.syncDao().addRevoked(RevokedDeviceEntity(it.id, now, cutoffJson(cutoff)))
            db.deviceDao().upsert(it.copy(state = DeviceState.REVOKED.name, revokedAtMs = now))
        }
        db.deviceDao().upsert(DeviceEntity(newDeviceId, couple.partnerUserId, couple.partnerUserId, newKeys.signPub, newKeys.boxPub, DeviceState.ACTIVE.name, now, null))
        db.coupleDao().upsert(couple.copy(keyEpoch = newKey.epoch))
        // 공유 데이터 전체를 새 기기로. 다음 push 가 새 키로 새 기기 우편함에 올린다.
        sync?.writeFullSnapshot()
        sync?.syncOnce()
        return PairingState.Linked(couple.id).also { state = it }
    }

    private suspend fun confirmReceiver(): PairingState {
        transport.putStep(code, Steps.CONFIRM_JOINER, mapOf("sas" to sas))
        state = PairingState.WaitingConfirm
        return finishReceiver()
    }

    /** 함수가 새 기기를 활성화하면 커플 키를 풀고 커플 · 기기 행을 만든다. 데이터는 이어지는 동기화로 들어온다. */
    private suspend fun finishReceiver(): PairingState {
        val coupleId = relinkCoupleId ?: transport.coupleId(code)?.also { relinkCoupleId = it } ?: return PairingState.WaitingConfirm
        if (!transport.activated(coupleId, identity.deviceId)) return PairingState.WaitingConfirm
        val wrapped = transport.wrappedKeyFor(coupleId, identity.deviceId) ?: return PairingState.WaitingConfirm
        val key = CoupleKey.unwrap(myKeys(), wrapped)
        vault.putCoupleKey(key)
        val (giverDeviceId, giverKeys) = checkNotNull(newDevice)
        val giverUid = relinkGiverUid ?: transport.giverUid(code)?.also { relinkGiverUid = it } ?: return PairingState.WaitingConfirm
        val keys = myKeys()
        val now = clock.now().toEpochMilliseconds()
        db.coupleDao().clear()
        db.coupleDao().upsert(CoupleEntity(coupleId, identity.userId, giverUid, key.epoch, now, null, 0))
        db.deviceDao().upsert(DeviceEntity(identity.deviceId, identity.userId, identity.userId, keys.signPub, keys.boxPub, DeviceState.ACTIVE.name, now, null))
        db.deviceDao().upsert(DeviceEntity(giverDeviceId, giverUid, giverUid, giverKeys.signPub, giverKeys.boxPub, DeviceState.ACTIVE.name, now, null))
        sync?.syncOnce()
        return PairingState.Linked(coupleId)
    }

    private fun cutoffJson(cutoff: Map<String, Long>): String =
        Json.encodeToString(JsonObject.serializer(), buildJsonObject { cutoff.forEach { (k, v) -> put(k, JsonPrimitive(v)) } })
}

/** 넘겨받는 중 화면의 세 줄(Figma 재연결 / 민준 · 넘겨받는 중): 편지 · 그림 · 일정. */
data class RelinkCounts(val letters: Int, val drawings: Int, val events: Int) {
    val total: Int get() = letters + drawings + events
}
