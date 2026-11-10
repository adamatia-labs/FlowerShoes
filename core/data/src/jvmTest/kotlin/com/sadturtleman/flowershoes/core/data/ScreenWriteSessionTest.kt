package com.sadturtleman.flowershoes.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.sadturtleman.flowershoes.core.data.sync.*
import com.sadturtleman.flowershoes.core.database.*
import com.sadturtleman.flowershoes.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalDate
import okio.FileSystem
import kotlin.test.*
import kotlin.time.Clock
import kotlin.uuid.Uuid

class ScreenWriteSessionTest {
    private val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "screen-write-${Uuid.random()}"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val settings = DeviceSettings(PreferenceDataStoreFactory.createWithPath(scope = scope) { root / "settings.preferences_pb" })
    private val db = Room.databaseBuilder<AppDatabase>((root / "app.db").toString()).buildKkotsin()
    private val identity = IdentityHolder()

    @AfterTest fun close(): Unit = runBlocking {
        db.close(); scope.coroutineContext[Job]!!.cancelAndJoin(); FileSystem.SYSTEM.deleteRecursively(root)
    }

    @Test fun `정리가 끝나 새 관계가 생겨도 이전 화면의 새 일정은 저장하지 않는다`(): Unit = runBlocking {
        val oldEditor = prepare().events
        val draft = event("unsaved-draft")
        UnlinkService(db, InMemoryKeyVault(), settings, null).onPartnerUnlinked(AfterUnlink.WIPE, "old-couple")
        assertNull(db.cleanupDao().get())
        db.coupleDao().upsert(CoupleEntity("new-couple", "owner", "new-partner", 1, 200))
        val before = db.syncDao().log("${identity.deviceId}:app")
        val failure = runCatching { oldEditor.save(draft) }.exceptionOrNull()
        assertTrue(failure is CancellationException, "An old editor must lose write permission after completed cleanup")
        assertNull(db.eventDao().get(draft.id))
        assertEquals(before, db.syncDao().log("${identity.deviceId}:app"))
    }

    @Test fun `계정이 돌아와도 이전 설정과 첨부 권한은 복구되지 않는다`(): Unit = runBlocking {
        val old = prepare()
        settings.bindAccount("other"); settings.bindAccount("owner")
        assertFailsWith<ScreenSessionChangedException> { old.settings.setTheme(AppTheme.SOLDIER) }
        assertFailsWith<ScreenSessionChangedException> { old.attachments.put(AttachmentKind.PHOTO, byteArrayOf(1)) }
        assertEquals(AppTheme.GOMSIN, settings.state.first().theme)
        assertTrue(db.attachmentDao().all().isEmpty())
        assertFalse(FileSystem.SYSTEM.exists(root / "attachments"))
    }

    @Test fun `다른 DB 쓰기를 기다린 편집은 커밋된 새 관계를 다시 확인한다`(): Unit = runBlocking {
        val old = prepare()
        val other = Room.databaseBuilder<AppDatabase>((root / "app.db").toString()).buildKkotsin()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        try {
            val replacement = async(Dispatchers.IO) {
                other.useWriterConnection { tx -> tx.immediateTransaction {
                    other.coupleDao().clear()
                    other.coupleDao().upsert(CoupleEntity("new-couple", "owner", "new-partner", 1, 200))
                    entered.complete(Unit); release.await()
                } }
            }
            entered.await()
            val write = async(Dispatchers.IO) { runCatching { old.events.save(event("late")) }.exceptionOrNull() }
            // Wait until the actual account lock is held, then release the independent SQLite writer.
            withTimeout(5_000) { while (!settingsBusy()) { delay(10) } }
            release.complete(Unit); replacement.await()
            assertIs<ScreenSessionChangedException>(withTimeout(5_000) { write.await() })
            assertNull(db.eventDao().get("late"))
        } finally { release.complete(Unit); other.close() }
    }

    private suspend fun settingsBusy(): Boolean = withTimeoutOrNull(50) {
        settings.withCurrentAccount(settings.state.first()) {}
        false
    } ?: true

    @Test fun `현재 화면의 설정 상태 기념일과 초안 전송은 중첩 잠금 없이 저장된다`(): Unit = runBlocking {
        val data = prepare()
        withTimeout(5_000) {
            data.settings.setTheme(AppTheme.SOLDIER)
            data.profile.setAnniversary(LocalDate(2026, 11, 11))
            val edit = assertNotNull(data.statusEditor.open("owner"))
            data.statusEditor.save(edit, "훈련 중", 1, null)
            val drafts = RoomLetterDraftStore(db, settings)
            val session = assertNotNull(settings.draftSessions.first())
            val editor = assertNotNull(drafts.open(session, "draft"))
            val letter = Letter("letter", "owner", blocks = listOf(LetterBlock.Text("hello")), paper = "plain", visibleAt = Clock.System.now())
            assertTrue(data.letters.sendDraft(drafts, editor, letter, emptyList()))
            assertNotNull(data.letters.find("letter"))
            assertEquals("훈련 중", data.statuses.observeLatest("owner").first()?.label)
        }
    }

    @Test fun `같은 계정 초안이더라도 바뀐 관계로 이전 편지 전송을 우회하지 못한다`(): Unit = runBlocking {
        val old = prepare()
        val drafts = RoomLetterDraftStore(db, settings)
        val editor = assertNotNull(drafts.open(assertNotNull(settings.draftSessions.first()), "draft"))
        db.coupleDao().clear()
        db.coupleDao().upsert(CoupleEntity("new-couple", "owner", "new-partner", 1, 200))
        val letter = Letter("old-letter", "owner", blocks = listOf(LetterBlock.Text("old")), paper = "plain", visibleAt = Clock.System.now())
        withTimeout(5_000) {
            assertFailsWith<ScreenSessionChangedException> { old.letters.sendDraft(drafts, editor, letter, emptyList()) }
        }
        assertNull(old.letters.find("old-letter"))
    }

    @Test fun `다른 계정의 초안 잠금을 이전 화면이 재사용하지 못한다`(): Unit = runBlocking {
        val old = prepare()
        settings.bindAccount("other")
        val current = assertNotNull(settings.draftSessions.first())
        withTimeout(5_000) {
            settings.withDraftSession(current) {
                assertFailsWith<ScreenSessionChangedException> { old.events.save(event("foreign")) }
            }
        }
        assertNull(db.eventDao().get("foreign"))
    }

    @Test fun `이전 확인 화면은 새 관계 해제나 계정 탈퇴를 시작하지 못한다`(): Unit = runBlocking {
        val old = prepare()
        UnlinkService(db, InMemoryKeyVault(), settings, null).onPartnerUnlinked(AfterUnlink.WIPE, "old-couple")
        val next = CoupleEntity("new-couple", "owner", "new-partner", 1, 200)
        db.coupleDao().upsert(next)
        var serverCalls = 0
        val server = object : AccountTransport {
            override suspend fun unlinkCouple(coupleId: String) { serverCalls++ }
            override suspend fun deleteAccount() { serverCalls++ }
        }
        val service = UnlinkService(db, InMemoryKeyVault(), settings, server)
        assertFailsWith<ScreenSessionChangedException> { service.unlink(AfterUnlink.WIPE, old.session) }
        assertFailsWith<ScreenSessionChangedException> { service.deleteAccount(old.session) }
        assertFailsWith<ScreenSessionChangedException> { service.onPartnerUnlinked(AfterUnlink.WIPE, screen = old.session) }
        assertEquals(0, serverCalls)
        assertNull(db.cleanupDao().get())
        assertEquals(next, db.coupleDao().get())
    }

    private suspend fun prepare(): ScreenData {
        settings.ensureIdentity(); settings.bindAccount("owner"); settings.setKeyEpoch(1); settings.setOnboarded()
        identity.update(settings.state.first())
        EventRepository(db, SyncWriter(db, identity), identity).save(event("before-link"))
        db.coupleDao().upsert(CoupleEntity("old-couple", "owner", "partner", 1, 100))
        return ScreenData(db, settings, settings.state.first(), db.coupleDao().get(), AttachmentStore(db, root / "attachments"))
    }

    private fun event(id: String) = Event(id, EventType.VISIT, "old input", LocalDateTime(2026, 11, 1, 10, 0),
        LocalDateTime(2026, 11, 1, 12, 0), false, Clock.System.now(), 0, identity.deviceId)
}
