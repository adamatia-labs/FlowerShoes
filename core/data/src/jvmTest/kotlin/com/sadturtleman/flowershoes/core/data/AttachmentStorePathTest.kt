package com.sadturtleman.flowershoes.core.data

import com.sadturtleman.flowershoes.core.database.AttachmentEntity
import com.sadturtleman.flowershoes.core.database.buildKkotsin
import com.sadturtleman.flowershoes.core.database.inMemoryDatabaseBuilder
import com.sadturtleman.flowershoes.core.model.AttachmentKind
import kotlinx.coroutines.test.runTest
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
