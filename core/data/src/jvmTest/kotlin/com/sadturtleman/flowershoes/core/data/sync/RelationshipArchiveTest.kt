package com.sadturtleman.flowershoes.core.data.sync

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.sadturtleman.flowershoes.core.crypto.Sodium
import com.sadturtleman.flowershoes.core.data.*
import com.sadturtleman.flowershoes.core.database.*
import com.sadturtleman.flowershoes.core.model.*
import com.sadturtleman.flowershoes.core.model.ReceiptEntity
import com.sadturtleman.flowershoes.core.sync.OpKind
import kotlin.test.*
import kotlin.time.Clock
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import okio.FileSystem

class RelationshipArchiveTest {
    private val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "relationship-${Uuid.random()}"
    private val cloud = InMemoryCloud()
    private val phones = mutableListOf<Phone>()

    private inner class Phone(val uid: String) {
        val db = inMemoryDatabaseBuilder().buildKkotsin()
        val vault = InMemoryKeyVault()
        val identity = object : Identity {
            override val userId = uid
            override val deviceId = "device-$uid"
            override val keyEpoch = 1
        }
        val settings = DeviceSettings(PreferenceDataStoreFactory.createWithPath { root / "$uid.preferences_pb" })
        val writer = SyncWriter(db, identity)
        val attachments = AttachmentStore(db, root / uid)
        val letters = LetterRepository(db, writer, identity)
        val events = EventRepository(db, writer, identity)
        val leave = LeaveRepository(db, writer, identity)
        val profile = ProfileRepository(db, writer, identity)
        val cover = BookCoverRepository(db, writer, identity)
        val pairing = PairingSession(db, identity, vault, cloud)
        val sync = SyncClient(db, identity, vault, cloud, cloud, attachments, InboxApplier(db), writer)
        val unlink = UnlinkService(db, vault, settings, cloud)
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

    private suspend fun exchange(a: Phone, b: Phone) {
        a.sync.syncOnce(); b.sync.syncOnce(); a.sync.syncOnce()
    }

    private fun event(id: String) = Event(id, EventType.VISIT, id,
        LocalDateTime(2026, 11, 1, 10, 0), LocalDateTime(2026, 11, 1, 12, 0),
        false, Clock.System.now(), 0, "device-a")

    @Test fun `첫 연결 전 쓴 편지와 사진은 정상적으로 자동 전달된다`() = runTest {
        val a = Phone("a"); val b = Phone("b")
        val photo = a.attachments.put(AttachmentKind.PHOTO, "solo photo".encodeToByteArray())
        val letter = a.letters.newLetter(listOf(LetterBlock.Text("solo"), LetterBlock.Photo(photo)))
        a.letters.send(letter, emptyList())
        link(a, b); exchange(a, b)
        assertEquals(a.letters.find(letter.id), b.letters.find(letter.id))
        assertContentEquals("solo photo".encodeToByteArray(), b.attachments.read(photo))
    }

    @Test fun `A B 보관본은 A C 로그 스냅샷 기기이전과 첨부에 섞이지 않는다`() = runTest {
        val a = Phone("a"); val b = Phone("b"); val c = Phone("c")
        a.profile.saveMe(Role.GOMSIN, "A"); b.profile.saveMe(Role.SOLDIER, "B")
        link(a, b)
        val attachment = a.attachments.put(AttachmentKind.DRAWING_DOC, "old drawing".encodeToByteArray())
        val photo = a.attachments.put(AttachmentKind.PHOTO, "old photo".encodeToByteArray())
        val letter = a.letters.newLetter(listOf(LetterBlock.Text("AB secret"), LetterBlock.Photo(photo), LetterBlock.DrawingRef("old-drawing")))
        a.letters.send(letter, listOf(DrawingMeta("old-drawing", 0, "SQUARE", attachment, attachment, "old", true, 10)))
        a.events.save(event("old-event").copy(photoIds = listOf(photo), memo = "AB memo"))
        a.leave.save(LeaveGrant("old-leave", LeaveKind.REWARD, "AB leave", 1, LocalDate(2026, 11, 1), Clock.System.now(), 0, "device-a"))
        a.profile.setAnniversary(LocalDate(2025, 1, 1))
        a.cover.choose(BookCover.entries.first())
        exchange(a, b)
        b.letters.markRead(ReceiptEntity.LETTER, letter.id); exchange(b, a)
        val oldCouple = a.db.coupleDao().get()!!.id
        a.unlink.unlink(AfterUnlink.KEEP_AS_MEMORIES)

        val newLetter = a.letters.newLetter(listOf(LetterBlock.Text("for C")))
        a.letters.send(newLetter, emptyList())
        link(a, c)
        a.profile.setAnniversary(LocalDate(2026, 10, 1))
        a.cover.choose(BookCover.entries.last())
        a.events.save(event("new-event"))
        exchange(a, c)
        assertEquals(LocalDate(2026, 10, 1), c.profile.observeAnniversary().first())
        a.sync.writeSnapshot(0)
        a.sync.writeFullSnapshot()
        exchange(a, c)

        assertNotNull(a.letters.find(letter.id))
        assertContentEquals("old drawing".encodeToByteArray(), a.attachments.read(attachment))
        assertNotNull(a.db.eventDao().get("archived:$oldCouple:${Event.COUPLE_ANNIVERSARY_ID}"))
        assertEquals(listOf(newLetter.id), c.db.letterDao().allActive().map { it.id })
        assertNull(c.db.eventDao().get("old-event")); assertNull(c.db.leaveDao().get("old-leave"))
        assertNull(c.db.userDao().get("b")); assertTrue(c.db.receiptDao().all().isEmpty())
        assertNull(c.attachments.read(photo)); assertNull(c.attachments.read(attachment))
        assertEquals(0, cloud.blobCount())
        assertEquals(LocalDate(2026, 10, 1), c.profile.observeAnniversary().first())
        assertEquals(BookCover.entries.last(), c.cover.observe().first())
        assertEquals(TransferCounts(letters = 1, events = 2), a.db.transferCounts())

        assertFailsWith<IllegalStateException> { a.letters.send(letter, emptyList()) }
        assertFailsWith<IllegalStateException> { a.events.save(event("old-event").copy(title = "edited")) }
        assertFailsWith<IllegalStateException> { a.events.writeDayLine("old-event", "changed") }
        assertFailsWith<IllegalStateException> { a.attachments.putWithId(attachment, AttachmentKind.PHOTO, byteArrayOf(1)) }
        assertFailsWith<IllegalStateException> { a.letters.send(a.letters.newLetter(listOf(LetterBlock.Photo(photo))), emptyList()) }
        val nextSeq = a.db.syncDao().nextSeq("device-a:app")
        a.letters.markRead(ReceiptEntity.HEART, letter.id)
        a.letters.delete(letter.id); a.events.delete("old-event"); a.leave.delete("old-leave")
        assertEquals(nextSeq, a.db.syncDao().nextSeq("device-a:app"), "보관본 삭제와 읽음은 로컬에서만 처리")
        assertNull(a.letters.find(letter.id))
        a.sync.writeFullSnapshot(); exchange(a, c)
        assertNull(c.db.letterDao().get(letter.id)); assertNull(c.db.eventDao().get("old-event"))
    }

    @Test fun `구버전 보관본은 새 쓰기 전에 이관되고 다음 솔로 편지는 공유된다`() = runTest {
        val a = Phone("a"); val b = Phone("b"); val c = Phone("c")
        link(a, b)
        val old = a.letters.newLetter(listOf(LetterBlock.Text("legacy")))
        a.letters.send(old, emptyList()); exchange(a, b)
        // 구버전 KEEP: 커플 행은 남지만 보관 표식은 없고 로그는 지웠다.
        val oldCouple = a.db.coupleDao().get()!!
        cloud.unlinkCouple(oldCouple.id)
        a.db.coupleDao().upsert(oldCouple.copy(partnerUserId = "", keyEpoch = 0))
        a.vault.clearCoupleKeys()
        a.db.useWriterConnection { tx -> tx.immediateTransaction {
            for (table in listOf("sync_op", "stream_counter", "recv_cursor", "recv_pending", "peer_cursor", "device")) usePrepared("DELETE FROM $table") { it.step() }
            usePrepared("DELETE FROM meta WHERE `key` LIKE 'uploaded:%'") { it.step() }
            usePrepared("DELETE FROM meta WHERE `key` = 'archive:initialized'") { it.step() }
        } }
        val fresh = a.letters.newLetter(listOf(LetterBlock.Text("after migration")))
        a.letters.send(fresh, emptyList())
        assertFailsWith<IllegalStateException> { a.letters.send(old, emptyList()) }
        link(a, c); a.sync.writeSnapshot(0); a.sync.writeFullSnapshot(); exchange(a, c)
        assertEquals(listOf(fresh.id), c.db.letterDao().allActive().map { it.id })
        assertNotNull(a.letters.find(old.id))
    }

    @Test fun `이미 재연결한 구버전의 출처 불명 기록은 최초 이관에서 모두 로컬 보관한다`() = runTest {
        val a = Phone("a"); val c = Phone("c"); val d = Phone("d")
        link(a, c)
        val current = a.letters.newLetter(listOf(LetterBlock.Text("legacy current relationship")))
        a.letters.send(current, emptyList())
        val previous = a.letters.newLetter(listOf(LetterBlock.Text("legacy former relationship")))
        a.db.letterDao().upsert(previous.toEntity(0))
        a.db.receiptDao().insertFirst(com.sadturtleman.flowershoes.core.database.ReceiptEntity("LETTER", "deleted-old-letter", "a", 1))
        // 이미 A-C로 연결된 구버전 DB. A가 쓴 두 편지의 소속을 구별할 열이나 메타가 없다.
        a.db.useWriterConnection { connection -> connection.immediateTransaction {
            usePrepared("DELETE FROM meta WHERE `key` = 'archive:initialized'") { it.step() }
        } }
        val fresh = a.letters.newLetter(listOf(LetterBlock.Text("after upgrade")))
        a.letters.send(fresh, emptyList())
        a.sync.writeSnapshot(0); a.sync.writeFullSnapshot(); exchange(a, c)
        assertEquals(listOf(fresh.id), c.db.letterDao().allActive().map { it.id })
        assertTrue(c.db.receiptDao().all().isEmpty())
        assertNotNull(a.letters.find(previous.id)); assertNotNull(a.letters.find(current.id))
        assertFailsWith<IllegalStateException> { a.letters.send(current, emptyList()) }
        // 초기 이관과 다음 연결 해제는 별도 경계다. 이관 뒤 쓴 편지도 A-D에는 넘기지 않는다.
        a.unlink.unlink(AfterUnlink.KEEP_AS_MEMORIES); link(a, d)
        a.sync.writeFullSnapshot(); exchange(a, d)
        assertTrue(d.db.letterDao().allActive().isEmpty())
    }

    @Test fun `지연된 옛 로그와 중첩 스냅샷을 건너뛰어도 새 편지 스트림은 이어진다`() = runTest {
        val a = Phone("a"); val b = Phone("b"); val c = Phone("c")
        link(a, b)
        val photo = a.attachments.put(AttachmentKind.PHOTO, "old".encodeToByteArray())
        val old = a.letters.newLetter(listOf(LetterBlock.Photo(photo)))
        a.letters.send(old, emptyList()); exchange(a, b)
        a.unlink.unlink(AfterUnlink.KEEP_AS_MEMORIES); link(a, c)
        // 보관 경계 직전에 만들어져 나중에 추가된 로그도 최종 암호화 경계에서 걸러야 한다.
        a.db.syncDao().appendOp(SyncOpEntity("device-a:app", 1, "stale", "letter", "letter", old.id,
            OpCodec.encode(OpPayload.LetterPut(old)), 1, 1, Clock.System.now().toEpochMilliseconds()))
        a.db.syncDao().setNextSeq(StreamCounterEntity("device-a:app", 2))
        a.writer.write(OpKind.SNAPSHOT, "snapshot", "stale", {}, { OpPayload.Snapshot(0,
            listOf(OpPayload.Snapshot(0, listOf(OpPayload.LetterPut(old))))) })
        val fresh = a.letters.newLetter(listOf(LetterBlock.Text("new")))
        a.letters.send(fresh, emptyList()); exchange(a, c)
        assertEquals(listOf(fresh.id), c.db.letterDao().allActive().map { it.id })
        assertNull(c.attachments.read(photo)); assertEquals(0, cloud.blobCount())
        assertEquals(3L, c.db.syncDao().cursor("device-a:app"))
    }

    @Test fun `새 상대가 보낸 같은 id도 보관 편지 일정과 첨부를 덮어쓰지 못한다`() = runTest {
        val a = Phone("a"); val b = Phone("b"); val c = Phone("c")
        link(a, b)
        val photo = a.attachments.put(AttachmentKind.PHOTO, "original".encodeToByteArray())
        val old = a.letters.newLetter(listOf(LetterBlock.Photo(photo)))
        a.letters.send(old, emptyList()); a.events.save(event("collision")); exchange(a, b)
        a.unlink.unlink(AfterUnlink.KEEP_AS_MEMORIES); link(a, c)
        c.attachments.putWithId(photo, AttachmentKind.PHOTO, "replacement".encodeToByteArray())
        c.letters.send(old.copy(authorId = "c"), emptyList())
        c.events.save(event("collision").copy(title = "replacement"))
        c.sync.writeFullSnapshot(); exchange(c, a)
        assertContentEquals("original".encodeToByteArray(), a.attachments.read(photo))
        assertEquals("a", a.letters.find(old.id)?.authorId)
        assertEquals("collision", a.events.get("collision")?.title)
    }

    @Test fun `진행 중인 동기화가 끝나기 전에는 관계와 키를 바꾸지 않는다`() = runTest {
        val a = Phone("a"); val b = Phone("b"); val c = Phone("c")
        link(a, b)
        val oldCouple = a.db.coupleDao().get()!!.id
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val removedOnServer = CompletableDeferred<Unit>()
        val slowMailbox = object : Mailbox by cloud {
            override suspend fun fetch(coupleId: String, myDevice: String): List<MailItem> {
                entered.complete(Unit)
                release.await()
                return cloud.fetch(coupleId, myDevice)
            }
        }
        val slowSync = SyncClient(a.db, a.identity, a.vault, slowMailbox, cloud, a.attachments, InboxApplier(a.db), a.writer)
        val account = object : AccountTransport by cloud {
            override suspend fun unlinkCouple(coupleId: String) {
                cloud.unlinkCouple(coupleId)
                removedOnServer.complete(Unit)
            }
        }
        val syncing = async { slowSync.syncOnce() }
        entered.await()
        val unlinkStarted = CompletableDeferred<Unit>()
        val unlinking = async {
            unlinkStarted.complete(Unit)
            UnlinkService(a.db, a.vault, a.settings, account).unlink(AfterUnlink.KEEP_AS_MEMORIES)
        }
        unlinkStarted.await()
        assertFalse(unlinking.isCompleted)
        assertFalse(removedOnServer.isCompleted)
        assertEquals(oldCouple, a.db.coupleDao().get()?.id)
        assertNotNull(a.vault.currentCoupleKey())
        release.complete(Unit)
        syncing.await(); unlinking.await()
        assertNull(a.vault.currentCoupleKey())
        link(a, c)
        val fresh = a.letters.newLetter(listOf(LetterBlock.Text("only C")))
        a.letters.send(fresh, emptyList()); exchange(a, c)
        assertNotNull(c.letters.find(fresh.id))
        assertTrue(cloud.fetch(oldCouple, b.identity.deviceId).isEmpty())
    }

    @Test fun `늦은 서버 해제 응답과 이전 상대 알림이 새 연결을 지우지 않는다`() = runTest {
        val a = Phone("a"); val b = Phone("b"); val c = Phone("c")
        link(a, b)
        val oldCouple = a.db.coupleDao().get()!!.id
        val serverEntered = CompletableDeferred<Unit>()
        val serverRelease = CompletableDeferred<Unit>()
        val slowAccount = object : AccountTransport by cloud {
            override suspend fun unlinkCouple(coupleId: String) {
                cloud.unlinkCouple(coupleId)
                serverEntered.complete(Unit)
                serverRelease.await()
            }
        }
        val unlinking = async { UnlinkService(a.db, a.vault, a.settings, slowAccount).unlink(AfterUnlink.KEEP_AS_MEMORIES) }
        serverEntered.await()
        val partnerCleanup = async { a.unlink.onPartnerUnlinked(AfterUnlink.KEEP_AS_MEMORIES, oldCouple) }
        assertFalse(partnerCleanup.isCompleted)
        serverRelease.complete(Unit)
        unlinking.await(); partnerCleanup.await()
        link(a, c)
        val newCouple = a.db.coupleDao().get()!!.id
        val fresh = a.letters.newLetter(listOf(LetterBlock.Text("new relationship")))
        a.letters.send(fresh, emptyList())
        a.unlink.onPartnerUnlinked(AfterUnlink.WIPE, oldCouple)
        assertEquals(newCouple, a.db.coupleDao().get()?.id)
        assertNotNull(a.vault.currentCoupleKey())
        exchange(a, c)
        assertNotNull(c.letters.find(fresh.id))
    }
}
