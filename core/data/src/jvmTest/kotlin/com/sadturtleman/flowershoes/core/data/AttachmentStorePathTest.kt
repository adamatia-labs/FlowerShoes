package com.sadturtleman.flowershoes.core.data

import com.sadturtleman.flowershoes.core.database.AttachmentEntity
import com.sadturtleman.flowershoes.core.database.buildKkotsin
import com.sadturtleman.flowershoes.core.database.inMemoryDatabaseBuilder
import com.sadturtleman.flowershoes.core.model.AttachmentKind
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import okio.FileSystem
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class AttachmentStorePathTest {
    private val fs = FileSystem.SYSTEM
    private val area = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "kk-attachment-path-${Uuid.random()}"
    private val root = area / "attachments"
    private val db = inMemoryDatabaseBuilder().buildKkotsin()
    private val store = AttachmentStore(db, root, fs)

    @AfterTest
    fun close() { db.close(); if (fs.exists(area)) fs.deleteRecursively(area) }

    @Test
    fun `읽기 한도를 넘는 원본 파일은 지우거나 바꾸지 않는다`() = runTest {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val id = store.put(AttachmentKind.DRAWING_DOC, bytes)
        val row = db.attachmentDao().get(id)
        assertNull(store.read(id, maxBytes = 3))
        assertContentEquals(bytes, store.read(id, maxBytes = 4))
        assertContentEquals(bytes, fs.read(root / id) { readByteArray() })
        assertEquals(row, db.attachmentDao().get(id))
    }

    @Test
    fun `같은 첨부 식별자의 다른 내용은 기존 파일과 메타데이터를 보존한다`() = runTest {
        val original = "original photo".encodeToByteArray()
        store.putWithId("same-id", AttachmentKind.PHOTO, original)
        val before = db.attachmentDao().get("same-id")
        assertFailsWith<IllegalStateException> { store.putWithId("same-id", AttachmentKind.PHOTO, "replacement".encodeToByteArray()) }
        assertContentEquals(original, store.read("same-id"))
        assertEquals(before, db.attachmentDao().get("same-id"))
    }

    @Test
    fun `같은 내용의 재수신은 허용하지만 첨부 종류 변경은 거절한다`() = runTest {
        val bytes = "photo".encodeToByteArray()
        store.putWithId("same-id", AttachmentKind.PHOTO, bytes)
        val before = db.attachmentDao().get("same-id")
        assertEquals("same-id", store.putWithId("same-id", AttachmentKind.PHOTO, bytes))
        assertFailsWith<IllegalStateException> { store.putWithId("same-id", AttachmentKind.DRAWING_DOC, bytes) }
        assertEquals(before, db.attachmentDao().get("same-id"))
        assertContentEquals(bytes, store.read("same-id"))
    }

    @Test
    fun `파일만 남은 저장 실패 뒤에도 다른 내용으로 덮어쓰지 않는다`() = runTest {
        fs.createDirectories(root)
        val bytes = "uncommitted original".encodeToByteArray()
        fs.write(root / "orphan") { write(bytes) }
        assertFailsWith<IllegalStateException> { store.putWithId("orphan", AttachmentKind.PHOTO, "other".encodeToByteArray()) }
        assertContentEquals(bytes, fs.read(root / "orphan") { readByteArray() })
        assertNull(db.attachmentDao().get("orphan"))
        store.putWithId("orphan", AttachmentKind.PHOTO, bytes)
        assertContentEquals(bytes, store.read("orphan"))
    }

    @Test
    fun `저장소 두 개가 같은 식별자에 동시에 쓰면 한 원본만 확정한다`() = runTest {
        val other = AttachmentStore(db, root, fs)
        val first = async(Dispatchers.IO) { runCatching { store.putWithId("race", AttachmentKind.PHOTO, byteArrayOf(1)) } }
        val second = async(Dispatchers.IO) { runCatching { other.putWithId("race", AttachmentKind.PHOTO, byteArrayOf(2)) } }
        val outcomes = listOf(first.await(), second.await())
        assertEquals(1, outcomes.count { it.isSuccess })
        assertEquals(1, outcomes.count { it.exceptionOrNull() is AttachmentConflictException })
        val winner = if (outcomes.first().isSuccess) byteArrayOf(1) else byteArrayOf(2)
        assertContentEquals(winner, store.read("race"))
    }

    @Test
    fun `상대가 보낸 상위 경로는 첨부 폴더 밖에 쓰지 않는다`() = runTest {
        fs.createDirectories(root)
        val outside = area / "outside"
        fs.write(outside) { writeUtf8("original") }
        assertFailsWith<IllegalArgumentException> { store.putWithId("../outside", AttachmentKind.PHOTO, "changed".encodeToByteArray()) }
        assertContentEquals("original".encodeToByteArray(), fs.read(outside) { readByteArray() })
    }

    @Test
    fun `잘못된 기존 첨부 행으로 폴더 밖 파일을 읽지 않는다`() = runTest {
        fs.createDirectories(root)
        fs.write(area / "outside") { writeUtf8("private") }
        db.attachmentDao().upsert(AttachmentEntity("../outside", AttachmentKind.PHOTO.name, "ignored", "hash", 7))
        assertNull(store.read("../outside"))
    }

    @Test
    fun `안전한 단일 이름은 기존 형식도 보존하고 경로 구분자와 제어 문자는 거부한다`() = runTest {
        val bytes = "photo".encodeToByteArray()
        for (name in listOf("legacy-id_42", "기록-1", Uuid.random().toString())) {
            store.putWithId(name, AttachmentKind.PHOTO, bytes)
            assertContentEquals(bytes, store.read(name))
        }
        for (bad in listOf("", ".", "..", "/absolute", "../escape", "dir/file", "dir\\file", "C:escape", "null\u0000byte", "\n", "가".repeat(100))) {
            assertFailsWith<IllegalArgumentException>(bad) { store.putWithId(bad, AttachmentKind.PHOTO, bytes) }
            assertNull(store.read(bad))
        }
    }

    @Test
    fun `오래된 위젯 경로도 첨부 폴더의 직접 자식만 허용한다`() {
        assertEquals(root / "safe-id", AttachmentStore.safePath(root, (root / "safe-id").toString()))
        assertNull(AttachmentStore.safePath(root, "$root/../outside"))
        assertNull(AttachmentStore.safePath(root, "${root}-other/safe-id"))
        assertNull(AttachmentStore.safePath(root, "$root/dir/nested"))
        assertNull(AttachmentStore.safePath(root, "relative"))
    }

    @Test
    fun `첨부와 위젯은 폴더 밖을 가리키는 심볼릭 링크를 따르지 않는다`() = runTest {
        fs.createDirectories(root)
        fs.write(area / "outside") { writeUtf8("private") }
        fs.write(root / "safe") { writeUtf8("photo") }
        fs.createSymlink(root / "link", area / "outside")
        fs.createSymlink(root / "dangling", area / "not-created")
        assertEquals(fs.canonicalize(root / "safe"), AttachmentStore.safeExistingPath(root, (root / "safe").toString(), fs))
        assertNull(AttachmentStore.safeExistingPath(root, (root / "link").toString(), fs))
        db.attachmentDao().upsert(AttachmentEntity("link", AttachmentKind.PHOTO.name, "ignored", "hash", 7))
        assertNull(store.read("link"))
        assertFailsWith<IllegalArgumentException> { store.putWithId("link", AttachmentKind.PHOTO, "changed".encodeToByteArray()) }
        assertFailsWith<IllegalArgumentException> { store.putWithId("dangling", AttachmentKind.PHOTO, "changed".encodeToByteArray()) }
        assertContentEquals("private".encodeToByteArray(), fs.read(area / "outside") { readByteArray() })
        assertNull(fs.metadataOrNull(area / "not-created"))
    }
}
