package com.sadturtleman.flowershoes

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sadturtleman.flowershoes.core.data.*
import com.sadturtleman.flowershoes.core.data.sync.*
import com.sadturtleman.flowershoes.core.database.*
import com.sadturtleman.flowershoes.core.model.*
import com.sadturtleman.flowershoes.core.sync.StreamId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import okio.Path.Companion.toOkioPath
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import kotlin.time.Clock

/** Real Android SQLite, KEEP cleanup, restart, snapshot writer and receiver. Transport is tested on JVM. */
@RunWith(AndroidJUnit4::class)
class RelationshipArchiveTest {
    @Test fun repairSnapshotKeepsPreviousRelationshipLocalAfterReopen() = verifyArchive(deviceChange = false)
    @Test fun deviceChangeSnapshotKeepsPreviousRelationshipLocalAfterReopen() = verifyArchive(deviceChange = true)

    private fun verifyArchive(deviceChange: Boolean): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val root = File(context.cacheDir, "archive-${UUID.randomUUID()}").apply { mkdirs() }
        val settingsJob = SupervisorJob()
        val settings = DeviceSettings(PreferenceDataStoreFactory.createWithPath(
            scope = CoroutineScope(Dispatchers.IO + settingsJob),
            produceFile = { root.toOkioPath() / "settings.preferences_pb" },
        ))
        fun open(name: String) = Room.databaseBuilder<AppDatabase>(context, File(root, name).absolutePath).buildKkotsin()
        var sender = open("sender.db")
        val receiver = open("receiver.db")
        val identity = object : Identity {
            override val userId = "a"
            override val deviceId = "device-a"
            override val keyEpoch = 1
        }
        try {
            var writer = SyncWriter(sender, identity)
            var profile = ProfileRepository(sender, writer, identity)
            // A real first write initializes the archive boundary before the first relationship.
            profile.saveMe(Role.GOMSIN, "A")
            sender.coupleDao().upsert(CoupleEntity("ab", "a", "b", 1, 1))
            sender.userDao().upsert(UserEntity("b", Role.SOLDIER.name, "B", 1))
            var attachments = AttachmentStore(sender, root.toOkioPath() / "attachments")
            var letters = LetterRepository(sender, writer, identity)
            val oldPhoto = attachments.put(AttachmentKind.PHOTO, "AB photo".encodeToByteArray())
            val oldDrawing = attachments.put(AttachmentKind.DRAWING_DOC, "AB drawing".encodeToByteArray())
            val oldLetter = letters.newLetter(listOf(LetterBlock.Photo(oldPhoto), LetterBlock.DrawingRef("ab-drawing")))
            letters.send(oldLetter, listOf(DrawingMeta("ab-drawing", 0, "SQUARE", oldDrawing, oldDrawing, "AB", true, 10)))
            val oldEvent = Event("ab-event", EventType.VISIT, "AB", LocalDateTime(2026, 1, 1, 10, 0),
                LocalDateTime(2026, 1, 1, 12, 0), false, Clock.System.now(), 0, identity.deviceId,
                photoIds = listOf(oldPhoto))
            EventRepository(sender, writer, identity).save(oldEvent)
            LeaveRepository(sender, writer, identity).save(LeaveGrant("ab-leave", LeaveKind.REWARD, "AB", 1,
                LocalDate(2026, 1, 1), Clock.System.now(), 0, identity.deviceId))
            profile.setAnniversary(LocalDate(2025, 1, 1))
            profile.saveService(Service("b", Branch.ARMY, LocalDate(2025, 1, 1), 1))
            profile.saveService(Service(PENDING_SOLDIER, Branch.NAVY, LocalDate(2025, 2, 1), 1))
            BookCoverRepository(sender, writer, identity).choose(BookCover.entries.first())
            MissYouRepository(sender, writer, identity).send()
            StatusRepository(sender, writer, identity).set("AB private", 1)
            letters.markRead(com.sadturtleman.flowershoes.core.model.ReceiptEntity.LETTER, oldLetter.id)
            // An orphaned receipt must stay local even though its original has already disappeared.
            sender.receiptDao().insertFirst(com.sadturtleman.flowershoes.core.database.ReceiptEntity("LETTER", "ab-deleted", "b", 1))
            UnlinkService(sender, InMemoryKeyVault(), settings, null)
                .onPartnerUnlinked(AfterUnlink.KEEP_AS_MEMORIES, "ab")
            sender.close()
            sender = open("sender.db")

            writer = SyncWriter(sender, identity)
            profile = ProfileRepository(sender, writer, identity)
            letters = LetterRepository(sender, writer, identity)
            attachments = AttachmentStore(sender, root.toOkioPath() / "attachments")
            val fresh = letters.newLetter(listOf(LetterBlock.Text("after unlink")))
            letters.send(fresh, emptyList())
            MissYouRepository(sender, writer, identity).send()
            StatusRepository(sender, writer, identity).set("for C", 2)
            profile.saveService(Service(PENDING_SOLDIER, Branch.AIR_FORCE, LocalDate(2026, 10, 1), 1))
            sender.coupleDao().upsert(CoupleEntity("ac", "a", "c", 1, 1))
            receiver.coupleDao().upsert(CoupleEntity("ac", "c", "a", 1, 1))
            profile.setAnniversary(LocalDate(2026, 10, 1))
            BookCoverRepository(sender, writer, identity).choose(BookCover.entries.last())
            val transport = NoNetwork()
            val client = SyncClient(sender, identity, InMemoryKeyVault(), transport, transport, attachments, InboxApplier(sender), writer)
            if (deviceChange) client.writeFullSnapshot()
            else client.writeSnapshot(sender.syncDao().nextSeq("device-a:app")!! - 1)
            val stream = StreamId.parse("device-a:app")
            val snapshots = sender.syncDao().log(stream.wire).filter { it.kind == "snapshot" }
            assertTrue(snapshots.isNotEmpty())
            val inbox = InboxApplier(receiver)
            snapshots.reversed().forEach { inbox.apply(ReceivedOp(stream, it.seq, it.opId, it.lamport, it.payload)) }

            assertEquals(listOf(fresh.id), receiver.letterDao().all().map { it.id })
            assertEquals(listOf(Event.COUPLE_ANNIVERSARY_ID), receiver.eventDao().all().map { it.id })
            assertTrue(receiver.leaveDao().all().isEmpty())
            assertTrue(receiver.receiptDao().all().isEmpty())
            assertTrue(receiver.attachmentDao().all().isEmpty())
            assertTrue(receiver.drawingDao().forLetter(oldLetter.id).isEmpty())
            assertEquals(listOf("a"), receiver.userDao().all().map { it.id })
            assertEquals(listOf("c"), receiver.serviceDao().all().map { it.userId })
            assertEquals(Branch.AIR_FORCE.name, receiver.serviceDao().get("c")?.branch)
            assertEquals(1, receiver.missYouDao().all().size)
            assertEquals(if (deviceChange) emptyList<String>() else listOf("for C"), receiver.statusDao().all().map { it.label })
            assertEquals(BookCover.entries.last().name, receiver.bookCoverDao().get()?.cover)
            assertEquals(snapshots.last().seq, receiver.syncDao().cursor(stream.wire))

            assertNotNull(sender.letterDao().get(oldLetter.id))
            assertEquals(1, sender.drawingDao().forLetter(oldLetter.id).size)
            assertNotNull(sender.eventDao().get("ab-event"))
            assertNotNull(sender.eventDao().get("archived:ab:${Event.COUPLE_ANNIVERSARY_ID}"))
            assertNotNull(sender.leaveDao().get("ab-leave"))
            assertNotNull(sender.serviceDao().get("b"))
            assertNotNull(sender.serviceDao().get("archived:ab:$PENDING_SOLDIER"))
            assertEquals(2, sender.missYouDao().all().size)
            assertEquals(setOf("AB private", "for C"), sender.statusDao().all().map { it.label }.toSet())
            assertArrayEquals("AB photo".encodeToByteArray(), attachments.read(oldPhoto))
            assertArrayEquals("AB drawing".encodeToByteArray(), attachments.read(oldDrawing))
            assertEquals(TransferCounts(letters = 1, events = 1), sender.transferCounts())
        } finally {
            sender.close(); receiver.close(); settingsJob.cancelAndJoin(); root.deleteRecursively()
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
