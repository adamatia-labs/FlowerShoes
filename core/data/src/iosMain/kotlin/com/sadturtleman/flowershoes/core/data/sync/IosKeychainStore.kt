@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.sadturtleman.flowershoes.core.data.sync

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFRelease
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
import platform.Security.SecItemUpdate
import platform.Security.errSecDuplicateItem
import platform.Security.errSecItemNotFound
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

/** 단일 Keychain 항목 조회와 실패 안전 갱신. */
internal class IosKeychainStore(
    private val accessGroup: String?,
    private val updateItem: (platform.CoreFoundation.CFDictionaryRef?, platform.CoreFoundation.CFDictionaryRef?) -> Int = { query, attributes -> SecItemUpdate(query, attributes) },
    private val addItem: (platform.CoreFoundation.CFDictionaryRef?) -> Int = { query -> SecItemAdd(query, null) },
) {
    // ── Keychain ──

    private inline fun <T> withQuery(account: String, extra: Map<Any?, Any?> = emptyMap(), block: (platform.CoreFoundation.CFMutableDictionaryRef?) -> T): T {
        val d = CFDictionaryCreateMutable(null, 0, null, null)
        val accountRef = CFBridgingRetain(account as NSString)
        val groupRef = accessGroup?.let { CFBridgingRetain(it as NSString) }
        try {
            val pairs = mapOf<Any?, Any?>(
                kSecClass to kSecClassGenericPassword,
                kSecAttrAccount to accountRef,
            ) + (groupRef?.let { mapOf(kSecAttrAccessGroup to it) } ?: emptyMap()) + extra
            pairs.forEach { (k, v) -> CFDictionaryAddValue(d, k as platform.CoreFoundation.CFTypeRef?, v as platform.CoreFoundation.CFTypeRef?) }
            return block(d)
        } finally {
            CFRelease(d)
            CFRelease(accountRef)
            if (groupRef != null) CFRelease(groupRef)
        }
    }

    fun write(account: String, bytes: ByteArray) {
        val data = bytes.toNSData()
        upsertKeychainItem(
            update = {
                val attributes = CFDictionaryCreateMutable(null, 0, null, null)
                val retainedData = CFBridgingRetain(data)
                val status = try {
                    CFDictionaryAddValue(attributes, kSecValueData as platform.CoreFoundation.CFTypeRef?, retainedData)
                    withQuery(account) { query -> updateItem(query, attributes) }
                } finally {
                    CFRelease(attributes)
                    CFRelease(retainedData)
                }
                when (status) {
                    errSecSuccess -> true
                    errSecItemNotFound -> false
                    else -> error("Keychain 갱신 실패: $status")
                }
            },
            add = {
                val retainedData = CFBridgingRetain(data)
                val status = try {
                    withQuery(account, mapOf(kSecValueData to retainedData, kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)) { query ->
                        addItem(query)
                    }
                } finally {
                    CFRelease(retainedData)
                }
                when (status) {
                    errSecSuccess -> true
                    errSecDuplicateItem -> false
                    else -> error("Keychain 저장 실패: $status")
                }
            },
        )
    }

    fun read(account: String): ByteArray? = memScoped {
        val out = alloc<CFTypeRefVar>()
        if (withQuery(account, mapOf(kSecReturnData to kCFBooleanTrue)) { query -> SecItemCopyMatching(query, out.ptr) } != errSecSuccess) return null
        (CFBridgingRelease(out.value) as? NSData)?.toByteArray()
    }

    fun delete(account: String) {
        withQuery(account) { query -> SecItemDelete(query) }
    }

}

private fun ByteArray.toNSData(): NSData = usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }

private fun NSData.toByteArray(): ByteArray = ByteArray(length.toInt()).also { out ->
    if (out.isNotEmpty()) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
}
