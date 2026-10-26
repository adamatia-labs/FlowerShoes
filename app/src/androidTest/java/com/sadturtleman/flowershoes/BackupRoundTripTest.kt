package com.sadturtleman.flowershoes

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sadturtleman.flowershoes.core.backup.*
import com.sadturtleman.flowershoes.core.crypto.Sodium
import com.sadturtleman.flowershoes.core.data.AttachmentStore
import com.sadturtleman.flowershoes.core.data.DeviceSettings
import com.sadturtleman.flowershoes.core.data.sync.InMemoryKeyVault
import com.sadturtleman.flowershoes.core.database.*
import com.sadturtleman.flowershoes.core.model.AttachmentKind
import com.sadturtleman.flowershoes.core.model.Role
import com.sadturtleman.flowershoes.core.model.ThemeAccent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okio.Path.Companion.toPath
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Real Android Room/DataStore + shared encryption/restore. No process restart or cloud account. */
@OptIn(kotlin.time.ExperimentalTime::class)
@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {
    @Test fun restoreKeepsDatabaseOpenAndPersistsBackupKeyAndPreferences() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = context.cacheDir.resolve("restore-test-${UUID.randomUUID()}").apply { mkdirs() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        fun settings(name: String) = DeviceSettings(PreferenceDataStoreFactory.createWithPath(scope = scope) {
            root.resolve("$name.preferences_pb").absolutePath.toPath()
        })
        val oldDb = Room.inMemoryDatabaseBuilder<AppDatabase>(context).buildKkotsin()
        val newDb = Room.inMemoryDatabaseBuilder<AppDatabase>(context).buildKkotsin()
        try {
            Sodium.init()
            val before = settings("before")
            before.ensureIdentity()
            before.bindAccount("old-account")
            before.setRole(Role.GOMSIN)
            before.setTheme(ThemeAccent.NAVY)
            before.setOnboarded()
            before.setDisplayMode("DARK")
            before.setAutoBackup(false)
            before.setCellularBackup(true)
            before.saveLetterDraft("saved draft")
            val oldMedia = AttachmentStore(oldDb, root.resolve("old-media").absolutePath.toPath())
            val photo = oldMedia.put(AttachmentKind.PHOTO, byteArrayOf(2, 3, 4))
            oldDb.letterDao().upsert(LetterEntity("saved", "me", null, "[]", "plain", 1, null, 0))
            val storage = FileBackupStorage(root.resolve("encrypted").absolutePath.toPath())
            val oldVault = InMemoryKeyVault()
            val work = root.resolve("work").absolutePath.toPath()
            val original = BackupManager(storage, RoomBackupSource(oldDb, oldMedia, oldVault, before, work),
                oldVault, null, RestoreApplier {}, AppDatabase.VERSION)
            original.backupNow("u1")
            val recovery = original.recoveryCode()!!

            val after = settings("after")
            val newDeviceId = after.ensureIdentity().deviceId
            after.bindAccount("current-account")
            val newVault = InMemoryKeyVault()
            val newMedia = AttachmentStore(newDb, root.resolve("new-media").absolutePath.toPath())
            val restored = BackupManager(storage, RoomBackupSource(newDb, newMedia, newVault, after, work), newVault, null,
                RoomRestoreApplier(newDb, newMedia, newVault, after, work,
                    openBackup = { Room.databaseBuilder<AppDatabase>(context, it).buildKkotsin() }, scheduler = { null }), AppDatabase.VERSION)
            assertTrue(restored.restore("u1", recovery) is RestoreResult.Restored)
            assertArrayEquals(oldVault.backupSecrets()!!.first, newVault.backupSecrets()!!.first)
            assertEquals(recovery, restored.recoveryCode())
            assertArrayEquals(byteArrayOf(2, 3, 4), newMedia.read(photo))
            assertEquals("saved", newDb.letterDao().allActive().single().id)
            assertEquals(newDeviceId, after.state.first().deviceId)
            assertEquals("current-account", after.state.first().userId)
            assertEquals(ThemeAccent.NAVY, after.state.first().theme)
            assertEquals("DARK", after.displayMode.first())
            assertEquals("saved draft", after.letterDraft.first())
            assertFalse(after.autoBackup.first())
            assertTrue(after.cellularBackup.first())
            // A second operation proves the restored Room and the same backup key remain usable.
            newDb.letterDao().upsert(LetterEntity("after", "me", null, "[]", "plain", 2, null, 0))
            restored.backupNow("u1")
            assertEquals(2, restored.preview("u1")!!.summary!!.letters)
        } finally {
            oldDb.close(); newDb.close()
            scope.coroutineContext[Job]!!.cancelAndJoin()
            root.deleteRecursively()
        }
    }
}
