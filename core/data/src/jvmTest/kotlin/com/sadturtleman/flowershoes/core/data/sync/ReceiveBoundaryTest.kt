package com.sadturtleman.flowershoes.core.data.sync

import androidx.room.Room
import com.sadturtleman.flowershoes.core.crypto.Aead
import com.sadturtleman.flowershoes.core.crypto.CoupleKey
import com.sadturtleman.flowershoes.core.crypto.DeviceKeys
import com.sadturtleman.flowershoes.core.crypto.OpEnvelope
import com.sadturtleman.flowershoes.core.crypto.OpHeader
import com.sadturtleman.flowershoes.core.crypto.Sodium
import com.sadturtleman.flowershoes.core.data.*
import com.sadturtleman.flowershoes.core.database.*
import com.sadturtleman.flowershoes.core.model.LetterBlock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import okio.FileSystem
import kotlin.test.*
import kotlin.uuid.Uuid

/** 별도 Room 인스턴스가 다운로드 중 관계/기기를 바꿔도 실제 파일과 본문에 반영하지 않는다. */
class ReceiveBoundaryTest {
    private val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "receive-boundary-${Uuid.random()}"
    private val db by lazy { Room.databaseBuilder<AppDatabase>((root / "db").toString()).buildKkotsin() }
    private val other by lazy { Room.databaseBuilder<AppDatabase>((root / "db").toString()).buildKkotsin() }
    private val cloud = InMemoryCloud()
    private val identity = object : Identity {
        override val userId = "receiver"
        override val deviceId = "receiver-device"
        override val keyEpoch = 1
    }
    private val vault = InMemoryKeyVault()
    private val attachments by lazy { AttachmentStore(db, root / "attachments") }
    private val couple = CoupleEntity("couple", "receiver", "sender", 1, 100)
    private lateinit var sender: DeviceEntity
    private lateinit var item: MailItem
    private lateinit var letterId: String

    @BeforeTest
    fun prepareDirectory() {
        Sodium.init()
        FileSystem.SYSTEM.createDirectories(root)
    }

    @AfterTest
    fun close() {
        other.close()
        db.close()
        FileSystem.SYSTEM.deleteRecursively(root)
    }

    private suspend fun prepare(withPhoto: Boolean = true) {
        val signer = DeviceKeys.generate()
        val key = CoupleKey.generate(1)
        vault.putDeviceKeys(DeviceKeys.generate())
        vault.putCoupleKey(key)
        sender = DeviceEntity("sender-device", "sender", "sender", signer.signPub, signer.boxPub, "ACTIVE", 10, null)
        db.coupleDao().upsert(couple)
        db.deviceDao().upsert(sender)
        RelationshipArchive(db).prepare()
        val writer = SyncWriter(db, identity)
        val letter = LetterRepository(db, writer, identity).newLetter(
            if (withPhoto) listOf(LetterBlock.Photo("photo")) else listOf(LetterBlock.Text("본문")),
        )
        letterId = letter.id
        val blobKey = Sodium.random(Aead.KEY_BYTES)
        cloud.put(couple.id, "blob", Aead.seal(blobKey, "photo bytes".encodeToByteArray()))
        val payload = OpPayload.LetterPut(letter.copy(authorId = "sender"), photos =
            if (withPhoto) listOf(PhotoRef("photo", "blob", blobKey.toByteString().base64())) else emptyList(),
        )
        val header = OpHeader("op", "sender-device:app", 1, 1)
        val envelope = OpEnvelope.seal(header, Plain(1, OpCodec.encode(payload)).encode(), key, signer)
        item = MailItem("op", "letter", sender.id, header.toBytes(), envelope.ciphertext, envelope.signature)
    }

    private fun receiver(blobs: BlobStore, keys: KeyVault = vault) =
        SyncClient(db, identity, keys, cloud, blobs, attachments, InboxApplier(db), SyncWriter(db, identity))

    private fun duringDownload(change: suspend () -> Unit) = runTest {
        prepare()
        val downloading = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        val blobs = object : BlobStore by cloud {
            override suspend fun get(coupleId: String, blobId: String): ByteArray? {
                downloading.complete(Unit)
                resume.await()
                return cloud.get(coupleId, blobId)
            }
        }
        val receive = async { receiver(blobs).receiveQueued(item) }
        downloading.await()
        change()
        resume.complete(Unit)
        assertFalse(receive.await())
        assertNull(db.attachmentDao().get("photo"))
        assertFalse(FileSystem.SYSTEM.exists(root / "attachments" / "photo"))
        assertNull(db.letterDao().get(letterId))
        assertNull(db.syncDao().cursor("sender-device:app"))
        assertNotNull(cloud.get(couple.id, "blob"), "거부된 수신은 서버 첨부도 지우지 않는다")
    }

    @Test
    fun `다운로드 중 연결 해제는 첨부와 편지를 남기지 않는다`() = duringDownload { other.coupleDao().clear() }

    @Test
    fun `다운로드 중 새 관계 연결은 이전 첨부를 가져오지 않는다`() = duringDownload {
        other.coupleDao().clear()
        other.coupleDao().upsert(couple.copy(id = "new-couple", partnerUserId = "new-partner"))
    }

    @Test
    fun `다운로드 중 송신 서명키 변경을 다시 확인한다`() = duringDownload {
        other.deviceDao().upsert(sender.copy(signPub = DeviceKeys.generate().signPub))
    }

    @Test
    fun `다운로드 중 동일 관계 ID의 키 교체도 다시 확인한다`() = duringDownload {
        other.coupleDao().upsert(couple.copy(keyEpoch = 2))
    }

    @Test
    fun `다운로드 중 기기 소유자 변경도 다시 확인한다`() = duringDownload {
        other.deviceDao().upsert(sender.copy(userId = "different-user"))
    }

    @Test
    fun `다운로드 중 송신 기기 폐기를 다시 확인한다`() = duringDownload {
        other.deviceDao().upsert(sender.copy(state = "REVOKED", revokedAtMs = 200))
    }

    @Test
    fun `다운로드 중 cutoff 추가는 파일 기록 전에도 적용한다`() = duringDownload {
        other.syncDao().addRevoked(RevokedDeviceEntity(sender.id, 200, """{"sender-device:app":0}"""))
    }

    @Test
    fun `첨부 저장 뒤 관계가 바뀌면 본문과 수신 커서를 쓰지 않는다`() = runTest {
        prepare()
        val blobs = object : BlobStore by cloud {
            override suspend fun delete(coupleId: String, blobId: String) {
                cloud.delete(coupleId, blobId)
                other.coupleDao().clear()
                other.coupleDao().upsert(couple.copy(id = "new-couple", partnerUserId = "new-partner"))
            }
        }
        assertFalse(receiver(blobs).receiveQueued(item))
        assertNull(db.letterDao().get(letterId))
        assertNull(db.syncDao().cursor("sender-device:app"))
        assertEquals("new-couple", other.coupleDao().get()?.id)
    }

    @Test
    fun `변경 없는 관계는 별도 DB 연결에서도 첨부와 본문을 받는다`() = runTest {
        prepare()
        assertTrue(receiver(cloud).receiveQueued(item))
        assertContentEquals("photo bytes".encodeToByteArray(), attachments.read("photo"))
        assertNotNull(other.letterDao().get(letterId))
        assertEquals(1L, other.syncDao().cursor("sender-device:app"))
    }

    @Test
    fun `첨부 없는 편지도 복호화 중 연결 변경을 본문 쓰기 전에 확인한다`() = runTest {
        prepare(withPhoto = false)
        val keys = object : KeyVault by vault {
            override suspend fun coupleKey(epoch: Int): CoupleKey? {
                val key = vault.coupleKey(epoch)
                other.coupleDao().clear()
                return key
            }
        }
        assertFalse(receiver(cloud, keys).receiveQueued(item))
        assertNull(db.letterDao().get(letterId))
        assertNull(db.syncDao().cursor("sender-device:app"))
    }
}
