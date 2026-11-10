package com.sadturtleman.flowershoes.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.sadturtleman.flowershoes.core.crypto.CoupleKey
import com.sadturtleman.flowershoes.core.data.sync.AccountTransport
import com.sadturtleman.flowershoes.core.data.sync.AfterUnlink
import com.sadturtleman.flowershoes.core.data.sync.InMemoryKeyVault
import com.sadturtleman.flowershoes.core.data.sync.UnlinkService
import com.sadturtleman.flowershoes.core.database.CoupleEntity
import com.sadturtleman.flowershoes.core.database.StreamCounterEntity
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
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class UnlinkRecoveryTest {
    @Test
    fun `모두 지우기 DB 실패는 기존 관계와 복호화 키를 보존한다`() = databaseFailure(AfterUnlink.WIPE)

    @Test
    fun `보관 DB 실패는 기존 관계와 복호화 키를 보존한다`() = databaseFailure(AfterUnlink.KEEP_AS_MEMORIES)

    @Test
    fun `탈퇴 DB 실패는 로컬 정리를 재시도할 때까지 복호화 키를 보존한다`() = databaseFailure(AfterUnlink.WIPE, deletingAccount = true)

    private fun databaseFailure(after: AfterUnlink, deletingAccount: Boolean = false) = runBlocking {
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "unlink-recovery-${Uuid.random()}"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val db = inMemoryDatabaseBuilder().buildKkotsin()
        try {
            val settings = DeviceSettings(PreferenceDataStoreFactory.createWithPath(scope = scope) { root / "settings.preferences_pb" })
            settings.ensureIdentity()
            settings.bindAccount("owner")
            settings.setKeyEpoch(1)
            val original = CoupleEntity("couple", "owner", "partner", 1, 100)
            db.coupleDao().upsert(original)
            db.syncDao().setNextSeq(StreamCounterEntity("device:app", 2))
            val key = CoupleKey(1, ByteArray(32) { it.toByte() })
            val vault = InMemoryKeyVault().also { it.putCoupleKey(key) }
            db.useWriterConnection { tx -> tx.immediateTransaction {
                usePrepared("CREATE TRIGGER fail_cleanup BEFORE DELETE ON stream_counter BEGIN SELECT RAISE(ABORT, 'injected cleanup failure'); END") { it.step() }
            } }
            val server = object : AccountTransport {
                override suspend fun unlinkCouple(coupleId: String) = Unit
                override suspend fun deleteAccount() = Unit
            }
            val service = UnlinkService(db, vault, settings, server)

            val failure = assertFails {
                if (deletingAccount) service.deleteAccount() else service.unlink(after)
            }

            assertTrue(failure.message.orEmpty().contains("injected cleanup failure"))
            assertEquals(original, db.coupleDao().get(), "DB 정리는 롤백되어야 한다")
            assertContentEquals(key.export(), assertNotNull(vault.coupleKey(1), "DB가 롤백되면 기존 기록의 키도 남아야 한다").export())
            assertEquals(1, settings.state.first().keyEpoch)
            assertEquals(2L, db.syncDao().nextSeq("device:app"))
        } finally {
            db.close()
            scope.coroutineContext[Job]!!.cancelAndJoin()
            if (FileSystem.SYSTEM.exists(root)) FileSystem.SYSTEM.deleteRecursively(root)
        }
    }
}
