package com.sadturtleman.flowershoes

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import androidx.test.espresso.Espresso
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sadturtleman.flowershoes.core.data.*
import com.sadturtleman.flowershoes.core.database.*
import com.sadturtleman.flowershoes.core.designsystem.KkotsinTheme
import com.sadturtleman.flowershoes.core.model.AppTheme
import com.sadturtleman.flowershoes.core.sync.StreamId
import com.sadturtleman.flowershoes.core.widget.*
import com.sadturtleman.flowershoes.feature.home.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okio.Path.Companion.toOkioPath
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Instant

/** Actual Android UI, Room and DataStore; relay begins after envelope verification/decryption. */
@RunWith(AndroidJUnit4::class)
class CurrentStatusAndroidTest {
    @get:Rule val compose = createComposeRule()
    private val clock = object : Clock { override fun now() = Instant.parse("2026-11-11T09:00:00Z") }

    @Test fun saveAndClearReachPartnersWidgetSnapshot(): Unit = runBlocking {
        val f = Fixture()
        try {
            f.prepare()
            val snapshots = SnapshotStore(f.root.toOkioPath() / "snapshot.json")
            val writer = SyncWriter(f.receiver, f.receiverIdentity)
            WidgetSnapshotPublisher(
                f.receiver, f.receiverSettings,
                ProfileRepository(f.receiver, writer, f.receiverIdentity),
                EventRepository(f.receiver, writer, f.receiverIdentity),
                StatusRepository(f.receiver, writer, f.receiverIdentity, clock),
                MissYouRepository(f.receiver, writer, f.receiverIdentity, clock),
                snapshots, f.root.toOkioPath() / "attachments", clock,
            ).start(f.io, drawingWidgetPlaced = { false }) {}
            render(f)
            open(f)
            compose.onNode(hasSetTextAction()).performTextInput("당직 중")
            Espresso.closeSoftKeyboard()
            compose.onNodeWithText("내일 오전 8시까지").performScrollTo().performClick()
            compose.onNodeWithText("상태 저장").performScrollTo().performClick()
            compose.waitUntil(15_000) { f.editor.state.value == null }
            f.relay()
            withTimeout(15_000) { while (snapshots.read()?.partnerStatus != "당직 중") delay(25) }
            assertEquals("민준은 지금", snapshots.read()?.partnerStatusCaption)
            assertEquals("내일 08:00까지", snapshots.read()?.partnerStatusUntil)
            assertEquals(Instant.parse("2026-11-11T23:00:00Z").toEpochMilliseconds(), f.receiver.statusDao().all().single().expiresAtMs)
            open(f)
            compose.onNodeWithText("상태 지우기").performScrollTo().performClick()
            compose.waitUntil(15_000) { f.editor.state.value == null }
            f.relay()
            withTimeout(15_000) { while (snapshots.read()?.partnerStatus != null) delay(25) }
            assertEquals(2, f.receiver.statusDao().all().size)
            assertNull(snapshots.read()?.partnerStatusUntil)
        } finally { f.close() }
    }

    @Test fun changingAccountWhileEditingRejectsStaleInputOnScreen(): Unit = runBlocking {
        val f = Fixture()
        try {
            f.prepare(); render(f); open(f)
            compose.onNode(hasSetTextAction()).performTextInput("이전 계정의 입력")
            Espresso.closeSoftKeyboard()
            f.settings.bindAccount("another-owner") // Keep the old IdentityHolder deliberately.
            compose.onNodeWithText("상태 저장").performScrollTo().performClick()
            compose.waitUntil(15_000) { f.editor.state.value?.error != null }
            compose.onNodeWithText("계정 또는 연결이 바뀌었어요. 홈에서 다시 열어 주세요").assertExists()
            compose.onNodeWithText("상태 저장").assertIsNotEnabled()
            assertTrue(f.sender.statusDao().all().isEmpty())
            assertTrue(f.sender.syncDao().log("${f.identity.deviceId}:app").isEmpty())
        } finally { f.close() }
    }

    private fun render(f: Fixture) = compose.setContent {
        if (!f.mounted.value) return@setContent
        val state by f.editor.state.collectAsState()
        val current by f.statuses.observeLatest("sender").collectAsState(null)
        KkotsinTheme(AppTheme.GOMSIN) {
            HomeScreen(HomeState(loading = false, linked = true), {}, {},
                currentStatusLabel = current?.label, onOpenStatus = f.editor::open)
            state?.let { CurrentStatusSheet(it, f.editor) }
        }
    }

    @Test fun dismissDuringSaveKeepsFailureVisibleAndAllowsReopening(): Unit = runBlocking {
        val f = Fixture()
        val release = CompletableDeferred<Unit>()
        try {
            f.prepare(); render(f); open(f)
            compose.onNode(hasSetTextAction()).performTextInput("당직 중")
            Espresso.closeSoftKeyboard()
            val entered = CompletableDeferred<Unit>()
            val blocker = async(Dispatchers.IO) {
                f.sender.useWriterConnection { tx -> tx.immediateTransaction {
                    f.sender.coupleDao().clear()
                    f.sender.coupleDao().upsert(CoupleEntity("new-couple", "sender", "new-partner", 1, 200))
                    entered.complete(Unit)
                    release.await()
                } }
            }
            entered.await()
            compose.onNodeWithText("상태 저장").performScrollTo().performClick()
            compose.waitUntil(15_000) { f.editor.state.value?.saving == true }
            // 모델 변경 뒤 저장 중 UI가 반영된 상태에서 시스템 뒤로가기를 검증한다.
            compose.onNodeWithText("저장 중…").performScrollTo().assertIsDisplayed()
            Espresso.pressBack()
            compose.waitForIdle()
            compose.onNodeWithText("저장 중…").performScrollTo().assertIsDisplayed()
            Espresso.pressBack() // Expanded → partially expanded → hidden must also be blocked.
            compose.waitForIdle()
            compose.onNodeWithText("저장 중…").performScrollTo().assertIsDisplayed()
            release.complete(Unit); blocker.await()
            compose.waitUntil(15_000) { f.editor.state.value?.error != null }
            compose.onNodeWithText("계정 또는 연결이 바뀌었어요. 홈에서 다시 열어 주세요").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("닫기").performScrollTo().performClick()
            compose.waitUntil(15_000) { f.editor.state.value == null }
            open(f)
            assertTrue(f.sender.statusDao().all().isEmpty())
        } finally { release.complete(Unit); f.close() }
    }

    private fun open(f: Fixture) {
        compose.onNodeWithText("내 상태").performClick()
        compose.waitUntil(15_000) { f.editor.state.value?.ready == true }
    }

    private inner class Fixture {
        private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val root = File(context.cacheDir, "current-status-${UUID.randomUUID()}").apply { mkdirs() }
        val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val mounted = mutableStateOf(true)
        private val ui = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val sender = Room.databaseBuilder<AppDatabase>(context, File(root, "sender.db").absolutePath).buildKkotsin()
        val receiver = Room.databaseBuilder<AppDatabase>(context, File(root, "receiver.db").absolutePath).buildKkotsin()
        val settings = settings("sender")
        val receiverSettings = settings("receiver")
        val identity = IdentityHolder()
        val receiverIdentity = IdentityHolder()
        val statuses = StatusRepository(sender, SyncWriter(sender, identity), identity, clock)
        val editor = CurrentStatusEditor(ui, StatusEditorStore(sender, settings, identity, statuses, clock), "sender", clock)

        private fun settings(name: String) = DeviceSettings(PreferenceDataStoreFactory.createWithPath(scope = io) {
            root.toOkioPath() / "$name.preferences_pb"
        })

        suspend fun prepare() {
            settings.ensureIdentity(); settings.bindAccount("sender"); settings.setKeyEpoch(1)
            receiverSettings.ensureIdentity(); receiverSettings.bindAccount("receiver"); receiverSettings.setKeyEpoch(1)
            identity.update(settings.state.first()); receiverIdentity.update(receiverSettings.state.first())
            sender.coupleDao().upsert(CoupleEntity("couple", "sender", "receiver", 1, 100))
            receiver.coupleDao().upsert(CoupleEntity("couple", "receiver", "sender", 1, 100))
            receiver.userDao().upsert(UserEntity("sender", "SOLDIER", "민준", 1))
        }

        suspend fun relay() {
            val stream = StreamId.parse("${identity.deviceId}:app")
            sender.syncDao().log(stream.wire).forEach { row ->
                InboxApplier(receiver, clock).apply(ReceivedOp(stream, row.seq, row.opId, row.lamport, row.payload))
            }
        }

        suspend fun close() {
            compose.runOnIdle { mounted.value = false }
            compose.waitForIdle() // Dispose Room collectors before closing the databases.
            ui.coroutineContext[Job]!!.cancelAndJoin(); io.coroutineContext[Job]!!.cancelAndJoin()
            sender.close(); receiver.close(); root.deleteRecursively()
        }
    }
}
