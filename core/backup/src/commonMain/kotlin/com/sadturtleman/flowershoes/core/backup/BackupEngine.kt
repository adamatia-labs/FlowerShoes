package com.sadturtleman.flowershoes.core.backup

import com.sadturtleman.flowershoes.core.compression.Zstd
import com.sadturtleman.flowershoes.core.crypto.Aead
import com.sadturtleman.flowershoes.core.crypto.BackupKey
import com.sadturtleman.flowershoes.core.crypto.KeyWrap
import com.sadturtleman.flowershoes.core.crypto.RecoveryCode
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 서버 저장소(backups/{uid}/…). 실제 구현은 Firebase Storage, 테스트는 메모리. */
interface BackupStorage {
    class Entry(val path: String, val createdAtMs: Long)

    suspend fun put(path: String, bytes: ByteArray)

    suspend fun get(path: String): ByteArray?

    suspend fun list(prefix: String): List<Entry>

    suspend fun delete(path: String)
}

/** 백업할 것을 앱이 내준다. DB 는 VACUUM INTO 로 만든 일관된 사본이어야 한다. */
interface BackupSource {
    class Attachment(val id: String, val kind: String, val sha256: ByteArray, val read: suspend () -> ByteArray)

    /** Live sources freeze their DB and attachment references before network uploads begin. */
    suspend fun capture(): BackupSource? = null

    suspend fun databaseSnapshot(): ByteArray

    suspend fun attachments(): List<Attachment>

    /** 현재 커플 키와 직전 epoch(31일 보관분). epoch → 32바이트. */
    suspend fun coupleKeys(): Map<Int, ByteArray>

    /** 이 기기만 쓰는 설정(테마 · 알림 · 임시저장). 복원할 때 편의로 함께 돌려준다. */
    suspend fun deviceSettings(): ByteArray

    /** 복원 화면이 보여 주는 요약(편지 · 그림 · 사진 개수, 이름). manifest 와 함께 암호화된다. */
    suspend fun summary(): BackupSummary = BackupSummary()

    val schemaVersion: Int
}

@Serializable
data class BlobRef(val attachmentId: String, val kind: String, val name: String)

/** 복원 화면(복원 · 백업 찾음)의 "편지 128 · 그림 42 · 사진 310 · 1.2GB" 와 인사말 이름. */
@Serializable
data class BackupSummary(
    val letters: Int = 0,
    val drawings: Int = 0,
    val photos: Int = 0,
    val bytes: Long = 0,
    val myName: String = "",
    val partnerName: String = "",
)

/** 복원 단계(복원 · 복원 중 화면의 줄). */
enum class RestoreStage { DATA, MEDIA, RELINK }

@Serializable
data class BackupManifest(
    val version: Long,
    val createdAtMs: Long,
    val schemaVersion: Int,
    val dbChunks: List<String>,
    val blobs: List<BlobRef>,
    val coupleKeys: Map<Int, String>,
    val deviceSettings: String,
    val summary: BackupSummary = BackupSummary(),
)

/** 복원 결과. 앱이 DB 파일을 바꿔 끼우고 첨부 · 키 · 설정을 되돌린다. */
class RestoredBackup(
    val manifest: BackupManifest,
    val database: ByteArray,
    val attachments: Map<String, Pair<String, ByteArray>>,
    val coupleKeys: Map<Int, ByteArray>,
    val deviceSettings: ByteArray,
)

class BackupTooNewException(val schemaVersion: Int) : Exception("앱을 업데이트해 주세요(백업 스키마 $schemaVersion)")

/**
 * 암호화 백업(설계 7-6).
 *
 * - DB: 4MB 조각마다 BK 의 data 키로 XChaCha20-Poly1305. AAD 는 조각 경로라 조각을 바꿔치기하면 풀리지 않는다.
 * - 첨부: 이름은 HMAC(BK, sha256) — 내용 해시가 드러나지 않고, 같은 첨부는 한 번만 올린다.
 * - manifest(암호화): 조각 목록 · 첨부 목록 · 커플 키 · 기기 설정. 최근 2개만 둔다.
 * - 정리: 어느 manifest 도 가리키지 않는 첨부를 지우되, 생성 7일이 안 된 것은 남긴다(업로드 중인 것 보호).
 *   업로드 순서는 첨부 → DB 조각 → manifest. manifest 가 마지막이라 중간에 끊겨도 이전 백업은 온전하다.
 *
 * zstd 압축은 플랫폼 쪽에서 DB 사본에 덧씌운다(순수 Kotlin 구현이 없다).
 */
class BackupEngine(
    private val key: BackupKey,
    private val storage: BackupStorage,
    private val clock: Clock = Clock.System,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun backup(uid: String, source: BackupSource): BackupManifest {
        val captured = source.capture() ?: source
        val root = "backups/$uid"
        val now = clock.now().toEpochMilliseconds()
        val version = now
        val dataKey = key.dataKey()

        // 1) 첨부(없는 것만)
        val existing = storage.list("$root/blobs/").map { it.path.substringAfterLast('/') }.toSet()
        var blobsBytes = 0L
        val blobs = captured.attachments().map { a ->
            val name = key.blobName(a.sha256)
            val bytes = a.read()
            blobsBytes += bytes.size
            if (name !in existing) {
                val path = "$root/blobs/$name"
                storage.put(path, Aead.seal(dataKey, Zstd.packFlagged(bytes), path.encodeToByteArray()))
            }
            BlobRef(a.id, a.kind, name)
        }

        // 2) DB 조각
        val db = captured.databaseSnapshot()
        val chunks = (0 until maxOf(1, (db.size + CHUNK - 1) / CHUNK)).map { n ->
            val path = "$root/db/$version/$n"
            val part = db.copyOfRange(n * CHUNK, minOf(db.size, (n + 1) * CHUNK))
            storage.put(path, Aead.seal(dataKey, Zstd.packFlagged(part), path.encodeToByteArray()))
            path
        }

        // 3) manifest(마지막)
        val manifest = BackupManifest(
            version = version,
            createdAtMs = now,
            schemaVersion = captured.schemaVersion,
            dbChunks = chunks,
            blobs = blobs,
            coupleKeys = captured.coupleKeys().mapValues { Base64.encode(it.value) },
            deviceSettings = Base64.encode(captured.deviceSettings()),
            summary = captured.summary().copy(bytes = db.size.toLong() + blobsBytes),
        )
        val manifestPath = "$root/manifests/$version"
        storage.put(manifestPath, Aead.seal(dataKey, json.encodeToString(BackupManifest.serializer(), manifest).encodeToByteArray(), manifestPath.encodeToByteArray()))

        prune(root, now)
        return manifest
    }

    /** 가장 최근 manifest 로 복원한다. 백업이 없으면 null. 앱보다 새 스키마면 [BackupTooNewException]. */
    suspend fun restore(
        uid: String,
        appSchemaVersion: Int,
        onProgress: (fraction: Float, stage: RestoreStage) -> Unit = { _, _ -> },
    ): RestoredBackup? {
        val root = "backups/$uid"
        val dataKey = key.dataKey()
        val manifest = latestManifest(uid) ?: return null
        if (manifest.schemaVersion > appSchemaVersion) throw BackupTooNewException(manifest.schemaVersion)
        val total = (manifest.dbChunks.size + manifest.blobs.size).coerceAtLeast(1)
        var done = 0
        onProgress(0f, RestoreStage.DATA)
        val db = manifest.dbChunks.fold(ByteArray(0)) { acc, path ->
            val part = Zstd.unpackFlagged(Aead.open(dataKey, checkNotNull(storage.get(path)) { "조각이 없다: $path" }, path.encodeToByteArray()))
            onProgress(++done / total.toFloat(), RestoreStage.DATA)
            acc + part
        }
        val attachments = manifest.blobs.associate { b ->
            val path = "$root/blobs/${b.name}"
            val bytes = Zstd.unpackFlagged(Aead.open(dataKey, checkNotNull(storage.get(path)) { "첨부가 없다: $path" }, path.encodeToByteArray()))
            onProgress(++done / total.toFloat(), RestoreStage.MEDIA)
            b.attachmentId to (b.kind to bytes)
        }
        onProgress(1f, RestoreStage.RELINK)
        return RestoredBackup(
            manifest, db, attachments,
            manifest.coupleKeys.mapValues { Base64.decode(it.value) },
            Base64.decode(manifest.deviceSettings),
        )
    }

    /** 가장 최근 manifest(이 키로 풀리는 것). 없으면 null. */
    suspend fun latestManifest(uid: String): BackupManifest? = manifests("backups/$uid").maxByOrNull { it.version }

    private suspend fun manifests(root: String): List<BackupManifest> {
        val dataKey = key.dataKey()
        return storage.list("$root/manifests/").mapNotNull { e ->
            val sealed = storage.get(e.path) ?: return@mapNotNull null
            runCatching {
                json.decodeFromString(BackupManifest.serializer(), Aead.open(dataKey, sealed, e.path.encodeToByteArray()).decodeToString())
            }.getOrNull()
        }
    }

    private suspend fun prune(root: String, now: Long) {
        val all = manifests(root).sortedByDescending { it.version }
        val keep = all.take(KEEP_MANIFESTS)
        all.drop(KEEP_MANIFESTS).forEach { old ->
            storage.delete("$root/manifests/${old.version}")
            old.dbChunks.forEach { storage.delete(it) }
        }
        val referenced = keep.flatMap { m -> m.blobs.map { it.name } }.toSet()
        storage.list("$root/blobs/").forEach { e ->
            val name = e.path.substringAfterLast('/')
            if (name !in referenced && now - e.createdAtMs >= BLOB_GRACE.inWholeMilliseconds) storage.delete(e.path)
        }
    }

    companion object {
        const val CHUNK = 4 * 1024 * 1024
        const val KEEP_MANIFESTS = 2
        val BLOB_GRACE = 7.days

        /** 복구 코드로 감싼 BK 를 서버에 둔다(backups/{uid}/keywrap). 다른 기종으로 옮길 때 쓴다. */
        suspend fun storeKeyWrap(storage: BackupStorage, uid: String, code: String, key: BackupKey) {
            val wrap = RecoveryCode.wrap(code, key)
            storage.put("backups/$uid/keywrap", byteArrayOf(wrap.salt.size.toByte()) + wrap.salt + wrap.sealed)
        }

        suspend fun recoverKey(storage: BackupStorage, uid: String, code: String): BackupKey? {
            val bytes = storage.get("backups/$uid/keywrap") ?: return null
            val saltLen = bytes[0].toInt()
            val wrap = KeyWrap(bytes.copyOfRange(1, 1 + saltLen), bytes.copyOfRange(1 + saltLen, bytes.size))
            return RecoveryCode.unwrap(code, wrap)
        }
    }
}
