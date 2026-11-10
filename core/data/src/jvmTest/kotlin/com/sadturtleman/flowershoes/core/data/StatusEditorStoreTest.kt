package com.sadturtleman.flowershoes.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.sadturtleman.flowershoes.core.database.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okio.FileSystem
import kotlin.test.*
import kotlin.uuid.Uuid

class StatusEditorStoreTest {
    private val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "status-editor-${Uuid.random()}"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val settings = DeviceSettings(PreferenceDataStoreFactory.createWithPath(scope = scope) { root / "settings.preferences_pb" })
    private val db = Room.databaseBuilder<AppDatabase>((root / "status.db").toString()).buildKkotsin()
    private val identity = IdentityHolder()
    private val statuses = StatusRepository(db, SyncWriter(db, identity), identity)
    private val editor = StatusEditorStore(db, settings, identity, statuses)

    @BeforeTest fun prepare(): Unit = runBlocking {
        settings.ensureIdentity(); settings.bindAccount("owner"); settings.setKeyEpoch(1)
        identity.update(settings.state.first())
        db.coupleDao().upsert(CoupleEntity("old-couple", "owner", "partner", 1, 100))
    }

    @AfterTest fun close(): Unit = runBlocking {
        db.close(); scope.coroutineContext[Job]!!.cancelAndJoin(); FileSystem.SYSTEM.deleteRecursively(root)
    }

    @Test fun `열었던 계정과 연결에 상태를 저장하고 지운다`(): Unit = runBlocking {
        val session = assertNotNull(editor.open("owner"))
        editor.save(session, "  당직 중  ", 1, null)
        assertEquals("당직 중", editor.observe("owner").first()?.label)
        editor.clear(session)
        assertNull(editor.observe("owner").first())
        assertEquals(2, db.statusDao().all().size)
        assertEquals(listOf("status", "status"), db.syncDao().log("${identity.deviceId}:app").map { it.kind })
    }

    @Test fun `캐시가 이전 계정이어도 설정 계정이 바뀌면 저장하지 않는다`(): Unit = runBlocking {
        val session = assertNotNull(editor.open("owner"))
        settings.bindAccount("other-owner")
        assertFailsWith<StatusEditChangedException> { editor.save(session, "old input", 1, null) }
        assertTrue(db.statusDao().all().isEmpty())
    }

    @Test fun `다른 계정을 거쳐 같은 계정으로 돌아와도 이전 편집 권한을 재사용하지 않는다`(): Unit = runBlocking {
        val session = assertNotNull(editor.open("owner"))
        settings.bindAccount("other-owner"); settings.bindAccount("owner")
        identity.update(settings.state.first())
        assertFailsWith<StatusEditChangedException> { editor.save(session, "old input", 1, null) }
        assertTrue(db.statusDao().all().isEmpty())
    }

    @Test fun `다른 DB 연결에서 새 관계를 커밋하면 이전 입력과 지우기를 거부한다`(): Unit = runBlocking {
        val session = assertNotNull(editor.open("owner"))
        val other = Room.databaseBuilder<AppDatabase>((root / "status.db").toString()).buildKkotsin()
        try {
            other.useWriterConnection { tx -> tx.immediateTransaction {
                other.coupleDao().clear()
                other.coupleDao().upsert(CoupleEntity("new-couple", "owner", "new-partner", 1, 200))
            } }
            assertFailsWith<StatusEditChangedException> { editor.save(session, "old input", 1, null) }
            assertFailsWith<StatusEditChangedException> { editor.clear(session) }
            assertTrue(db.statusDao().all().isEmpty())
            assertTrue(db.syncDao().log("${identity.deviceId}:app").isEmpty())
        } finally { other.close() }
    }
}
