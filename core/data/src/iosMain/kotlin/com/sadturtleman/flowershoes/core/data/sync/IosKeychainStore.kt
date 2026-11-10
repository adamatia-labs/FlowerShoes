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
import platform.CoreFoundation.CFRetain
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
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecReturnData
import platform.Security.kSecReturnAttributes
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitAll
import platform.Security.kSecValueData
import platform.posix.memcpy

/** 단일 Keychain 항목 조회와 실패 안전 갱신. */
internal class IosKeychainStore(
    private val accessGroup: String?,
    private val updateItem: (platform.CoreFoundation.CFDictionaryRef?, platform.CoreFoundation.CFDictionaryRef?) -> Int = { query, attributes -> SecItemUpdate(query, attributes) },
    private val addItem: (platform.CoreFoundation.CFDictionaryRef?) -> Int = { query -> SecItemAdd(query, null) },
    private val readItem: (platform.CoreFoundation.CFDictionaryRef?, kotlinx.cinterop.CPointer<platform.CoreFoundation.CFTypeRefVar>?) -> Int = { query, out -> SecItemCopyMatching(query, out) },
    private val deleteItem: (platform.CoreFoundation.CFDictionaryRef?) -> Int = { query -> SecItemDelete(query) },
    private val accountPrefix: String = "",
    // Older versions omitted service; generic-password items use the empty service in that case.
    private val service: String = "",
) : VaultItems {
    // ── Keychain ──

    private inline fun <T> withQuery(account: String?, extra: Map<Any?, Any?> = emptyMap(), block: (platform.CoreFoundation.CFMutableDictionaryRef?) -> T): T {
        val d = CFDictionaryCreateMutable(null, 0, null, null)
        val accountRef = account?.let { CFBridgingRetain((accountPrefix + it) as NSString) }
        val groupRef = accessGroup?.let { CFBridgingRetain(it as NSString) }
        val serviceRef = CFBridgingRetain(service as NSString)
        try {
            val pairs = mapOf<Any?, Any?>(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to serviceRef,
            ) + (accountRef?.let { mapOf(kSecAttrAccount to it) } ?: emptyMap()) + (groupRef?.let { mapOf(kSecAttrAccessGroup to it) } ?: emptyMap()) + extra
            pairs.forEach { (k, v) -> CFDictionaryAddValue(d, k as platform.CoreFoundation.CFTypeRef?, v as platform.CoreFoundation.CFTypeRef?) }
            return block(d)
        } finally {
            CFRelease(d)
            if (accountRef != null) CFRelease(accountRef)
            if (groupRef != null) CFRelease(groupRef)
            CFRelease(serviceRef)
        }
    }

    override fun write(account: String, bytes: ByteArray) {
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

    override fun read(account: String): ByteArray? = memScoped {
        val out = alloc<CFTypeRefVar>()
        val status = withQuery(account, mapOf(kSecReturnData to kCFBooleanTrue)) { query -> readItem(query, out.ptr) }
        when (status) {
            errSecItemNotFound -> null
            errSecSuccess -> (CFBridgingRelease(out.value) as? NSData)?.toByteArray()
                ?: error("Keychain 항목 형식이 올바르지 않아요")
            else -> error("Keychain 조회 실패: $status")
        }
    }

    override fun delete(account: String) {
        when (val status = withQuery(account) { query -> deleteItem(query) }) {
            errSecSuccess, errSecItemNotFound -> Unit
            else -> error("Keychain 삭제 실패: $status")
        }
    }

    override fun accounts(): Set<String> = memScoped {
        val out = alloc<CFTypeRefVar>()
        when (val status = withQuery(null, mapOf(kSecReturnAttributes to kCFBooleanTrue, kSecMatchLimit to kSecMatchLimitAll)) { readItem(it, out.ptr) }) {
            errSecItemNotFound -> emptySet()
            errSecSuccess -> {
                val rows = CFBridgingRelease(out.value) as? List<*> ?: error("Keychain 목록 형식이 올바르지 않아요")
                val accountAttribute = CFBridgingRelease(CFRetain(kSecAttrAccount)) as String
                rows.mapNotNull { (it as? Map<*, *>)?.get(accountAttribute) as? String }
                    .filter { it.startsWith(accountPrefix) }.map { it.removePrefix(accountPrefix) }.toSet()
            }
            else -> error("Keychain 목록 조회 실패: $status")
        }
    }

}

private fun ByteArray.toNSData(): NSData = usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }

private fun NSData.toByteArray(): ByteArray = ByteArray(length.toInt()).also { out ->
    if (out.isNotEmpty()) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
}
