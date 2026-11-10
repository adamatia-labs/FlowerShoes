package com.sadturtleman.flowershoes.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
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
                reached.await()
                other.coupleDao().upsert(CoupleEntity("new-couple", "owner", "new-partner", 9, 200))
                settings.setKeyEpoch(9)
                fs.write(root / "attachments" / "new-photo") { writeUtf8("new photo") }
                resume.complete(Unit)
                oldCleanup.await()
            }
            assertTrue(fs.exists(root / "attachments" / "new-photo"))
            assertEquals(9, settings.state.first().keyEpoch)
            assertEquals("new-couple", other.coupleDao().get()?.id)
        } finally {
            other.close(); db.close(); scope.coroutineContext[Job]!!.cancelAndJoin(); fs.deleteRecursively(root)
        }
    }
}
