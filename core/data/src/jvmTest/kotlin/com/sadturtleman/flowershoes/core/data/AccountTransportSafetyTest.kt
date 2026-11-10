package com.sadturtleman.flowershoes.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.sadturtleman.flowershoes.core.data.sync.AccountTransport
import com.sadturtleman.flowershoes.core.data.sync.AfterUnlink
import com.sadturtleman.flowershoes.core.data.sync.InMemoryKeyVault
import com.sadturtleman.flowershoes.core.data.sync.KeyVault
import com.sadturtleman.flowershoes.core.data.sync.UnlinkService
import com.sadturtleman.flowershoes.core.database.CoupleEntity
import com.sadturtleman.flowershoes.core.database.UserEntity
import com.sadturtleman.flowershoes.core.database.buildKkotsin
import com.sadturtleman.flowershoes.core.database.inMemoryDatabaseBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.uuid.Uuid

class AccountTransportSafetyTest {
    private val fs = FileSystem.SYSTEM
    private val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "kk-account-safety-${Uuid.random()}"

    @Test
    fun `서버 삭제 후 로컬 정리가 실패하면 재실행해도 서버 호출 없이 Apple 안내를 복구한다`() = runBlocking {
        fs.createDirectories(root)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val db = inMemoryDatabaseBuilder().buildKkotsin()
        try {
            val store = PreferenceDataStoreFactory.createWithPath(scope = scope) { root / "settings.preferences_pb" }
            val settings = DeviceSettings(store)
            settings.bindAccount("apple-owner", "APPLE")
            val server = object : AccountTransport {
                var calls = 0
                override suspend fun unlinkCouple(coupleId: String) = Unit
                override suspend fun deleteAccount() = Unit
                override suspend fun deleteAccountWithOutcome(): com.sadturtleman.flowershoes.core.data.sync.AppleRevocation {
                    calls++
                    return com.sadturtleman.flowershoes.core.data.sync.AppleRevocation.MANUAL
                }
            }
            val vault = InMemoryKeyVault()
            val first = UnlinkService(db, vault, settings, server) { error("disk unavailable") }
            assertFailsWith<IllegalStateException> { first.deleteAccount() }
            assertEquals(true, settings.pendingAccountCleanup.first())
            assertFalse(settings.farewellPending.first())

            val reopened = DeviceSettings(store)
            UnlinkService(db, vault, reopened, null).deleteAccount()
            assertEquals(1, server.calls)
            assertEquals(null, reopened.pendingAccountCleanup.first())
            assertEquals(true, reopened.farewellAppleInstructions.first())
            assertEquals(true, reopened.farewellPending.first())
        } finally {
            db.close()
            scope.coroutineContext[Job]!!.cancelAndJoin()
            if (fs.exists(root)) fs.deleteRecursively(root)
        }
    }

    @Test
    fun `탈퇴 서버가 없으면 서버 탈퇴를 가장하지 않고 기기 기록을 보존한다`() = runBlocking {
        fs.createDirectories(root)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val db = inMemoryDatabaseBuilder().buildKkotsin()
        try {
            val settings = DeviceSettings(PreferenceDataStoreFactory.createWithPath(scope = scope) { root / "settings.preferences_pb" })
            val device = settings.ensureIdentity().deviceId
            settings.bindAccount("owner")
            db.userDao().upsert(UserEntity("owner", "GOMSIN", "나", 1))
            val drafts = RoomLetterDraftStore(db, settings)
            drafts.save(checkNotNull(drafts.open(checkNotNull(settings.draftSessions.first()), "draft")), "{}", atMs = 100)
            val vault = object : KeyVault by InMemoryKeyVault() {
                var cleared = false
                override suspend fun clearAll() { cleared = true }
                override suspend fun clearCoupleKeys() { cleared = true }
            }
            var wiped = false
            val service = UnlinkService(db, vault, settings, null) { wiped = true }

            assertFailsWith<IllegalStateException> { service.deleteAccount() }
            assertNotNull(db.userDao().get("owner"))
            assertEquals(listOf("draft"), drafts.observe().first { it.loaded }.drafts.map { it.id })
            assertFalse(vault.cleared)
            assertEquals("owner", settings.state.first().userId)
            assertEquals(device, settings.state.first().deviceId)
            assertFalse(settings.farewellPending.first())
            assertFalse(wiped)
        } finally {
            db.close()
            scope.coroutineContext[Job]!!.cancelAndJoin()
            if (fs.exists(root)) fs.deleteRecursively(root)
        }
    }

    @Test
    fun `연결 해제 서버가 없으면 선택한 삭제를 실행하지 않고 관계를 보존한다`() = runBlocking {
        fs.createDirectories(root)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val db = inMemoryDatabaseBuilder().buildKkotsin()
        try {
            val settings = DeviceSettings(PreferenceDataStoreFactory.createWithPath(scope = scope) { root / "settings.preferences_pb" })
            settings.ensureIdentity()
            settings.bindAccount("owner")
            db.coupleDao().upsert(CoupleEntity("couple", "owner", "partner", 1, 100))
            val drafts = RoomLetterDraftStore(db, settings)
            drafts.save(checkNotNull(drafts.open(checkNotNull(settings.draftSessions.first()), "draft")), "{}", atMs = 100)
            val vault = object : KeyVault by InMemoryKeyVault() {
                var cleared = false
                override suspend fun clearAll() { cleared = true }
                override suspend fun clearCoupleKeys() { cleared = true }
            }
            var wiped = false
            val service = UnlinkService(db, vault, settings, null) { wiped = true }

            assertFailsWith<IllegalStateException> { service.unlink(AfterUnlink.WIPE) }
            assertEquals("couple", db.coupleDao().get()?.id)
            assertEquals("partner", db.coupleDao().get()?.partnerUserId)
            assertEquals(listOf("draft"), drafts.observe().first { it.loaded }.drafts.map { it.id })
            assertFalse(vault.cleared)
            assertFalse(wiped)
        } finally {
            db.close()
            scope.coroutineContext[Job]!!.cancelAndJoin()
            if (fs.exists(root)) fs.deleteRecursively(root)
        }
    }
    @Test
    fun `서버 호출이 실패해도 관계와 임시 편지와 키를 지우지 않는다`() = runBlocking {
        fs.createDirectories(root)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val db = inMemoryDatabaseBuilder().buildKkotsin()
        try {
            val settings = DeviceSettings(PreferenceDataStoreFactory.createWithPath(scope = scope) { root / "settings.preferences_pb" })
            settings.ensureIdentity()
            settings.bindAccount("owner")
            db.userDao().upsert(UserEntity("owner", "GOMSIN", "나", 1))
            db.coupleDao().upsert(CoupleEntity("couple", "owner", "partner", 1, 100))
            val drafts = RoomLetterDraftStore(db, settings)
            drafts.save(checkNotNull(drafts.open(checkNotNull(settings.draftSessions.first()), "draft")), "{}", atMs = 100)
            val vault = object : KeyVault by InMemoryKeyVault() {
                var cleared = false
                override suspend fun clearAll() { cleared = true }
                override suspend fun clearCoupleKeys() { cleared = true }
            }
            val server = object : AccountTransport {
                override suspend fun unlinkCouple(coupleId: String) { error("offline") }
                override suspend fun deleteAccount() { error("offline") }
            }
            var wiped = false
            val service = UnlinkService(db, vault, settings, server) { wiped = true }
            assertFailsWith<IllegalStateException> { service.unlink(AfterUnlink.WIPE) }
            assertFailsWith<IllegalStateException> { service.deleteAccount() }
            assertEquals("partner", db.coupleDao().get()?.partnerUserId)
            assertNotNull(db.userDao().get("owner"))
            assertEquals(listOf("draft"), drafts.observe().first { it.loaded }.drafts.map { it.id })
            assertFalse(vault.cleared)
            assertFalse(wiped)
            assertEquals("owner", settings.state.first().userId)
        } finally {
            db.close()
            scope.coroutineContext[Job]!!.cancelAndJoin()
            if (fs.exists(root)) fs.deleteRecursively(root)
        }
    }

}

