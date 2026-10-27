package com.sadturtleman.flowershoes.core.backup

import com.sadturtleman.flowershoes.core.crypto.BackupKey
import com.sadturtleman.flowershoes.core.crypto.CryptoException
import com.sadturtleman.flowershoes.core.crypto.RecoveryCode
import com.sadturtleman.flowershoes.core.data.sync.KeyVault
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.FileSystem
import okio.Path

/**
 * 백업 키(BK) 플랫폼 동기화(설계 7-6). iOS: iCloud 키체인(kSecAttrSynchronizable),
 * Android: Block Store(setShouldBackupToCloud, 종단간 암호화 가능할 때만).
 */
interface BackupKeySync {
    /** 종단간 암호화로 동기화할 수 있는지. 아니면 복구 코드를 더 적극적으로 안내한다. */
    suspend fun isEndToEndAvailable(): Boolean

    suspend fun store(key: ByteArray)

    suspend fun load(): ByteArray?

    suspend fun delete()
}

/** 복원한 백업을 앱에 되돌린다(DB 파일 교체 · 첨부 · 키 · 설정). 플랫폼마다 다르다. */
fun interface RestoreApplier {
    suspend fun apply(restored: RestoredBackup)
}

/**
 * 로그인 직후 보여 줄 백업 정보(복원 · 백업 찾음). 열쇠가 있으면 manifest 를 풀어 요약까지,
 * 없으면 시각만 안다([summary] null).
 */
class BackupPreview(val createdAtMs: Long, val summary: BackupSummary?, val keySource: KeySource)

/** 백업 열쇠를 어디서 찾았는지. NONE 이면 복구 코드가 필요하다. */
enum class KeySource { THIS_DEVICE, PLATFORM_SYNC, NONE }

sealed interface RestoreResult {
    data object NoBackup : RestoreResult

    /** 동기화된 백업 키가 없다(다른 기종으로 옮겼거나 초기 설정에서 복원을 건너뜀). 복구 코드를 물어야 한다. */
    data object NeedsRecoveryCode : RestoreResult

    data object WrongRecoveryCode : RestoreResult

    data class TooNew(val schemaVersion: Int) : RestoreResult

    data class Restored(val manifest: BackupManifest) : RestoreResult
}

/**
 * 백업 · 복원 흐름(설계 7-6 · 7-3-A).
 *
 * - 처음 백업할 때 BK 와 복구 코드(24자리)를 만든다. BK 는 이 기기 보관함 · 플랫폼 동기화에 두고,
 *   복구 코드로 감싼 사본(keywrap)을 서버에 둔다. 복구 코드는 온보딩에서 보여 주지 않는다(설정에서 확인).
 * - 복원: 동기화된 BK → 없으면 복구 코드. 백업이 앱보다 새 스키마면 업데이트를 안내한다.
 */
class BackupManager(
    private val storage: BackupStorage,
    private val source: BackupSource,
    private val vault: KeyVault,
    private val keySync: BackupKeySync?,
    private val applier: RestoreApplier,
    private val appSchemaVersion: Int,
    private val clock: Clock = Clock.System,
) {
    private val operations = Mutex()
    private val _lastBackupAt = MutableStateFlow<Long?>(null)
    val lastBackupAt: StateFlow<Long?> = _lastBackupAt.asStateFlow()

    /** 지금 쓰는 BK. 없으면 만들고, 복구 코드 keywrap 을 서버에 · BK 를 플랫폼 동기화에 둔다. */
    suspend fun ensureKey(uid: String): BackupKey = operations.withLock { ensureKeyInternal(uid) }

    private suspend fun ensureKeyInternal(uid: String): BackupKey {
        vault.backupSecrets()?.let { return BackupKey(it.first) }
        val key = BackupKey.generate()
        val code = RecoveryCode.generate()
        BackupEngine.storeKeyWrap(storage, uid, code, key)
        vault.putBackupSecrets(key.export(), code)
        runCatching { keySync?.store(key.export()) }
        return key
    }

    suspend fun recoveryCode(): String? = vault.backupSecrets()?.second

    suspend fun deleteSyncedKey() { keySync?.delete() }

    /**
     * 새 복구 코드(설정 › 복구 코드 › 새 코드 만들기). 같은 BK 를 새 코드로 다시 감싸 서버의 keywrap 을 바꾼다 —
     * 옛 코드로는 더 이상 열 수 없다.
     */
    suspend fun regenerateRecoveryCode(uid: String): String = operations.withLock {
        val key = ensureKeyInternal(uid)
        val code = RecoveryCode.generate()
        BackupEngine.storeKeyWrap(storage, uid, code, key)
        vault.putBackupSecrets(key.export(), code)
        code
    }

    suspend fun backupNow(uid: String): BackupManifest = operations.withLock {
        val key = ensureKeyInternal(uid)
        runCatching { keySync?.store(key.export()) }
        val manifest = BackupEngine(key, storage, clock).backup(uid, source)
        _lastBackupAt.value = manifest.createdAtMs
        manifest
    }

    suspend fun hasBackup(uid: String): Boolean = storage.list("backups/$uid/manifests/").isNotEmpty()

    suspend fun preview(uid: String): BackupPreview? {
        val newest = storage.list("backups/$uid/manifests/").maxByOrNull { it.createdAtMs } ?: return null
        val local = vault.backupSecrets()?.first
        val synced = if (local == null) runCatching { keySync?.load() }.getOrNull() else null
        val source = when {
            local != null -> KeySource.THIS_DEVICE
            synced != null -> KeySource.PLATFORM_SYNC
            else -> KeySource.NONE
        }
        val manifest = (local ?: synced)?.let { runCatching { BackupEngine(BackupKey(it), storage, clock).latestManifest(uid) }.getOrNull() }
        return BackupPreview(manifest?.createdAtMs ?: newest.createdAtMs, manifest?.summary, source)
    }

    /** [recoveryCode] 가 null 이면 동기화된 BK 로만 시도한다. */
    suspend fun restore(
        uid: String,
        recoveryCode: String? = null,
        onProgress: (fraction: Float, stage: RestoreStage) -> Unit = { _, _ -> },
    ): RestoreResult = operations.withLock { restoreInternal(uid, recoveryCode, onProgress) }

    private suspend fun restoreInternal(
        uid: String,
        recoveryCode: String?,
        onProgress: (Float, RestoreStage) -> Unit,
    ): RestoreResult {
        if (!hasBackup(uid)) return RestoreResult.NoBackup
        val key = when {
            recoveryCode != null -> try {
                BackupEngine.recoverKey(storage, uid, recoveryCode) ?: return RestoreResult.NoBackup
            } catch (_: CryptoException) {
                return RestoreResult.WrongRecoveryCode
            }
            else -> (vault.backupSecrets()?.first ?: runCatching { keySync?.load() }.getOrNull())?.let(::BackupKey)
                ?: return RestoreResult.NeedsRecoveryCode
        }
        val restored = try {
            BackupEngine(key, storage, clock).restore(uid, appSchemaVersion, onProgress)
        } catch (e: BackupTooNewException) {
            return RestoreResult.TooNew(e.schemaVersion)
        } ?: return RestoreResult.NeedsRecoveryCode // 이 키로 풀리는 manifest 가 없다
        // 복원한 기기도 같은 BK 를 계속 쓴다(새 코드로 바꾸려면 설정에서 다시 만든다).
        val code = recoveryCode ?: vault.backupSecrets()?.second ?: RecoveryCode.generate().also { BackupEngine.storeKeyWrap(storage, uid, it, key) }
        applier.apply(restored)
        vault.putBackupSecrets(key.export(), code)
        runCatching { keySync?.store(key.export()) }
        _lastBackupAt.value = restored.manifest.createdAtMs
        return RestoreResult.Restored(restored.manifest)
    }
}

/**
 * 파일 시스템 저장소. 서버 설정이 없는 빌드 · 테스트에서 쓴다. 들어가는 것은 전부 암호문이다.
 */
class FileBackupStorage(private val root: Path, private val fs: FileSystem = FileSystem.SYSTEM) : BackupStorage {
    private fun file(path: String) = root / path

    override suspend fun put(path: String, bytes: ByteArray) {
        val f = file(path)
        f.parent?.let { fs.createDirectories(it) }
        fs.write(f) { write(bytes) }
    }

    override suspend fun get(path: String): ByteArray? = file(path).takeIf { fs.exists(it) }?.let { fs.read(it) { readByteArray() } }

    override suspend fun list(prefix: String): List<BackupStorage.Entry> {
        val dir = file(prefix.trimEnd('/'))
        if (!fs.exists(dir)) return emptyList()
        return fs.list(dir).filter { fs.metadata(it).isRegularFile }.map { p ->
            val meta = fs.metadata(p)
            BackupStorage.Entry("${prefix.trimEnd('/')}/${p.name}", meta.createdAtMillis ?: meta.lastModifiedAtMillis ?: 0L)
        }
    }

    override suspend fun delete(path: String) {
        fs.delete(file(path), mustExist = false)
    }
}
