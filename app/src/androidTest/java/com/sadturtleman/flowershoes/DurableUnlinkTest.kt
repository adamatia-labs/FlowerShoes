package com.sadturtleman.flowershoes

import android.content.Context
import android.content.ContextWrapper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sadturtleman.flowershoes.core.crypto.CoupleKey
import com.sadturtleman.flowershoes.core.crypto.Sodium
import com.sadturtleman.flowershoes.core.data.*
import com.sadturtleman.flowershoes.core.data.sync.*
import com.sadturtleman.flowershoes.core.database.*
import com.sadturtleman.flowershoes.core.model.LetterBlock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okio.Path.Companion.toOkioPath
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Android Keystore, actual files, Room/DataStore close + reopen after an interrupted unlink. */
@RunWith(AndroidJUnit4::class)
class DurableUnlinkTest {
    @Test fun wipeResumesAfterFileFailureWithOriginalChoice(): Unit = recover(AfterUnlink.WIPE)
    @Test fun keepResumesAfterKeyFailureWithOriginalChoice(): Unit = recover(AfterUnlink.KEEP_AS_MEMORIES)

    private fun recover(choice: AfterUnlink): Unit = runBlocking {
        Sodium.init()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = File(context.cacheDir, "unlink-${UUID.randomUUID()}").apply { mkdirs() }
        val alias = "unlink-test-${UUID.randomUUID()}"
        val isolated = object : ContextWrapper(context) {
            override fun getFilesDir() = File(root, "vault").apply { mkdirs() }
        }
        fun open() = Room.databaseBuilder<AppDatabase>(context, File(root, "test.db").absolutePath).buildKkotsin()
        var db = open()
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        fun openSettings() = DeviceSettings(PreferenceDataStoreFactory.createWithPath(scope = scope) { root.toOkioPath() / "settings.preferences_pb" })
        try {
            var settings = openSettings()
            settings.ensureIdentity(); settings.bindAccount("owner"); settings.setKeyEpoch(1)
            val state = settings.state.first()
            val identity = object : Identity {
                override val userId = state.userId
                override val deviceId = state.deviceId
                override val keyEpoch = 1
            }
            db.coupleDao().upsert(CoupleEntity("couple", "owner", "partner", 1, 100))
            val letters = LetterRepository(db, SyncWriter(db, identity), identity)
            val letter = letters.newLetter(listOf(LetterBlock.Text("보관할 편지")))
            letters.send(letter, emptyList())
            val photo = File(root, "attachments/photo").apply { parentFile!!.mkdirs(); writeText("photo") }
            var platform = AndroidKeyVault(isolated, alias)
            platform.putCoupleKey(CoupleKey.generate(1))
            var calls = 0
            val server = object : AccountTransport {
                override suspend fun unlinkCouple(coupleId: String) { calls++ }
                override suspend fun deleteAccount() = Unit
            }
            val interrupted = object : KeyVault by platform {
                override suspend fun clearCoupleKeys() {
                    if (choice == AfterUnlink.KEEP_AS_MEMORIES) error("injected key failure")
                    platform.clearCoupleKeys()
                }
            }
            val first = UnlinkService(db, interrupted, settings, server) { error("injected file failure") }
            assertNotNull(runCatching { first.unlink(choice) }.exceptionOrNull())
            assertEquals(choice.name, db.cleanupDao().get()?.choice)
            assertTrue(photo.exists())
            db.close(); scope.coroutineContext[Job]!!.cancelAndJoin()
            db = open(); scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            settings = openSettings(); platform = AndroidKeyVault(isolated, alias)
            val opposite = if (choice == AfterUnlink.WIPE) AfterUnlink.KEEP_AS_MEMORIES else AfterUnlink.WIPE
            val completed = UnlinkService(db, platform, settings, null) { File(root, "attachments").deleteRecursively(); Unit }.unlink(opposite)
            assertEquals(choice, completed)
            assertEquals(1, calls)
            assertEquals(0, settings.state.first().keyEpoch)
            assertNull(platform.currentCoupleKey())
            assertNull(db.cleanupDao().get())
            assertEquals(choice == AfterUnlink.KEEP_AS_MEMORIES, photo.exists())
            assertEquals(choice == AfterUnlink.KEEP_AS_MEMORIES, db.letterDao().get(letter.id) != null)
        } finally {
            db.close(); scope.coroutineContext[Job]!!.cancelAndJoin(); root.deleteRecursively()
            java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
        }
    }
}
