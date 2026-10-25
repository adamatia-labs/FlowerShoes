package com.sadturtleman.flowershoes.core.backup

import com.sadturtleman.flowershoes.core.data.AttachmentStore
import com.sadturtleman.flowershoes.core.data.DeviceSettings
import com.sadturtleman.flowershoes.core.data.NotificationPrefs
import com.sadturtleman.flowershoes.core.data.sync.KeyVault
import com.sadturtleman.flowershoes.core.database.AppDatabase
import com.sadturtleman.flowershoes.core.database.writeBackupSnapshot
import com.sadturtleman.flowershoes.core.model.AttachmentKind
import com.sadturtleman.flowershoes.core.model.Role
import com.sadturtleman.flowershoes.core.model.ThemeAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import kotlin.uuid.Uuid

/** Login, device ID and device signing keys are deliberately never copied to the new phone. */
@Serializable
data class BackedUpSettings(
    val theme: String,
    val role: String?,
    val onboarded: Boolean,
    val notifyPrefs: NotificationPrefs? = null,
    val draft: String? = null,
    val displayMode: String? = null,
    val widgetFrame: String? = null,
    val autoBackup: Boolean? = null,
    val cellularBackup: Boolean? = null,
) {
    suspend fun applyTo(settings: DeviceSettings) {
        settings.setTheme(ThemeAccent.valueOf(theme))
        role?.let { settings.setRole(Role.valueOf(it)) }
        notifyPrefs?.let { settings.setNotifyPrefs(it) }
        settings.saveLetterDraft(draft)
        displayMode?.let { settings.setDisplayMode(it) }
        widgetFrame?.let { settings.setWidgetFrame(it) }
        autoBackup?.let { settings.setAutoBackup(it) }
        cellularBackup?.let { settings.setCellularBackup(it) }
        if (onboarded) settings.setOnboarded()
    }

    companion object {
        fun decode(bytes: ByteArray): BackedUpSettings = Json { ignoreUnknownKeys = true }
            .decodeFromString(serializer(), bytes.decodeToString()).also {
                ThemeAccent.valueOf(it.theme)
                it.role?.let(Role::valueOf)
            }
    }
}

class RoomBackupSource(
    private val db: AppDatabase,
    private val attachments: AttachmentStore,
    private val vault: KeyVault,
    private val settings: DeviceSettings,
    private val temporaryRoot: Path,
    private val fs: FileSystem = FileSystem.SYSTEM,
) : BackupSource {
    override val schemaVersion: Int = AppDatabase.VERSION

    override suspend fun capture(): BackupSource = withContext(Dispatchers.IO) {
        fs.createDirectories(temporaryRoot)
        val path = temporaryRoot / "backup-${Uuid.random()}.db"
        try {
            val snapshot = db.writeBackupSnapshot(path.toString())
            val bytes = fs.read(path) { readByteArray() }
            val refs = snapshot.attachments.map { a ->
                BackupSource.Attachment(a.id, a.kind, a.sha256.chunked(2).map { it.toInt(16).toByte() }.toByteArray()) {
                    checkNotNull(attachments.read(a.id)) { "백업할 첨부가 없어요: ${a.id}" }
                }
            }
            val keys = coupleKeys()
            val preferences = deviceSettings()
            val kinds = snapshot.attachments.groupingBy { it.kind }.eachCount()
            val summary = summary().copy(letters = snapshot.letters,
                drawings = kinds[AttachmentKind.DRAWING_DOC.name] ?: 0,
                photos = kinds[AttachmentKind.PHOTO.name] ?: 0)
            object : BackupSource {
                override val schemaVersion = AppDatabase.VERSION
                override suspend fun databaseSnapshot() = bytes
                override suspend fun attachments() = refs
                override suspend fun coupleKeys() = keys
                override suspend fun deviceSettings() = preferences
                override suspend fun summary() = summary
            }
        } finally { fs.delete(path, mustExist = false) }
    }

    override suspend fun databaseSnapshot(): ByteArray = withContext(Dispatchers.IO) {
        fs.createDirectories(temporaryRoot)
        val path = temporaryRoot / "backup-${Uuid.random()}.db"
        try {
            db.writeBackupSnapshot(path.toString())
            fs.read(path) { readByteArray() }
        } finally { fs.delete(path, mustExist = false) }
    }

    override suspend fun attachments(): List<BackupSource.Attachment> = db.attachmentDao().all().map { a ->
        BackupSource.Attachment(a.id, a.kind, a.sha256.chunked(2).map { it.toInt(16).toByte() }.toByteArray()) {
            checkNotNull(attachments.read(a.id)) { "백업할 첨부가 없어요: ${a.id}" }
        }
    }

    override suspend fun coupleKeys(): Map<Int, ByteArray> = vault.allCoupleKeys().associate { it.epoch to it.export() }

    override suspend fun summary(): BackupSummary {
        val kinds = db.attachmentDao().all().groupingBy { it.kind }.eachCount()
        val me = settings.state.first().userId
        val partner = db.coupleDao().get()?.partnerUserId
        return BackupSummary(
            letters = db.letterDao().allActive().size,
            drawings = kinds[AttachmentKind.DRAWING_DOC.name] ?: 0,
            photos = kinds[AttachmentKind.PHOTO.name] ?: 0,
            myName = db.userDao().get(me)?.nickname.orEmpty(),
            partnerName = partner?.let { db.userDao().get(it)?.nickname }.orEmpty(),
        )
    }

    override suspend fun deviceSettings(): ByteArray {
        val s = settings.state.first()
        return Json.encodeToString(BackedUpSettings.serializer(), BackedUpSettings(
            s.theme.name, s.role?.name, s.onboarded,
            settings.notifyPrefs.first(), settings.letterDraft.first(), settings.displayMode.first(),
            settings.widgetFrame.first(), settings.autoBackup.first(), settings.cellularBackup.first(),
        )).encodeToByteArray()
    }
}
