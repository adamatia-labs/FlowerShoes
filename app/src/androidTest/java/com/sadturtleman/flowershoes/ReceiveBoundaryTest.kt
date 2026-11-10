package com.sadturtleman.flowershoes

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sadturtleman.flowershoes.core.crypto.*
import com.sadturtleman.flowershoes.core.data.*
import com.sadturtleman.flowershoes.core.data.sync.*
import com.sadturtleman.flowershoes.core.database.*
import com.sadturtleman.flowershoes.core.model.LetterBlock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okio.Path.Companion.toOkioPath
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import kotlin.io.encoding.Base64

/** Actual Android SQLite connections, native crypto, file writes and database reopen. */
@RunWith(AndroidJUnit4::class)
class ReceiveBoundaryTest {
    @Test fun relinkWhileDownloadingDoesNotInstallOldAttachment(): Unit = verifyChange(relink = true)
    @Test fun senderKeyChangeWhileDownloadingDoesNotInstallOldAttachment(): Unit = verifyChange(relink = false)

    private fun verifyChange(relink: Boolean): Unit = runBlocking {
        Sodium.init()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val root = File(context.cacheDir, "receive-${UUID.randomUUID()}").apply { mkdirs() }
        fun open() = Room.databaseBuilder<AppDatabase>(context, File(root, "test.db").absolutePath).buildKkotsin()
        var db = open()
        val other = open()
        try {
            val couple = CoupleEntity("couple", "receiver", "sender", 1, 1)
            val identity = object : Identity {
                override val userId = "receiver"
                override val deviceId = "receiver-device"
                override val keyEpoch = 1
            }
            val signer = DeviceKeys.generate()
            val sender = DeviceEntity("sender-device", "sender", "sender", signer.signPub, signer.boxPub, "ACTIVE", 1, null)
            val key = CoupleKey.generate(1)
            val vault = InMemoryKeyVault().also { it.putDeviceKeys(DeviceKeys.generate()); it.putCoupleKey(key) }
            db.coupleDao().upsert(couple)
            db.deviceDao().upsert(sender)
            val blobKey = Sodium.random(Aead.KEY_BYTES)
            val photo = "actual Android attachment".encodeToByteArray()
            val transport = Download(Aead.seal(blobKey, photo))
            fun client() = SyncClient(db, identity, vault, transport, transport,
                AttachmentStore(db, root.toOkioPath() / "attachments"), InboxApplier(db), SyncWriter(db, identity))
            val letter = LetterRepository(db, SyncWriter(db, identity), identity)
                .newLetter(listOf(LetterBlock.Photo("photo"))).copy(authorId = "sender")
            val payload = OpCodec.encode(OpPayload.LetterPut(letter, photos = listOf(PhotoRef("photo", "blob", Base64.encode(blobKey)))))
            val header = OpHeader("op", "sender-device:app", 1, 1)
            val envelope = OpEnvelope.seal(header, byteArrayOf(0, 0, 0, 0, 0, 0, 0, 1) + payload, key, signer)
            val item = MailItem("op", "letter", sender.id, header.toBytes(), envelope.ciphertext, envelope.signature)
            withTimeout(30_000) {
                val receiving = async { client().receiveQueued(item) }
                transport.entered.await()
                if (relink) {
                    other.coupleDao().clear()
                    other.coupleDao().upsert(couple.copy(id = "new-couple", partnerUserId = "new-partner"))
                } else {
                    other.deviceDao().upsert(sender.copy(signPub = DeviceKeys.generate().signPub))
                }
                transport.resume.complete(Unit)
                assertFalse(receiving.await())
            }
            assertFalse(File(root, "attachments/photo").exists())
            assertFalse(transport.deleted)
            db.close(); db = open()
            assertNull(db.attachmentDao().get("photo"))
            assertNull(db.letterDao().get(letter.id))
            assertNull(db.syncDao().cursor("sender-device:app"))
            // The same valid message still works after restoring the fixture's original authorization.
            other.coupleDao().clear()
            other.coupleDao().upsert(couple)
            other.deviceDao().upsert(sender)
            assertTrue(client().receiveQueued(item))
            assertArrayEquals(photo, File(root, "attachments/photo").readBytes())
            assertNotNull(db.letterDao().get(letter.id))
            assertEquals(1L, db.syncDao().cursor("sender-device:app"))
        } finally {
            other.close(); db.close(); root.deleteRecursively()
        }
    }

    private class Download(private val bytes: ByteArray) : Mailbox, BlobStore {
        val entered = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        var deleted = false
        override suspend fun get(coupleId: String, blobId: String): ByteArray {
            entered.complete(Unit)
            resume.await()
            return bytes
        }
        override suspend fun delete(coupleId: String, blobId: String) { deleted = true }
        override suspend fun put(coupleId: String, toDevice: String, item: MailItem): Unit = error("unexpected network")
        override suspend fun fetch(coupleId: String, myDevice: String): List<MailItem> = error("unexpected network")
        override suspend fun delete(coupleId: String, myDevice: String, opId: String): Unit = error("unexpected network")
        override suspend fun putControl(coupleId: String, myDevice: String, doc: ControlDoc): Unit = error("unexpected network")
        override suspend fun getControl(coupleId: String, device: String): ControlDoc? = error("unexpected network")
        override suspend fun put(coupleId: String, blobId: String, bytes: ByteArray): Unit = error("unexpected network")
    }
}
