package com.sadturtleman.flowershoes.core.data.sync

import com.sadturtleman.flowershoes.core.crypto.Sodium
import com.sadturtleman.flowershoes.core.data.AttachmentStore
import com.sadturtleman.flowershoes.core.data.DrawingMeta
import com.sadturtleman.flowershoes.core.data.Identity
import com.sadturtleman.flowershoes.core.data.InboxApplier
import com.sadturtleman.flowershoes.core.data.LetterRepository
import com.sadturtleman.flowershoes.core.data.MissYouRepository
import com.sadturtleman.flowershoes.core.data.ProfileRepository
import com.sadturtleman.flowershoes.core.data.PENDING_SOLDIER
import com.sadturtleman.flowershoes.core.data.OpCodec
import com.sadturtleman.flowershoes.core.data.OpPayload
import com.sadturtleman.flowershoes.core.data.PhotoRef
import com.sadturtleman.flowershoes.core.data.SyncWriter
import com.sadturtleman.flowershoes.core.database.AppDatabase
import com.sadturtleman.flowershoes.core.database.RevokedDeviceEntity
import com.sadturtleman.flowershoes.core.database.buildKkotsin
import com.sadturtleman.flowershoes.core.database.inMemoryDatabaseBuilder
import com.sadturtleman.flowershoes.core.model.AttachmentKind
import com.sadturtleman.flowershoes.core.model.Branch
import com.sadturtleman.flowershoes.core.model.LetterBlock
import com.sadturtleman.flowershoes.core.model.ReceiptEntity
import com.sadturtleman.flowershoes.core.model.Role
import com.sadturtleman.flowershoes.core.model.Service
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import okio.FileSystem
import okio.ByteString.Companion.toByteString
import kotlin.uuid.Uuid
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

/**
 * 두 폰 + 메모리 서버로 첫 연결부터 편지 · 그림 · 읽음 · 커서 · 바꿔치기 거부까지 끝까지 돌린다.
 */
class EndToEndTest {

    private val cloud = InMemoryCloud()
    private val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "kkotsin-e2e-${Uuid.random()}"

    private inner class Phone(val userId: String, val deviceId: String) {
        val db: AppDatabase = inMemoryDatabaseBuilder().buildKkotsin()
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
        val pairing = PairingSession(db, identity, vault, cloud)
        val sync = SyncClient(db, identity, vault, cloud, cloud, attachments, InboxApplier(db), writer)
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
    fun tearDown() {
        jisu.db.close()
        minjun.db.close()
        FileSystem.SYSTEM.deleteRecursively(root)
    }

    private suspend fun link() {
        val token = assertIs<PairingState.Waiting>(jisu.pairing.createLink("지수")).token
        assertIs<PairingState.Linked>(minjun.pairing.accept(token))
        assertIs<PairingState.Linked>(jisu.pairing.poll())
    }

    private suspend fun roundTrip() {
        jisu.sync.syncOnce()
        minjun.sync.syncOnce()
        jisu.sync.syncOnce()
    }

    @Test
    fun `첨부 수신 취소는 우편함을 지우지 않고 호출자에게 전달한다`() = runTest {
        link()
        val couple = jisu.db.coupleDao().get()!!
        val letter = jisu.letters.newLetter(listOf(LetterBlock.Photo("first"), LetterBlock.Photo("photo")))
        val key = ByteArray(32)
        val payload = OpPayload.LetterPut(letter, photos = listOf(
            PhotoRef("first", "first-blob", key.toByteString().base64()),
            PhotoRef("photo", "blob", key.toByteString().base64()),
        ))
        val id = Uuid.random().toString()
        val header = com.sadturtleman.flowershoes.core.crypto.OpHeader(id, "dev-a:app", 1, 1)
        val env = com.sadturtleman.flowershoes.core.crypto.OpEnvelope.seal(
            header, Plain(1, OpCodec.encode(payload)).encode(),
            jisu.vault.currentCoupleKey()!!, jisu.vault.deviceKeys()!!,
        )
        val item = MailItem(id, "letter", "dev-a", header.toBytes(), env.ciphertext, env.signature)
        cloud.put(couple.id, minjun.deviceId, item)
        val firstPhoto = "먼저 받은 사진".encodeToByteArray()
        cloud.put(couple.id, "first-blob", com.sadturtleman.flowershoes.core.crypto.Aead.seal(key, firstPhoto))
        val cancelledBlobs = object : BlobStore by cloud {
            override suspend fun get(coupleId: String, blobId: String): ByteArray? =
                if (blobId == "blob") throw CancellationException("test cancellation") else cloud.get(coupleId, blobId)
        }
        val receiver = SyncClient(minjun.db, minjun.identity, minjun.vault, cloud, cancelledBlobs, minjun.attachments, InboxApplier(minjun.db), minjun.writer)

        assertFailsWith<CancellationException> { receiver.syncOnce() }
        assertEquals(1, cloud.inboxSize(couple.id, minjun.deviceId))
        assertNull(minjun.db.letterDao().get(letter.id))
        assertContentEquals(firstPhoto, minjun.attachments.read("first"))
        assertFailsWith<CancellationException> { receiver.receiveQueued(item) }
        assertEquals(1, cloud.inboxSize(couple.id, minjun.deviceId))
        val entered = CompletableDeferred<Unit>()
        val slowBlobs = object : BlobStore by cloud {
            override suspend fun get(coupleId: String, blobId: String): ByteArray? {
                entered.complete(Unit)
                awaitCancellation()
            }
        }
        val slowReceiver = SyncClient(minjun.db, minjun.identity, minjun.vault, cloud, slowBlobs, minjun.attachments, InboxApplier(minjun.db), minjun.writer)
        val job = async { slowReceiver.syncOnce() }
        entered.await()
        job.cancelAndJoin()
        assertEquals(1, cloud.inboxSize(couple.id, minjun.deviceId))
        val photo = "다시 받은 사진".encodeToByteArray()
        cloud.put(couple.id, "blob", com.sadturtleman.flowershoes.core.crypto.Aead.seal(key, photo))
        minjun.sync.syncOnce()
        assertNotNull(minjun.db.letterDao().get(letter.id))
        assertContentEquals(firstPhoto, minjun.attachments.read("first"))
        assertContentEquals(photo, minjun.attachments.read("photo"))
        assertEquals(0, cloud.inboxSize(couple.id, minjun.deviceId))
    }

    @Test
    fun `서명한 기기와 다른 스트림으로 폐기 검사를 우회하지 못한다`() = runTest {
        link()
        minjun.db.syncDao().addRevoked(RevokedDeviceEntity("dev-a", 0, """{"dev-a:app":0}"""))
        val letter = jisu.letters.newLetter(listOf(LetterBlock.Text("차단해야 하는 편지")))
        val id = Uuid.random().toString()
        val header = com.sadturtleman.flowershoes.core.crypto.OpHeader(id, "other-device:app", 1, 1)
        val env = com.sadturtleman.flowershoes.core.crypto.OpEnvelope.seal(header, Plain(1, OpCodec.encode(OpPayload.LetterPut(letter))).encode(), jisu.vault.currentCoupleKey()!!, jisu.vault.deviceKeys()!!)
        val item = MailItem(id, "letter", "dev-a", header.toBytes(), env.ciphertext, env.signature)
        assertEquals(0, minjun.sync.receiveQueued(listOf(item)))
        assertNull(minjun.db.letterDao().get(letter.id))
        assertNull(minjun.db.syncDao().cursor("other-device:app"))
    }

    @Test
    fun `상대가 아닌 내 기기의 메시지는 상대 메시지로 반영하지 않는다`() = runTest {
        link()
        val letter = minjun.letters.newLetter(listOf(LetterBlock.Text("내 기기")))
        val id = Uuid.random().toString()
        val header = com.sadturtleman.flowershoes.core.crypto.OpHeader(id, "dev-b:app", 1, 1)
        val env = com.sadturtleman.flowershoes.core.crypto.OpEnvelope.seal(header, Plain(1, OpCodec.encode(OpPayload.LetterPut(letter))).encode(), minjun.vault.currentCoupleKey()!!, minjun.vault.deviceKeys()!!)
        assertEquals(0, minjun.sync.receiveQueued(listOf(MailItem(id, "letter", "dev-b", header.toBytes(), env.ciphertext, env.signature))))
        assertNull(minjun.db.letterDao().get(letter.id))
    }

    @Test
    fun `번호가 0인 메시지는 반영하지 않는다`() = runTest {
        link()
        val letter = jisu.letters.newLetter(listOf(LetterBlock.Text("잘못된 번호")))
        val id = Uuid.random().toString()
        val header = com.sadturtleman.flowershoes.core.crypto.OpHeader(id, "dev-a:app", 0, 1)
        val env = com.sadturtleman.flowershoes.core.crypto.OpEnvelope.seal(header, Plain(1, OpCodec.encode(OpPayload.LetterPut(letter))).encode(), jisu.vault.currentCoupleKey()!!, jisu.vault.deviceKeys()!!)
        assertEquals(0, minjun.sync.receiveQueued(listOf(MailItem(id, "letter", "dev-a", header.toBytes(), env.ciphertext, env.signature))))
        assertNull(minjun.db.letterDao().get(letter.id))
    }

    @Test
    fun `경로를 벗어나는 첨부는 건너뛰고 뒤의 정상 편지는 계속 받는다`() = runTest {
        link()
        val couple = jisu.db.coupleDao().get()!!
        val outside = root / "outside"
        FileSystem.SYSTEM.createDirectories(root)
        FileSystem.SYSTEM.write(outside) { writeUtf8("original") }
        val key = Sodium.random(com.sadturtleman.flowershoes.core.crypto.Aead.KEY_BYTES)
        val encrypted = com.sadturtleman.flowershoes.core.crypto.Aead.seal(key, "changed".encodeToByteArray())
        cloud.put(couple.id, "blob-safe", encrypted)
        cloud.put(couple.id, "../bad-blob", encrypted)
        cloud.put(couple.id, "valid-blob", encrypted)
        val bad = jisu.letters.newLetter(listOf(LetterBlock.Text("잘못된 첨부"), LetterBlock.Photo("../outside"), LetterBlock.Photo("safe-ref"), LetterBlock.Photo("valid-photo")))
        val good = jisu.letters.newLetter(listOf(LetterBlock.Text("다음 편지")))
        suspend fun deliver(seq: Long, payload: OpPayload) {
            val opId = Uuid.random().toString()
            val header = com.sadturtleman.flowershoes.core.crypto.OpHeader(opId, "dev-a:app", seq, 1)
            val env = com.sadturtleman.flowershoes.core.crypto.OpEnvelope.seal(
                header, Plain(seq, OpCodec.encode(payload)).encode(),
                jisu.vault.currentCoupleKey()!!, jisu.vault.deviceKeys()!!,
            )
            cloud.put(couple.id, "dev-b", MailItem(opId, "letter", "dev-a", header.toBytes(), env.ciphertext, env.signature))
        }
        deliver(1, OpPayload.LetterPut(bad, photos = listOf(
            PhotoRef("../outside", "blob-safe", key.toByteString().base64()),
            PhotoRef("safe-ref", "../bad-blob", key.toByteString().base64()),
            PhotoRef("valid-photo", "valid-blob", key.toByteString().base64()),
        )))
        deliver(2, OpPayload.LetterPut(good))
        minjun.sync.syncOnce()
        assertContentEquals("original".encodeToByteArray(), FileSystem.SYSTEM.read(outside) { readByteArray() })
        assertNull(minjun.attachments.read("../outside"))
        assertNull(minjun.attachments.read("safe-ref"))
        assertContentEquals("changed".encodeToByteArray(), minjun.attachments.read("valid-photo"))
        assertNotNull(minjun.db.letterDao().get(bad.id))
        assertNotNull(minjun.db.letterDao().get(good.id))
        assertEquals(2L, minjun.db.syncDao().cursors().first { it.stream == "dev-a:app" }.contiguousSeq)
    }

    private suspend fun widgetItem(seq: Long): MailItem {
        val id = Uuid.random().toString()
        val header = com.sadturtleman.flowershoes.core.crypto.OpHeader(id, "dev-a:widget", seq, 1)
        val payload = com.sadturtleman.flowershoes.core.data.OpPayload.MissYouPut(
            com.sadturtleman.flowershoes.core.model.MissYou(id, "jisu", kotlin.time.Clock.System.now()),
        )
        val envelope = com.sadturtleman.flowershoes.core.crypto.OpEnvelope.seal(
            header, Plain(seq, com.sadturtleman.flowershoes.core.data.OpCodec.encode(payload)).encode(),
            jisu.vault.currentCoupleKey()!!, jisu.vault.deviceKeys()!!,
        )
        return MailItem(id, "missyou", "dev-a", header.toBytes(), envelope.ciphertext, envelope.signature)
    }

    @Test
    fun `오프라인 위젯 사본은 순서가 뒤바뀌고 앱이 다시 읽어도 한 번씩 전송된다`() = runTest {
        link()
        val couple = jisu.db.coupleDao().get()!!.id
        val first = widgetItem(1)
        val second = widgetItem(2)
        assertTrue(jisu.sync.importWidgetOp(couple, second))
        assertTrue(jisu.sync.importWidgetOp(couple, second))
        roundTrip()
        assertTrue(jisu.sync.importWidgetOp(couple, first))
        roundTrip()
        assertTrue(jisu.sync.importWidgetOp(couple, first)) // ACK 뒤 재시작해도 다시 쌓이지 않는다.
        roundTrip()
        assertEquals(2, jisu.db.missYouDao().all().size)
        assertEquals(2, minjun.db.missYouDao().all().size)
        assertEquals(2L, minjun.db.syncDao().cursor("dev-a:widget"))
        assertTrue(jisu.db.syncDao().log("dev-a:widget").isEmpty())
    }

    @Test
    fun `다른 연결과 변조된 위젯 사본은 전송 로그에 들어가지 않는다`() = runTest {
        link()
        val item = widgetItem(1)
        assertEquals(false, jisu.sync.importWidgetOp("other-couple", item))
        val bad = MailItem(item.opId, item.kind, item.from, item.header, item.ciphertext + byteArrayOf(1), item.signature)
        assertTrue(runCatching { jisu.sync.importWidgetOp(jisu.db.coupleDao().get()!!.id, bad) }.isFailure)
        assertTrue(jisu.db.missYouDao().all().isEmpty())
        assertTrue(jisu.db.syncDao().log("dev-a:widget").isEmpty())
    }

    @Test
    fun `연결하면 양쪽이 같은 커플 키를 갖는다`() = runTest {
        link()
        assertContentEquals(jisu.vault.currentCoupleKey()!!.export(), minjun.vault.currentCoupleKey()!!.export())
        assertEquals("minjun", jisu.db.coupleDao().get()?.partnerUserId)
        assertEquals("jisu", minjun.db.coupleDao().get()?.partnerUserId)
    }

    @Test
    fun `혼자 쓴 편지 · 그림은 연결 뒤 첫 동기화까지 보낼 것으로 남는다`() = runTest {
        val doc = byteArrayOf(0x4B, 0x4B, 0x44, 1)
        val attachmentId = jisu.attachments.put(AttachmentKind.DRAWING_DOC, doc)
        jisu.letters.send(jisu.letters.newLetter(listOf(LetterBlock.Text("처음"))), emptyList())
        val withDrawing = jisu.letters.newLetter(listOf(LetterBlock.Text("그림도"), LetterBlock.DrawingRef("d1")))
        jisu.letters.send(withDrawing, listOf(DrawingMeta("d1", 0, "SQUARE", attachmentId, attachmentId, null, true, 4000)))
        jisu.letters.send(withDrawing, listOf(DrawingMeta("d1", 0, "SQUARE", attachmentId, attachmentId, null, true, 4000)))
        jisu.profile.saveMe(Role.GOMSIN, "지수")

        val before = jisu.sync.outbox()
        assertEquals(2, before.letters, "같은 편지를 두 번 고쳐도 한 통")
        assertEquals(1, before.drawings)
        assertTrue(before.ops >= 4)

        link()
        jisu.sync.syncOnce()
        assertEquals(Outbox(0, 0, 0), jisu.sync.outbox())
    }

    @Test
    fun `그림 편지가 첨부까지 암호화돼 건너가고 읽음이 돌아온다`() = runTest {
        link()
        val doc = byteArrayOf(0x4B, 0x4B, 0x44, 1, 9, 9, 9)
        val attachmentId = jisu.attachments.put(AttachmentKind.DRAWING_DOC, doc)
        val letter = jisu.letters.newLetter(listOf(LetterBlock.Text("보고 싶어"), LetterBlock.DrawingRef("d1")))
        jisu.letters.send(letter, listOf(DrawingMeta("d1", 0, "SQUARE", attachmentId, attachmentId, null, true, 4000)))

        roundTrip()
        val received = minjun.letters.observeInbox().first().single()
        assertEquals("보고 싶어", (received.blocks.first() as LetterBlock.Text).text)
        assertContentEquals(doc, minjun.attachments.read(attachmentId))
        assertEquals(0, cloud.blobCount(), "받은 blob 은 서버에서 지운다")
        assertEquals(0, cloud.inboxSize("couple-${jisu.db.coupleDao().get()!!.id.removePrefix("couple-")}", "dev-b"))

        minjun.letters.markRead(ReceiptEntity.LETTER, letter.id)
        roundTrip()
        assertEquals("minjun", jisu.db.receiptDao().get("LETTER", letter.id, "minjun")?.readerId)
    }

    @Test
    fun `상대가 확인한 Op 은 보낸 쪽 로그에서 지운다`() = runTest {
        link()
        repeat(3) { jisu.missYou.send() }
        jisu.sync.syncOnce()
        assertEquals(3, jisu.db.syncDao().log("dev-a:app").size)
        minjun.sync.syncOnce() // 받고 커서를 올린다
        jisu.sync.syncOnce() // 커서를 읽고 로그를 지운다
        assertEquals(0, jisu.db.syncDao().log("dev-a:app").size)
        assertEquals(3, minjun.missYou.observeTodayFrom("jisu").first())
    }

    @Test
    fun `서버가 암호문을 바꿔치기하면 반영하지 않는다`() = runTest {
        link()
        cloud.tamper = { item -> MailItem(item.opId, item.kind, item.from, item.header, item.ciphertext.copyOf().also { it[it.size - 1] = (it.last() + 1).toByte() }, item.signature) }
        jisu.missYou.send()
        jisu.sync.syncOnce()
        val report = minjun.sync.syncOnce()
        assertEquals(1, report.rejected)
        assertEquals(0, minjun.missYou.observeTodayFrom("jisu").first())
    }

    @Test
    fun `연결 전 곰신이 넣은 군 정보는 군인이 넣은 값에 진다`() = runTest {
        // 지수(곰신)가 혼자 모드에서 민준의 입대일을 넣었고, 민준(군인)도 직접 넣었다.
        jisu.profile.saveMe(Role.GOMSIN, "지수")
        jisu.profile.saveService(Service(PENDING_SOLDIER, Branch.ARMY, LocalDate.parse("2026-04-01"), 1))
        minjun.profile.saveMe(Role.SOLDIER, "민준")
        minjun.profile.saveService(Service("minjun", Branch.ARMY, LocalDate.parse("2026-04-16"), 1))
        link()
        roundTrip()
        assertNull(jisu.db.serviceDao().get(PENDING_SOLDIER))
        assertEquals("2026-04-16", jisu.db.serviceDao().get("minjun")?.enlistDate)
        assertEquals("2026-04-16", minjun.db.serviceDao().get("minjun")?.enlistDate)
    }

    @Test
    fun `연결을 해제하면 키 · 동기화 상태가 지워지고 추억은 고른 대로 남는다`() = runTest {
        link()
        val letter = jisu.letters.newLetter(listOf(LetterBlock.Text("추억")))
        jisu.letters.send(letter, emptyList())
        roundTrip()

        val settingsStore = androidx.datastore.preferences.core.PreferenceDataStoreFactory.createWithPath {
            (root / "jisu-settings.preferences_pb")
        }
        UnlinkService(jisu.db, jisu.vault, com.sadturtleman.flowershoes.core.data.DeviceSettings(settingsStore), cloud).unlink(AfterUnlink.KEEP_AS_MEMORIES)
        assertNull(jisu.vault.currentCoupleKey())
        assertEquals(emptyList(), jisu.db.syncDao().log("dev-a:app"))
        assertEquals(1, jisu.letters.observeInbox().first().size, "추억으로 보관")
        assertEquals("", jisu.db.coupleDao().get()?.partnerUserId)
        assertEquals(1, cloud.unlinked.size)

        // 민준은 해제 알림을 받고 모두 지우기를 골랐다.
        val minjunSettings = androidx.datastore.preferences.core.PreferenceDataStoreFactory.createWithPath { (root / "minjun-settings.preferences_pb") }
        UnlinkService(minjun.db, minjun.vault, com.sadturtleman.flowershoes.core.data.DeviceSettings(minjunSettings), null).onPartnerUnlinked(AfterUnlink.WIPE)
        assertEquals(0, minjun.letters.observeInbox().first().size)
        assertNull(minjun.db.coupleDao().get())
    }

    @Test
    fun `해제 뒤 다시 연결해도 새로 쓴 것이 건너간다`() = runTest {
        link()
        // 첫 연결에서 여러 개를 올려 둔다 — 올린 위치(uploaded)가 앞서 있게.
        repeat(3) { jisu.letters.send(jisu.letters.newLetter(listOf(LetterBlock.Text("처음 $it"))), emptyList()) }
        roundTrip()
        val jisuSettings = androidx.datastore.preferences.core.PreferenceDataStoreFactory.createWithPath { (root / "jisu-relink.preferences_pb") }
        val minjunSettings = androidx.datastore.preferences.core.PreferenceDataStoreFactory.createWithPath { (root / "minjun-relink.preferences_pb") }
        UnlinkService(jisu.db, jisu.vault, com.sadturtleman.flowershoes.core.data.DeviceSettings(jisuSettings), cloud).unlink(AfterUnlink.KEEP_AS_MEMORIES)
        UnlinkService(minjun.db, minjun.vault, com.sadturtleman.flowershoes.core.data.DeviceSettings(minjunSettings), null).onPartnerUnlinked(AfterUnlink.WIPE)

        link()
        jisu.letters.send(jisu.letters.newLetter(listOf(LetterBlock.Text("다시 만나서"))), emptyList())
        roundTrip()
        assertEquals(listOf("다시 만나서"), minjun.letters.observeInbox().first().map { (it.blocks.first() as LetterBlock.Text).text })
    }

    @Test
    fun `Apple 폐기 결과에 따라 탈퇴 완료 안내를 보관한다`() = runTest {
        for ((index, outcome) in listOf(AppleRevocation.MANUAL, AppleRevocation.UNKNOWN, AppleRevocation.REVOKED).withIndex()) {
            val store = androidx.datastore.preferences.core.PreferenceDataStoreFactory.createWithPath { root / "apple-outcome-$index.preferences_pb" }
            val settings = com.sadturtleman.flowershoes.core.data.DeviceSettings(store)
            settings.bindAccount("jisu", "APPLE")
            val transport = object : AccountTransport {
                override suspend fun unlinkCouple(coupleId: String) = Unit
                override suspend fun deleteAccount() = Unit
                override suspend fun deleteAccountWithOutcome() = outcome
            }
            UnlinkService(jisu.db, jisu.vault, settings, transport).deleteAccount()
            val reopened = com.sadturtleman.flowershoes.core.data.DeviceSettings(store)
            assertEquals(true, reopened.farewellPending.first())
            assertEquals(outcome != AppleRevocation.REVOKED, reopened.farewellAppleInstructions.first())
            reopened.setFarewellPending(false)
            assertEquals(false, reopened.farewellAppleInstructions.first())
        }
    }

    @Test
    fun `탈퇴하면 이 폰의 데이터 · 키 · 설정이 모두 지워진다`() = runTest {
        link()
        jisu.profile.saveMe(com.sadturtleman.flowershoes.core.model.Role.GOMSIN, "지수")
        jisu.letters.send(jisu.letters.newLetter(listOf(LetterBlock.Text("안녕"))), emptyList())
        val store = androidx.datastore.preferences.core.PreferenceDataStoreFactory.createWithPath { (root / "del-settings.preferences_pb") }
        val settings = com.sadturtleman.flowershoes.core.data.DeviceSettings(store)
        settings.ensureIdentity()
        UnlinkService(jisu.db, jisu.vault, settings, cloud).deleteAccount()

        assertNull(jisu.vault.deviceKeys())
        assertNull(jisu.vault.currentCoupleKey())
        assertEquals(0, jisu.letters.observeInbox().first().size)
        assertNull(jisu.db.userDao().get("jisu"))
        assertNull(jisu.db.coupleDao().get())
        assertEquals("", settings.state.first().userId, "다음 실행은 처음 화면부터")
        assertEquals(true, settings.farewellPending.first(), "처음 화면 앞에 탈퇴 완료 화면을 한 번 보인다")
        settings.setFarewellPending(false)
        assertEquals(false, settings.farewellPending.first())
        assertEquals(1, cloud.unlinked.size + 0, "연결 해제와 별개로 서버 탈퇴 callable 이 불렸다")
    }
}
