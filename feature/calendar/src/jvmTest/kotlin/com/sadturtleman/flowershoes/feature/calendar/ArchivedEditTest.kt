package com.sadturtleman.flowershoes.feature.calendar

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sadturtleman.flowershoes.core.data.DeviceSettings
import com.sadturtleman.flowershoes.core.data.EventRepository
import com.sadturtleman.flowershoes.core.data.Identity
import com.sadturtleman.flowershoes.core.data.LeaveRepository
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
import com.sadturtleman.flowershoes.core.model.LeaveGrant
import com.sadturtleman.flowershoes.core.model.LeaveKind
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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import okio.FileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ArchivedEditTest {
    @Test
    fun `해제 전에 연 커플 기념일 초안은 새 일정으로 복제하지 않는다`() = staleAnniversaryDraftTest(replaced = false)

    @Test
    fun `해제 전에 연 커플 기념일 초안은 새 관계의 같은 id를 덮어쓰지 않는다`() = staleAnniversaryDraftTest(replaced = true)

    private fun staleAnniversaryDraftTest(replaced: Boolean) = archivedEditTest(archiveFirst = false) {
        val viewModel = CalendarViewModel(events).also { viewModels += it }
        archive {
            val original = assertNotNull(events.get(Event.COUPLE_ANNIVERSARY_ID))
            viewModel.onIntent(CalendarIntent.ChangeDraft(EventDraft(
                id = original.id, type = original.type, title = "이전 연인과의 메모",
                start = original.start.date, end = original.end.date, original = original,
            )))
        }
        if (replaced) {
            db.coupleDao().upsert(CoupleEntity("new-couple", "owner", "new-partner", 1, 200))
            profile.setAnniversary(LocalDate(2026, 11, 10))
        }
        val current = db.eventDao().get(Event.COUPLE_ANNIVERSARY_ID)
        val log = db.syncDao().log("device:app").map { it.opId to it.payload.decodeToString() }
        val notice = async(start = CoroutineStart.UNDISPATCHED) { viewModel.notices.first() }

        viewModel.onIntent(CalendarIntent.SaveDraft)

        assertEquals("기록이 바뀌었어요. 다시 열어 주세요", notice.await())
        assertEquals(current, db.eventDao().get(Event.COUPLE_ANNIVERSARY_ID))
        assertEquals(log, db.syncDao().log("device:app").map { it.opId to it.payload.decodeToString() })
        assertNotNull(db.eventDao().get("archived:old-couple:${Event.COUPLE_ANNIVERSARY_ID}"))
    }

    @Test
    fun `보관한 일정을 고치면 안내하고 원본과 동기화 로그를 유지한다`() = archivedEditTest {
        val before = assertNotNull(db.eventDao().get("old-event"))
        val nextSeq = db.syncDao().nextSeq("device:app")
        val original = assertNotNull(events.get(before.id))
        val viewModel = CalendarViewModel(events).also { viewModels += it }
        val notice = async(start = CoroutineStart.UNDISPATCHED) { viewModel.notices.first() }

        viewModel.onIntent(CalendarIntent.ChangeDraft(EventDraft(
            id = original.id, type = original.type, title = "바꾼 제목",
            start = original.start.date, end = original.end.date, original = original,
        )))
        viewModel.onIntent(CalendarIntent.SaveDraft)

        assertEquals("보관한 기록은 읽거나 지울 수 있어요", notice.await())
        assertEquals(before, db.eventDao().get(before.id))
        assertTrue(db.syncDao().log("device:app").isEmpty())
        assertEquals(nextSeq, db.syncDao().nextSeq("device:app"))
    }

    @Test
    fun `보관한 받은 휴가를 고치면 안내하고 원본과 동기화 로그를 유지한다`() = archivedEditTest {
        val before = assertNotNull(db.leaveDao().get("old-leave"))
        val nextSeq = db.syncDao().nextSeq("device:app")
        val original = leave.observeGrants().first().single()
        val viewModel = LeaveViewModel(leave, profile).also { viewModels += it }
        val notice = async(start = CoroutineStart.UNDISPATCHED) { viewModel.notices.first() }

        viewModel.save(viewModel.editDraft(original).copy(memo = "바꾼 휴가", days = 4))

        assertEquals("보관한 기록은 읽거나 지울 수 있어요", notice.await())
        assertEquals(before, db.leaveDao().get(before.id))
        assertTrue(db.syncDao().log("device:app").isEmpty())
        assertEquals(nextSeq, db.syncDao().nextSeq("device:app"))
    }

    private fun archivedEditTest(archiveFirst: Boolean = true, block: suspend Fixture.() -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture(this)
        try {
            if (archiveFirst) fixture.archive()
            fixture.block()
        } finally {
            fixture.close()
            Dispatchers.resetMain()
        }
    }

    private class Fixture(scope: TestScope) : CoroutineScope by scope {
        val db = inMemoryDatabaseBuilder().buildKkotsin()
        val viewModels = mutableListOf<ViewModel>()
        private val identity = object : Identity {
            override val userId = "owner"
            override val deviceId = "device"
            override val keyEpoch = 1
        }
        private val writer = SyncWriter(db, identity)
        val events = EventRepository(db, writer, identity)
        val leave = LeaveRepository(db, writer, identity)
        val profile = ProfileRepository(db, writer, identity)
        private val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "kk-calendar-archive-${Uuid.random()}"
        private val settingsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val settings = DeviceSettings(PreferenceDataStoreFactory.createWithPath(scope = settingsScope) { root / "settings.preferences_pb" })

        suspend fun archive(beforeUnlink: suspend () -> Unit = {}) {
            // 첫 솔로 쓰기로 초기화한 뒤 A–B 관계에서 보관한다. 이관 구현에 직접 표식을 넣지 않는다.
            events.save(Event("old-event", EventType.VISIT, "원래 일정",
                LocalDateTime(2026, 11, 1, 10, 0), LocalDateTime(2026, 11, 1, 12, 0),
                false, Clock.System.now(), 0, identity.deviceId))
            leave.save(LeaveGrant("old-leave", LeaveKind.REWARD, "원래 휴가", 2,
                LocalDate(2026, 11, 1), Clock.System.now(), 0, identity.deviceId))
            profile.setAnniversary(LocalDate(2025, 11, 1))
            db.coupleDao().upsert(CoupleEntity("old-couple", "owner", "partner", 1, 100))
            beforeUnlink()
            UnlinkService(db, InMemoryKeyVault(), settings, null).onPartnerUnlinked(AfterUnlink.KEEP_AS_MEMORIES)
        }

        suspend fun close() {
            viewModels.forEach { it.viewModelScope.cancel() }
            settingsScope.coroutineContext[Job]!!.cancelAndJoin()
            db.close()
            if (FileSystem.SYSTEM.exists(root)) FileSystem.SYSTEM.deleteRecursively(root)
        }
    }
}
