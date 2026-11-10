@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.sadturtleman.flowershoes.core.data.sync

import com.sadturtleman.flowershoes.core.crypto.*
import kotlinx.coroutines.*
import libsodium.sodium_init
import okio.FileSystem
import kotlin.test.*
import kotlin.uuid.Uuid
import platform.posix.*

/** Real simulator Keychain and independent file descriptors; no in-memory Keychain replacement. */
class IosVaultStorageTest {
    private val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "vault-lock-${Uuid.random()}"
    private val items = IosKeychainStore(null, accountPrefix = "vault-test-${Uuid.random()}:")
    private fun vault() = IosKeyVault(items, root.toString())

    @BeforeTest fun initialize() = runBlocking {
        try { Sodium.awaitInit() } catch (_: IllegalStateException) { check(sodium_init() == 1) }
        check(sodium_init() >= 0)
    }

    @AfterTest fun clean() {
        items.accounts().forEach(items::delete)
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
