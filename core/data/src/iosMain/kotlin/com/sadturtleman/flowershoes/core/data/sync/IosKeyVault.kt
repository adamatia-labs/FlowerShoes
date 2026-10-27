@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.sadturtleman.flowershoes.core.data.sync

import com.sadturtleman.flowershoes.core.crypto.CoupleKey
import com.sadturtleman.flowershoes.core.crypto.DeviceKeys
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessGroup
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.posix.memcpy

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

    @Serializable
    private data class Stored(
        val signPub: String? = null,
        val boxPub: String? = null,
        val deviceSecret: String? = null,
        val coupleKeys: Map<Int, String> = emptyMap(),
        val backupKey: String? = null,
        val recoveryCode: String? = null,
    )

    private val lock = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private var cache: Stored? = null

    private fun load(): Stored = cache ?: (read(ACCOUNT)?.let { runCatching { json.decodeFromString(Stored.serializer(), it.decodeToString()) }.getOrNull() } ?: Stored()).also { cache = it }

    private fun save(s: Stored) {
        write(ACCOUNT, json.encodeToString(Stored.serializer(), s).encodeToByteArray())
        cache = s
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

    override suspend fun allCoupleKeys(): List<CoupleKey> = lock.withLock { load().coupleKeys.map { CoupleKey(it.key, it.value.unb64()) }.sortedBy { it.epoch } }

    override suspend fun backupSecrets(): Pair<ByteArray, String>? = lock.withLock {
        val s = load()
        if (s.backupKey == null || s.recoveryCode == null) null else s.backupKey.unb64() to s.recoveryCode
    }

    override suspend fun putBackupSecrets(backupKey: ByteArray, recoveryCode: String) = lock.withLock {
        save(load().copy(backupKey = backupKey.b64(), recoveryCode = recoveryCode))
    }

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

    override suspend fun clearCoupleKeys() = lock.withLock {
        val s = load()
        s.coupleKeys.keys.forEach { delete("coupleKey.$it") }
        save(s.copy(coupleKeys = emptyMap()))
    }

    override suspend fun clearAll() = lock.withLock {
        load().coupleKeys.keys.forEach { delete("coupleKey.$it") }
        listOf(ACCOUNT, "device.signSecret", "partner.signPub").forEach(::delete)
        cache = Stored()
    }

    /** NSE 가 상대 서명을 확인할 때 쓴다. 연결 · 기기 교체 때 앱이 부른다. */
    fun putPartnerSignPub(pub: ByteArray) = write("partner.signPub", pub)

    // ── Keychain ──

    private fun query(account: String, extra: Map<Any?, Any?> = emptyMap()) =
        CFDictionaryCreateMutable(null, 0, null, null).also { d ->
            val pairs = mapOf<Any?, Any?>(
                kSecClass to kSecClassGenericPassword,
                kSecAttrAccount to CFBridgingRetain(account as NSString),
            ) + (accessGroup?.let { mapOf(kSecAttrAccessGroup to CFBridgingRetain(it as NSString)) } ?: emptyMap()) + extra
            pairs.forEach { (k, v) -> CFDictionaryAddValue(d, k as platform.CoreFoundation.CFTypeRef?, v as platform.CoreFoundation.CFTypeRef?) }
        }

    private fun write(account: String, bytes: ByteArray) {
        SecItemDelete(query(account))
        val data = bytes.toNSData()
        val status = SecItemAdd(query(account, mapOf(kSecValueData to CFBridgingRetain(data), kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)), null)
        check(status == errSecSuccess) { "Keychain 저장 실패: $status" }
    }

    private fun read(account: String): ByteArray? = memScoped {
        val out = alloc<CFTypeRefVar>()
        if (SecItemCopyMatching(query(account, mapOf(kSecReturnData to kCFBooleanTrue)), out.ptr) != errSecSuccess) return null
        (CFBridgingRelease(out.value) as? NSData)?.toByteArray()
    }

    private fun delete(account: String) {
        SecItemDelete(query(account))
    }

    companion object {
        /** SharedStore.swift 의 keychainGroup 앞에 팀 접두사가 붙은 값. 빌드 설정에서 맞춘다. */
        const val ACCESS_GROUP = "com.sadturtleman.kkotsin.shared"
        private const val ACCOUNT = "kkotsin.vault"
    }
}

private fun ByteArray.toNSData(): NSData = usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }

private fun NSData.toByteArray(): ByteArray = ByteArray(length.toInt()).also { out ->
    if (out.isNotEmpty()) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
}
