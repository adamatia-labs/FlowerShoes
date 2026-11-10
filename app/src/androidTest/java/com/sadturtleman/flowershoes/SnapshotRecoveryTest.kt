package com.sadturtleman.flowershoes

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sadturtleman.flowershoes.core.data.*
import com.sadturtleman.flowershoes.core.data.sync.*
import com.sadturtleman.flowershoes.core.database.*
import com.sadturtleman.flowershoes.core.model.LetterBlock
import com.sadturtleman.flowershoes.core.sync.StreamId
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toOkioPath
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Android SQLite file reopen, production snapshot writer and receiver; no external backend. */
@RunWith(AndroidJUnit4::class)
class SnapshotRecoveryTest {
    @Test fun incompleteBatchSurvivesDatabaseReopen(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val root = File(context.cacheDir, "snapshot-${UUID.randomUUID()}").apply { mkdirs() }
        fun open(name: String) = Room.databaseBuilder<AppDatabase>(context, File(root, name).absolutePath).buildKkotsin()
        val sender = open("sender.db")
        var receiver = open("receiver.db")
        try {
            sender.coupleDao().upsert(CoupleEntity("test-couple", "a", "b", 1, 1))
            receiver.coupleDao().upsert(CoupleEntity("test-couple", "b", "a", 1, 1))
            val identity = object : Identity {
                override val userId = "a"
                override val deviceId = "device-a"
                override val keyEpoch = 1
            }
            val writer = SyncWriter(sender, identity)
            val letters = LetterRepository(sender, writer, identity)
            repeat(3) { letters.send(letters.newLetter(listOf(LetterBlock.Text("x".repeat(25_000)))), emptyList()) }
            MissYouRepository(sender, writer, identity).send()
            StatusRepository(sender, writer, identity).set("휴가", 1)
            val transport = NoNetwork()
            val client = SyncClient(sender, identity, InMemoryKeyVault(), transport, transport,
                AttachmentStore(sender, root.toOkioPath() / "attachments"), InboxApplier(sender), writer)
            client.writeFullSnapshot()
            val stream = StreamId.parse("device-a:app")
            val parts = sender.syncDao().log(stream.wire).filter { it.kind == "snapshot" }
            assertTrue(parts.size > 1)
            fun received(row: SyncOpEntity) = ReceivedOp(stream, row.seq, row.opId, row.lamport, row.payload)
            InboxApplier(receiver).apply(received(parts.last()))
            assertEquals(0L, receiver.syncDao().cursor(stream.wire))
            receiver.close()
            receiver = open("receiver.db")
            val inbox = InboxApplier(receiver)
            assertEquals(ApplyResult.DUPLICATE, inbox.apply(received(parts.last())))
            parts.dropLast(1).reversed().forEach { inbox.apply(received(it)) }
            assertEquals(parts.last().seq, receiver.syncDao().cursor(stream.wire))
            assertEquals(3, receiver.letterDao().allActive().size)
            assertEquals(1, receiver.missYouDao().all().size)
            assertEquals("휴가", receiver.statusDao().all().single().label)
        } finally {
            sender.close(); receiver.close(); root.deleteRecursively()
        }
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
