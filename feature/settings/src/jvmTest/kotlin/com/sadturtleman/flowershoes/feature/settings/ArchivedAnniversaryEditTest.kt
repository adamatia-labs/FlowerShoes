package com.sadturtleman.flowershoes.feature.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.viewModelScope
import com.sadturtleman.flowershoes.core.data.DeviceSettings
import com.sadturtleman.flowershoes.core.data.EventRepository
import com.sadturtleman.flowershoes.core.data.Identity
import com.sadturtleman.flowershoes.core.data.ProfileRepository
import com.sadturtleman.flowershoes.core.data.SyncWriter
import com.sadturtleman.flowershoes.core.data.sync.AfterUnlink
import com.sadturtleman.flowershoes.core.data.sync.InMemoryKeyVault
import com.sadturtleman.flowershoes.core.data.sync.UnlinkService
import com.sadturtleman.flowershoes.core.database.CoupleEntity
import com.sadturtleman.flowershoes.core.database.buildKkotsin
import com.sadturtleman.flowershoes.core.database.inMemoryDatabaseBuilder
import com.sadturtleman.flowershoes.core.model.Event
import com.sadturtleman.flowershoes.core.model.EventType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atTime
import okio.FileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ArchivedAnniversaryEditTest {
    @Test
    fun `보관한 기념일을 고치면 안내하고 원본과 동기화 로그를 유지한다`() = anniversaryEditTest(wipe = false)

    @Test
    fun `해제하며 지운 기념일 초안은 새 일정으로 복제하지 않는다`() = anniversaryEditTest(wipe = true)

    private fun anniversaryEditTest(wipe: Boolean) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "kk-settings-archive-${Uuid.random()}"
        val settingsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val db = inMemoryDatabaseBuilder().buildKkotsin()
        var viewModel: SettingsViewModel? = null
        try {
            val settings = DeviceSettings(PreferenceDataStoreFactory.createWithPath(scope = settingsScope) { root / "settings.preferences_pb" })
            val identity = object : Identity {
                override val userId = "owner"
                override val deviceId = "device"
                override val keyEpoch = 1
            }
            val writer = SyncWriter(db, identity)
            val events = EventRepository(db, writer, identity)
            val unlink = UnlinkService(db, InMemoryKeyVault(), settings, null)
            val date = LocalDate(2025, 11, 1)
            events.save(Event("custom-anniversary", EventType.ANNIVERSARY, "원래 기념일",
                date.atTime(0, 0), date.atTime(23, 59), false, Clock.System.now(), 0, identity.deviceId))
            db.coupleDao().upsert(CoupleEntity("old-couple", "owner", "partner", 1, 100))
            val before = assertNotNull(db.eventDao().get("custom-anniversary"))
            val original = assertNotNull(events.get(before.id))
            unlink.onPartnerUnlinked(if (wipe) AfterUnlink.WIPE else AfterUnlink.KEEP_AS_MEMORIES)
            val nextSeq = db.syncDao().nextSeq("device:app")
            val vm = SettingsViewModel(settings, ProfileRepository(db, writer, identity), unlink, identity, events)
            viewModel = vm
            val notice = async(start = CoroutineStart.UNDISPATCHED) { vm.notices.first() }

            vm.saveAnniversary(before.id, "바꾼 기념일", LocalDate(2026, 11, 2), repeats = false, original = original)

            val message = if (wipe) "기록이 바뀌었어요. 다시 열어 주세요" else "보관한 기록은 읽거나 지울 수 있어요"
            assertEquals(SettingsViewModel.Notice(message, error = true), notice.await())
            assertEquals(if (wipe) null else before, db.eventDao().get(before.id))
            assertTrue(db.syncDao().log("device:app").isEmpty())
            assertEquals(nextSeq, db.syncDao().nextSeq("device:app"))
        } finally {
            viewModel?.viewModelScope?.cancel()
            settingsScope.coroutineContext[Job]!!.cancelAndJoin()
            db.close()
            if (FileSystem.SYSTEM.exists(root)) FileSystem.SYSTEM.deleteRecursively(root)
            Dispatchers.resetMain()
        }
    }
}
