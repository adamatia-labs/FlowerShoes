package com.sadturtleman.flowershoes.feature.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.sadturtleman.flowershoes.core.data.DeviceSettings
import com.sadturtleman.flowershoes.core.data.EventRepository
import com.sadturtleman.flowershoes.core.data.Identity
import com.sadturtleman.flowershoes.core.data.ProfileRepository
import com.sadturtleman.flowershoes.core.data.SyncWriter
import com.sadturtleman.flowershoes.core.data.sync.AccountTransport
import com.sadturtleman.flowershoes.core.data.sync.AfterUnlink
import com.sadturtleman.flowershoes.core.data.sync.InMemoryKeyVault
import com.sadturtleman.flowershoes.core.data.sync.UnlinkService
import com.sadturtleman.flowershoes.core.database.CoupleEntity
import com.sadturtleman.flowershoes.core.database.buildKkotsin
import com.sadturtleman.flowershoes.core.database.inMemoryDatabaseBuilder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import okio.FileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class UnlinkFailureTest {
    @Test
    fun `서버 기능이 없으면 오류를 안내하고 관계를 남긴다`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "kk-unlink-ui-${Uuid.random()}"
        FileSystem.SYSTEM.createDirectories(root)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val db = inMemoryDatabaseBuilder().buildKkotsin()
        try {
            val settings = DeviceSettings(PreferenceDataStoreFactory.createWithPath(scope = scope) { root / "settings.preferences_pb" })
            settings.ensureIdentity()
            settings.bindAccount("owner")
            db.coupleDao().upsert(CoupleEntity("couple", "owner", "partner", 1, 100))
            val identity = object : Identity {
                override val userId = "owner"
                override val deviceId = "device"
                override val keyEpoch = 1
            }
            val writer = SyncWriter(db, identity)
            val viewModel = SettingsViewModel(settings, ProfileRepository(db, writer, identity), UnlinkService(db, InMemoryKeyVault(), settings, null), identity, EventRepository(db, writer, identity))
            viewModel.unlink(AfterUnlink.WIPE).join()
            val result = viewModel.unlinkOperation.value as UnlinkOperation.Failed
            assertEquals(AfterUnlink.WIPE, result.after)
            assertEquals(AfterUnlink.WIPE, viewModel.unlinkSelection.value)
            assertEquals("partner", db.coupleDao().get()?.partnerUserId)
        } finally {
            db.close()
            scope.coroutineContext[Job]!!.cancelAndJoin()
            if (FileSystem.SYSTEM.exists(root)) FileSystem.SYSTEM.deleteRecursively(root)
            Dispatchers.resetMain()
        }
    }
    @Test
    fun `서버 완료 전에는 화면을 닫지 않고 중복 요청을 보내지 않는다`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "kk-unlink-ui-${Uuid.random()}"
        FileSystem.SYSTEM.createDirectories(root)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val db = inMemoryDatabaseBuilder().buildKkotsin()
        try {
            val settings = DeviceSettings(PreferenceDataStoreFactory.createWithPath(scope = scope) { root / "settings.preferences_pb" })
            settings.ensureIdentity()
            settings.bindAccount("owner")
            db.coupleDao().upsert(CoupleEntity("couple", "owner", "partner", 1, 100))
            val identity = object : Identity {
                override val userId = "owner"
                override val deviceId = "device"
                override val keyEpoch = 1
            }
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var calls = 0
            val server = object : AccountTransport {
                override suspend fun unlinkCouple(coupleId: String) { calls++; started.complete(Unit); release.await() }
                override suspend fun deleteAccount() = Unit
            }
            val writer = SyncWriter(db, identity)
            val viewModel = SettingsViewModel(settings, ProfileRepository(db, writer, identity), UnlinkService(db, InMemoryKeyVault(), settings, server), identity, EventRepository(db, writer, identity))
            val first = viewModel.unlink(AfterUnlink.WIPE, withoutDeviceLock = true)
            started.await()
            val running = viewModel.unlinkOperation.value as UnlinkOperation.Running
            assertEquals(AfterUnlink.WIPE, running.after)
            assertTrue(running.withoutDeviceLock)
            viewModel.selectUnlinkAfter(AfterUnlink.KEEP_AS_MEMORIES)
            assertEquals(AfterUnlink.WIPE, viewModel.unlinkSelection.value)
            val second = viewModel.unlink(AfterUnlink.KEEP_AS_MEMORIES)
            second.join()
            assertEquals(1, calls)
            assertTrue(viewModel.unlinkOperation.value === running)
            assertEquals("partner", db.coupleDao().get()?.partnerUserId)
            release.complete(Unit)
            first.join()
            val completed = viewModel.unlinkOperation.value as UnlinkOperation.Completed
            assertEquals(AfterUnlink.WIPE, completed.after)
            assertTrue(completed.withoutDeviceLock)
            assertTrue(viewModel.consumeUnlinkCompletion(completed))
            assertTrue(!viewModel.consumeUnlinkCompletion(completed))
            assertEquals(null, db.coupleDao().get())
        } finally {
            db.close()
            scope.coroutineContext[Job]!!.cancelAndJoin()
            if (FileSystem.SYSTEM.exists(root)) FileSystem.SYSTEM.deleteRecursively(root)
            Dispatchers.resetMain()
        }
    }

}
