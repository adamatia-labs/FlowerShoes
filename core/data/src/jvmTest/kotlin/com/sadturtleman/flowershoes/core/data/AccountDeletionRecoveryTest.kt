package com.sadturtleman.flowershoes.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import com.sadturtleman.flowershoes.core.data.sync.*
import com.sadturtleman.flowershoes.core.database.AppDatabase
import com.sadturtleman.flowershoes.core.database.UserEntity
import com.sadturtleman.flowershoes.core.database.buildKkotsin
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okio.FileSystem
import kotlin.test.*
import kotlin.uuid.Uuid

class AccountDeletionRecoveryTest {
    @Test
    fun `응답과 인증을 잃어도 저장한 같은 작업으로 재시작 후 탈퇴를 마친다`(): Unit = runBlocking {
        val fs = FileSystem.SYSTEM
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "kk-deletion-receipt-${Uuid.random()}"
        fs.createDirectories(root)
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var db = Room.databaseBuilder<AppDatabase>((root / "db").toString()).buildKkotsin()
        fun settings() = DeviceSettings(PreferenceDataStoreFactory.createWithPath(scope = scope) { root / "device.preferences_pb" })
        var currentUid: String? = "owner"
        var receipt: AccountDeletionAttempt? = null
        var serverCalls = 0
        val vault = InMemoryKeyVault()
        try {
            val firstSettings = settings()
            firstSettings.ensureIdentity()
            firstSettings.bindAccount("owner", "APPLE")
            db.userDao().upsert(UserEntity("owner", "GOMSIN", "나", 1))
            val first = AccountDeletionRecovery({ currentUid }, delete = {
                serverCalls++
                receipt = it
                currentUid = null
                error("response lost")
            }, completed = {
                if (receipt != null) error("offline")
                null
            })
            fun transport(recovery: AccountDeletionRecovery) = object : AccountTransport {
                override suspend fun unlinkCouple(coupleId: String) = Unit
                override suspend fun deleteAccount(): Unit = error("durable attempt required")
                override suspend fun deleteAccountWithOutcome(attempt: AccountDeletionAttempt) = recovery.execute(attempt)
            }
            assertFailsWith<IllegalStateException> { UnlinkService(db, vault, firstSettings, transport(first)).deleteAccount() }
            assertNotNull(db.userDao().get("owner"))
            assertFalse(firstSettings.farewellPending.first())
            assertEquals(receipt?.token, db.cleanupDao().get()?.token)
            db.close()
            scope.coroutineContext[Job]!!.cancelAndJoin()

            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            db = Room.databaseBuilder<AppDatabase>((root / "db").toString()).buildKkotsin()
            val reopened = settings()
            val resumed = AccountDeletionRecovery({ currentUid }, delete = { error("must not delete again") }, completed = {
                assertEquals(receipt, it)
                AppleRevocation.MANUAL
            })
            assertTrue(UnlinkService(db, vault, reopened, transport(resumed)).resumePending())
            assertEquals(1, serverCalls)
            assertNull(db.cleanupDao().get())
            assertNull(db.userDao().get("owner"))
            assertTrue(reopened.farewellPending.first())
            assertTrue(reopened.farewellAppleInstructions.first())
        } finally {
            db.close()
            scope.coroutineContext[Job]!!.cancelAndJoin()
            fs.deleteRecursively(root)
        }
    }

    @Test
    fun `현재 다른 계정으로 삭제 요청을 보내지 않는다`(): Unit = runBlocking {
        val recovery = AccountDeletionRecovery({ "other" }, delete = { error("must not call") }, completed = { null })
        assertFailsWith<IllegalStateException> { recovery.execute(AccountDeletionAttempt("owner", Uuid.random().toString())) }
    }

    @Test
    fun `취소는 삼키거나 추가 조회로 바꾸지 않는다`(): Unit = runBlocking {
        var reads = 0
        val recovery = AccountDeletionRecovery({ "owner" }, delete = { throw CancellationException("cancel") }, completed = { reads++; null })
        assertFailsWith<CancellationException> { recovery.execute(AccountDeletionAttempt("owner", Uuid.random().toString())) }
        assertEquals(1, reads)
    }

    @Test
    fun `응답 직후 확인 가능한 완료 결과는 재삭제 없이 돌려준다`(): Unit = runBlocking {
        var finished = false
        val recovery = AccountDeletionRecovery({ "owner" }, delete = { finished = true; error("response lost") }, completed = {
            if (finished) AppleRevocation.REVOKED else null
        })
        assertEquals(AppleRevocation.REVOKED, recovery.execute(AccountDeletionAttempt("owner", Uuid.random().toString())))
    }
}
