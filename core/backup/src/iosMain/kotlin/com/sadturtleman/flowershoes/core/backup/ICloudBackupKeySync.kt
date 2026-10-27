@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.sadturtleman.flowershoes.core.backup

import kotlinx.cinterop.*
import platform.CoreFoundation.*
import platform.Foundation.*
import platform.Security.*
import platform.posix.memcpy

/** Only the backup key syncs. Device signing keys stay in IosKeyVault/ThisDeviceOnly. */
class ICloudBackupKeySync(private val account: () -> String) : BackupKeySync {
    // Synchronizable Keychain uses end-to-end encryption. This is capability, not proof that
    // the user enabled iCloud Keychain: iOS offers no supported API for that setting.
    override suspend fun isEndToEndAvailable(): Boolean = true

    override suspend fun store(key: ByteArray) {
        require(key.size == 32)
        val data = key.usePinned { NSData.create(bytes = it.addressOf(0), length = key.size.toULong()) }
        query { q ->
            dictionary(mapOf(kSecValueData to data, kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlock)) { values ->
                val updated = SecItemUpdate(q, values)
                if (updated == errSecItemNotFound) {
                    query(mapOf(kSecValueData to data, kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlock)) { add ->
                        check(SecItemAdd(add, null) == errSecSuccess) { "iCloud 백업 열쇠를 저장하지 못했어요" }
                    }
                } else check(updated == errSecSuccess) { "iCloud 백업 열쇠를 갱신하지 못했어요: $updated" }
            }
        }
    }

    override suspend fun load(): ByteArray? = query(mapOf(kSecReturnData to kCFBooleanTrue)) { q ->
        memScoped {
            val out = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(q, out.ptr)
            if (status == errSecItemNotFound) null
            else {
                check(status == errSecSuccess) { "iCloud 백업 열쇠를 읽지 못했어요: $status" }
                val data = CFBridgingRelease(out.value) as NSData
                require(data.length == 32UL)
                ByteArray(32).also { bytes -> bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) } }
            }
        }
    }

    override suspend fun delete() = query { q ->
        val status = SecItemDelete(q)
        check(status == errSecSuccess || status == errSecItemNotFound) { "iCloud 백업 열쇠를 지우지 못했어요: $status" }
    }

    private fun <T> query(extra: Map<CFStringRef?, Any?> = emptyMap(), block: (CFDictionaryRef?) -> T): T = dictionary(
        mapOf(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to "com.sadturtleman.kkotsin.backup",
            kSecAttrAccount to account().also { require(it.isNotBlank()) },
            kSecAttrSynchronizable to kCFBooleanTrue,
        ) + extra,
        block,
    )

    private fun <T> dictionary(values: Map<CFStringRef?, Any?>, block: (CFDictionaryRef?) -> T): T {
        val dictionary = CFDictionaryCreateMutable(null, 0, null, null)
        val retained = mutableListOf<CFTypeRef?>()
        try {
            values.forEach { (key, value) ->
                val cf: CFTypeRef? = when (value) {
                    is String -> CFBridgingRetain(value).also { retained.add(it) }
                    is NSData -> CFBridgingRetain(value).also { retained.add(it) }
                    else -> value as CFTypeRef?
                }
                CFDictionaryAddValue(dictionary, key, cf)
            }
            return block(dictionary)
        } finally {
            CFRelease(dictionary)
            retained.forEach { CFRelease(it) }
        }
    }
}
