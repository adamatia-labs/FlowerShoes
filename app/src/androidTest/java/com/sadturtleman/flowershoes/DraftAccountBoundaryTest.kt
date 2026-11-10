package com.sadturtleman.flowershoes

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sadturtleman.flowershoes.core.data.*
import com.sadturtleman.flowershoes.core.database.*
import com.sadturtleman.flowershoes.core.designsystem.KkotsinTheme
import com.sadturtleman.flowershoes.core.logging.*
import com.sadturtleman.flowershoes.feature.letter.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okio.Path.Companion.toOkioPath
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Actual Android Room/DataStore, production ViewModel, rendered editor and DB reopen. */
@RunWith(AndroidJUnit4::class)
class DraftAccountBoundaryTest {
    @get:Rule val compose = createComposeRule()

    @Test fun accountChangeClearsRenderedEditorAndPreservesOriginalPendingInput(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val root = File(context.cacheDir, "draft-boundary-${UUID.randomUUID()}").apply { mkdirs() }
        val settingsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val settings = DeviceSettings(PreferenceDataStoreFactory.createWithPath(scope = settingsScope) { root.toOkioPath() / "prefs.preferences_pb" })
        fun openDb() = Room.databaseBuilder<AppDatabase>(context, File(root, "drafts.db").absolutePath).buildKkotsin()
        var db = openDb()
        var vm: LetterViewModel? = null
        try {
            settings.ensureIdentity(); settings.bindAccount("owner-a")
            val identity = object : Identity {
                // Deliberately retain the old cache: draft authorization must use persisted settings.
                override val userId = "owner-a"
                override val deviceId = "cached-device"
                override val keyEpoch = 0
            }
            val writer = SyncWriter(db, identity)
            val drafts = RoomLetterDraftStore(db, settings)
            val screen = withContext(Dispatchers.Main) {
                LetterViewModel(LetterRepository(db, writer, identity), AttachmentStore(db, root.toOkioPath() / "attachments"),
                    ProfileRepository(db, writer, identity), drafts, bizLogger = object : BizLogger {
                        override fun init() = Unit
                        override fun destroy() = Unit
                        override fun makeUUID() = "test"
                        override fun record(viewName: String, event: BizEvent) = Unit
                    })
            }
            vm = screen
            compose.setContent {
                val state by screen.state.collectAsState()
                KkotsinTheme(com.sadturtleman.flowershoes.core.model.AppTheme.GOMSIN) { LetterComposeScreen(state, false, false, screen::onIntent, {}, {}) }
            }
            compose.waitUntil(15_000) { screen.state.value.draftReady && !screen.state.value.loading }
            val oldId = screen.state.value.draftId
            compose.runOnIdle { screen.onIntent(LetterIntent.EditText("A_PRIVATE_PENDING_TEXT")) }
            compose.onNodeWithText("A_PRIVATE_PENDING_TEXT", useUnmergedTree = true).assertExists()
            settings.bindAccount("owner-b")
            compose.waitUntil(15_000) { screen.state.value.draftId != oldId && screen.state.value.draftReady }
            compose.onNodeWithText("A_PRIVATE_PENDING_TEXT", useUnmergedTree = true).assertDoesNotExist()
            compose.runOnIdle { screen.onIntent(LetterIntent.AddPhoto(byteArrayOf(1, 2, 3), oldId)) }
            assertTrue(screen.state.value.draft.photos.isEmpty())
            withTimeout(10_000) { while (db.letterDraftDao().get(oldId)?.draftJson?.contains("A_PRIVATE_PENDING_TEXT") != true) delay(20) }
            assertTrue(drafts.observe().first { it.loaded }.drafts.isEmpty())
            screen.viewModelScope.coroutineContext[Job]!!.cancelAndJoin()
            db.close(); db = openDb()
            assertTrue(RoomLetterDraftStore(db, settings).observe().first { it.loaded }.drafts.isEmpty())
            settings.bindAccount("owner-a")
            val restored = RoomLetterDraftStore(db, settings).observe().first { it.loaded }.drafts
            assertEquals(oldId, restored.single().id)
            assertTrue(restored.single().json.contains("A_PRIVATE_PENDING_TEXT"))
        } finally {
            vm?.viewModelScope?.coroutineContext?.get(Job)?.cancelAndJoin()
            settingsScope.coroutineContext[Job]!!.cancelAndJoin()
            db.close(); root.deleteRecursively()
        }
    }
}
