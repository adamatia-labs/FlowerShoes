package com.sadturtleman.flowershoes

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sadturtleman.flowershoes.core.crypto.*
import com.sadturtleman.flowershoes.core.data.Identity
import com.sadturtleman.flowershoes.core.data.sync.*
import com.sadturtleman.flowershoes.core.database.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Real Android Keystore-backed device secret and SQLite commit/reopen; transport is controlled. */
@RunWith(AndroidJUnit4::class)
class PairingCommitTest {
    @Test fun failedDeviceInsertDoesNotPublishKeyAndReopenCompletesSamePairing(): Unit = runBlocking {
        try { Sodium.init() } catch (alreadyInitialized: IllegalStateException) {
            check(com.ionspin.kotlin.crypto.LibsodiumInitializer.sodiumJna.sodium_init() == 1)
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = File(context.cacheDir, "pairing-${UUID.randomUUID()}").apply { mkdirs() }
        // The QA Application can create its own vault concurrently; keep this fixture's wrapping key separate.
        val keyAlias = "pairing-test-${UUID.randomUUID()}"
        val isolated = object : ContextWrapper(context) {
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
        }
        fun open() = Room.databaseBuilder<AppDatabase>(context, File(root, "test.db").absolutePath).buildKkotsin()
        var db = open()
        val identity = object : Identity {
            override val userId = "a"
            override val deviceId = "device-a"
            override val keyEpoch = 1
        }
        val partner = DeviceKeys.generate()
        val pending = InMemoryPendingInvite()
        var linked = false
        val transport = object : PairingTransport {
            override suspend fun createInvite(token: String, invite: Invite) = Unit
            override suspend fun invite(token: String): Invite? = error("unexpected invite read")
            override suspend fun accept(token: String, uid: String, deviceId: String, keys: Pairing.PublicKeys): Unit = error("unexpected accept")
            override suspend fun linkedCoupleId(token: String, myUid: String) = if (linked) "ab" else null
            override suspend fun partnerDevice(coupleId: String, myUid: String) = LinkedDevice("b", "device-b", Pairing.PublicKeys.of(partner))
        }
        try {
            var platform = AndroidKeyVault(isolated, keyAlias)
            val session = PairingSession(db, identity, platform, transport, pending = pending)
            val waiting = session.createLink() as PairingState.Waiting
            linked = true
            db.useWriterConnection { tx -> tx.immediateTransaction {
                usePrepared("CREATE TRIGGER fail_pairing BEFORE INSERT ON device WHEN NEW.id = 'device-b' BEGIN SELECT RAISE(ABORT, 'injected device failure'); END") { it.step() }
            } }
            val failure = runCatching { session.poll() }.exceptionOrNull()
            assertNotNull(failure)
            assertNull(db.coupleDao().get())
            assertNull(db.deviceDao().get("device-a"))
            assertNull(platform.currentCoupleKey())
            db.close(); db = open()
            platform = AndroidKeyVault(isolated, keyAlias)
            db.useWriterConnection { tx -> tx.immediateTransaction {
                usePrepared("DROP TRIGGER fail_pairing") { it.step() }
            } }
            assertTrue(PairingSession(db, identity, platform, transport, pending = pending).poll() is PairingState.Linked)
            val expected = Pairing.coupleKey(platform.deviceKeys()!!, Pairing.PublicKeys.of(partner), waiting.token)
            db.close(); db = open()
            platform = AndroidKeyVault(isolated, keyAlias)
            assertArrayEquals(expected.export(), platform.forDatabase(db, identity).currentCoupleKey()!!.export())
            assertNotNull(db.deviceDao().get("device-a"))
            assertNotNull(db.deviceDao().get("device-b"))
            assertNull("The raw vault must not publish a separate active pairing key", platform.currentCoupleKey())
            assertNull(pending.load())
        } finally {
            db.close(); root.deleteRecursively()
            java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(keyAlias) }
        }
    }
}
