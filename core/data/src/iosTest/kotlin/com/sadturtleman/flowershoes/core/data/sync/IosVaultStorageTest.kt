@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.sadturtleman.flowershoes.core.data.sync

import com.sadturtleman.flowershoes.core.crypto.*
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.*
import libsodium.sodium_init
import okio.FileSystem
import kotlin.test.*
import kotlin.uuid.Uuid
import platform.posix.*
import platform.CoreFoundation.*
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.create
import platform.Security.*

/** Real simulator Keychain and independent file descriptors; no in-memory Keychain replacement. */
class IosVaultStorageTest {
    private val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "vault-lock-${Uuid.random()}"
    private val prefix = "vault-test-${Uuid.random()}:"
    private val items = IosKeychainStore(null, accountPrefix = prefix)
    private val unrelated = IosKeychainStore(null, accountPrefix = prefix, service = "unrelated")
    private fun vault() = IosKeyVault(items, root.toString())

    @BeforeTest fun initialize() = runBlocking {
        try { Sodium.awaitInit() } catch (_: IllegalStateException) { check(sodium_init() == 1) }
        check(sodium_init() >= 0)
    }

    @AfterTest fun clean() {
        items.accounts().forEach(items::delete)
        unrelated.accounts().forEach(unrelated::delete)
        FileSystem.SYSTEM.deleteRecursively(root, mustExist = false)
    }

    @Test fun `시스템 키체인을 공유하는 인스턴스의 모든 성공한 저장을 보존한다`() = runBlocking {
        val first = vault(); val second = vault()
        first.putDeviceKeys(DeviceKeys.generate())
        first.deviceKeys(); second.deviceKeys()
        first.putCoupleKey(CoupleKey.generate(1)); second.putCoupleKey(CoupleKey.generate(2))
        coroutineScope { (3..8).map { epoch -> async(Dispatchers.Default) { vault().putCoupleKey(CoupleKey.generate(epoch)) } }.awaitAll() }
        (1..8).forEach { assertNotNull(vault().coupleKey(it)) }
        assertEquals(8, items.accounts().count { it.startsWith("coupleKey.") })
    }

    @Test fun `시스템 키체인의 삭제 결과를 오래된 인스턴스도 그대로 읽는다`() = runBlocking {
        val stale = vault()
        stale.putDeviceKeys(DeviceKeys.generate()); stale.putCoupleKey(CoupleKey.generate(1))
        stale.deviceKeys()
        vault().clearAll()
        assertNull(stale.deviceKeys()); assertNull(stale.currentCoupleKey())
        assertFails { stale.putCoupleKey(CoupleKey.generate(2)) }
        assertEquals(setOf(SerializedKeyVault.ACCOUNT), items.accounts())
    }

    @Test fun `같은 계정 이름을 쓰는 다른 서비스의 항목은 읽거나 수정하거나 지우지 않는다`() = runBlocking {
        val other = byteArrayOf(9, 9)
        unrelated.write(SerializedKeyVault.ACCOUNT, other)
        unrelated.write("coupleKey.99", other)
        assertNull(items.read(SerializedKeyVault.ACCOUNT))
        assertTrue(items.accounts().isEmpty())
        val first = vault()
        first.putDeviceKeys(DeviceKeys.generate())
        items.write("coupleKey.99", ByteArray(32))
        first.clearCoupleKeys()
        assertNull(items.read("coupleKey.99"))
        assertContentEquals(other, unrelated.read("coupleKey.99"))
        assertContentEquals(other, unrelated.read(SerializedKeyVault.ACCOUNT))
        first.clearAll()
        assertContentEquals(other, unrelated.read("coupleKey.99"))
        assertContentEquals(other, unrelated.read(SerializedKeyVault.ACCOUNT))
    }

    @Test fun `service 없이 저장한 구버전 항목도 빈 service 범위로 읽고 갱신하고 지운다`() {
        val account = "legacy-value"
        val old = byteArrayOf(1, 2)
        val query = CFDictionaryCreateMutable(null, 0, null, null)
        val accountRef = CFBridgingRetain((prefix + account) as NSString)
        val dataRef = CFBridgingRetain(old.usePinned { NSData.create(bytes = it.addressOf(0), length = old.size.toULong()) })
        try {
            CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
            CFDictionaryAddValue(query, kSecAttrAccount, accountRef)
            CFDictionaryAddValue(query, kSecValueData, dataRef)
            assertEquals(errSecSuccess, SecItemAdd(query, null))
        } finally { CFRelease(query); CFRelease(accountRef); CFRelease(dataRef) }
        assertContentEquals(old, items.read(account))
        assertTrue(account in items.accounts())
        val updated = byteArrayOf(3, 4)
        items.write(account, updated)
        assertContentEquals(updated, items.read(account))
        items.delete(account)
        assertNull(items.read(account))
    }

    @Test fun `독립 파일 디스크립터가 잠금을 공유하고 기다림을 취소할 수 있다`() = runBlocking {
        FileSystem.SYSTEM.createDirectories(root)
        val fd = open((root / "vault.lock").toString(), O_RDWR or O_CREAT, 0x180)
        assertTrue(fd >= 0)
        assertEquals(0, flock(fd, LOCK_EX or LOCK_NB))
        try {
            val started = CompletableDeferred<Unit>()
            val waiting = async(Dispatchers.Default) { started.complete(Unit); vault().deviceKeys() }
            started.await()
            assertNull(withTimeoutOrNull(150) { waiting.await(); true })
            waiting.cancelAndJoin()
        } finally { flock(fd, LOCK_UN); close(fd) }
        withTimeout(5_000) { assertNull(vault().deviceKeys()) }
    }
}
