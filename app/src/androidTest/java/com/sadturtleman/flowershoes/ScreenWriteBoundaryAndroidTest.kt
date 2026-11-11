package com.sadturtleman.flowershoes

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sadturtleman.flowershoes.app.*
import com.sadturtleman.flowershoes.core.data.*
import com.sadturtleman.flowershoes.core.data.sync.*
import com.sadturtleman.flowershoes.core.database.*
import com.sadturtleman.flowershoes.core.model.*
import com.sadturtleman.flowershoes.feature.calendar.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import okio.Path.Companion.toOkioPath
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.compose.KoinIsolatedContext
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.dsl.koinApplication
import java.io.File
import java.util.UUID
import kotlin.time.Clock

/** Keep the old composition deliberately alive while real cleanup finishes; cancellation is not the guard. */
@RunWith(AndroidJUnit4::class)
class ScreenWriteBoundaryAndroidTest {
    @get:Rule val compose = createComposeRule()

    @Test fun realViewModelRejectsOldDraftAndNewScreenCanSave(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = File(context.cacheDir, "screen-boundary-${UUID.randomUUID()}").apply { mkdirs() }
        val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val prefs = PreferenceDataStoreFactory.create(scope = io) { File(root, "settings.preferences_pb") }
        val db = Room.databaseBuilder<AppDatabase>(context, File(root, "app.db").absolutePath).buildKkotsin()
        val application = koinApplication { modules(kkotsinModules(db, prefs, root.toOkioPath() / "attachments")) }
        val mounted = mutableStateOf(true)
        try {
            val settings = application.koin.get<DeviceSettings>()
            settings.ensureIdentity(); settings.bindAccount("owner"); settings.setOnboarded(); settings.setKeyEpoch(1)
            val account = mutableStateOf(settings.state.first())
            application.koin.get<IdentityHolder>().update(account.value)
            val rootEvents = application.koin.get<EventRepository>()
            rootEvents.save(Event("seed", EventType.VISIT, "seed", LocalDateTime(2026, 11, 1, 10, 0),
                LocalDateTime(2026, 11, 1, 12, 0), false, Clock.System.now(), 0, account.value.deviceId))
            val couple = mutableStateOf(CoupleEntity("old-couple", "owner", "partner", 1, 100))
            db.coupleDao().upsert(couple.value)
            var vm: CalendarViewModel? = null
            var data: ScreenData? = null
            compose.setContent {
                if (mounted.value) KoinIsolatedContext(application) {
                    RelationshipScreenScope(account.value, couple.value) {
                        ScreenDataScope(account.value, couple.value) {
                            val editor = koinViewModel<CalendarViewModel>()
                            val captured = koinInject<ScreenData>()
                            SideEffect { vm = editor; data = captured }
                            Text("editor")
                        }
                    }
                }
            }
            compose.waitForIdle()
            val old = checkNotNull(vm)
            assertNotSame(rootEvents, checkNotNull(data).events)
            assertSame(rootEvents, application.koin.get<EventRepository>())
            compose.runOnIdle { old.onIntent(CalendarIntent.ChangeDraft(draft("old relationship"))) }
            application.koin.get<UnlinkService>().onPartnerUnlinked(AfterUnlink.WIPE, "old-couple")
            val next = CoupleEntity("new-couple", "owner", "new-partner", 1, 200)
            db.coupleDao().upsert(next)
            assertNull(db.cleanupDao().get())
            saveAndJoin(old)
            assertTrue(db.eventDao().all().isEmpty())
            assertTrue(db.syncDao().log("${account.value.deviceId}:app").isEmpty())

            val nextAccount = settings.state.first()
            application.koin.get<IdentityHolder>().update(nextAccount)
            compose.runOnIdle { account.value = nextAccount; couple.value = next }
            compose.waitForIdle()
            val fresh = checkNotNull(vm)
            assertNotSame(old, fresh)
            compose.runOnIdle { fresh.onIntent(CalendarIntent.ChangeDraft(draft("new relationship"))) }
            saveAndJoin(fresh)
            assertEquals("new relationship", db.eventDao().all().single().title)
        } finally {
            compose.runOnIdle { mounted.value = false }
            application.close(); io.cancel(); db.close(); root.deleteRecursively()
        }
    }

    private suspend fun saveAndJoin(vm: CalendarViewModel) {
        var saves: List<Job> = emptyList()
        compose.runOnIdle {
            val parent = checkNotNull(vm.viewModelScope.coroutineContext[Job])
            val before = parent.children.toSet()
            vm.onIntent(CalendarIntent.SaveDraft)
            saves = parent.children.filterNot { it in before }.toList()
        }
        withTimeout(10_000) { saves.forEach { it.join() } }
    }

    private fun draft(title: String) = EventDraft(null, title = title, start = LocalDate(2026, 11, 12), end = LocalDate(2026, 11, 12))
}
