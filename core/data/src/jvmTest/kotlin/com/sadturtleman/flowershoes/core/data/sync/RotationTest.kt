package com.sadturtleman.flowershoes.core.data.sync

import com.sadturtleman.flowershoes.core.crypto.CoupleKey
import com.sadturtleman.flowershoes.core.crypto.DeviceKeys
import com.sadturtleman.flowershoes.core.crypto.Sodium
import com.sadturtleman.flowershoes.core.data.AttachmentStore
import com.sadturtleman.flowershoes.core.data.Identity
import com.sadturtleman.flowershoes.core.data.InboxApplier
import com.sadturtleman.flowershoes.core.data.MissYouRepository
import com.sadturtleman.flowershoes.core.data.SyncWriter
import com.sadturtleman.flowershoes.core.database.AppDatabase
import com.sadturtleman.flowershoes.core.database.buildKkotsin
import com.sadturtleman.flowershoes.core.database.inMemoryDatabaseBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * 기기 교체(7-3-A): 민준이 폰을 잃어버리고 새 폰에서 백업을 복원했다.
 * 새 폰은 백업에 있던 DB · 커플 키를 갖고, 기기 키는 새로 만들었다.
 */
class RotationTest {

    private val cloud = InMemoryCloud()
    private val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "kkotsin-rot-${Uuid.random()}"

    private inner class Phone(
        val userId: String,
        val deviceId: String,
        val db: AppDatabase = inMemoryDatabaseBuilder().buildKkotsin(),
        val vault: KeyVault = InMemoryKeyVault(),
    ) {
        val identity = object : Identity {
            override val userId = this@Phone.userId
            override val deviceId = this@Phone.deviceId
            override val keyEpoch = 1
        }
        val writer = SyncWriter(db, identity)
        val missYou = MissYouRepository(db, writer, identity)
        val pairing = PairingSession(db, identity, vault, cloud)
        val sync = SyncClient(db, identity, vault, cloud, cloud, AttachmentStore(db, root / deviceId), InboxApplier(db), writer)
        val requester = RotationRequester(db, identity, vault, cloud)
        val approver = RotationApprover(db, identity, vault, cloud, drainInbox = { sync.syncOnce() })
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

    /** 백업 복원: 같은 DB 사본 + 백업 속 커플 키, 기기 키는 새로. */
    private suspend fun restoreOnNewPhone(): Phone {
        val vault = InMemoryKeyVault()
        vault.putCoupleKey(CoupleKey(1, minjun.vault.currentCoupleKey()!!.export()))
        vault.putDeviceKeys(DeviceKeys.generate())
        return Phone("minjun", "dev-b2", db = minjun.db, vault = vault)
    }

    @Test
    fun `복원한 새 폰은 상대 폰의 자동 확인을 거쳐 이어진다`() = runTest {
        link()
        val newPhone = restoreOnNewPhone()
        val request = newPhone.requester.request(oldDeviceId = "dev-b")
        assertEquals(null, newPhone.requester.finishIfApproved(request), "상대 확인 전에는 대기")

        val decisions = jisu.approver.check()
        assertIs<RotationDecision.Approved>(decisions.single().second)
        assertEquals(2, jisu.vault.currentCoupleKey()!!.epoch)
        assertNotNull(newPhone.requester.finishIfApproved(request))

        // 이후 지수의 보고싶어는 새 키(epoch 2)로 새 폰에 간다.
        jisu.missYou.send()
        jisu.sync.syncOnce()
        newPhone.sync.syncOnce()
        assertEquals(1, newPhone.missYou.observeTodayFrom("jisu").first())
    }

    @Test
    fun `폐기된 옛 폰이 보낸 Op 은 반영하지 않는다`() = runTest {
        link()
        val newPhone = restoreOnNewPhone()
        val request = newPhone.requester.request("dev-b")
        jisu.approver.check()
        newPhone.requester.finishIfApproved(request)

        // 도난당한 옛 폰(dev-b)이 옛 키로 계속 보내려 한다 — 서버 규칙이 막지 못했다고 가정해 우편함에 직접 넣는다.
        val oldDb = inMemoryDatabaseBuilder().buildKkotsin()
        val thief = Phone("minjun", "dev-b", db = oldDb, vault = minjun.vault)
        thief.missYou.send()
        val op = oldDb.syncDao().log("dev-b:app").single()
        val key = minjun.vault.coupleKey(1)!!
        val header = com.sadturtleman.flowershoes.core.crypto.OpHeader(op.opId, op.stream, op.seq, 1)
        val env = com.sadturtleman.flowershoes.core.crypto.OpEnvelope.seal(header, Plain(op.lamport, op.payload).encode(), key, minjun.vault.deviceKeys()!!)
        cloud.put(jisu.db.coupleDao().get()!!.id, "dev-a", MailItem(op.opId, op.kind, "dev-b", header.toBytes(), env.ciphertext, env.signature))

        jisu.sync.syncOnce()
        assertEquals(0, jisu.db.missYouDao().observeCountSince("minjun", 0).first())
        oldDb.close()
    }

    @Test
    fun `계정만 털린 사람은 커플 키가 없어 교체할 수 없다`() = runTest {
        link()
        val attackerVault = InMemoryKeyVault().apply {
            putCoupleKey(CoupleKey.generate(1)) // 진짜 키를 모른다
            putDeviceKeys(DeviceKeys.generate())
        }
        val attacker = Phone("minjun", "dev-evil", db = minjun.db, vault = attackerVault)
        attacker.requester.request("dev-b")
        assertIs<RotationDecision.Rejected>(jisu.approver.check().single().second)
        assertEquals(1, jisu.vault.currentCoupleKey()!!.epoch, "키는 바뀌지 않는다")
    }

    @Test
    fun `하루에 두 번째 교체 요청은 자동으로 승인하지 않는다`() = runTest {
        link()
        val first = restoreOnNewPhone()
        first.requester.request("dev-b")
        assertIs<RotationDecision.Approved>(jisu.approver.check().single().second)

        // 같은 날 또 다른 기기가(도난 폰이 백업 키까지 갖고 있다고 가정) 교체를 요청한다.
        val second = Phone("minjun", "dev-b3", db = minjun.db, vault = InMemoryKeyVault().apply {
            putCoupleKey(CoupleKey(2, jisu.vault.currentCoupleKey()!!.export()))
            putDeviceKeys(DeviceKeys.generate())
        })
        second.requester.request("dev-b2")
        val decision = jisu.approver.check().single().second
        assertTrue(decision is RotationDecision.NeedsManual)
    }

    @Test
    fun `백업 속 키가 너무 오래되면 상대 넘겨주기로 안내한다`() = runTest {
        link()
        val stale = Phone("minjun", "dev-b2", db = minjun.db, vault = InMemoryKeyVault().apply {
            putCoupleKey(CoupleKey.generate(7)) // 상대에게 없는 epoch
            putDeviceKeys(DeviceKeys.generate())
        })
        stale.requester.request("dev-b")
        assertIs<RotationDecision.NeedsManual>(jisu.approver.check().single().second)
    }
}
