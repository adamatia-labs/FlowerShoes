package com.sadturtleman.flowershoes

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.sadturtleman.flowershoes.core.data.DeviceSettings
import com.sadturtleman.flowershoes.core.data.sync.*
import com.sadturtleman.flowershoes.core.database.AppDatabase
import com.sadturtleman.flowershoes.core.database.UserEntity
import com.sadturtleman.flowershoes.core.database.buildKkotsin
import com.sadturtleman.flowershoes.core.firebase.FunctionsAccount
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import okio.Path.Companion.toOkioPath
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** 실제 로컬 Firebase Auth/Functions와 Android SDK. 운영 프로젝트에는 실행할 수 없다. */
@RunWith(AndroidJUnit4::class)
class AccountDeletionReceiptAndroidTest {
    @Test
    fun deletedAuthAndLostResponseResumeThroughUnauthenticatedReceipt(): Unit = runBlocking {
        check(BuildConfig.FIREBASE_EMULATOR_HOST.isNotBlank())
        check(FirebaseApp.getInstance().options.projectId == "demo-kkotsin")
        val auth = FirebaseAuth.getInstance()
        check(auth.currentUser == null) { "Isolated emulator session required" }
        val uid = checkNotNull(auth.signInAnonymously().await().user).uid
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = File(context.cacheDir, "deletion-receipt-${UUID.randomUUID()}").apply { mkdirs() }
        fun openDb() = Room.databaseBuilder<AppDatabase>(context, File(root, "test.db").absolutePath).buildKkotsin()
        var db = openDb()
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        fun openSettings() = DeviceSettings(PreferenceDataStoreFactory.createWithPath(scope = scope) { root.toOkioPath() / "settings.preferences_pb" })
        val vault = InMemoryKeyVault()
        val cloud = FunctionsAccount(emulatorHost = BuildConfig.FIREBASE_EMULATOR_HOST)
        var requests = 0
        try {
            var settings = openSettings()
            settings.ensureIdentity()
            settings.bindAccount(uid)
            db.userDao().upsert(UserEntity(uid, "GOMSIN", "테스트", 1))
            val lostResponse = object : AccountTransport by cloud {
                override suspend fun deleteAccountWithOutcome(attempt: AccountDeletionAttempt): AppleRevocation {
                    requests++
                    assertEquals(AppleRevocation.NOT_REQUIRED, cloud.deleteAccountWithOutcome(attempt))
                    auth.signOut()
                    error("injected loss before local commit")
                }
            }
            val failure = runCatching { UnlinkService(db, vault, settings, lostResponse).deleteAccount() }.exceptionOrNull()
            assertEquals("injected loss before local commit", failure?.message)
            assertNull(auth.currentUser)
            val pending = checkNotNull(db.cleanupDao().get())
            assertEquals("REQUESTED", pending.phase)
            assertNotNull(db.userDao().get(uid))
            db.close()
            scope.coroutineContext[Job]!!.cancelAndJoin()

            db = openDb()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            settings = openSettings()
            val resumed = FunctionsAccount(emulatorHost = BuildConfig.FIREBASE_EMULATOR_HOST)
            assertTrue(UnlinkService(db, vault, settings, resumed).resumePending())
            assertEquals(1, requests)
            assertNull(db.userDao().get(uid))
            assertNull(db.cleanupDao().get())
            assertTrue(settings.farewellPending.first())
            assertFalse(settings.farewellAppleInstructions.first())
            assertNull(FirebaseAuth.getInstance(FirebaseApp.getInstance("deletion-receipt")).currentUser)
        } finally {
            auth.signOut()
            db.close()
            scope.coroutineContext[Job]!!.cancelAndJoin()
            root.deleteRecursively()
        }
    }
}
