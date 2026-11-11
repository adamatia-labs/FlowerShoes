package com.sadturtleman.flowershoes

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sadturtleman.flowershoes.core.crypto.*
import com.sadturtleman.flowershoes.core.data.*
import com.sadturtleman.flowershoes.core.data.sync.*
import com.sadturtleman.flowershoes.core.database.*
import com.sadturtleman.flowershoes.core.logging.*
import com.sadturtleman.flowershoes.feature.pairing.LinkWatchViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Relationship commit may replace the screen before its settings handoff finishes. */
@RunWith(AndroidJUnit4::class)
class LinkHandoffCancellationTest {
    @Test fun cancelledHandoffResumesAndDialogStaysPendingUntilAcknowledged(): Unit = runBlocking {
        try { Sodium.init() } catch (_: IllegalStateException) {
            check(com.ionspin.kotlin.crypto.LibsodiumInitializer.sodiumJna.sodium_init() == 1)
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = File(context.cacheDir, "link-handoff-${UUID.randomUUID()}").apply { mkdirs() }
        val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val db = Room.databaseBuilder<AppDatabase>(context, File(root, "test.db").absolutePath).buildKkotsin()
        val delegate = PreferenceDataStoreFactory.create(scope = io) { File(root, "test.preferences_pb") }
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var hold = false
        val settings = DeviceSettings(object : DataStore<Preferences> {
            override val data = delegate.data
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                if (hold) { hold = false; entered.complete(Unit); release.await() }
                return delegate.updateData(transform)
            }
        })
        val stores = mutableListOf<ViewModelStore>()
        try {
            val state = settings.ensureIdentity()
            val identity = IdentityHolder().apply { update(state) }
            val partner = DeviceKeys.generate()
            val coupleId = UUID.randomUUID().toString()
            val transport = object : PairingTransport {
                override suspend fun createInvite(token: String, invite: Invite) = Unit
                override suspend fun invite(token: String): Invite? = error("unexpected invite read")
                override suspend fun accept(token: String, uid: String, deviceId: String, keys: Pairing.PublicKeys): Unit = error("unexpected accept")
                override suspend fun linkedCoupleId(token: String, myUid: String) = coupleId
                override suspend fun partnerDevice(coupleId: String, myUid: String) = LinkedDevice("partner", "partner-device", Pairing.PublicKeys.of(partner))
            }
            val session = PairingSession(db, identity, InMemoryKeyVault(), transport)
            session.createLink()
            assertTrue(session.poll() is PairingState.Linked)
            val events = mutableListOf<BizEvent>()
            val logger = object : BizLogger {
                override fun init() = Unit
                override fun makeUUID() = "test"
                override fun record(viewName: String, event: BizEvent) { events += event }
                override fun destroy() = Unit
            }
            val profile = ProfileRepository(db, SyncWriter(db, identity), identity)
            fun create(): LinkWatchViewModel {
                val store = ViewModelStore().also(stores::add)
                val owner = object : ViewModelStoreOwner { override val viewModelStore = store }
                val factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T = LinkWatchViewModel(session, settings, null, profile, logger) as T
                }
                return ViewModelProvider(owner, factory)[LinkWatchViewModel::class.java]
            }
            hold = true
            withContext(Dispatchers.Main) { create().watch() }
            withTimeout(5_000) { entered.await() }
            withContext(Dispatchers.Main) { stores.last().clear() }
            release.complete(Unit)
            assertEquals(0, settings.state.first().keyEpoch)

            val restored = withContext(Dispatchers.Main) { create().also { it.watch() } }
            withTimeout(5_000) { restored.ui.first { it != null } }
            assertEquals(1, settings.state.first().keyEpoch)
            withContext(Dispatchers.Main) { stores.last().clear() }
            db.coupleDao().upsert(checkNotNull(db.coupleDao().get()).copy(keyEpoch = 2))
            val pending = withContext(Dispatchers.Main) { create().also { it.watch() } }
            withTimeout(5_000) { pending.ui.first { it != null } }
            assertEquals(2, settings.state.first().keyEpoch)
            withContext(Dispatchers.Main) { pending.start(); stores.last().clear() }
            val dismissed = withContext(Dispatchers.Main) { create().also { it.watch() } }
            assertNull(dismissed.ui.value)
            assertEquals(1, events.count { it is BizEvent.PairingLinked })
        } finally {
            release.complete(Unit)
            withContext(Dispatchers.Main) { stores.forEach { it.clear() } }
            io.cancel(); db.close(); root.deleteRecursively()
        }
    }
}
