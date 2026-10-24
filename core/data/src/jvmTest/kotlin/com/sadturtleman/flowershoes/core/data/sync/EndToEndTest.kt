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
import com.sadturtleman.flowershoes.core.data.SyncWriter
import com.sadturtleman.flowershoes.core.database.AppDatabase
import com.sadturtleman.flowershoes.core.database.buildKkotsin
import com.sadturtleman.flowershoes.core.database.inMemoryDatabaseBuilder
import com.sadturtleman.flowershoes.core.model.AttachmentKind
import com.sadturtleman.flowershoes.core.model.Branch
import com.sadturtleman.flowershoes.core.model.LetterBlock
import com.sadturtleman.flowershoes.core.model.ReceiptEntity
import com.sadturtleman.flowershoes.core.model.Role
import com.sadturtleman.flowershoes.core.model.Service
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import okio.FileSystem
import kotlin.uuid.Uuid
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        val code = assertIs<PairingState.ShowCode>(jisu.pairing.start()).code
        assertIs<PairingState.WaitingPartner>(minjun.pairing.join(code.lowercase()))
        jisu.pairing.poll() // join 을 보고 공개키 공개
        val sasB = assertIs<PairingState.CompareSas>(minjun.pairing.poll()).sas
        val sasA = assertIs<PairingState.CompareSas>(jisu.pairing.poll()).sas
        assertEquals(sasA, sasB, "두 화면의 확인 코드가 같아야 한다")
        jisu.pairing.confirm()
        assertIs<PairingState.Linked>(minjun.pairing.confirm())
        assertIs<PairingState.Linked>(jisu.pairing.poll())
    }

    private suspend fun roundTrip() {
        jisu.sync.syncOnce()
        minjun.sync.syncOnce()
        jisu.sync.syncOnce()
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
    fun `잘못된 코드 형식은 바로 거부한다`() = runTest {
        assertTrue(minjun.pairing.join("12U") is PairingState.Failed)
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
    fun `탈퇴하면 이 폰의 데이터 · 키 · 설정이 모두 지워진다`() = runTest {
        link()
        jisu.profile.saveMe(com.sadturtleman.flowershoes.core.model.Role.GOMSIN, "지수")
        jisu.letters.send(jisu.letters.newLetter(listOf(LetterBlock.Text("안녕"))), emptyList())
        val store = androidx.datastore.preferences.core.PreferenceDataStoreFactory.createWithPath { (root / "del-settings.preferences_pb") }
        val settings = com.sadturtleman.flowershoes.core.data.DeviceSettings(store)
        settings.ensureIdentity()
        var syncedKeyDeleted = false
        UnlinkService(jisu.db, jisu.vault, settings, cloud).deleteAccount { syncedKeyDeleted = true }

        assertNull(jisu.vault.deviceKeys())
        assertNull(jisu.vault.currentCoupleKey())
        assertEquals(0, jisu.letters.observeInbox().first().size)
        assertNull(jisu.db.userDao().get("jisu"))
        assertNull(jisu.db.coupleDao().get())
        assertEquals("", settings.state.first().userId, "다음 실행은 처음 화면부터")
        assertTrue(syncedKeyDeleted)
        assertEquals(1, cloud.unlinked.size + 0, "연결 해제와 별개로 서버 탈퇴 callable 이 불렸다")
    }
}
