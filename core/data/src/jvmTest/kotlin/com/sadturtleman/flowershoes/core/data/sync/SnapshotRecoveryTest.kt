package com.sadturtleman.flowershoes.core.data.sync

import com.sadturtleman.flowershoes.core.crypto.Sodium
import com.sadturtleman.flowershoes.core.crypto.OpHeader
import com.sadturtleman.flowershoes.core.crypto.OpEnvelope
import com.sadturtleman.flowershoes.core.data.*
import com.sadturtleman.flowershoes.core.database.*
import com.sadturtleman.flowershoes.core.model.LetterBlock
import com.sadturtleman.flowershoes.core.model.ReceiptEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.uuid.Uuid

/** Real Room, pairing, encryption, delivery, ACK and resend; only transport is in memory. */
class SnapshotRecoveryTest {
    private val cloud = InMemoryCloud()
    private val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "snapshot-${Uuid.random()}"
    private val phones = mutableListOf<Phone>()
    private var now = Clock.System.now()
    private val clock = object : Clock { override fun now() = this@SnapshotRecoveryTest.now }
    private var deliver: (MailItem) -> Boolean = { true }
    private val mailbox = object : Mailbox by cloud {
        override suspend fun fetch(coupleId: String, myDevice: String) = cloud.fetch(coupleId, myDevice).filter(deliver).reversed()
    }

    private inner class Phone(val uid: String) {
        val db = inMemoryDatabaseBuilder().buildKkotsin()
        val vault = InMemoryKeyVault()
        val identity = object : Identity {
            override val userId = uid
            override val deviceId = "device-$uid"
            override val keyEpoch = 1
        }
        val stream = "device-$uid:app"
        val writer = SyncWriter(db, identity, clock)
        val attachments = AttachmentStore(db, root / uid)
        val letters = LetterRepository(db, writer, identity, clock)
        val missYou = MissYouRepository(db, writer, identity, clock)
        val status = StatusRepository(db, writer, identity, clock)
        val pairing = PairingSession(db, identity, vault, cloud)
        fun client() = SyncClient(db, identity, vault, mailbox, cloud, attachments, InboxApplier(db), writer, clock)
        val sync = client()
        init { phones += this }
    }

    @BeforeTest fun start() { Sodium.init() }
    @AfterTest fun stop() {
        phones.forEach { it.db.close() }
        if (FileSystem.SYSTEM.exists(root)) FileSystem.SYSTEM.deleteRecursively(root)
    }

    private suspend fun link(a: Phone, b: Phone) {
        val token = assertIs<PairingState.Waiting>(a.pairing.createLink()).token
        assertIs<PairingState.Linked>(b.pairing.accept(token))
        assertIs<PairingState.Linked>(a.pairing.poll())
    }

    private suspend fun decoded(a: Phone, mail: MailItem): OpPayload {
        val header = OpHeader.fromBytes(mail.header)
        val key = a.vault.currentCoupleKey()!!
        val envelope = OpEnvelope(header, mail.ciphertext, mail.signature)
        return OpCodec.decode(Plain.decode(OpEnvelope.open(envelope, a.vault.deviceKeys()!!.signPub) { key }).payload)!!
    }

    @Test fun `snapshot before original preserves miss you status receipt and deletion`() = runTest {
        val a = Phone("a"); val b = Phone("b"); link(a, b)
        val deleted = a.letters.newLetter(listOf(LetterBlock.Text("deleted")))
        a.letters.send(deleted, emptyList())
        a.sync.syncOnce(); b.sync.syncOnce(); a.sync.syncOnce()
        a.letters.delete(deleted.id)
        a.missYou.send()
        a.status.set("휴가", 1)
        a.letters.markRead(ReceiptEntity.LETTER, deleted.id)
        val through = a.db.syncDao().nextSeq(a.stream)!! - 1
        a.sync.writeSnapshot(through)
        deliver = { it.kind == "snapshot" }
        a.sync.syncOnce(); b.sync.syncOnce()
        assertEquals(a.db.missYouDao().all(), b.db.missYouDao().all())
        assertEquals("휴가", b.status.observeLatest("a").first()?.label)
        assertEquals(a.db.receiptDao().all(), b.db.receiptDao().all())
        assertNull(b.letters.find(deleted.id))
        deliver = { true }
        b.sync.syncOnce()
        assertEquals(1, b.db.missYouDao().all().size)
        assertNull(b.letters.find(deleted.id), "late original must not resurrect the letter")
    }

    @Test fun `partial snapshot does not acknowledge originals and resumes after restart and mail expiry`() = runTest {
        val a = Phone("a"); val b = Phone("b"); link(a, b)
        repeat(3) { a.letters.send(a.letters.newLetter(listOf(LetterBlock.Text("x".repeat(25_000)))), emptyList()) }
        val originals = a.db.syncDao().log(a.stream)
        val through = originals.last().seq
        a.sync.writeSnapshot(through)
        val parts = a.db.syncDao().log(a.stream).filter { it.kind == "snapshot" }
        assertTrue(parts.size > 1)
        deliver = { it.opId == parts.first().opId }
        a.sync.syncOnce(); b.sync.syncOnce()
        assertEquals(0L, b.db.syncDao().cursor(a.stream) ?: 0L, "one chunk cannot cover the missing original range")
        a.sync.syncOnce()
        assertTrue(a.db.syncDao().log(a.stream).map { it.opId }.containsAll(originals.map { it.opId }))
        val couple = a.db.coupleDao().get()!!.id
        // Firestore TTL removes undelivered mail; durable local logs must republish it.
        cloud.fetch(couple, "device-b").forEach { cloud.delete(couple, "device-b", it.opId) }
        now += 8.days
        deliver = { it.kind == "snapshot" }
        a.client().syncOnce(); b.client().syncOnce(); a.client().syncOnce()
        assertEquals(3, b.db.letterDao().allActive().size)
        assertEquals(parts.last().seq, b.db.syncDao().cursor(a.stream))
        assertTrue(a.db.syncDao().log(a.stream).isEmpty())
    }

    @Test fun `below floor recovery retransmits the same incomplete batch until every chunk arrives`() = runTest {
        val a = Phone("a"); val b = Phone("b"); link(a, b)
        repeat(3) { a.letters.send(a.letters.newLetter(listOf(LetterBlock.Text("x".repeat(25_000)))), emptyList()) }
        a.missYou.send()
        a.sync.syncOnce()
        val couple = a.db.coupleDao().get()!!.id
        val through = a.db.syncDao().nextSeq(a.stream)!! - 1
        // Old backup / capped sender log: neither original mail nor original logs remain.
        a.db.syncDao().deleteThrough(a.stream, through)
        cloud.fetch(couple, "device-b").forEach { cloud.delete(couple, "device-b", it.opId) }
        b.sync.syncOnce()
        assertTrue(a.sync.syncOnce().snapshot)
        val batch = a.db.syncDao().log(a.stream)
        deliver = { it.opId == batch.last().opId }
        b.sync.syncOnce()
        assertEquals(0L, b.db.syncDao().cursor(a.stream) ?: 0L)
        repeat(3) { a.client().syncOnce() }
        assertEquals(batch.map { it.opId }, a.db.syncDao().log(a.stream).map { it.opId }, "must reuse the incomplete batch")
        deliver = { true }
        b.client().syncOnce(); a.client().syncOnce()
        assertEquals(3, b.db.letterDao().allActive().size)
        assertEquals(1, b.db.missYouDao().all().size)
        assertEquals(batch.last().seq, b.db.syncDao().cursor(a.stream))
        assertTrue(a.db.syncDao().log(a.stream).isEmpty())
    }

    @Test fun `legacy receiver early acknowledgement cannot prune original or snapshot chunks`() = runTest {
        val a = Phone("a"); val b = Phone("b"); link(a, b)
        repeat(3) { a.letters.send(a.letters.newLetter(listOf(LetterBlock.Text("x".repeat(25_000)))), emptyList()) }
        a.sync.writeFullSnapshot()
        val rows = a.db.syncDao().log(a.stream)
        val parts = rows.filter { it.kind == "snapshot" }
        val couple = a.db.coupleDao().get()!!.id
        suspend fun acknowledge(seq: Long) {
            val bytes = """{"cursors":{"${a.stream}":$seq},"missing":{},"atMs":0}""".encodeToByteArray()
            cloud.putControl(couple, "device-b", ControlDoc(bytes, b.vault.deviceKeys()!!.sign(bytes)))
            a.sync.syncOnce()
        }
        acknowledge(parts.first().seq)
        assertEquals(rows.map { it.opId }, a.db.syncDao().log(a.stream).map { it.opId })
        acknowledge(parts.last().seq)
        assertTrue(a.db.syncDao().log(a.stream).isEmpty())
    }

    @Test fun `legacy receiver understands snapshot deletion before original delete arrives`() = runTest {
        val a = Phone("a"); val b = Phone("b"); link(a, b)
        val letter = a.letters.newLetter(listOf(LetterBlock.Text("delete me")))
        a.letters.send(letter, emptyList())
        a.sync.syncOnce(); b.sync.syncOnce(); a.sync.syncOnce()
        a.letters.delete(letter.id)
        a.sync.writeFullSnapshot(); a.sync.syncOnce()
        // Pre-batch receiver: LetterPut is insert-only, Delete updates an existing row.
        suspend fun legacyApply(p: OpPayload) {
            when (p) {
                is OpPayload.Snapshot -> p.items.forEach { legacyApply(it) }
                is OpPayload.LetterPut -> if (b.db.letterDao().get(p.letter.id) == null) b.db.letterDao().upsert(p.letter.toEntity(0))
                is OpPayload.Delete -> if (p.entity == DeletableEntity.LETTER) {
                    b.db.letterDao().get(p.id)?.let { b.db.letterDao().upsert(it.copy(deletedAtMs = p.atMs)) }
                }
                else -> Unit
            }
        }
        val couple = a.db.coupleDao().get()!!.id
        val mail = cloud.fetch(couple, "device-b").filter { it.kind == "snapshot" }
        mail.forEach { item ->
            legacyApply(decoded(a, item))
            b.db.syncDao().setCursor(RecvCursorEntity(a.stream, OpHeader.fromBytes(item.header).seq))
            cloud.delete(couple, "device-b", item.opId)
        }
        b.sync.syncOnce(); a.sync.syncOnce()
        assertNull(b.letters.find(letter.id))
        assertTrue(a.db.syncDao().log(a.stream).isEmpty())
    }

    @Test fun `queued snapshot with expired blob waits for verified attachment retransmission`() = runTest {
        val a = Phone("a"); val b = Phone("b"); link(a, b)
        val bytes = "snapshot photo".encodeToByteArray()
        val photo = a.attachments.put(com.sadturtleman.flowershoes.core.model.AttachmentKind.PHOTO, bytes)
        val letter = a.letters.newLetter(listOf(LetterBlock.Photo(photo)))
        a.letters.send(letter, emptyList()); a.sync.writeFullSnapshot(); a.sync.syncOnce()
        val couple = a.db.coupleDao().get()!!.id
        val queued = cloud.fetch(couple, "device-b").single { it.kind == "snapshot" }
        val snapshot = decoded(a, queued) as OpPayload.Snapshot
        val ref = snapshot.items.filterIsInstance<OpPayload.LetterPut>().single().photos.single()
        cloud.delete(couple, ref.blobId!!)
        assertFalse(b.sync.receiveQueued(queued), "unverified absent blob must not complete the chunk")
        assertNull(b.db.letterDao().get(letter.id))
        assertEquals(0L, b.db.syncDao().cursor(a.stream) ?: 0L)
        // Transport TTL also removes original mail; only sender's durable log can restore it.
        cloud.fetch(couple, "device-b").forEach { cloud.delete(couple, "device-b", it.opId) }
        b.sync.syncOnce()
        now += 33.days
        deliver = { it.kind == "snapshot" }
        a.client().syncOnce(); b.client().syncOnce(); a.client().syncOnce()
        assertContentEquals(bytes, b.attachments.read(photo))
        assertNotNull(b.letters.find(letter.id))
        assertTrue(a.db.syncDao().log(a.stream).isEmpty())
    }
}
