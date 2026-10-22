package com.sadturtleman.flowershoes.core.backup

import com.sadturtleman.flowershoes.core.crypto.Sodium
import com.sadturtleman.flowershoes.core.data.sync.InMemoryKeyVault
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.uuid.Uuid

/** 백업 → 새 폰에서 복원(동기화된 키 / 복구 코드 / 틀린 코드). 저장소는 파일 시스템. */
class BackupManagerTest {

    private val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "kkotsin-bm-${Uuid.random()}"
    private val storage = FileBackupStorage(root)

    private class Source : BackupSource {
        override suspend fun databaseSnapshot() = "SQLite format 3 …".encodeToByteArray()
        override suspend fun attachments() = listOf(BackupSource.Attachment("a1", "DRAWING_DOC", ByteArray(32) { 1 }) { byteArrayOf(9, 9) })
        override suspend fun coupleKeys() = mapOf(1 to ByteArray(32) { 7 })
        override suspend fun deviceSettings() = "{}".encodeToByteArray()
        override val schemaVersion = 1
    }

    private class FakeSync(var stored: ByteArray? = null) : BackupKeySync {
        override suspend fun isEndToEndAvailable() = true
        override suspend fun store(key: ByteArray) { stored = key }
        override suspend fun load() = stored
        override suspend fun delete() { stored = null }
    }

    private class Capture : RestoreApplier {
        var restored: RestoredBackup? = null
        override suspend fun apply(restored: RestoredBackup) { this.restored = restored }
    }

    @BeforeTest
    fun init() = Sodium.init()

    @AfterTest
    fun clean() = FileSystem.SYSTEM.deleteRecursively(root)

    @Test
    fun `같은 OS 의 새 폰은 동기화된 키로 바로 복원한다`() = runTest {
        val sync = FakeSync()
        BackupManager(storage, Source(), InMemoryKeyVault(), sync, Capture(), 1).backupNow("u1")
        val newPhone = Capture()
        val result = BackupManager(storage, Source(), InMemoryKeyVault(), sync, newPhone, 1).restore("u1")
        assertIs<RestoreResult.Restored>(result)
        assertContentEquals(byteArrayOf(9, 9), newPhone.restored!!.attachments.getValue("a1").second)
        assertContentEquals(ByteArray(32) { 7 }, newPhone.restored!!.coupleKeys.getValue(1))
    }

    @Test
    fun `다른 기종이면 복구 코드를 묻고, 맞는 코드로 복원한다`() = runTest {
        val oldVault = InMemoryKeyVault()
        val old = BackupManager(storage, Source(), oldVault, FakeSync(), Capture(), 1)
        old.backupNow("u1")
        val code = assertNotNull(old.recoveryCode())

        val newPhone = BackupManager(storage, Source(), InMemoryKeyVault(), FakeSync(stored = null), Capture(), 1)
        assertIs<RestoreResult.NeedsRecoveryCode>(newPhone.restore("u1"))
        assertIs<RestoreResult.WrongRecoveryCode>(newPhone.restore("u1", recoveryCode = "0".repeat(24)))
        assertIs<RestoreResult.Restored>(newPhone.restore("u1", recoveryCode = code.lowercase()))
    }

    @Test
    fun `백업이 없으면 그렇다고 알려 준다`() = runTest {
        assertEquals(RestoreResult.NoBackup, BackupManager(storage, Source(), InMemoryKeyVault(), null, Capture(), 1).restore("nobody"))
    }
}
