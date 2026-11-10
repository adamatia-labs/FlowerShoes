package com.sadturtleman.flowershoes.core.data.sync

import androidx.room.Room
import com.sadturtleman.flowershoes.core.crypto.*
import com.sadturtleman.flowershoes.core.data.Identity
import com.sadturtleman.flowershoes.core.database.*
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.*

class CommittedKeyVaultTest {
    @get:Rule val directory = TemporaryFolder()
    private fun identity(uid: String = "a", device: String = "device-a") = object : Identity {
        override val userId = uid
        override val deviceId = device
        override val keyEpoch = 1
    }
    @BeforeTest fun initialize() = Sodium.init()

    @Test fun `커밋된 키는 DB 재개방 후 복구되고 플랫폼 활성 키를 바꾸지 않는다`() = runTest {
        val path = directory.root.resolve("keys.db").absolutePath
        fun open() = Room.databaseBuilder<AppDatabase>(name = path).buildKkotsin()
        var db = open()
        val platform = InMemoryKeyVault().also { it.putDeviceKeys(DeviceKeys.generate()) }
        val oldKey = CoupleKey.generate(8)
        platform.putCoupleKey(oldKey)
        val key = CoupleKey.generate(1)
        try {
            val vault = platform.forDatabase(db, identity())
            val sealed = vault.sealRelationship(CoupleEntity("ac", "a", "c", 1, 1), key)
            assertContentEquals(oldKey.export(), platform.currentCoupleKey()!!.export())
            assertFalse(sealed.sealedKeys!!.contains(key.export().b64()))
            db.coupleDao().upsert(sealed)
            db.close(); db = open()
            val reopened = platform.forDatabase(db, identity())
            assertContentEquals(key.export(), reopened.currentCoupleKey()!!.export())
            assertNull(reopened.coupleKey(8), "다른 관계의 높은 epoch로 폴백하면 안 된다")
            platform.clearCoupleKeys() // 이전 관계의 지연된 보관함 정리가 새 DB 키를 지우면 안 된다.
            assertContentEquals(key.export(), reopened.currentCoupleKey()!!.export())
        } finally { db.close() }
    }

    @Test fun `다른 관계 계정 기기 및 손상된 봉투는 이전 키로 대체하지 않는다`() = runTest {
        val db = inMemoryDatabaseBuilder().buildKkotsin()
        val platform = InMemoryKeyVault().also { it.putDeviceKeys(DeviceKeys.generate()); it.putCoupleKey(CoupleKey.generate(1)) }
        try {
            val vault = platform.forDatabase(db, identity())
            val original = vault.sealRelationship(CoupleEntity("ac", "a", "c", 1, 1), CoupleKey.generate(1))
            db.coupleDao().upsert(original)
            assertNull(platform.forDatabase(db, identity("other")).currentCoupleKey())
            assertFailsWith<IllegalStateException> { platform.forDatabase(db, identity(device = "other")).currentCoupleKey() }
            db.coupleDao().clear(); db.coupleDao().upsert(original.copy(id = "ad"))
            assertFailsWith<IllegalStateException> { vault.currentCoupleKey() }
            db.coupleDao().clear(); db.coupleDao().upsert(original.copy(sealedKeys = "broken"))
            assertFailsWith<IllegalStateException> { vault.currentCoupleKey() }
            db.coupleDao().upsert(original)
            platform.putDeviceKeys(DeviceKeys.generate())
            assertFailsWith<IllegalStateException> { vault.currentCoupleKey() }
        } finally { db.close() }
    }

    @Test fun `구버전 관계의 키는 기존 보관함에서 계속 읽는다`() = runTest {
        val db = inMemoryDatabaseBuilder().buildKkotsin()
        try {
            val key = CoupleKey.generate(3)
            val platform = InMemoryKeyVault().also { it.putCoupleKey(key) }
            db.coupleDao().upsert(CoupleEntity("legacy", "a", "b", 3, 1))
            val vault = platform.forDatabase(db, identity())
            assertContentEquals(key.export(), vault.currentCoupleKey()!!.export())
            assertContentEquals(key.export(), vault.coupleKey(3)!!.export())
        } finally { db.close() }
    }
}
