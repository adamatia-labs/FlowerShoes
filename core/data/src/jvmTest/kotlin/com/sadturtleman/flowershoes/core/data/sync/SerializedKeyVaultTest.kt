package com.sadturtleman.flowershoes.core.data.sync

import com.sadturtleman.flowershoes.core.crypto.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class SerializedKeyVaultTest {
    private class Store : VaultStorage, VaultItems {
        val values = mutableMapOf<String, ByteArray>()
        private val lock = Mutex()
        var fail: (String, String) -> Boolean = { _, _ -> false }
        override suspend fun <T> transaction(block: suspend VaultItems.() -> T): T = lock.withLock { block() }
        override fun read(account: String) = values[account]?.copyOf()
        override fun write(account: String, bytes: ByteArray) {
            check(!fail("write", account)) { "injected write failure" }
            values[account] = bytes.copyOf()
        }
        override fun delete(account: String) {
            check(!fail("delete", account)) { "injected delete failure" }
            values.remove(account)
        }
        override fun accounts() = values.keys.toSet()
        fun vault() = SerializedKeyVault(this) { Sodium.init() }
        fun canonical() = Json.parseToJsonElement(values.getValue(SerializedKeyVault.ACCOUNT).decodeToString()).jsonObject
    }

    @BeforeTest fun initialize() = Sodium.init()

    @Test fun `여러 인스턴스가 읽은 뒤 저장해도 모든 epoch를 보존한다`() = runTest {
        val store = Store(); val first = store.vault(); val second = store.vault()
        first.putDeviceKeys(DeviceKeys.generate())
        first.deviceKeys(); second.deviceKeys()
        first.putCoupleKey(CoupleKey.generate(1)); second.putCoupleKey(CoupleKey.generate(2))
        assertNotNull(store.vault().coupleKey(1)); assertNotNull(store.vault().coupleKey(2))
        coroutineScope { (3..18).map { epoch -> async(Dispatchers.Default) { store.vault().putCoupleKey(CoupleKey.generate(epoch)) } }.awaitAll() }
        (1..18).forEach { assertNotNull(store.vault().coupleKey(it)) }
    }

    @Test fun `원본 저장 실패는 기존 키와 미러를 그대로 둔다`() = runTest {
        val store = Store(); val vault = store.vault()
        vault.putDeviceKeys(DeviceKeys.generate()); val key = CoupleKey.generate(1); vault.putCoupleKey(key)
        val original = store.values.mapValues { it.value.toList() }
        store.fail = { op, account -> op == "write" && account == SerializedKeyVault.ACCOUNT }
        assertFails { vault.putCoupleKey(CoupleKey.generate(2)) }
        assertEquals(original, store.values.mapValues { it.value.toList() })
    }

    @Test fun `미러 저장 실패 후 새 인스턴스가 원본의 복구 의도를 완료한다`() = runTest {
        val store = Store(); val vault = store.vault()
        vault.putDeviceKeys(DeviceKeys.generate())
        val key = CoupleKey.generate(1)
        store.fail = { op, account -> op == "write" && account == "coupleKey.1" }
        assertFails { vault.putCoupleKey(key) }
        assertEquals(true, store.canonical().getValue("repairMirrors").jsonPrimitive.boolean)
        assertNotNull(store.canonical().getValue("coupleKeys").jsonObject["1"])
        store.fail = { _, _ -> false }
        assertContentEquals(key.export(), store.vault().coupleKey(1)!!.export())
        assertContentEquals(key.export(), store.values["coupleKey.1"])
        assertEquals(false, store.canonical().getValue("repairMirrors").jsonPrimitive.boolean)
    }

    @Test fun `삭제 중 실패해도 원본은 빈 상태이고 재개방이 남은 미러를 지운다`() = runTest {
        val store = Store(); val stale = store.vault()
        stale.putDeviceKeys(DeviceKeys.generate()); stale.putCoupleKey(CoupleKey.generate(1)); stale.deviceKeys()
        store.fail = { op, account -> op == "delete" && account == "coupleKey.1" }
        assertFails { store.vault().clearAll() }
        assertEquals(JsonNull, store.canonical()["deviceSecret"])
        assertTrue(store.values.containsKey("coupleKey.1"))
        store.fail = { _, _ -> false }
        assertNull(stale.deviceKeys()); assertNull(stale.currentCoupleKey())
        assertFalse(store.values.containsKey("coupleKey.1"))
        assertFails { stale.putCoupleKey(CoupleKey.generate(2)) }
        assertNull(store.vault().deviceKeys())
    }

    @Test fun `미러 쓰기가 막혀도 탈퇴는 이전 값을 다시 쓰지 않고 삭제한다`() = runTest {
        val store = Store(); val vault = store.vault()
        vault.putDeviceKeys(DeviceKeys.generate())
        store.fail = { op, account -> op == "write" && account == "coupleKey.1" }
        assertFails { vault.putCoupleKey(CoupleKey.generate(1)) }
        store.vault().clearAll()
        assertNull(store.vault().deviceKeys())
        assertEquals(setOf(SerializedKeyVault.ACCOUNT), store.values.keys)
    }

    @Test fun `새 기기 키 저장은 이전 관계와 확장 사본을 함께 비운다`() = runTest {
        val store = Store(); val vault = store.vault(); val old = DeviceKeys.generate()
        vault.putDeviceKeys(old); vault.putCoupleKey(CoupleKey.generate(1))
        vault.putPartnerSignPub(ByteArray(32) { 1 })
        vault.putSharedRelationship("old:device:1", listOf(CoupleKey.generate(1)), ByteArray(32) { 1 }, old)
        vault.putDeviceKeys(DeviceKeys.generate())
        assertNull(vault.currentCoupleKey())
        assertFalse(store.values.containsKey("relationship.keys")); assertFalse(store.values.containsKey("partner.signPub"))
        assertFails { vault.putSharedRelationship("old:device:1", listOf(CoupleKey.generate(1)), ByteArray(32) { 1 }, old) }
    }

    @Test fun `관계 키 미러의 부분 실패에도 확장 원본은 완전한 새 묶음이다`() = runTest {
        val store = Store(); val vault = store.vault(); val device = DeviceKeys.generate()
        vault.putDeviceKeys(device)
        vault.putSharedRelationship("old", listOf(CoupleKey.generate(1)), ByteArray(32) { 1 }, device)
        store.fail = { op, account -> op == "write" && account == "relationship.keys" }
        assertFails { vault.putSharedRelationship("new", listOf(CoupleKey.generate(1)), ByteArray(32) { 2 }, device) }
        assertEquals("new", store.canonical().getValue("sharedRelationship").jsonObject.getValue("scope").jsonPrimitive.content)
        assertEquals("old", Json.parseToJsonElement(store.values.getValue("relationship.keys").decodeToString()).jsonObject.getValue("scope").jsonPrimitive.content)
        store.fail = { _, _ -> false }
        store.vault().deviceKeys()
        assertEquals("new", Json.parseToJsonElement(store.values.getValue("relationship.keys").decodeToString()).jsonObject.getValue("scope").jsonPrimitive.content)
    }

    @Test fun `구버전 미러를 이전하고 출처를 잃은 미러도 모두 지우기에서 제거한다`() = runTest {
        val store = Store(); val device = DeviceKeys.generate()
        store.write(SerializedKeyVault.ACCOUNT, buildJsonObject {
            put("signPub", device.signPub.b64()); put("boxPub", device.boxPub.b64()); put("deviceSecret", device.exportSecret().b64())
        }.toString().encodeToByteArray())
        store.write("partner.signPub", ByteArray(32) { 4 })
        store.write("coupleKey.99", ByteArray(32) { 5 })
        store.write("unrelated-account", byteArrayOf(7))
        store.vault().putCoupleKey(CoupleKey.generate(1))
        assertEquals(ByteArray(32) { 4 }.b64(), store.canonical().getValue("partnerSignPub").jsonPrimitive.content)
        assertTrue(store.values.containsKey("coupleKey.99"), "출처 불명 구버전 사본은 일반 쓰기에서 지우거나 활성화하지 않는다")
        assertNull(store.vault().coupleKey(99))
        store.vault().clearAll()
        assertEquals(setOf(SerializedKeyVault.ACCOUNT, "unrelated-account"), store.values.keys)
    }

    @Test fun `복구 완료 표시 저장 실패도 다음 호출에서 멱등하게 끝난다`() = runTest {
        val store = Store(); val vault = store.vault()
        vault.putDeviceKeys(DeviceKeys.generate())
        var writes = 0
        store.fail = { op, account -> op == "write" && account == SerializedKeyVault.ACCOUNT && ++writes == 2 }
        val key = CoupleKey.generate(1)
        assertFails { vault.putCoupleKey(key) }
        assertContentEquals(key.export(), store.values["coupleKey.1"])
        assertEquals(true, store.canonical().getValue("repairMirrors").jsonPrimitive.boolean)
        store.fail = { _, _ -> false }
        assertContentEquals(key.export(), store.vault().coupleKey(1)!!.export())
        assertEquals(false, store.canonical().getValue("repairMirrors").jsonPrimitive.boolean)
    }
}
