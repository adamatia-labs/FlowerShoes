package com.sadturtleman.flowershoes.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Room
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.sadturtleman.flowershoes.core.crypto.DeviceKeys
import com.sadturtleman.flowershoes.core.crypto.InviteToken
import com.sadturtleman.flowershoes.core.crypto.Pairing
import com.sadturtleman.flowershoes.core.crypto.Sodium
import com.sadturtleman.flowershoes.core.data.sync.*
import com.sadturtleman.flowershoes.core.database.*
import com.sadturtleman.flowershoes.core.model.LetterBlock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okio.FileSystem
import kotlin.test.*
import kotlin.uuid.Uuid

class DurableUnlinkTest {
    private val fs = FileSystem.SYSTEM
    private val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "durable-unlink-${Uuid.random()}"
    private fun open() = Room.databaseBuilder<AppDatabase>((root / "db").toString()).buildKkotsin()
    private fun settings(scope: CoroutineScope) = DeviceSettings(
        PreferenceDataStoreFactory.createWithPath(scope = scope) { root / "settings.preferences_pb" },
    )
    private suspend fun prepare(db: AppDatabase, settings: DeviceSettings): String {
        settings.ensureIdentity()
        settings.bindAccount("owner")
        settings.setKeyEpoch(1)
        db.coupleDao().upsert(CoupleEntity("old-couple", "owner", "partner", 1, 100))
        val identity = object : Identity {
            override val userId = "owner"
            override val deviceId = "device"
            override val keyEpoch = 1
        }
        val letters = LetterRepository(db, SyncWriter(db, identity), identity)
        val letter = letters.newLetter(listOf(LetterBlock.Text("보관할 편지")))
        letters.send(letter, emptyList())
        fs.createDirectories(root / "attachments")
        fs.write(root / "attachments" / "photo") { writeUtf8("photo") }
        return letter.id
    }
    private class Server : AccountTransport {
        var unlinks = 0
        override suspend fun unlinkCouple(coupleId: String) { unlinks++ }
        override suspend fun deleteAccount() = Unit
    }

    @Test
    fun `첫 설정 커밋 실패에는 Room 작업과 서버 요청을 남기지 않는다`() = runBlocking {
        fs.createDirectories(root)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val db = open()
        try {
            val original = PreferenceDataStoreFactory.createWithPath(scope = scope) { root / "settings.preferences_pb" }
            val token = stringPreferencesKey("relationship_cleanup_token")
            val failingStore = object : DataStore<Preferences> by original {
                override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = original.updateData { before ->
                    val after = transform(before)
                    if (before[token] == null && after[token] != null) error("injected first settings commit failure")
                    after
                }
            }
            val settings = DeviceSettings(failingStore)
            val letterId = prepare(db, settings)
            val server = Server()
            val service = UnlinkService(db, InMemoryKeyVault(), settings, server)
            assertFailsWith<IllegalStateException> { service.unlink(AfterUnlink.WIPE) }
            assertNull(db.cleanupDao().get())
            assertNull(settings.cleanupRequest())
            assertEquals(0, server.unlinks)
            settings.bindAccount("next-owner")
            assertFalse(service.resumePending())
            assertNotNull(db.letterDao().get(letterId))
            assertTrue(fs.exists(root / "attachments" / "photo"))
        } finally { db.close(); scope.coroutineContext[Job]!!.cancelAndJoin(); fs.deleteRecursively(root) }
    }

    @Test
    fun `설정 예약 뒤 Room 저장 실패는 재실행에서 같은 선택으로 복원한다`() = bootstrapFailure(replaceRelationship = false)

    @Test
    fun `예약 뒤 대상이 바뀌면 서버 요청 없이 예약을 해제하고 새 관계를 보존한다`() = bootstrapFailure(replaceRelationship = true)

    private fun bootstrapFailure(replaceRelationship: Boolean): Unit = runBlocking {
        fs.createDirectories(root)
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var db = open()
        try {
            var settings = settings(scope)
            val letterId = prepare(db, settings)
            val server = Server()
            db.useWriterConnection { tx -> tx.immediateTransaction {
                usePrepared("CREATE TRIGGER reject_cleanup BEFORE INSERT ON relationship_cleanup BEGIN SELECT RAISE(ABORT, 'injected journal commit failure'); END") { it.step() }
            } }
            val first = UnlinkService(db, InMemoryKeyVault(), settings, server)
            assertFails { first.unlink(AfterUnlink.KEEP_AS_MEMORIES) }
            assertNull(db.cleanupDao().get())
            assertNotNull(settings.cleanupRequest())
            assertEquals(AfterUnlink.KEEP_AS_MEMORIES, first.pendingChoice.first())
            assertEquals(0, server.unlinks)
            assertFailsWith<IllegalStateException> { settings.bindAccount("next-owner") }
            val owner = settings.state.first()
            assertFailsWith<IllegalStateException> {
                settings.pendingInvites.saveAcceptance(PendingAcceptance("token", 1, owner.userId, owner.deviceId, "partner", "keys"))
            }
            db.close(); scope.coroutineContext[Job]!!.cancelAndJoin()
            db = open(); scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            settings = settings(scope)
            db.useWriterConnection { tx -> tx.immediateTransaction {
                usePrepared("DROP TRIGGER reject_cleanup") { it.step() }
            } }
            val replacement = CoupleEntity("next-couple", "owner", "next-partner", 9, 900)
            if (replaceRelationship) db.useWriterConnection { tx -> tx.immediateTransaction {
                db.coupleDao().clear()
                db.coupleDao().upsert(replacement)
            } }
            val recovered = UnlinkService(db, InMemoryKeyVault(), settings, server) { error("KEEP must not wipe files") }
            if (replaceRelationship) {
                assertFailsWith<IllegalStateException> { recovered.resumePending() }
                assertEquals(0, server.unlinks)
                assertEquals(replacement, db.coupleDao().get())
                assertFalse(recovered.resumePending())
            } else {
                assertEquals(AfterUnlink.KEEP_AS_MEMORIES, recovered.unlink(AfterUnlink.WIPE))
                assertEquals(1, server.unlinks)
                assertEquals("", db.coupleDao().get()?.partnerUserId)
            }
            assertNull(db.cleanupDao().get())
            assertNull(settings.cleanupRequest())
            assertNotNull(db.letterDao().get(letterId))
            assertTrue(fs.exists(root / "attachments" / "photo"))
            settings.bindAccount("next-owner")
        } finally { db.close(); scope.coroutineContext[Job]!!.cancelAndJoin(); fs.deleteRecursively(root) }
    }

    @Test
    fun `정리 중 쓰기 거부는 화면 작업을 취소하고 미처리 예외로 전파되지 않는다`(): Unit = runBlocking {
        fs.createDirectories(root)
        val failures = java.util.concurrent.CopyOnWriteArrayList<Throwable>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, error -> failures.add(error) })
        val db = open()
        try {
            val settings = settings(scope)
            prepare(db, settings)
            val state = settings.state.first()
            val identity = object : Identity {
                override val userId = state.userId
                override val deviceId = state.deviceId
                override val keyEpoch = state.keyEpoch
            }
            assertFailsWith<IllegalStateException> {
                UnlinkService(db, InMemoryKeyVault(), settings, Server()) { error("disk busy") }.unlink(AfterUnlink.WIPE)
            }
            val attachments = AttachmentStore(db, root / "attachments")
            val writer = SyncWriter(db, identity)
            val writes = listOf(
                scope.launch { MissYouRepository(db, writer, identity).send(); fail("write should be cancelled") },
                scope.launch { attachments.put(com.sadturtleman.flowershoes.core.model.AttachmentKind.PHOTO, byteArrayOf(1)); fail("write should be cancelled") },
            )
            writes.joinAll()
            assertTrue(writes.all { it.isCancelled })
            assertTrue(failures.isEmpty(), "예상된 쓰기 취소가 앱 예외 처리기로 올라가면 안 된다: $failures")
            assertNotNull(db.cleanupDao().get())
        } finally { db.close(); scope.coroutineContext[Job]!!.cancelAndJoin(); fs.deleteRecursively(root) }
    }

    @Test
    fun `미완료 정리는 새 쓰기를 막고 백그라운드 동기화가 먼저 재개한다`() = runBlocking {
        fs.createDirectories(root)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val db = open()
        try {
            Sodium.init()
            val settings = settings(scope)
            prepare(db, settings)
            val state = settings.state.first()
            val identity = object : Identity {
                override val userId = state.userId
                override val deviceId = state.deviceId
                override val keyEpoch = state.keyEpoch
            }
            val server = Server()
            val vault = InMemoryKeyVault()
            assertFailsWith<IllegalStateException> {
                UnlinkService(db, vault, settings, server) { error("disk busy") }.unlink(AfterUnlink.WIPE)
            }
            val attachments = AttachmentStore(db, root / "attachments")
            val writer = SyncWriter(db, identity)
            assertFailsWith<IllegalStateException> { attachments.put(com.sadturtleman.flowershoes.core.model.AttachmentKind.PHOTO, byteArrayOf(1)) }
            assertFailsWith<IllegalStateException> { MissYouRepository(db, writer, identity).send() }
            assertFailsWith<IllegalStateException> { vault.forDatabase(db, identity).putDeviceKeys(DeviceKeys.generate()) }
            assertFailsWith<IllegalStateException> { settings.bindAccount("different-owner") }
            assertFailsWith<IllegalStateException> {
                settings.pendingInvites.saveAcceptance(PendingAcceptance("token", 1, state.userId, state.deviceId, "partner", "keys"))
            }
            val recovered = UnlinkService(db, vault, settings, null) { fs.deleteRecursively(root / "attachments") }
            val cloud = InMemoryCloud()
            val client = SyncClient(db, identity, vault, cloud, cloud, attachments, InboxApplier(db), writer)
            assertNotNull(SyncScheduler(db, client, resumeCleanup = { recovered.resumePending() }).runOnce())
            assertNull(db.cleanupDao().get())
            assertFalse(fs.exists(root / "attachments"))
            assertEquals(1, server.unlinks)
            assertNotNull(settings.draftSessions.first())
            Unit
        } finally { db.close(); scope.coroutineContext[Job]!!.cancelAndJoin(); fs.deleteRecursively(root) }
    }

    @Test
    fun `수락 요청이 서버에서 진행 중이면 이전 연결 정리를 시작하지 않는다`() = runBlocking {
        fs.createDirectories(root)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val db = open()
        try {
            Sodium.init()
            val settings = settings(scope)
            prepare(db, settings)
            db.coupleDao().upsert(db.coupleDao().get()!!.copy(partnerUserId = "", keyEpoch = 0))
            val state = settings.state.first()
            val identity = object : Identity {
                override val userId = state.userId
                override val deviceId = state.deviceId
                override val keyEpoch = 0
            }
            val cloud = InMemoryCloud().also { it.holdAccepts = true }
            val token = InviteToken.random()
            cloud.createInvite(token, Invite("next-partner", "next-device", Pairing.PublicKeys.of(DeviceKeys.generate()), "partner", Long.MAX_VALUE))
            val vault = InMemoryKeyVault()
            val pairing = PairingSession(db, identity, vault, cloud, pending = settings.pendingInvites)
            assertIs<PairingState.Accepting>(pairing.accept(token))
            val server = Server()
            assertFailsWith<IllegalStateException> { UnlinkService(db, vault, settings, server).unlink(AfterUnlink.WIPE) }
            assertNull(db.cleanupDao().get())
            assertEquals(0, server.unlinks)
            cloud.releaseAccepts()
            assertIs<PairingState.Linked>(pairing.poll())
            assertEquals("next-partner", db.coupleDao().get()?.partnerUserId)
            assertTrue(fs.exists(root / "attachments" / "photo"))
        } finally { db.close(); scope.coroutineContext[Job]!!.cancelAndJoin(); fs.deleteRecursively(root) }
    }

    private fun settingsFailure(finalRelease: Boolean): Unit = runBlocking {
        fs.createDirectories(root)
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var db = open()
        try {
            val original = PreferenceDataStoreFactory.createWithPath(scope = scope) { root / "settings.preferences_pb" }
            var fail = true
            val token = stringPreferencesKey("relationship_cleanup_token")
            val finished = stringPreferencesKey("relationship_cleanup_finished")
            val failingStore = object : DataStore<Preferences> by original {
                override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = original.updateData { before ->
                    val after = transform(before)
                    val matches = if (finalRelease) before[finished] != null && after[token] == null else before[finished] == null && after[finished] != null
                    if (fail && matches) { fail = false; error("injected settings commit failure") }
                    after
                }
            }
            var settings = DeviceSettings(failingStore)
            prepare(db, settings)
            val server = Server()
            val vault = InMemoryKeyVault()
            var wipes = 0
            val first = UnlinkService(db, vault, settings, server) { wipes++ }
            assertFailsWith<IllegalStateException> { first.unlink(AfterUnlink.WIPE) }
            assertEquals(1, wipes)
            db.close()
            scope.coroutineContext[Job]!!.cancelAndJoin()
            db = open()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            settings = settings(scope)
            UnlinkService(db, vault, settings, null) { wipes++ }.resumePending()
            assertEquals(1, server.unlinks)
            assertEquals(1, wipes, "설정 저장만 실패했으면 파일을 다시 지우지 않는다")
            assertEquals(0, settings.state.first().keyEpoch)
            assertNull(db.cleanupDao().get())
            settings.bindAccount("next-owner")
            assertNotNull(settings.draftSessions.first(), "마지막 정리 표식도 해제되어야 한다")
        } finally {
            db.close(); scope.coroutineContext[Job]!!.cancelAndJoin(); fs.deleteRecursively(root)
        }
    }

    @Test fun `최종 설정 저장 실패는 파일 삭제를 반복하지 않고 재시작 후 복구한다`() = settingsFailure(finalRelease = false)
    @Test fun `DB 작업 제거 후 설정 실패도 재시작 후 잠금을 해제한다`() = settingsFailure(finalRelease = true)

    @Test
    fun `파일 삭제 실패 뒤 DB와 설정을 다시 열어도 원래 WIPE를 끝낸다`() = runBlocking {
        fs.createDirectories(root)
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var db = open()
        try {
            var settings = settings(scope)
            prepare(db, settings)
            val server = Server()
            val vault = InMemoryKeyVault()
            assertFailsWith<IllegalStateException> {
                UnlinkService(db, vault, settings, server) { error("injected file deletion failure") }.unlink(AfterUnlink.WIPE)
            }
            assertTrue(fs.exists(root / "attachments" / "photo"))
            db.close()
            scope.coroutineContext[Job]!!.cancelAndJoin()
            db = open()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            settings = settings(scope)
            // The server already succeeded; retry must use the durable choice even without transport.
            UnlinkService(db, vault, settings, null) { fs.deleteRecursively(root / "attachments") }
                .unlink(AfterUnlink.KEEP_AS_MEMORIES)
            assertFalse(fs.exists(root / "attachments" / "photo"))
            assertEquals(0, settings.state.first().keyEpoch)
            assertEquals(1, server.unlinks)
        } finally {
            db.close(); scope.coroutineContext[Job]!!.cancelAndJoin(); fs.deleteRecursively(root)
        }
    }

    @Test
    fun `키 정리 실패 뒤 다시 열어도 KEEP 선택을 WIPE로 바꾸지 않는다`() = runBlocking {
        fs.createDirectories(root)
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var db = open()
        try {
            var settings = settings(scope)
            val letterId = prepare(db, settings)
            val server = Server()
            var fail = true
            val vault = object : KeyVault by InMemoryKeyVault() {
                override suspend fun clearCoupleKeys() {
                    if (fail) { fail = false; error("injected key cleanup failure") }
                }
            }
            assertFailsWith<IllegalStateException> {
                UnlinkService(db, vault, settings, server).unlink(AfterUnlink.KEEP_AS_MEMORIES)
            }
            db.close()
            scope.coroutineContext[Job]!!.cancelAndJoin()
            db = open()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            settings = settings(scope)
            var wiped = false
            UnlinkService(db, vault, settings, server) { wiped = true }.unlink(AfterUnlink.WIPE)
            assertNotNull(db.letterDao().get(letterId))
            assertFalse(wiped)
            assertEquals(1, server.unlinks)
            assertEquals(0, settings.state.first().keyEpoch)
        } finally {
            db.close(); scope.coroutineContext[Job]!!.cancelAndJoin(); fs.deleteRecursively(root)
        }
    }

    @Test
    fun `늦은 정리가 다른 DB 인스턴스의 새 관계 파일과 설정을 지우지 않는다`() = runBlocking {
        fs.createDirectories(root)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val db = open()
        val other = open()
        try {
            val settings = settings(scope)
            prepare(db, settings)
            val reached = CompletableDeferred<Unit>()
            val resume = CompletableDeferred<Unit>()
            val vault = object : KeyVault by InMemoryKeyVault() {
                override suspend fun clearCoupleKeys() { reached.complete(Unit); resume.await() }
            }
            withTimeout(15_000) {
                val oldCleanup = async {
                    runCatching {
                        UnlinkService(db, vault, settings, Server()) { fs.deleteRecursively(root / "attachments") }.unlink(AfterUnlink.WIPE)
                    }
                }
                try {
                    reached.await()
                    val replacement = async {
                        other.coupleDao().upsert(CoupleEntity("new-couple", "owner", "new-partner", 9, 200))
                        fs.createDirectories(root / "attachments")
                        fs.write(root / "attachments" / "new-photo") { writeUtf8("new photo") }
                        settings.setKeyEpoch(9)
                    }
                    // Old code lets this complete during cleanup. A SQLite boundary may correctly hold it.
                    withTimeoutOrNull(200) { replacement.await() }
                    resume.complete(Unit)
                    oldCleanup.await()
                    replacement.await()
                } finally { resume.complete(Unit) }
            }
            assertTrue(fs.exists(root / "attachments" / "new-photo"))
            assertEquals(9, settings.state.first().keyEpoch)
            assertEquals("new-couple", other.coupleDao().get()?.id)
        } finally {
            other.close(); db.close(); scope.coroutineContext[Job]!!.cancelAndJoin(); fs.deleteRecursively(root)
        }
    }
}
