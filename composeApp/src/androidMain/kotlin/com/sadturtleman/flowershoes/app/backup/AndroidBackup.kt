package com.sadturtleman.flowershoes.app.backup

import android.content.Context
import android.content.Intent
import androidx.room.useWriterConnection
import com.google.android.gms.auth.blockstore.Blockstore
import com.google.android.gms.auth.blockstore.DeleteBytesRequest
import com.google.android.gms.auth.blockstore.RetrieveBytesRequest
import com.google.android.gms.auth.blockstore.StoreBytesData
import com.google.firebase.storage.FirebaseStorage
import com.sadturtleman.flowershoes.core.backup.BackupKeySync
import com.sadturtleman.flowershoes.core.backup.BackupSource
import com.sadturtleman.flowershoes.core.backup.BackupStorage
import com.sadturtleman.flowershoes.core.backup.RestoreApplier
import com.sadturtleman.flowershoes.core.backup.RestoredBackup
import com.sadturtleman.flowershoes.core.crypto.CoupleKey
import com.sadturtleman.flowershoes.core.data.AttachmentStore
import com.sadturtleman.flowershoes.core.data.DeviceSettings
import com.sadturtleman.flowershoes.core.data.sync.KeyVault
import com.sadturtleman.flowershoes.core.database.AppDatabase
import com.sadturtleman.flowershoes.core.model.AttachmentKind
import com.sadturtleman.flowershoes.core.model.Role
import com.sadturtleman.flowershoes.core.model.ThemeAccent
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 기기만 쓰는 설정(테마 · 역할 · 온보딩). 복원할 때 편의로 함께 돌려준다. */
@Serializable
private data class DeviceSettingsBackup(val theme: String, val role: String?, val onboarded: Boolean)

/**
 * 백업할 것을 앱이 내준다. DB 는 VACUUM INTO 로 만든 일관된 사본이다(쓰는 중이어도 깨지지 않는다).
 */
class AndroidBackupSource(
    private val context: Context,
    private val db: AppDatabase,
    private val attachments: AttachmentStore,
    private val vault: KeyVault,
    private val settings: DeviceSettings,
) : BackupSource {

    override val schemaVersion: Int = DB_VERSION

    override suspend fun databaseSnapshot(): ByteArray = withContext(Dispatchers.IO) {
        val tmp = File(context.cacheDir, "backup-snapshot.db").apply { delete() }
        db.useWriterConnection { c -> c.usePrepared("VACUUM INTO '${tmp.absolutePath.replace("'", "''")}'") { it.step() } }
        tmp.readBytes().also { tmp.delete() }
    }

    override suspend fun attachments(): List<BackupSource.Attachment> = db.attachmentDao().all().map { a ->
        BackupSource.Attachment(a.id, a.kind, a.sha256.chunked(2).map { it.toInt(16).toByte() }.toByteArray()) {
            attachments.read(a.id) ?: ByteArray(0)
        }
    }

    override suspend fun coupleKeys(): Map<Int, ByteArray> = vault.allCoupleKeys().associate { it.epoch to it.export() }

    override suspend fun summary(): com.sadturtleman.flowershoes.core.backup.BackupSummary {
        val kinds = db.attachmentDao().all().groupingBy { it.kind }.eachCount()
        val me = settings.state.first().userId
        val partner = db.coupleDao().get()?.partnerUserId
        return com.sadturtleman.flowershoes.core.backup.BackupSummary(
            letters = db.letterDao().allActive().size,
            drawings = kinds[AttachmentKind.DRAWING_DOC.name] ?: 0,
            photos = kinds[AttachmentKind.PHOTO.name] ?: 0,
            myName = db.userDao().get(me)?.nickname.orEmpty(),
            partnerName = partner?.let { db.userDao().get(it)?.nickname }.orEmpty(),
        )
    }

    override suspend fun deviceSettings(): ByteArray {
        val s = settings.state.first()
        return Json.encodeToString(DeviceSettingsBackup.serializer(), DeviceSettingsBackup(s.theme.name, s.role?.name, s.onboarded)).encodeToByteArray()
    }

    companion object {
        const val DB_VERSION = 1
    }
}

/**
 * 복원 적용: DB 를 닫고 파일을 바꿔 끼운 뒤 앱을 다시 시작한다(열린 Room 이 옛 파일을 들고 있으므로).
 * 첨부 · 커플 키 · 기기 설정은 그 전에 되돌린다.
 */
class AndroidRestoreApplier(
    private val context: Context,
    private val db: AppDatabase,
    private val attachments: AttachmentStore,
    private val vault: KeyVault,
    private val settings: DeviceSettings,
) : RestoreApplier {
    override suspend fun apply(restored: RestoredBackup) {
        restored.attachments.forEach { (id, kindAndBytes) ->
            attachments.putWithId(id, runCatching { AttachmentKind.valueOf(kindAndBytes.first) }.getOrDefault(AttachmentKind.DRAWING_DOC), kindAndBytes.second)
        }
        restored.coupleKeys.forEach { (epoch, bytes) -> vault.putCoupleKey(CoupleKey(epoch, bytes)) }
        runCatching { Json.decodeFromString(DeviceSettingsBackup.serializer(), restored.deviceSettings.decodeToString()) }.getOrNull()?.let { s ->
            runCatching { settings.setTheme(ThemeAccent.valueOf(s.theme)) }
            s.role?.let { runCatching { settings.setRole(Role.valueOf(it)) } }
            if (s.onboarded) settings.setOnboarded()
        }
        withContext(Dispatchers.IO) {
            db.close()
            val file = context.getDatabasePath(AppDatabase.FILE_NAME)
            File(file.path + "-wal").delete()
            File(file.path + "-shm").delete()
            file.writeBytes(restored.database)
        }
        restart(context)
    }

    private fun restart(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }
}

/** Firebase Storage(backups/{uid}/…). 규칙: 본인만, 파일 50MB 상한. */
class FirebaseStorageBackup(private val storage: FirebaseStorage = FirebaseStorage.getInstance()) : BackupStorage {
    override suspend fun put(path: String, bytes: ByteArray) {
        storage.reference.child(path).putBytes(bytes).await()
    }

    override suspend fun get(path: String): ByteArray? =
        runCatching { storage.reference.child(path).getBytes(MAX_BYTES).await() }.getOrNull()

    override suspend fun list(prefix: String): List<BackupStorage.Entry> {
        val result = runCatching { storage.reference.child(prefix.trimEnd('/')).listAll().await() }.getOrNull() ?: return emptyList()
        return result.items.map { item ->
            val created = runCatching { item.metadata.await().creationTimeMillis }.getOrDefault(0L)
            BackupStorage.Entry(item.path.trimStart('/'), created)
        }
    }

    override suspend fun delete(path: String) {
        runCatching { storage.reference.child(path).delete().await() }
    }

    private companion object {
        const val MAX_BYTES = 50L * 1024 * 1024
    }
}

/**
 * Android Block Store. 종단간 암호화가 가능할 때(화면 잠금 있음)만 클라우드 백업을 켠다.
 * 새 기기 초기 설정에서 Google 백업으로 복원한 경우에만 돌아온다 — 그렇지 않으면 복구 코드로.
 */
class BlockStoreKeySync(context: Context) : BackupKeySync {
    private val client = Blockstore.getClient(context)

    override suspend fun isEndToEndAvailable(): Boolean = runCatching { client.isEndToEndEncryptionAvailable.await() }.getOrDefault(false)

    override suspend fun store(key: ByteArray) {
        val data = StoreBytesData.Builder()
            .setKey(KEY)
            .setBytes(key)
            .setShouldBackupToCloud(isEndToEndAvailable())
            .build()
        client.storeBytes(data).await()
    }

    override suspend fun load(): ByteArray? = runCatching {
        client.retrieveBytes(RetrieveBytesRequest.Builder().setKeys(listOf(KEY)).build()).await().blockstoreDataMap[KEY]?.bytes
    }.getOrNull()

    override suspend fun delete() {
        runCatching { client.deleteBytes(DeleteBytesRequest.Builder().setKeys(listOf(KEY)).build()).await() }
    }

    private companion object {
        const val KEY = "kkotsin_backup_key"
    }
}
