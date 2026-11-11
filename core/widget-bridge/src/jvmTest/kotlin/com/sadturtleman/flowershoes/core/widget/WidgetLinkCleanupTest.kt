package com.sadturtleman.flowershoes.core.widget

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.sadturtleman.flowershoes.core.data.DeviceSettings
import com.sadturtleman.flowershoes.core.data.sync.*
import com.sadturtleman.flowershoes.core.database.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okio.FileSystem
import kotlin.test.*
import kotlin.uuid.Uuid

class WidgetLinkCleanupTest {
    private class Fixture {
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "widget-cleanup-${Uuid.random()}"
        val fs = FileSystem.SYSTEM
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val db = inMemoryDatabaseBuilder().buildKkotsin()
        val settings = DeviceSettings(PreferenceDataStoreFactory.createWithPath(scope = scope) { root / "settings.preferences_pb" })
        val store = SnapshotStore(root / "widget/snapshot.json")
        val vault = InMemoryKeyVault()
        var deletions = 0
        val server = object : AccountTransport {
            override suspend fun unlinkCouple(coupleId: String) = Unit
            override suspend fun deleteAccount() { deletions++ }
        }
        suspend fun prepare() {
            settings.ensureIdentity(); settings.bindAccount("a"); settings.setOnboarded()
            db.coupleDao().upsert(CoupleEntity("couple-a", "a", "partner", 1, 100))
            store.write(WidgetSnapshot(theme = "GOMSIN", linked = true,
                owner = WidgetOwner.from(settings.state.first(), db.coupleDao().get()), updatedAtMs = 1))
            fs.write(root / "link.json") { writeUtf8("old link") }
        }
        fun service(invalidate: suspend () -> Unit = { invalidateWidgetFiles(root) }) =
            UnlinkService(db, vault, settings, server, invalidateWidget = invalidate)
        suspend fun close() {
            scope.coroutineContext[Job]!!.cancelAndJoin(); db.close()
            if (fs.exists(root)) fs.deleteRecursively(root)
        }
    }

    @Test fun `보관과 지우기와 탈퇴는 새 발행기를 기다리지 않고 확장 파일을 무효화한다`(): Unit = runBlocking {
        for (choice in listOf(AfterUnlink.WIPE, AfterUnlink.KEEP_AS_MEMORIES, null)) {
            val f = Fixture()
            try {
                f.prepare()
                if (choice == null) f.service().deleteAccount() else f.service().unlink(choice)
                assertNull(f.store.read(), "cleanup=$choice")
                assertFalse(f.fs.exists(f.root / "link.json"))
                assertNull(f.db.cleanupDao().get())
            } finally { f.close() }
        }
    }

    @Test fun `확장 파일 무효화 실패는 정리 기록에 남고 새 서비스가 재시도한다`(): Unit = runBlocking {
        val f = Fixture()
        try {
            f.prepare()
            assertFailsWith<IllegalStateException> {
                f.service { f.store.clear(); error("link file busy") }.deleteAccount()
            }
            assertNotNull(f.db.cleanupDao().get())
            assertNull(f.store.read())
            assertTrue(f.fs.exists(f.root / "link.json"))
            assertTrue(f.service().resumePending())
            assertEquals(1, f.deletions)
            assertNull(f.db.cleanupDao().get())
            assertFalse(f.fs.exists(f.root / "link.json"))
        } finally { f.close() }
    }

    @Test fun `계정 B와 이전 A 관계가 함께 보이면 이전 링크를 지우고 새 관계만 발행한다`(): Unit = runBlocking {
        val f = Fixture()
        try {
            f.prepare(); f.settings.bindAccount("b")
            publishWidgetLink(f.db, f.settings, f.root) { _, _ -> error("foreign relationship") }
            assertNull(f.store.read())
            assertFalse(f.fs.exists(f.root / "link.json"))
            f.db.coupleDao().clear()
            f.db.coupleDao().upsert(CoupleEntity("couple-b", "b", "partner-b", 1, 200))
            publishWidgetLink(f.db, f.settings, f.root) { account, couple ->
                assertEquals("b", account.userId); assertEquals("couple-b", couple.id)
                "new link"
            }
            assertEquals("new link", f.fs.read(f.root / "link.json") { readUtf8() })
        } finally { f.close() }
    }

    @Test fun `링크를 발행하지 못해도 현재 소유자의 스냅샷과 혼자 Dday는 유지한다`(): Unit = runBlocking {
        val f = Fixture()
        try {
            f.prepare()
            val linked = f.store.read()
            publishWidgetLink(f.db, f.settings, f.root) { _, _ -> null } // 상대 기기 정보가 아직 없음.
            assertEquals(linked, f.store.read())
            assertFalse(f.fs.exists(f.root / "link.json"))
            f.db.coupleDao().clear()
            val solo = WidgetSnapshot(theme = "GOMSIN", ddayValue = "D-100", updatedAtMs = 2)
            f.store.write(solo)
            publishWidgetLink(f.db, f.settings, f.root) { _, _ -> error("solo") }
            assertEquals(solo, f.store.read())
        } finally { f.close() }
    }

    @Test fun `중간 상태 없이 새 관계 링크를 발행해도 이전 스냅샷 권한은 먼저 지운다`(): Unit = runBlocking {
        val f = Fixture()
        try {
            f.prepare(); f.settings.bindAccount("b")
            f.db.coupleDao().clear()
            f.db.coupleDao().upsert(CoupleEntity("couple-b", "b", "partner-b", 1, 200))
            publishWidgetLink(f.db, f.settings, f.root) { _, _ -> "new link" }
            assertNull(f.store.read())
            assertEquals("new link", f.fs.read(f.root / "link.json") { readUtf8() })
            val fresh = WidgetSnapshot(theme = "GOMSIN", linked = true,
                owner = WidgetOwner.from(f.settings.state.first(), f.db.coupleDao().get()), updatedAtMs = 2)
            f.store.write(fresh)
            publishWidgetLink(f.db, f.settings, f.root) { _, _ -> "new link" }
            assertEquals(fresh, f.store.read())
        } finally { f.close() }
    }
}
