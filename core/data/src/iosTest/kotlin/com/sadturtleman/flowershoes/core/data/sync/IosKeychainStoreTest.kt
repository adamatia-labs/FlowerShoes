@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.sadturtleman.flowershoes.core.data.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNull
import com.sadturtleman.flowershoes.core.crypto.CoupleKey
import com.sadturtleman.flowershoes.core.crypto.DeviceKeys
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.pointed
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import libsodium.sodium_init
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Security.errSecDuplicateItem
import platform.Security.errSecItemNotFound
import platform.Security.errSecNotAvailable
import platform.Security.errSecSuccess

class IosKeychainStoreTest {
    @Test fun `삭제 오류는 성공으로 처리하지 않는다`() {
        val store = IosKeychainStore(null, deleteItem = { errSecNotAvailable })
        assertFailsWith<IllegalStateException> { store.delete("test-item") }
    }

    @Test fun `없는 항목 삭제는 멱등하게 성공한다`() {
        val store = IosKeychainStore(null, deleteItem = { errSecItemNotFound })
        store.delete("test-item")
    }
    private val syntheticKeys = """{"signPub":"0EqyMnQrtKs6E2i9RhXk5tAiSrcaAWuvhSCjMsl3hzc=","boxPub":"D6poTtKIZ7l/Smot7l34zpdOdrcBjj8iocTPJnhXDyA=","deviceSecret":"ERERERERERERERERERERERERERERERERERERERERERHQSrIydCu0qzoTaL1GFeTm0CJKtxoBa6+FIKMyyXeHNyIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIi"}"""
    @Test
    fun `새 프로세스에서 기존 키를 처음 읽을 때 초기화 완료를 기다린다`() = runBlocking {
        // 공개 테스트용 고정 입력이며 실제 사용자 키가 아니다.
        val raw = syntheticKeys
        assertTrue(vaultWithJson(raw).deviceKeys() != null)
    }

    @Test
    fun `새 프로세스에서 여러 보관함이 동시에 초기화해도 정상 키를 읽는다`() = runBlocking {
        val restored = coroutineScope {
            List(24) { async(Dispatchers.Default) { vaultWithJson(syntheticKeys).deviceKeys() } }.awaitAll()
        }
        assertTrue(restored.all { it != null })
    }

    @Test
    fun `다른 네이티브 코드가 먼저 초기화해도 정상 키를 읽는다`() = runBlocking {
        assertTrue(sodium_init() >= 0)
        assertTrue(vaultWithJson(syntheticKeys).deviceKeys() != null)
    }

    private fun vaultWithJson(raw: String, onWrite: () -> Unit = {}): IosKeyVault {
        val bytes = raw.encodeToByteArray()
        val store = IosKeychainStore(
            accessGroup = null,
            readItem = { _, out ->
                val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
                out!!.pointed.value = CFBridgingRetain(data)
                errSecSuccess
            },
            updateItem = { _, _ -> onWrite(); errSecSuccess },
            addItem = { onWrite(); errSecSuccess },
        )
        return IosKeyVault(null, store)
    }

    @Test
    fun `기기 키 일부만 남은 보관함은 새 기기 키로 덮어쓰지 않는다`() = runBlocking {
        var writes = 0
        val vault = vaultWithJson("{\"signPub\":\"AAAA\"}") { writes++ }
        assertFailsWith<IllegalStateException> { vault.deviceKeys() }
        assertFailsWith<IllegalStateException> { vault.putCoupleKey(CoupleKey(1, ByteArray(32))) }
        assertEquals(0, writes)
    }

    @Test
    fun `기기 키가 모두 있어도 길이가 잘못되면 저장하지 않는다`() = runBlocking {
        var writes = 0
        val vault = vaultWithJson("{\"signPub\":\"AAAA\",\"boxPub\":\"AAAA\",\"deviceSecret\":\"AAAA\"}") { writes++ }
        assertFailsWith<IllegalStateException> { vault.putCoupleKey(CoupleKey(1, ByteArray(32))) }
        assertEquals(0, writes)
    }

    @Test
    fun `정상적인 빈 보관함은 기존처럼 읽을 수 있다`() = runBlocking {
        val vault = vaultWithJson("{}")
        assertNull(vault.deviceKeys())
    }

    @Test
    fun `커플 키만 남고 기기 키가 사라진 보관함은 새 기기로 간주하지 않는다`() = runBlocking {
        val raw = "{\"coupleKeys\":{\"1\":\"${ByteArray(32).b64()}\"}}"
        var writes = 0
        val vault = vaultWithJson(raw) { writes++ }
        assertFailsWith<IllegalStateException> { vault.deviceKeys() }
        assertEquals(0, writes)
    }

    @Test
    fun `승인 대기 키만 남고 기기 키가 사라진 보관함은 새 기기로 간주하지 않는다`() = runBlocking {
        val raw = "{\"pendingApproval\":{\"requestId\":\"r\",\"epoch\":1,\"key\":\"${ByteArray(32).b64()}\"}}"
        val vault = vaultWithJson(raw)
        assertFailsWith<IllegalStateException> { vault.deviceKeys() }
        Unit
    }

    @Test
    fun `공개키와 비밀키가 맞지 않는 보관함은 덮어쓰지 않는다`() = runBlocking {
        val first = DeviceKeys.generate()
        val second = DeviceKeys.generate()
        val raw = "{\"signPub\":\"${second.signPub.b64()}\",\"boxPub\":\"${first.boxPub.b64()}\",\"deviceSecret\":\"${first.exportSecret().b64()}\"}"
        var writes = 0
        val vault = vaultWithJson(raw) { writes++ }
        assertFailsWith<IllegalStateException> { vault.putCoupleKey(CoupleKey(1, ByteArray(32))) }
        assertEquals(0, writes)
    }

    @Test
    fun `상자 공개키와 비밀키가 맞지 않는 보관함은 덮어쓰지 않는다`() = runBlocking {
        val first = DeviceKeys.generate()
        val second = DeviceKeys.generate()
        val raw = "{\"signPub\":\"${first.signPub.b64()}\",\"boxPub\":\"${second.boxPub.b64()}\",\"deviceSecret\":\"${first.exportSecret().b64()}\"}"
        var writes = 0
        val vault = vaultWithJson(raw) { writes++ }
        assertFailsWith<IllegalStateException> { vault.putCoupleKey(CoupleKey(1, ByteArray(32))) }
        assertEquals(0, writes)
    }

    @Test
    fun `잘못된 길이의 커플 키가 있으면 다른 키로 덮어쓰지 않는다`() = runBlocking {
        var writes = 0
        val vault = vaultWithJson("{\"coupleKeys\":{\"1\":\"AAAA\"}}") { writes++ }
        assertFailsWith<IllegalStateException> { vault.putCoupleKey(CoupleKey(2, ByteArray(32))) }
        assertEquals(0, writes)
    }

    @Test
    fun `음수 버전의 커플 키를 정상 키로 취급하지 않는다`() = runBlocking {
        val keys = DeviceKeys.generate()
        val raw = "{\"signPub\":\"${keys.signPub.b64()}\",\"boxPub\":\"${keys.boxPub.b64()}\",\"deviceSecret\":\"${keys.exportSecret().b64()}\",\"coupleKeys\":{\"-1\":\"${ByteArray(32).b64()}\"}}"
        val vault = vaultWithJson(raw)
        assertFailsWith<IllegalStateException> { vault.currentCoupleKey() }
        Unit
    }

    @Test
    fun `승인 정보의 식별자나 커서가 잘못됐으면 보관함을 갱신하지 않는다`() = runBlocking {
        val keys = DeviceKeys.generate()
        val device = "\"signPub\":\"${keys.signPub.b64()}\",\"boxPub\":\"${keys.boxPub.b64()}\",\"deviceSecret\":\"${keys.exportSecret().b64()}\""
        val raw = "{$device,\"pendingApproval\":{\"requestId\":\"\",\"epoch\":2,\"key\":\"${ByteArray(32).b64()}\",\"cutoff\":{\"device:app\":-1}}}"
        var writes = 0
        val vault = vaultWithJson(raw) { writes++ }
        assertFailsWith<IllegalStateException> { vault.putCoupleKey(CoupleKey(2, ByteArray(32))) }
        assertEquals(0, writes)
    }

    @Test
    fun `승인 대기 키의 인코딩이 손상되면 다른 키로 덮어쓰지 않는다`() = runBlocking {
        var writes = 0
        val vault = vaultWithJson("{\"pendingApproval\":{\"requestId\":\"r\",\"epoch\":1,\"key\":\"!\"}}") { writes++ }
        assertFailsWith<IllegalStateException> { vault.putCoupleKey(CoupleKey(2, ByteArray(32))) }
        assertEquals(0, writes)
    }

    @Test
    fun `정상적으로 저장된 기기 키는 읽을 수 있다`() = runBlocking {
        val keys = DeviceKeys.generate()
        val raw = "{\"signPub\":\"${keys.signPub.b64()}\",\"boxPub\":\"${keys.boxPub.b64()}\",\"deviceSecret\":\"${keys.exportSecret().b64()}\"}"
        val restored = vaultWithJson(raw).deviceKeys()
        assertTrue(restored != null)
        kotlin.test.assertContentEquals(keys.signPub, restored.signPub)
        kotlin.test.assertContentEquals(keys.boxPub, restored.boxPub)
    }

    @Test
    fun `정상적인 승인 대기 정보와 영점 커서는 읽을 수 있다`() = runBlocking {
        val keys = DeviceKeys.generate()
        val device = "\"signPub\":\"${keys.signPub.b64()}\",\"boxPub\":\"${keys.boxPub.b64()}\",\"deviceSecret\":\"${keys.exportSecret().b64()}\""
        val raw = "{$device,\"pendingApproval\":{\"requestId\":\"request\",\"epoch\":2,\"key\":\"${ByteArray(32).b64()}\",\"cutoff\":{\"device:app\":0}}}"
        val pending = vaultWithJson(raw).pendingApproval()
        assertEquals("request", pending?.requestId)
        assertEquals(0L, pending?.cutoff?.get("device:app"))
    }
    @Test
    fun `읽기 실패는 캐시에 빈 보관함으로 남지 않고 다음 호출에서 다시 조회한다`() = runBlocking {
        var reads = 0
        val store = IosKeychainStore(accessGroup = null, readItem = { _, _ ->
            reads++
            if (reads == 1) errSecNotAvailable else errSecItemNotFound
        })
        val vault = IosKeyVault(null, store)
        assertFailsWith<IllegalStateException> { vault.deviceKeys() }
        assertNull(vault.deviceKeys())
        assertEquals(2, reads)
    }

    @Test
    fun `손상된 키 보관 정보는 빈 보관함으로 간주하지 않고 덮어쓰지 않는다`() = runBlocking {
        var updates = 0
        var adds = 0
        val malformed = "{\"deviceSecret\":\"SYNTHETIC_CANARY_SECRET\",\"broken\":".encodeToByteArray()
        val store = IosKeychainStore(
            accessGroup = null,
            readItem = { _, out ->
                val data = malformed.usePinned { NSData.create(bytes = it.addressOf(0), length = malformed.size.toULong()) }
                out!!.pointed.value = CFBridgingRetain(data)
                errSecSuccess
            },
            updateItem = { _, _ -> updates++; errSecSuccess },
            addItem = { adds++; errSecSuccess },
        )
        val vault = IosKeyVault(null, store)
        val error = assertFailsWith<IllegalStateException> { vault.putCoupleKey(CoupleKey(1, ByteArray(32))) }
        assertFalse(error.stackTraceToString().contains("SYNTHETIC_CANARY_SECRET"))
        assertEquals(0, updates)
        assertEquals(0, adds)
    }
    @Test
    fun `키체인 읽기 실패는 항목 없음으로 처리하지 않는다`() {
        val store = IosKeychainStore(accessGroup = null, readItem = { _, _ -> errSecNotAvailable })
        val error = assertFailsWith<IllegalStateException> { store.read("test-item") }
        assertTrue(error.message.orEmpty().contains(errSecNotAvailable.toString()))
    }

    @Test
    fun `키체인에 항목이 없을 때만 null을 반환한다`() {
        val store = IosKeychainStore(accessGroup = null, readItem = { _, _ -> errSecItemNotFound })
        assertNull(store.read("test-item"))
    }
    @Test
    fun `없는 항목은 추가한다`() {
        var adds = 0
        val store = IosKeychainStore(
            accessGroup = null,
            updateItem = { _, _ -> errSecItemNotFound },
            addItem = { adds++; errSecSuccess },
        )
        store.write("test-item", byteArrayOf(1))
        assertEquals(1, adds)
    }

    @Test
    fun `항목 없음일 때만 추가하고 새 항목의 충돌은 다시 덮어쓰지 않는다`() {
        var updates = 0
        var adds = 0
        val store = IosKeychainStore(
            accessGroup = null,
            updateItem = { _, _ -> updates++; errSecItemNotFound },
            addItem = { adds++; errSecDuplicateItem },
        )
        assertFailsWith<IllegalStateException> { store.write("test-item", byteArrayOf(1, 2, 3)) }
        assertEquals(1, updates)
        assertEquals(1, adds)
    }

    @Test
    fun `키체인 사용 불가 오류는 항목 없음으로 해석하거나 새로 추가하지 않는다`() {
        var adds = 0
        val store = IosKeychainStore(
            accessGroup = null,
            updateItem = { _, _ -> errSecNotAvailable },
            addItem = { adds++; errSecSuccess },
        )
        val error = assertFailsWith<IllegalStateException> { store.write("test-item", byteArrayOf(1)) }
        assertTrue(error.message.orEmpty().contains(errSecNotAvailable.toString()))
        assertEquals(0, adds)
    }

    @Test
    fun `기존 항목 갱신 성공 뒤에는 추가하지 않는다`() {
        var adds = 0
        val store = IosKeychainStore(
            accessGroup = null,
            updateItem = { _, _ -> errSecSuccess },
            addItem = { adds++; errSecSuccess },
        )
        store.write("test-item", byteArrayOf(1))
        assertEquals(0, adds)
    }

    @Test
    fun `새 항목 추가 오류를 성공으로 처리하지 않는다`() {
        val store = IosKeychainStore(
            accessGroup = null,
            updateItem = { _, _ -> errSecItemNotFound },
            addItem = { errSecNotAvailable },
        )
        val error = assertFailsWith<IllegalStateException> { store.write("test-item", byteArrayOf(1)) }
        assertTrue(error.message.orEmpty().contains(errSecNotAvailable.toString()))
    }
}
