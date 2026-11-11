package com.sadturtleman.flowershoes.core.widget

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.sadturtleman.flowershoes.core.data.*
import com.sadturtleman.flowershoes.core.database.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import okio.FileSystem
import kotlin.test.*
import kotlin.time.Clock
import kotlin.uuid.Uuid

class WidgetAccountSessionTest {
    @Test fun `위젯 배치 조회가 늦게 끝나도 새 계정의 표시 시간을 지우지 않는다`(): Unit = runBlocking {
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "widget-late-placement-${Uuid.random()}"
        val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val db = inMemoryDatabaseBuilder().buildKkotsin()
        val release = CompletableDeferred<Unit>()
        var publishing: Job? = null
        try {
            val actual = PreferenceDataStoreFactory.createWithPath(scope = io) { root / "settings.preferences_pb" }
            val completedWrites = Channel<Unit>(Channel.UNLIMITED)
            val settings = DeviceSettings(object : DataStore<Preferences> {
                override val data = actual.data
                override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                    try { actual.updateData(transform) } finally { completedWrites.trySend(Unit) }
            })
            settings.ensureIdentity(); settings.bindAccount("a"); settings.setOnboarded()
            val identity = IdentityHolder().apply { update(settings.state.first()) }
            val writer = SyncWriter(db, identity)
            val store = SnapshotStore(root / "widget/snapshot.json")
            db.coupleDao().upsert(CoupleEntity("couple-a", "a", "partner-a", 1, 100))
            val querying = CompletableDeferred<Unit>()
            publishing = WidgetSnapshotPublisher(db, settings, ProfileRepository(db, writer, identity),
                EventRepository(db, writer, identity), StatusRepository(db, writer, identity),
                MissYouRepository(db, writer, identity), store, root / "attachments").start(io, drawingWidgetPlaced = {
                querying.complete(Unit); release.await(); false
            }, onUpdated = {})
            withTimeout(10_000) { querying.await() }

            settings.bindAccount("b") // Room의 a 관계가 아직 남은 별도 Flow 갱신 구간.
            val now = Clock.System.now().toEpochMilliseconds()
            settings.widgetShowing("new-account-drawing", now)
            store.write(WidgetSnapshot(theme = "GOMSIN", partnerName = "new snapshot", updatedAtMs = now))
            while (completedWrites.tryReceive().isSuccess) Unit
            release.complete(Unit)
            withTimeout(5_000) { completedWrites.receive() }

            assertEquals(1_000L, settings.widgetShownMs { now + 1_000 }.first()["new-account-drawing"])
            assertEquals("new snapshot", store.read()?.partnerName)
            assertTrue(publishing.isActive)
        } finally {
            release.complete(Unit)
            publishing?.cancelAndJoin()
            io.coroutineContext[Job]!!.cancelAndJoin()
            db.close()
            if (FileSystem.SYSTEM.exists(root)) FileSystem.SYSTEM.deleteRecursively(root)
        }
    }

    @Test fun `같은 발행기를 계속 사용해도 새 계정의 보낸 횟수로 전환한다`(): Unit = runBlocking {
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "widget-session-${Uuid.random()}"
        val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val db = inMemoryDatabaseBuilder().buildKkotsin()
        var publishing: Job? = null
        try {
            val settings = DeviceSettings(PreferenceDataStoreFactory.createWithPath(scope = io) { root / "settings.preferences_pb" })
            settings.ensureIdentity(); settings.bindAccount("a"); settings.setOnboarded()
            val identity = IdentityHolder().apply { update(settings.state.first()) }
            val writer = SyncWriter(db, identity)
            val profile = ProfileRepository(db, writer, identity)
            val store = SnapshotStore(root / "widget/snapshot.json")
            val snapshots = Channel<WidgetSnapshot>(Channel.UNLIMITED)
            db.userDao().upsert(UserEntity("partner-a", "GOMSIN", "Partner A", 0))
            db.userDao().upsert(UserEntity("partner-b", "GOMSIN", "Partner B", 0))
            val now = Clock.System.now().toEpochMilliseconds()
            db.missYouDao().insert(MissYouEntity("a-1", "a", now))
            db.missYouDao().insert(MissYouEntity("b-1", "b", now))
            db.missYouDao().insert(MissYouEntity("b-2", "b", now))
            db.coupleDao().upsert(CoupleEntity("couple-a", "a", "partner-a", 1, 100))
            publishing = WidgetSnapshotPublisher(db, settings, profile,
                EventRepository(db, writer, identity), StatusRepository(db, writer, identity),
                MissYouRepository(db, writer, identity), store, root / "attachments").start(io) {
                snapshots.send(assertNotNull(store.read()))
            }
            suspend fun nextFor(name: String): WidgetSnapshot = withTimeout(10_000) {
                var snapshot = snapshots.receive()
                while (snapshot.partnerName != name) snapshot = snapshots.receive()
                snapshot
            }
            assertEquals(1, nextFor("Partner A").missYouSentToday)

            settings.bindAccount("b")
            // 신원 캐시가 아직 a여도 실제 DataStore 계정 b로 집계를 다시 구독한다.
            db.coupleDao().clear()
            db.coupleDao().upsert(CoupleEntity("couple-b", "b", "partner-b", 1, 200))

            assertEquals(2, nextFor("Partner B").missYouSentToday)
        } finally {
            publishing?.cancelAndJoin()
            io.coroutineContext[Job]!!.cancelAndJoin()
            db.close()
            if (FileSystem.SYSTEM.exists(root)) FileSystem.SYSTEM.deleteRecursively(root)
        }
    }
}
