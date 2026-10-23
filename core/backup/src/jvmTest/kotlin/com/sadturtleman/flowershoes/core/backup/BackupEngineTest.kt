package com.sadturtleman.flowershoes.core.backup

import com.sadturtleman.flowershoes.core.crypto.BackupKey
import com.sadturtleman.flowershoes.core.crypto.CryptoException
import com.sadturtleman.flowershoes.core.crypto.RecoveryCode
import com.sadturtleman.flowershoes.core.crypto.Sodium
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class BackupEngineTest {

    private class MemoryStorage(private val clock: () -> Long) : BackupStorage {
        val files = linkedMapOf<String, Pair<ByteArray, Long>>()
        override suspend fun put(path: String, bytes: ByteArray) { files[path] = bytes to (files[path]?.second ?: clock()) }
        override suspend fun get(path: String) = files[path]?.first
        override suspend fun list(prefix: String) = files.filterKeys { it.startsWith(prefix) }.map { BackupStorage.Entry(it.key, it.value.second) }
        override suspend fun delete(path: String) { files.remove(path) }
    }

    private class FixedClock(var now: Instant) : Clock {
        override fun now() = now
    }

    private class Source(var db: ByteArray, val atts: MutableList<BackupSource.Attachment>) : BackupSource {
        override suspend fun databaseSnapshot() = db
        override suspend fun attachments() = atts
        override suspend fun coupleKeys() = mapOf(3 to ByteArray(32) { 3 })
        override suspend fun deviceSettings() = "theme=OLIVE".encodeToByteArray()
        override val schemaVersion = 1
    }

    private fun att(id: String, content: String) =
        BackupSource.Attachment(id, "DRAWING_DOC", content.encodeToByteArray().copyOf(32), read = { content.encodeToByteArray() })

    private val clock = FixedClock(Instant.parse("2026-10-22T00:00:00Z"))
    private val storage = MemoryStorage { clock.now.toEpochMilliseconds() }

    @BeforeTest
    fun init() = Sodium.init()

    @Test
    fun `백업한 것을 그대로 복원한다`() = runTest {
        val key = BackupKey.generate()
        val db = ByteArray(BackupEngine.CHUNK + 1000) { (it % 251).toByte() } // 조각 2개
        BackupEngine(key, storage, clock).backup("u1", Source(db, mutableListOf(att("a1", "drawing-1"))))

        val restored = BackupEngine(key, storage, clock).restore("u1", appSchemaVersion = 1)!!
        assertContentEquals(db, restored.database)
        assertContentEquals("drawing-1".encodeToByteArray(), restored.attachments.getValue("a1").second)
        assertContentEquals(ByteArray(32) { 3 }, restored.coupleKeys.getValue(3))
        assertEquals("theme=OLIVE", restored.deviceSettings.decodeToString())
        assertEquals(2, restored.manifest.dbChunks.size)
    }

    @Test
    fun `서버에는 평문이 없다`() = runTest {
        BackupEngine(BackupKey.generate(), storage, clock).backup("u1", Source("SECRET-LETTER".encodeToByteArray(), mutableListOf(att("a1", "SECRET-DRAWING"))))
        storage.files.values.forEach { (bytes, _) ->
            val text = bytes.decodeToString()
            assertTrue("SECRET" !in text)
        }
        // 첨부 이름에 내용 해시가 드러나지 않는다.
        assertTrue(storage.files.keys.none { it.contains("SECRET") })
    }

    @Test
    fun `다른 백업 키로는 풀 수 없다`() = runTest {
        BackupEngine(BackupKey.generate(), storage, clock).backup("u1", Source(byteArrayOf(1), mutableListOf()))
        assertNull(BackupEngine(BackupKey.generate(), storage, clock).restore("u1", 1))
    }

    @Test
    fun `같은 첨부는 한 번만 올리고 manifest 는 최근 2개만 둔다`() = runTest {
        val key = BackupKey.generate()
        val engine = BackupEngine(key, storage, clock)
        val source = Source(byteArrayOf(1), mutableListOf(att("a1", "same")))
        repeat(3) {
            engine.backup("u1", source)
            clock.now += 1.hours
        }
        assertEquals(1, storage.files.keys.count { "/blobs/" in it })
        assertEquals(2, storage.files.keys.count { "/manifests/" in it })
        assertEquals(2, storage.files.keys.count { "/db/" in it })
    }

    @Test
    fun `참조가 끊긴 첨부는 7일이 지나야 지운다`() = runTest {
        val engine = BackupEngine(BackupKey.generate(), storage, clock)
        val source = Source(byteArrayOf(1), mutableListOf(att("old", "old-drawing")))
        engine.backup("u1", source)
        source.atts.clear()
        repeat(2) {
            clock.now += 1.days
            engine.backup("u1", source)
        }
        assertEquals(1, storage.files.keys.count { "/blobs/" in it }, "아직 7일 전")
        clock.now += 7.days
        engine.backup("u1", source)
        assertEquals(0, storage.files.keys.count { "/blobs/" in it })
    }

    @Test
    fun `앱보다 새 스키마의 백업은 업데이트를 안내한다`() = runTest {
        val key = BackupKey.generate()
        BackupEngine(key, storage, clock).backup("u1", object : BackupSource by Source(byteArrayOf(1), mutableListOf()) {
            override val schemaVersion = 5
        })
        assertFailsWith<BackupTooNewException> { BackupEngine(key, storage, clock).restore("u1", appSchemaVersion = 1) }
    }

    @Test
    fun `복구 코드로 백업 키를 되찾는다`() = runTest {
        val key = BackupKey.generate()
        val code = RecoveryCode.generate()
        BackupEngine.storeKeyWrap(storage, "u1", code, key)
        assertContentEquals(key.export(), BackupEngine.recoverKey(storage, "u1", code)!!.export())
        assertFailsWith<CryptoException> { BackupEngine.recoverKey(storage, "u1", RecoveryCode.generate()) }
    }
}
