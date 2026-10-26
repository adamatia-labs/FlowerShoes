package com.sadturtleman.flowershoes.app.backup

import android.content.Context
import com.google.android.gms.auth.blockstore.Blockstore
import com.google.android.gms.auth.blockstore.DeleteBytesRequest
import com.google.android.gms.auth.blockstore.RetrieveBytesRequest
import com.google.android.gms.auth.blockstore.StoreBytesData
import com.google.firebase.storage.FirebaseStorage
import com.sadturtleman.flowershoes.core.backup.BackupKeySync
import com.sadturtleman.flowershoes.core.backup.BackupStorage
import kotlinx.coroutines.tasks.await

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
