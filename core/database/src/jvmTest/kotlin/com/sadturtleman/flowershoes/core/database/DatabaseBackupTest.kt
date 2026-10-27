package com.sadturtleman.flowershoes.core.database

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.room.useWriterConnection
import java.nio.file.Files
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.*

class DatabaseBackupTest {
    private val db = inMemoryDatabaseBuilder().buildKkotsin()
    private val directory = Files.createTempDirectory("kkotsin-restore").toFile()
    private val snapshot get() = directory.resolve("snapshot.db").absolutePath
    private fun letter(id: String) = LetterEntity(id, "me", null, "[]", "plain", 1, null, 0)

    @AfterTest fun close() { db.close(); directory.deleteRecursively() }

    @Test fun `captured attachment references belong to the snapshot before later writes`() = runBlocking {
        db.attachmentDao().upsert(AttachmentEntity("before", "PHOTO", "/before", "aa", 1))
        val captured = db.writeBackupSnapshot(snapshot)
        db.attachmentDao().upsert(AttachmentEntity("after", "PHOTO", "/after", "bb", 1))
        assertEquals(listOf("before"), captured.attachments.map { it.id })
        db.restoreBackupSnapshot(snapshot)
        assertEquals(captured.attachments.map { it.id }, db.attachmentDao().all().map { it.id })
    }

    @Test fun `snapshot restores records and notifies existing observers without restarting`() = runBlocking {
        db.letterDao().upsert(letter("saved"))
        db.writeBackupSnapshot(snapshot)
        db.letterDao().upsert(letter("later"))
        val observed = async { withTimeout(5000) { db.letterDao().observeAll().first { it.map { l -> l.id } == listOf("saved") } } }
        db.restoreBackupSnapshot(snapshot)
        assertEquals(listOf("saved"), db.letterDao().allActive().map { it.id })
        assertEquals("saved", observed.await().single().id)
        // Room remains usable and a second restore can attach the same file.
        db.letterDao().upsert(letter("again"))
        db.restoreBackupSnapshot(snapshot)
        assertEquals(1, db.letterDao().allActive().size)
    }

    @Test fun `missing table rejects snapshot and preserves all current records`() = runBlocking {
        db.letterDao().upsert(letter("saved"))
        db.writeBackupSnapshot(snapshot)
        BundledSQLiteDriver().open(snapshot).use { c -> c.prepare("DROP TABLE letter").use { it.step() } }
        assertFails { db.restoreBackupSnapshot(snapshot) }
        assertEquals("saved", db.letterDao().allActive().single().id)
    }

    @Test fun `failure after earlier tables were imported rolls back the transaction`() = runBlocking {
        db.letterDao().upsert(letter("saved"))
        db.writeBackupSnapshot(snapshot)
        db.letterDao().upsert(letter("later"))
        db.useWriterConnection { c ->
            c.usePrepared("CREATE TRIGGER fail_restore BEFORE INSERT ON letter BEGIN SELECT RAISE(ABORT, 'injected restore failure'); END") { it.step() }
        }
        assertFails { db.restoreBackupSnapshot(snapshot) }
        assertEquals(setOf("saved", "later"), db.letterDao().allActive().map { it.id }.toSet())
    }
}
