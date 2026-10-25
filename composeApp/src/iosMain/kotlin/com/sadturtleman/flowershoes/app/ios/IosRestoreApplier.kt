package com.sadturtleman.flowershoes.app.ios

import androidx.room.useReaderConnection
import com.sadturtleman.flowershoes.core.backup.BackedUpSettings
import com.sadturtleman.flowershoes.core.backup.RestoreApplier
import com.sadturtleman.flowershoes.core.backup.RestoredBackup
import com.sadturtleman.flowershoes.core.crypto.CoupleKey
import com.sadturtleman.flowershoes.core.data.AttachmentStore
import com.sadturtleman.flowershoes.core.data.DeviceSettings
import com.sadturtleman.flowershoes.core.data.sync.KeyVault
import com.sadturtleman.flowershoes.core.data.sync.SyncScheduler
import com.sadturtleman.flowershoes.core.database.AppDatabase
import com.sadturtleman.flowershoes.core.database.backupDatabaseBuilder
import com.sadturtleman.flowershoes.core.database.buildKkotsin
import com.sadturtleman.flowershoes.core.database.restoreBackupSnapshot
import com.sadturtleman.flowershoes.core.model.AttachmentKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlin.uuid.Uuid

/** iOS cannot relaunch its own process. Import atomically and keep the live Room observers. */
class IosRestoreApplier(
    private val db: AppDatabase,
    private val attachments: AttachmentStore,
    private val vault: KeyVault,
    private val settings: DeviceSettings,
    private val temporaryRoot: Path,
    private val scheduler: () -> SyncScheduler?,
) : RestoreApplier {
    override suspend fun apply(restored: RestoredBackup) {
        val deviceSettings = BackedUpSettings.decode(restored.deviceSettings)
        val media = restored.attachments.map { (id, value) ->
            require(id.isNotBlank() && '/' !in id && '\\' !in id && id != "." && id != "..")
            Triple(id, AttachmentKind.valueOf(value.first), value.second)
        }
        val keys = restored.coupleKeys.map { (epoch, key) -> CoupleKey(epoch, key) }
        val fs = FileSystem.SYSTEM
        val path = temporaryRoot / "restore-${Uuid.random()}.db"
        try {
            withContext(Dispatchers.IO) {
                fs.createDirectories(temporaryRoot)
                fs.write(path) { write(restored.database) }
                // Opening the copy runs Room's 1→6 migrations and validates its schema.
                val migrated = backupDatabaseBuilder(path.toString()).buildKkotsin()
                try {
                    migrated.useReaderConnection { c -> c.usePrepared("SELECT COUNT(*) FROM user") { it.step() } }
                } finally { migrated.close() }
            }
            val sync = scheduler()
            val apply: suspend () -> Unit = {
                // Files are immutable by ID. Existing DB remains usable if staging fails.
                withContext(Dispatchers.IO) { media.forEach { (id, _, bytes) -> attachments.stageRestoredFile(id, bytes) } }
                keys.forEach { vault.putCoupleKey(it) }
                db.restoreBackupSnapshot(path.toString())
                restored.coupleKeys.keys.maxOrNull()?.let { settings.setKeyEpoch(it) }
                deviceSettings.applyTo(settings)
            }
            if (sync != null) sync.whileSyncPaused(apply) else apply()
            sync?.requestNow()
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                listOf(path, (path.toString() + "-wal").toPath(), (path.toString() + "-shm").toPath())
                    .forEach { fs.delete(it, mustExist = false) }
            }
        }
    }
}
