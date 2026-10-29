package com.sadturtleman.flowershoes

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Run with -PfirebaseEmulatorHost=127.0.0.1 and adb reverse for 8080/9099/9199/5001. */
@RunWith(AndroidJUnit4::class)
class LocalBackendTest {
    @Test fun localQaUsesDemoProjectAndEmulatedAuthAndStorage(): Unit = runBlocking {
        assumeTrue(BuildConfig.DEBUG && BuildConfig.FIREBASE_EMULATOR_HOST.isNotBlank())
        // Do not create any fake user if initialization accidentally retained google-services.json.
        assertEquals("demo-kkotsin", FirebaseApp.getInstance().options.projectId)
        assertEquals("${BuildConfig.FIREBASE_EMULATOR_HOST}:8080", FirebaseFirestore.getInstance().firestoreSettings.host)
        assertFalse(FirebaseFirestore.getInstance().firestoreSettings.isSslEnabled)
        withTimeout(30_000) {
            val auth = FirebaseAuth.getInstance()
            val user = auth.createUserWithEmailAndPassword("qa-${UUID.randomUUID()}@kkotsin.test", "local-qa-only").await().user!!
            val file = FirebaseStorage.getInstance().reference.child("couples/qa-${UUID.randomUUID()}/qa-routing")
            try {
                // Not a couple member, so storage.rules denies it: the emulator with our rules answered.
                val denied = runCatching { file.putBytes(byteArrayOf(1, 7, 9)).await() }.exceptionOrNull()
                assertEquals(StorageException.ERROR_NOT_AUTHORIZED, (denied as StorageException).errorCode)
            } finally { user.delete().await(); auth.signOut() }
        }
    }
}
