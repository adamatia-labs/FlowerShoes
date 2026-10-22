package com.sadturtleman.flowershoes.core.data.sync

import com.sadturtleman.flowershoes.core.crypto.Sodium
import com.sadturtleman.flowershoes.core.data.AttachmentStore
import com.sadturtleman.flowershoes.core.data.DrawingMeta
import com.sadturtleman.flowershoes.core.data.Identity
import com.sadturtleman.flowershoes.core.data.InboxApplier
import com.sadturtleman.flowershoes.core.data.LetterRepository
import com.sadturtleman.flowershoes.core.data.MissYouRepository
import com.sadturtleman.flowershoes.core.data.SyncWriter
import com.sadturtleman.flowershoes.core.database.AppDatabase
import com.sadturtleman.flowershoes.core.database.buildKkotsin
import com.sadturtleman.flowershoes.core.database.inMemoryDatabaseBuilder
import com.sadturtleman.flowershoes.core.model.AttachmentKind
import com.sadturtleman.flowershoes.core.model.LetterBlock
import com.sadturtleman.flowershoes.core.model.ReceiptEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * 상대 폰이 넘겨주기(7-3-B): 민준이 폰을 잃어버렸고 백업도 복구 코드도 없다.
 * 지수 폰이 코드를 만들고, 새 폰(같은 계정)이 입력해 SAS 를 맞춘 뒤 지수 폰이 공유 데이터 전체를 넘겨준다.
 */
class RelinkTest {

    private val cloud = InMemoryCloud()
    private val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "kkotsin-relink-${Uuid.random()}"

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

    private suspend fun handOver(newPhone: Phone): Pair<String, String> {
        val code = assertIs<PairingState.ShowCode>(jisu.relink.offer()).code
        assertIs<PairingState.WaitingPartner>(newPhone.relink.accept(code))
        jisu.relink.poll()
        val sasNew = assertIs<PairingState.CompareSas>(newPhone.relink.poll()).sas
        val sasJisu = assertIs<PairingState.CompareSas>(jisu.relink.poll()).sas
        newPhone.relink.confirm()
        assertIs<PairingState.Linked>(jisu.relink.confirm())
        assertIs<PairingState.Linked>(newPhone.relink.poll())
        return sasJisu to sasNew
    }

    @Test
    fun `지수 폰이 공유 데이터 전체를 새 폰으로 넘겨준다`() = runTest {
        link()
        // 둘이 주고받은 것: 민준의 그림 편지, 지수의 편지, 지수의 읽음, 보고싶어
        val doc = byteArrayOf(0x4B, 0x4B, 0x44, 1, 7)
        val att = minjun.attachments.put(AttachmentKind.DRAWING_DOC, doc)
        val fromMinjun = minjun.letters.newLetter(listOf(LetterBlock.DrawingRef("d1")))
        minjun.letters.send(fromMinjun, listOf(DrawingMeta("d1", 0, "SQUARE", att, att, null, true, 3000)))
        jisu.letters.send(jisu.letters.newLetter(listOf(LetterBlock.Text("기다릴게"))), emptyList())
        repeat(2) { jisu.missYou.send() }
        minjun.sync.syncOnce(); jisu.sync.syncOnce(); minjun.sync.syncOnce()
        jisu.letters.markRead(ReceiptEntity.LETTER, fromMinjun.id)
        jisu.sync.syncOnce()

        // 민준이 폰을 잃어버렸다. 새 폰은 같은 계정, 빈 DB, 새 기기 키.
        val newPhone = Phone("minjun", "dev-b2")
        val (a, b) = handOver(newPhone)
        assertEquals(a, b, "두 화면의 확인 코드가 같아야 한다")

        // 새 커플 키(epoch 2)를 둘이 공유한다.
        assertContentEquals(jisu.vault.currentCoupleKey()!!.export(), newPhone.vault.currentCoupleKey()!!.export())
        assertEquals(2, newPhone.vault.currentCoupleKey()!!.epoch)

        newPhone.sync.syncOnce()
        val inbox = newPhone.letters.observeInbox().first()
        assertEquals(2, inbox.size, "양쪽이 쓴 편지가 모두 넘어온다")
        assertContentEquals(doc, newPhone.attachments.read(att), "그림 문서도 넘어온다")
        assertEquals(2, newPhone.missYou.observeTodayFrom("jisu").first())
        assertEquals("jisu", newPhone.db.receiptDao().get("LETTER", fromMinjun.id, "jisu")?.readerId)

        // 이후 새 폰이 보낸 것도 지수에게 간다.
        newPhone.missYou.send()
        newPhone.sync.syncOnce()
        jisu.sync.syncOnce()
        assertEquals(1, jisu.missYou.observeTodayFrom("minjun").first())
    }

    @Test
    fun `연인 계정이 아닌 사람은 넘겨받을 수 없다`() = runTest {
        link()
        val stranger = Phone("mallory", "dev-x")
        val code = assertIs<PairingState.ShowCode>(jisu.relink.offer()).code
        stranger.relink.accept(code)
        assertTrue(jisu.relink.poll() is PairingState.Failed)
    }
}
