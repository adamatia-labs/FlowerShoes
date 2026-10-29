package com.sadturtleman.flowershoes.core.data.sync

import com.sadturtleman.flowershoes.core.crypto.Sodium
import com.sadturtleman.flowershoes.core.data.AttachmentStore
import com.sadturtleman.flowershoes.core.data.DrawingMeta
import com.sadturtleman.flowershoes.core.data.EventRepository
import com.sadturtleman.flowershoes.core.data.Identity
import com.sadturtleman.flowershoes.core.data.InboxApplier
import com.sadturtleman.flowershoes.core.data.LeaveRepository
import com.sadturtleman.flowershoes.core.data.LetterRepository
import com.sadturtleman.flowershoes.core.data.MissYouRepository
import com.sadturtleman.flowershoes.core.data.ProfileRepository
import com.sadturtleman.flowershoes.core.data.SyncWriter
import com.sadturtleman.flowershoes.core.data.toModel
import com.sadturtleman.flowershoes.core.database.AppDatabase
import com.sadturtleman.flowershoes.core.database.buildKkotsin
import com.sadturtleman.flowershoes.core.database.inMemoryDatabaseBuilder
import com.sadturtleman.flowershoes.core.drawing.CanvasSpec
import com.sadturtleman.flowershoes.core.drawing.DrawingCodec
import com.sadturtleman.flowershoes.core.drawing.DrawingDocument
import com.sadturtleman.flowershoes.core.drawing.PhotoBackground
import com.sadturtleman.flowershoes.core.drawing.Rect
import com.sadturtleman.flowershoes.core.drawing.WidgetShape
import com.sadturtleman.flowershoes.core.model.AttachmentKind
import com.sadturtleman.flowershoes.core.model.Branch
import com.sadturtleman.flowershoes.core.model.Event
import com.sadturtleman.flowershoes.core.model.EventType
import com.sadturtleman.flowershoes.core.model.LeaveGrant
import com.sadturtleman.flowershoes.core.model.LeaveKind
import com.sadturtleman.flowershoes.core.model.LetterBlock
import com.sadturtleman.flowershoes.core.model.MeetNote
import com.sadturtleman.flowershoes.core.model.ReceiptEntity
import com.sadturtleman.flowershoes.core.model.Role
import com.sadturtleman.flowershoes.core.model.Service
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import okio.FileSystem
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * 상대 폰 재전송(기획 §5 · §9-6): 민준이 폰을 잃어버렸다. 지수 폰(A)에 있는 공유 데이터 전부가
 * 넘겨주기 뒤 새 폰(B)으로 와야 한다 — 편지 · 일정(메모 · 그날의 한 줄) · 휴가 · 기념일 · 프로필 · 보고싶어 ·
 * 읽음 · 사진 · 그림 · 추억. 민준 폰에만 있던 원본(그림 · 사진)도 지수 폰이 받아 둔 사본으로 다시 온다.
 *
 * [InMemoryCloud] 는 실제 규칙의 크기 한계(우편함 64KB · Storage 10MB)를 그대로 건다.
 */
class PartnerResendTest {

    private val cloud = InMemoryCloud()
    private val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "kkotsin-resend-${Uuid.random()}"

    private inner class Phone(val userId: String, val deviceId: String, val db: AppDatabase = inMemoryDatabaseBuilder().buildKkotsin()) {
        val vault = InMemoryKeyVault()
        val identity = object : Identity {
            override val userId = this@Phone.userId
            override val deviceId = this@Phone.deviceId
            override val keyEpoch = 1
        }
        val writer = SyncWriter(db, identity)
        val attachments = AttachmentStore(db, root / deviceId)
        val letters = LetterRepository(db, writer, identity)
        val missYou = MissYouRepository(db, writer, identity)
        val profile = ProfileRepository(db, writer, identity)
        val events = EventRepository(db, writer, identity)
        val leaves = LeaveRepository(db, writer, identity)
        val pairing = PairingSession(db, identity, vault, cloud)
        val sync = SyncClient(db, identity, vault, cloud, cloud, attachments, InboxApplier(db), writer)
        val relink = RelinkSession(db, identity, vault, cloud.relinks, sync)
    }

    private lateinit var jisu: Phone
    private lateinit var minjun: Phone

    @BeforeTest
    fun setUp() {
        Sodium.init()
        jisu = Phone("jisu", "dev-a")
        minjun = Phone("minjun", "dev-b")
    }

    @AfterTest
    fun tearDown() = FileSystem.SYSTEM.deleteRecursively(root)

    private suspend fun link() {
        val code = assertIs<PairingState.ShowCode>(jisu.pairing.start()).code
        minjun.pairing.join(code)
        jisu.pairing.poll()
        minjun.pairing.poll()
        jisu.pairing.poll()
        jisu.pairing.confirm()
        minjun.pairing.confirm()
        jisu.pairing.poll()
    }

    private suspend fun settle() = repeat(2) {
        minjun.sync.syncOnce()
        jisu.sync.syncOnce()
    }

    /** 민준의 새 폰: 같은 계정, 빈 DB, 새 기기 키. 지수 폰이 넘겨주고 새 폰이 한 번 동기화한다. */
    private suspend fun handOverToNewPhone(): Phone {
        val newPhone = Phone("minjun", "dev-b2")
        val code = assertIs<PairingState.ShowCode>(jisu.relink.offer()).code
        assertIs<PairingState.WaitingPartner>(newPhone.relink.accept(code))
        jisu.relink.poll()
        newPhone.relink.poll()
        jisu.relink.poll()
        newPhone.relink.confirm()
        assertIs<PairingState.Linked>(jisu.relink.confirm())
        assertIs<PairingState.Linked>(newPhone.relink.poll())
        newPhone.sync.syncOnce()
        return newPhone
    }

    private fun bytes(size: Int, seed: Int) = Random(seed).nextBytes(size)

    @Test
    fun `A 폰의 공유 데이터 전부가 새 B 폰으로 온다`() = runTest {
        link()

        // 프로필 · 복무 · 기념일
        jisu.profile.saveMe(Role.GOMSIN, "지수", deviceName = "iPhone 16", perks = setOf("book"))
        minjun.profile.saveMe(Role.SOLDIER, "민준", deviceName = "Galaxy S25", perks = setOf("lifetime"))
        minjun.profile.saveService(Service("minjun", Branch.ARMY, LocalDate(2026, 3, 2), rulesVersion = 1))
        jisu.profile.setAnniversary(LocalDate(2024, 5, 18))

        // 민준의 그림 편지: 배경 사진이 있는 그림(문서 · PNG · 사진 첨부 셋). 원본은 민준 폰에만 있다.
        val bgPhoto = bytes(300_000, 1)
        val bgId = minjun.attachments.put(AttachmentKind.PHOTO, bgPhoto)
        val doc = DrawingCodec.encodeForStorage(
            DrawingDocument(canvas = CanvasSpec(WidgetShape.SQUARE), elements = listOf(PhotoBackground("bg", 0, bgId, 0.2f, Rect(0, 0, 1000, 1000))), totalDurationMs = 0),
        )
        val docId = minjun.attachments.put(AttachmentKind.DRAWING_DOC, doc)
        val png = bytes(120_000, 2)
        val pngId = minjun.attachments.put(AttachmentKind.DRAWING_PNG, png)
        val drawingLetter = minjun.letters.newLetter(listOf(LetterBlock.Text("보고 싶어"), LetterBlock.DrawingRef("d1")), title = "주말에")
        minjun.letters.send(
            drawingLetter,
            listOf(DrawingMeta("d1", 0, "SQUARE", docId, pngId, "우리 강아지", true, 3000, showOnWidget = false, photoAttachmentId = bgId)),
        )

        // 지수의 사진 편지. 큰 사진(675KB 넘음)은 Firestore 대신 Storage 로 가는 크기다.
        val letterPhoto = bytes(800_000, 3)
        val letterPhotoId = jisu.attachments.put(AttachmentKind.PHOTO, letterPhoto)
        val photoLetter = jisu.letters.newLetter(listOf(LetterBlock.Text("면회 날"), LetterBlock.Photo(letterPhotoId)), paper = "rose", font = "nanum")
        jisu.letters.send(photoLetter, emptyList())

        // 휴가: 받은 휴가 하나와 그 휴가로 잡은 캘린더 일정
        val grant = LeaveGrant("g1", LeaveKind.REWARD, "포상휴가 · 사격 만발", 4, LocalDate(2026, 9, 1), Clock.System.now(), 0, "")
        minjun.leaves.save(grant)
        minjun.leaves.use(grant, LocalDate(2026, 11, 10), LocalDate(2026, 11, 13), notify = true)

        // 만남 일정: 지수가 메모와 자기 한 줄을 쓰고, 민준이 만남 사진과 자기 한 줄을 더한다.
        val meet = Event(
            id = "e1", type = EventType.VISIT, title = "면회", start = LocalDateTime(2026, 10, 3, 10, 0), end = LocalDateTime(2026, 10, 3, 16, 0),
            alarm = true, updatedAt = Clock.System.now(), lamport = 0, lastWriterDevice = "", memo = "도시락 챙기기",
            meetNotes = mapOf("jisu" to MeetNote("벌써 보고 싶다", 1_000)),
        )
        jisu.events.save(meet)
        settle()
        val meetPhoto = bytes(200_000, 4)
        val meetPhotoId = minjun.attachments.put(AttachmentKind.PHOTO, meetPhoto)
        val received = assertNotNull(minjun.events.get("e1"))
        minjun.events.save(received.copy(photoIds = listOf(meetPhotoId), meetNotes = received.meetNotes + ("minjun" to MeetNote("또 와 줘", 2_000))))

        // 보고싶어 · 읽음
        repeat(2) { jisu.missYou.send() }
        minjun.missYou.send()
        settle()
        jisu.letters.markRead(ReceiptEntity.LETTER, drawingLetter.id)
        jisu.letters.markRead(ReceiptEntity.DRAWING, "d1")
        minjun.letters.markRead(ReceiptEntity.LETTER, photoLetter.id)
        settle()

        // 민준이 폰을 잃어버렸다.
        val b = handOverToNewPhone()

        // 편지
        val inbox = b.letters.observeInbox().first().associateBy { it.id }
        assertEquals(setOf(drawingLetter.id, photoLetter.id), inbox.keys)
        assertEquals(drawingLetter.blocks, inbox.getValue(drawingLetter.id).blocks)
        assertEquals("주말에", inbox.getValue(drawingLetter.id).title)
        assertEquals("rose", inbox.getValue(photoLetter.id).paper)
        assertEquals("nanum", inbox.getValue(photoLetter.id).font)

        // 그림 메타 · 원본
        val drawing = b.letters.drawingsOf(drawingLetter.id).single()
        assertEquals("우리 강아지", drawing.caption)
        assertEquals(false, drawing.showOnWidget)
        assertEquals(3000, drawing.replayDurationMs)
        assertContentEquals(doc, b.attachments.read(docId), "그림 문서")
        assertContentEquals(png, b.attachments.read(pngId), "그림 PNG")
        assertContentEquals(bgPhoto, b.attachments.read(bgId), "그림 배경 사진")

        // 사진
        assertContentEquals(letterPhoto, b.attachments.read(letterPhotoId), "편지 사진(Storage 크기)")
        assertContentEquals(meetPhoto, b.attachments.read(meetPhotoId), "만남 사진")

        // 일정: 메모 · 그날의 한 줄(둘 다) · 사진
        val e1 = assertNotNull(b.events.get("e1"))
        assertEquals("도시락 챙기기", e1.memo)
        assertEquals(mapOf("jisu" to MeetNote("벌써 보고 싶다", 1_000), "minjun" to MeetNote("또 와 줘", 2_000)), e1.meetNotes)
        assertEquals(listOf(meetPhotoId), e1.photoIds)

        // 휴가: 받은 휴가와 사용 일정
        val grants = b.leaves.observeGrants().first()
        assertEquals(listOf("포상휴가 · 사격 만발"), grants.map { it.name })
        assertEquals(listOf("g1"), b.leaves.observeUses().first().map { it.leaveGrantId })

        // 기념일 · 프로필 · 복무
        assertEquals("2024-05-18", b.db.coupleDao().get()?.anniversary)
        val users = b.db.userDao().all().associateBy { it.id }
        assertEquals("지수" to "GOMSIN", users.getValue("jisu").let { it.nickname to it.role })
        assertEquals("민준" to "SOLDIER", users.getValue("minjun").let { it.nickname to it.role })
        assertEquals("iPhone 16", users.getValue("jisu").deviceName)
        assertEquals("book", users.getValue("jisu").perks)
        assertEquals("lifetime", users.getValue("minjun").perks)
        assertEquals(LocalDate(2026, 3, 2), b.db.serviceDao().get("minjun")?.toModel()?.enlistDate)

        // 보고싶어
        assertEquals(2, b.missYou.observeTodayFrom("jisu").first())
        assertEquals(1, b.missYou.observeTodayFrom("minjun").first())

        // 읽음(양쪽)
        assertNotNull(b.db.receiptDao().get("LETTER", drawingLetter.id, "jisu"))
        assertNotNull(b.db.receiptDao().get("DRAWING", "d1", "jisu"))
        assertNotNull(b.db.receiptDao().get("LETTER", photoLetter.id, "minjun"))

        // 추억(편지에서 만든 항목)
        assertEquals(setOf("letter-${drawingLetter.id}", "letter-${photoLetter.id}"), b.db.memoryDao().observeAll().first().map { it.id }.toSet())

        // 받은 blob 은 서버에서 지워졌다.
        assertEquals(0, cloud.blobCount())
    }

    @Test
    fun `편지가 많아 스냅샷이 우편함 한 칸(64KB)을 넘어도 전부 온다`() = runTest {
        link()
        val text = "보고 싶다. ".repeat(60) // 약 1KB(UTF-8)
        val sent = (1..150).map { i ->
            val author = if (i % 2 == 0) jisu else minjun
            author.letters.newLetter(listOf(LetterBlock.Text("$i $text"))).also { author.letters.send(it, emptyList()) }
        }
        settle()

        val b = handOverToNewPhone()

        assertEquals(sent.map { it.id }.toSet(), b.letters.observeInbox().first().map { it.id }.toSet())
    }
}
