package com.sadturtleman.flowershoes

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sadturtleman.flowershoes.core.crypto.*
import com.sadturtleman.flowershoes.core.data.*
import com.sadturtleman.flowershoes.core.data.sync.*
import com.sadturtleman.flowershoes.core.database.*
import com.sadturtleman.flowershoes.core.model.MissYou
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okio.Path.Companion.toOkioPath
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import kotlin.io.encoding.Base64
import kotlin.time.Clock

@RunWith(AndroidJUnit4::class)
class WidgetJournalImportTest {
    @Test fun committedEnvelopeImportsAfterDatabaseReopenWithoutOutboxFile(): Unit = runBlocking {
        try {
            Sodium.init()
        } catch (alreadyInitialized: IllegalStateException) {
            // Instrumentation and the application can initialize the same native library.
            // The 0.9.5 wrapper rejects sodium_init's documented already-initialized code.
            check(com.ionspin.kotlin.crypto.LibsodiumInitializer.sodiumJna.sodium_init() == 1)
        }
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val root = File(context.cacheDir, "widget-journal-${UUID.randomUUID()}").apply { mkdirs() }
        fun open() = Room.databaseBuilder<AppDatabase>(context, File(root, "test.db").absolutePath).buildKkotsin()
        var db = open()
        try {
            db.coupleDao().upsert(CoupleEntity("couple", "a", "b", 1, 1))
            val identity = object : Identity {
                override val userId = "a"
                override val deviceId = "device-a"
                override val keyEpoch = 1
            }
            val vault = InMemoryKeyVault()
            val keys = DeviceKeys.generate()
            val key = CoupleKey(1, Sodium.random(32))
            vault.putDeviceKeys(keys); vault.putCoupleKey(key)
            val id = UUID.randomUUID().toString()
            val header = OpHeader(id, "device-a:widget", 1, 1)
            val payload = OpCodec.encode(OpPayload.MissYouPut(MissYou(id, "a", Clock.System.now())))
            // OpPlain = big-endian lamport followed by the payload.
            val plain = byteArrayOf(0, 0, 0, 0, 0, 0, 0, 1) + payload
            val sealed = OpEnvelope.seal(header, plain, key, keys)
            val body = buildJsonObject {
                put("coupleId", "couple"); put("fromDeviceId", "device-a"); put("opId", id); put("kind", "missyou")
                put("header", Base64.encode(header.toBytes())); put("ct", Base64.encode(sealed.ciphertext)); put("sig", Base64.encode(sealed.signature))
            }.toString()
            File(root, "widget-send-journal.json").writeText(buildJsonObject {
                put("lastSeq", 1)
                put("pending", buildJsonObject { put("opId", id); put("body", Base64.encode(body.encodeToByteArray())) })
            }.toString())
            db.close(); db = open()
            val transport = NoNetwork()
            val sync = SyncClient(db, identity, vault, transport, transport, AttachmentStore(db, root.toOkioPath() / "attachments"), InboxApplier(db), SyncWriter(db, identity))
            repeat(2) { importWidgetOutbox(sync, root.toOkioPath()) }
            assertEquals(1, db.missYouDao().all().size)
            assertEquals(id, db.syncDao().log("device-a:widget").single().opId)
            assertEquals(1L, db.syncDao().cursor("device-a:widget"))
            assertFalse(File(root, "widget-outbox").exists())
        } finally { db.close(); root.deleteRecursively() }
    }

    private class NoNetwork : Mailbox, BlobStore {
        override suspend fun put(coupleId: String, toDevice: String, item: MailItem): Unit = error("unexpected network")
        override suspend fun fetch(coupleId: String, myDevice: String): List<MailItem> = error("unexpected network")
        override suspend fun delete(coupleId: String, myDevice: String, opId: String): Unit = error("unexpected network")
        override suspend fun putControl(coupleId: String, myDevice: String, doc: ControlDoc): Unit = error("unexpected network")
        override suspend fun getControl(coupleId: String, device: String): ControlDoc? = error("unexpected network")
        override suspend fun put(coupleId: String, blobId: String, bytes: ByteArray): Unit = error("unexpected network")
        override suspend fun get(coupleId: String, blobId: String): ByteArray? = error("unexpected network")
        override suspend fun delete(coupleId: String, blobId: String): Unit = error("unexpected network")
    }
}
