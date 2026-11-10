@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.sadturtleman.flowershoes.core.data.sync

import com.sadturtleman.flowershoes.core.crypto.CoupleKey
import com.sadturtleman.flowershoes.core.crypto.DeviceKeys
import com.sadturtleman.flowershoes.core.crypto.Signatures
import com.sadturtleman.flowershoes.core.crypto.Sodium
import com.sadturtleman.flowershoes.core.crypto.sealTo
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import libsodium.sodium_init

/**
 * iOS 키 보관함. Keychain 항목 하나(JSON)에 비밀 값들을 둔다.
 * access group 을 공유해 앱 · NSE · 위젯이 함께 읽고(SharedStore.swift 와 같은 그룹),
 * ThisDeviceOnly 라 iCloud 키체인 · 백업으로 나가지 않는다.
 *
 * NSE · 위젯(Swift)은 이 JSON 이 아니라 개별 항목("coupleKey.{epoch}", "partner.signPub", "device.signSecret")을 읽으므로
 * [putCoupleKey] · [putDeviceKeys] 때 그 항목도 함께 쓴다.
 *
 * macOS 에서 빌드 · 실기기 확인이 필요하다(Windows 에서는 iOS 타깃이 컴파일되지 않는다).
 */
class IosKeyVault(
    private val accessGroup: String? = (platform.Foundation.NSBundle.mainBundle.objectForInfoDictionaryKey("KkotsinKeychainAccessGroup") as? String)
        ?.takeIf { it.endsWith(".$ACCESS_GROUP") },
) : KeyVault {

    internal constructor(accessGroup: String?, keychain: IosKeychainStore) : this(accessGroup) {
        this.keychain = keychain
    }

    @Serializable
    private data class Stored(
        val signPub: String? = null,
        val boxPub: String? = null,
        val deviceSecret: String? = null,
        val coupleKeys: Map<Int, String> = emptyMap(),
        /** 기기 교체 승인 중인 새 커플 키(#20). */
        val pendingApproval: StoredApproval? = null,
    )

    private val lock = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private var cache: Stored? = null

    private suspend fun load(): Stored = cache ?: (read(ACCOUNT)?.let { data ->
        try {
            val stored = json.decodeFromString(Stored.serializer(), data.decodeToString())
            validate(stored)
            stored
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // 직렬화 예외에는 원본 키 데이터가 포함될 수 있으므로 원인을 로그에 전파하지 않는다.
            throw IllegalStateException("키 보관 정보를 읽지 못했어요")
        }
    } ?: Stored()).also { cache = it }

    private suspend fun save(s: Stored) {
        try {
            validate(s)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            throw IllegalStateException("키 보관 정보를 저장하지 못했어요")
        }
        write(ACCOUNT, json.encodeToString(Stored.serializer(), s).encodeToByteArray())
        cache = s
    }

    private suspend fun validate(s: Stored) {
        val present = listOf(s.signPub, s.boxPub, s.deviceSecret).count { it != null }
        check(present == 0 || present == 3)
        if (present == 0) check(s.coupleKeys.isEmpty() && s.pendingApproval == null)
        if (present == 3) {
            // 래퍼가 먼저 플래그를 설정할 수 있어, 네이티브 초기화를 다시 호출해 완료를 확인한다.
            try {
                Sodium.awaitInit()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IllegalStateException) {
                // 다른 네이티브 사용자가 먼저 초기화한 경우에만 복구한다.
                check(sodium_init() == 1)
            }
            check(sodium_init() >= 0)
            val sign = checkNotNull(s.signPub).unb64()
            val box = checkNotNull(s.boxPub).unb64()
            val secret = checkNotNull(s.deviceSecret).unb64()
            check(sign.size == 32 && box.size == 32 && secret.size == 96)
            val keys = DeviceKeys.import(sign, box, secret)
            val challenge = "kkotsin-key-validation-v1".encodeToByteArray()
            check(Signatures.verify(challenge, keys.sign(challenge), sign))
            check(keys.openSealed(sealTo(box, challenge)).contentEquals(challenge))
        }
        s.coupleKeys.forEach { (epoch, key) -> check(epoch > 0 && key.unb64().size == 32) }
        s.pendingApproval?.let { pending ->
            check(pending.requestId.isNotBlank() && pending.epoch > 0 && pending.key.unb64().size == 32)
            check(pending.cutoff.all { (stream, seq) -> stream.isNotBlank() && seq >= 0 })
        }
    }

    override suspend fun deviceKeys(): DeviceKeys? = lock.withLock {
        val s = load()
        if (s.signPub == null || s.boxPub == null || s.deviceSecret == null) null
        else DeviceKeys.import(s.signPub.unb64(), s.boxPub.unb64(), s.deviceSecret.unb64())
    }

    override suspend fun putDeviceKeys(keys: DeviceKeys) = lock.withLock {
        save(load().copy(signPub = keys.signPub.b64(), boxPub = keys.boxPub.b64(), deviceSecret = keys.exportSecret().b64()))
        // 위젯이 Op 에 서명할 때 쓴다(앞 64바이트가 서명 비밀키).
        write("device.signSecret", keys.exportSecret().copyOf(64))
    }

    override suspend fun coupleKey(epoch: Int): CoupleKey? = lock.withLock { load().coupleKeys[epoch]?.let { CoupleKey(epoch, it.unb64()) } }

    override suspend fun currentCoupleKey(): CoupleKey? = lock.withLock { load().coupleKeys.maxByOrNull { it.key }?.let { CoupleKey(it.key, it.value.unb64()) } }

    override suspend fun putCoupleKey(key: CoupleKey) = lock.withLock {
        val s = load()
        save(s.copy(coupleKeys = s.coupleKeys + (key.epoch to key.export().b64())))
        write("coupleKey.${key.epoch}", key.export()) // NSE 가 epoch 로 찾는다
    }

    override suspend fun dropEpochsBefore(epoch: Int) = lock.withLock {
        val s = load()
        s.coupleKeys.keys.filter { it < epoch }.forEach { delete("coupleKey.$it") }
        save(s.copy(coupleKeys = s.coupleKeys.filterKeys { it >= epoch }))
    }

    override suspend fun pendingApproval(): PendingApproval? = lock.withLock { load().pendingApproval?.toModel() }

    override suspend fun putPendingApproval(approval: PendingApproval?) = lock.withLock {
        save(load().copy(pendingApproval = approval?.let(StoredApproval::of)))
    }

    override suspend fun clearCoupleKeys() = lock.withLock {
        val s = load()
        delete("relationship.keys")
        s.coupleKeys.keys.forEach { delete("coupleKey.$it") }
        save(s.copy(coupleKeys = emptyMap(), pendingApproval = null))
    }

    override suspend fun clearAll() = lock.withLock {
        load().coupleKeys.keys.forEach { delete("coupleKey.$it") }
        listOf(ACCOUNT, "device.signSecret", "partner.signPub", "relationship.keys").forEach(::delete)
        cache = Stored()
    }

    /** NSE 가 상대 서명을 확인할 때 쓴다. 연결 · 기기 교체 때 앱이 부른다. */
    fun putPartnerSignPub(pub: ByteArray) = write("partner.signPub", pub)

    /** 관계 식별자와 모든 확장용 키를 단일 Keychain 항목으로 교체한다. */
    fun putSharedRelationship(scope: String, keys: List<CoupleKey>, partnerPub: ByteArray, device: DeviceKeys) {
        val bundle = buildJsonObject {
            put("scope", scope)
            put("values", buildJsonObject {
                keys.forEach { put("coupleKey.${it.epoch}", it.export().b64()) }
                put("partner.signPub", partnerPub.b64())
                put("device.signSecret", device.exportSecret().copyOf(64).b64())
            })
        }
        write("relationship.keys", bundle.toString().encodeToByteArray())
    }

    private var keychain = IosKeychainStore(accessGroup)

    private fun write(account: String, bytes: ByteArray) = keychain.write(account, bytes)

    private fun read(account: String): ByteArray? = keychain.read(account)

    private fun delete(account: String) = keychain.delete(account)

    companion object {
        /** SharedStore.swift 의 keychainGroup 앞에 팀 접두사가 붙은 값. 빌드 설정에서 맞춘다. */
        const val ACCESS_GROUP = "com.sadturtleman.kkotsin.shared"
        private const val ACCOUNT = "kkotsin.vault"
    }
}
