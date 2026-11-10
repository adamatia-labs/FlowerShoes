package com.sadturtleman.flowershoes.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.sadturtleman.flowershoes.core.data.sync.*
import com.sadturtleman.flowershoes.core.database.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import okio.FileSystem
import kotlin.test.*
import kotlin.uuid.Uuid

class LetterDraftStoreTest {
    private val db = inMemoryDatabaseBuilder().buildKkotsin()
    private val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "drafts-${Uuid.random()}"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val settings = DeviceSettings(PreferenceDataStoreFactory.createWithPath(scope = scope) { root / "settings.preferences_pb" })
    private val store = RoomLetterDraftStore(db, settings)
    private val transport = object : AccountTransport {
        override suspend fun unlinkCouple(coupleId: String) = Unit
        override suspend fun deleteAccount() = Unit
    }
    private val unlink = UnlinkService(db, InMemoryKeyVault(), settings, transport)
    private suspend fun login(uid: String = "account-a"): DraftSession {
        settings.ensureIdentity(); settings.bindAccount(uid)
        return checkNotNull(settings.draftSessions.first())
    }
    private suspend fun snapshot() = store.observe().first { it.loaded }
    private suspend fun write(id: String, json: String = "{}", at: Long = 100): DraftEditor {
        val session = settings.draftSessions.first() ?: login()
        val editor = checkNotNull(store.open(session, id))
        assertTrue(store.save(editor, json, at))
        return editor
    }
    @AfterTest fun close() = runBlocking {
        scope.cancel(); scope.coroutineContext[Job]!!.join()
        db.close(); FileSystem.SYSTEM.deleteRecursively(root, mustExist = false)
    }

    @Test fun `쓰던 편지는 여러 통 남고 마지막으로 고친 것이 맨 위다`() = runBlocking {
        write("a", "first", 100); write("b", "second", 200); write("a", "edited", 300)
        assertEquals(listOf(StoredDraft("a", "edited", 300), StoredDraft("b", "second", 200)), snapshot().drafts)
    }
    @Test fun `지운 편지는 목록에서 빠진다`() = runBlocking {
        val a = write("a"); write("b")
        assertTrue(store.delete(a.session, "a"))
        assertEquals(listOf("b"), snapshot().drafts.map { it.id })
    }
    @Test fun `연결이 해제되면 사본을 남겨도 현재 계정의 쓰던 편지는 지운다`() = runBlocking {
        val a = write("a")
        unlink.onPartnerUnlinked(AfterUnlink.KEEP_AS_MEMORIES)
        assertTrue(snapshot().drafts.isEmpty())
        assertFalse(store.save(a, "late", 200))
    }
    @Test fun `탈퇴하면 현재 계정의 쓰던 편지를 지운다`() = runBlocking {
        val a = write("a")
        unlink.deleteAccount()
        assertTrue(snapshot().drafts.isEmpty())
        assertFalse(store.save(a, "late", 200))
        assertNull(db.letterDraftDao().get("a"))
    }
    @Test fun `해제 직후 새로 쓴 입력도 계정 전환 때 원래 계정에 저장된다`() = runBlocking {
        write("old")
        unlink.onPartnerUnlinked(AfterUnlink.KEEP_AS_MEMORIES)
        val current = checkNotNull(settings.draftSessions.first())
        val editor = checkNotNull(store.open(current, "after-unlink"))
        settings.bindAccount("account-b")
        assertTrue(store.save(editor, "new solo input", 200))
        assertEquals("new solo input", db.letterDraftDao().get("after-unlink")?.draftJson)
    }
    @Test fun `탈퇴 서버가 없으면 성공으로 처리하거나 쓰던 편지를 지우지 않는다`() = runBlocking {
        write("a")
        assertFailsWith<IllegalStateException> { UnlinkService(db, InMemoryKeyVault(), settings, null).deleteAccount() }
        assertEquals(listOf("a"), snapshot().drafts.map { it.id })
    }
    @Test fun `계정을 바꾸면 이전 계정의 초안이 보이지 않는다`() = runBlocking {
        login("account-a"); write("private-a", "A")
        login("account-b")
        assertTrue(RoomLetterDraftStore(db, settings).observe().first { it.loaded }.drafts.isEmpty())
    }
    @Test fun `다른 계정은 같은 식별자로 원래 초안을 덮어쓸 수 없다`() = runBlocking {
        write("private-a", "original")
        val b = login("account-b")
        assertNull(store.open(b, "private-a"))
        assertEquals("original", db.letterDraftDao().get("private-a")?.draftJson)
    }
    @Test fun `다른 계정의 삭제 요청은 원래 초안을 보존한다`() = runBlocking {
        write("private-a", "original")
        assertFalse(store.delete(login("account-b"), "private-a"))
        assertNotNull(db.letterDraftDao().get("private-a"))
        Unit
    }
    @Test fun `관계 정리는 다른 계정과 소유자 미상 초안 및 첨부를 보존한다`() = runBlocking {
        db.letterDraftDao().upsert(LetterDraftEntity("legacy", "unknown owner", 1))
        login("account-b"); write("private-b", "B")
        login("account-a"); write("private-a", "A")
        val attachments = AttachmentStore(db, root / "attachments")
        val photo = attachments.put(com.sadturtleman.flowershoes.core.model.AttachmentKind.PHOTO, byteArrayOf(1, 2, 3))
        var filesWiped = false
        UnlinkService(db, InMemoryKeyVault(), settings, transport) { filesWiped = true }.onPartnerUnlinked(AfterUnlink.WIPE)
        assertEquals(setOf("legacy", "private-b"), db.letterDraftDao().observeAll().first().map { it.id }.toSet())
        assertFalse(filesWiped)
        assertContentEquals(byteArrayOf(1, 2, 3), attachments.read(photo))
    }
    @Test fun `탈퇴 정리 중에는 새 편집 권한을 발급하지 않는다`() = runBlocking {
        val a = write("a")
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val vault = object : KeyVault by InMemoryKeyVault() {
            override suspend fun clearCoupleKeys() { entered.complete(Unit); release.await() }
        }
        val deletion = async { UnlinkService(db, vault, settings, transport).deleteAccount() }
        try {
            withTimeout(5_000) { entered.await() }
            assertNull(settings.draftSessions.first())
            assertNull(store.open(a.session, "late-editor"))
            assertFalse(store.save(a, "late-input", 200))
        } finally { release.complete(Unit); deletion.await() }
        assertEquals(0, db.letterDraftDao().count())
    }
    @Test fun `다른 계정의 빈 예약만 남았으면 첨부 삭제를 막지 않는다`() = runBlocking {
        val a = login()
        assertNotNull(store.open(a, "empty-reservation"))
        settings.bindAccount("account-b")
        var wiped = false
        UnlinkService(db, InMemoryKeyVault(), settings, transport) { wiped = true }.onPartnerUnlinked(AfterUnlink.WIPE)
        assertTrue(wiped)
        assertNotNull(db.letterDraftDao().get("empty-reservation"))
        Unit
    }
    @Test fun `자동 저장 전 다른 계정 사진도 WIPE에서 보존한다`() = runBlocking {
        val a = checkNotNull(store.open(login(), "pending-photo"))
        assertTrue(store.reserveAttachments(a))
        val attachments = AttachmentStore(db, root / "attachments")
        val bytes = byteArrayOf(3, 2, 1)
        val photo = attachments.put(com.sadturtleman.flowershoes.core.model.AttachmentKind.PHOTO, bytes)
        assertEquals("", db.letterDraftDao().get(a.id)?.draftJson)
        settings.bindAccount("account-b")
        assertFalse(store.reserveAttachments(a))
        var wiped = false
        UnlinkService(db, InMemoryKeyVault(), settings, transport) { wiped = true }.onPartnerUnlinked(AfterUnlink.WIPE)
        assertFalse(wiped)
        assertTrue(store.save(a, "{photo:$photo}", 200))
        assertContentEquals(bytes, attachments.read(photo))
    }
    @Test fun `계정이 바뀐 초안 전송은 편지와 전송 기록을 만들지 않는다`() = runBlocking {
        val a = write("a", "private")
        val identity = object : Identity {
            override val userId = a.session.ownerUid
            override val deviceId = a.session.deviceId
            override val keyEpoch = 0
        }
        val letters = LetterRepository(db, SyncWriter(db, identity), identity)
        val letter = letters.newLetter(listOf(com.sadturtleman.flowershoes.core.model.LetterBlock.Text("private")))
        settings.bindAccount("account-b")
        assertFalse(letters.sendDraft(store, a, letter, emptyList()))
        assertTrue(db.letterDao().allActive().isEmpty())
        assertTrue(db.syncDao().log("${a.session.deviceId}:app").isEmpty())
        val current = login()
        val fresh = checkNotNull(store.open(current, "a"))
        assertTrue(letters.sendDraft(store, fresh, letter, emptyList()))
        assertEquals("account-a", db.letterDao().allActive().single().authorId)
        assertEquals(1, db.syncDao().log("${a.session.deviceId}:app").size)
    }
    @Test fun `전송 트랜잭션과 계정 전환을 같은 설정 경계에서 직렬화한다`() = runBlocking {
        val a = write("a")
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val sending = async { store.withCurrentEditor(a) { entered.complete(Unit); release.await() } }
        withTimeout(5_000) { entered.await() }
        val switching = async { settings.bindAccount("account-b") }
        try { assertNull(withTimeoutOrNull(100) { switching.await(); true }) }
        finally { release.complete(Unit) }
        assertTrue(sending.await()); switching.await()
        assertFalse(store.withCurrentEditor(a) { error("stale send must not execute") })
    }
    @Test fun `이전 계정의 예약된 마지막 입력은 그 계정에만 저장한다`() = runBlocking {
        val a = write("a", "before")
        login("account-b")
        assertTrue(store.save(a, "captured before switch", 200))
        assertTrue(snapshot().drafts.isEmpty())
        login("account-a")
        assertEquals("captured before switch", snapshot().drafts.single().json)
        assertFalse(store.save(a, "stale after login", 300))
        assertFalse(store.retire(a))
        login("account-b")
        assertFalse(store.save(a, "stale after another switch", 400))
    }
    @Test fun `신원 캐시와 무관하게 이전 로그인 세션은 새 편집을 열거나 삭제하지 못한다`() = runBlocking {
        val a = write("a")
        login("account-b")
        assertNull(store.open(a.session, "stale-new"))
        assertFalse(store.delete(a.session, "a"))
        login("account-a")
        assertNull(store.open(a.session, "stale-again"))
    }
    @Test fun `새 편집과 삭제는 이전 자동 저장 권한을 무효화한다`() = runBlocking {
        val old = write("a", "old")
        val fresh = checkNotNull(store.open(old.session, "a"))
        assertFalse(store.save(old, "stale", 200)); assertFalse(store.retire(old))
        assertTrue(store.save(fresh, "fresh", 300))
        assertTrue(store.retire(fresh))
        assertFalse(store.save(fresh, "resurrect", 400))
        assertNull(db.letterDraftDao().get("a"))
    }
    @Test fun `소유자 미상 초안은 로그인과 별도로 본인 확인해야 복구한다`() = runBlocking {
        db.letterDraftDao().upsert(LetterDraftEntity("legacy", "private", 1))
        val a = login()
        assertTrue(snapshot().drafts.isEmpty()); assertEquals(1, snapshot().unclaimedCount)
        assertNull(store.open(a, "legacy"))
        assertFalse(store.delete(a, "legacy"))
        assertFalse(store.recoverUnclaimed(a, confirmedOwner = false))
        login("account-b")
        assertFalse(store.recoverUnclaimed(a, confirmedOwner = true))
        val current = login()
        assertTrue(store.recoverUnclaimed(current, confirmedOwner = true))
        assertEquals("private", snapshot().drafts.single().json)
        assertEquals(0, snapshot().unclaimedCount)
    }
    @Test fun `이미 수집 중인 목록도 계정 변경으로 비워진다`() = runBlocking {
        write("a", "private")
        val events = Channel<DraftSnapshot>(Channel.UNLIMITED)
        val collector = launch { store.observe().collect { events.send(it) } }
        suspend fun nextReady(owner: String) = withTimeout(5_000) {
            var result: DraftSnapshot
            do { result = events.receive() } while (!result.loaded || result.session?.ownerUid != owner)
            result
        }
        try {
            assertEquals("a", nextReady("account-a").drafts.single().id)
            login("account-b")
            assertTrue(nextReady("account-b").drafts.isEmpty())
        } finally { collector.cancelAndJoin(); events.close() }
    }
}
