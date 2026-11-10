package com.sadturtleman.flowershoes.core.data.sync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import com.sadturtleman.flowershoes.core.crypto.CoupleKey
import com.sadturtleman.flowershoes.core.crypto.DeviceKeys
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.OverlappingFileLockException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Android 키 보관함. 비밀 값들을 Android Keystore 의 AES-GCM 키(기기 밖으로 나가지 않음)로 감싸 files/crypto/ 에 둔다.
 *
 * files/crypto/ 는 자동 백업 · 기기 간 이전에서 빠진다(data_extraction_rules.xml). Keystore 키도 백업되지 않으므로
 * 이 파일만 다른 기기로 옮겨져도 풀 수 없다 — 복원은 앱 자체 암호화 백업으로만 한다.
 */
class AndroidKeyVault(context: Context, private val wrappingKeyAlias: String = ALIAS) : KeyVault {

    private val file = File(context.filesDir, "crypto/vault.bin")
    private var cache: Stored? = null

    @Serializable
    private data class Stored(
        val signPub: String? = null,
        val boxPub: String? = null,
        val deviceSecret: String? = null,
        val coupleKeys: Map<Int, String> = emptyMap(),
        /** 기기 교체 승인 중인 새 커플 키(#20). */
        val pendingApproval: StoredApproval? = null,
    )

    private val json = Json { ignoreUnknownKeys = true }

    /** 같은 파일을 다루는 인스턴스와 프로세스가 하나의 저장 경계를 공유한다. */
    private suspend fun <T> withVault(block: suspend () -> T): T {
        val path = withContext(Dispatchers.IO) { file.canonicalPath }
        val processLock = synchronized(PROCESS_LOCKS) { PROCESS_LOCKS.getOrPut(path) { Mutex() } }
        return processLock.withLock {
            withContext(Dispatchers.IO) {
                val directory = checkNotNull(file.parentFile)
                check(directory.isDirectory || directory.mkdirs() || directory.isDirectory) { "키 보관 폴더를 열 수 없어요" }
                RandomAccessFile(File(directory, "vault.lock"), "rw").use { lockFile ->
                    lockFile.channel.use { channel ->
                        var acquired = try { channel.tryLock() } catch (_: OverlappingFileLockException) { null }
                        while (acquired == null) {
                            delay(25)
                            acquired = try { channel.tryLock() } catch (_: OverlappingFileLockException) { null }
                        }
                        acquired.use {
                            cache = null
                            block()
                        }
                    }
                }
            }
        }
    }

    private suspend fun load(): Stored = cache ?: withContext(Dispatchers.IO) {
        // 파일이 있는 경우 복호화나 파싱 실패를 빈 보관함으로 바꾸면 다음 쓰기가 원본 키를 덮어쓴다.
        if (!existsOrThrow(file)) Stored() else json.decodeFromString(Stored.serializer(), decrypt(file.readBytes()).decodeToString())
    }.also { cache = it }

    private suspend fun save(s: Stored) = withContext(Dispatchers.IO) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "vault.bin.tmp")
        tmp.writeBytes(encrypt(json.encodeToString(Stored.serializer(), s).encodeToByteArray()))
        check(tmp.renameTo(file)) { "키 보관 파일을 교체하지 못했어요" }
        cache = s
    }

    override suspend fun deviceKeys(): DeviceKeys? = withVault {
        val s = load()
        if (s.signPub == null || s.boxPub == null || s.deviceSecret == null) null
        else DeviceKeys.import(s.signPub.unb64(), s.boxPub.unb64(), s.deviceSecret.unb64())
    }

    override suspend fun putDeviceKeys(keys: DeviceKeys) = withVault {
        save(load().copy(signPub = keys.signPub.b64(), boxPub = keys.boxPub.b64(), deviceSecret = keys.exportSecret().b64()))
    }

    override suspend fun coupleKey(epoch: Int): CoupleKey? = withVault {
        load().coupleKeys[epoch]?.let { CoupleKey(epoch, it.unb64()) }
    }

    override suspend fun currentCoupleKey(): CoupleKey? = withVault {
        load().coupleKeys.maxByOrNull { it.key }?.let { CoupleKey(it.key, it.value.unb64()) }
    }

    override suspend fun putCoupleKey(key: CoupleKey) = withVault {
        val s = load()
        save(s.copy(coupleKeys = s.coupleKeys + (key.epoch to key.export().b64())))
    }

    override suspend fun dropEpochsBefore(epoch: Int) = withVault {
        val s = load()
        save(s.copy(coupleKeys = s.coupleKeys.filterKeys { it >= epoch }))
    }

    override suspend fun pendingApproval(): PendingApproval? = withVault { load().pendingApproval?.toModel() }

    override suspend fun putPendingApproval(approval: PendingApproval?) = withVault {
        save(load().copy(pendingApproval = approval?.let(StoredApproval::of)))
    }

    override suspend fun clearCoupleKeys() = withVault { save(load().copy(coupleKeys = emptyMap(), pendingApproval = null)) }

    override suspend fun clearAll() = withVault {
        // 삭제와 캐시 초기화 사이의 취소로 오래된 키가 다시 저장되지 않도록 한 상태 전이로 처리한다.
        withContext(NonCancellable + Dispatchers.IO) {
            deleteIfPresent(File(file.parentFile, "vault.bin.tmp"))
            deleteIfPresent(file)
            cache = Stored()
        }
    }

    private fun existsOrThrow(path: File): Boolean = try {
        Os.lstat(path.absolutePath)
        true
    } catch (e: ErrnoException) {
        if (e.errno == OsConstants.ENOENT) false else throw IOException("키 보관 파일 상태를 확인하지 못했어요", e)
    }

    private fun deleteIfPresent(path: File) {
        try {
            Os.remove(path.absolutePath)
        } catch (e: ErrnoException) {
            if (e.errno != OsConstants.ENOENT) throw IOException("키 보관 파일을 삭제하지 못했어요", e)
        }
    }

    // ── Keystore ──

    private fun wrappingKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(wrappingKeyAlias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(wrappingKeyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, wrappingKey()) }
        return byteArrayOf(c.iv.size.toByte()) + c.iv + c.doFinal(plain)
    }

    private fun decrypt(blob: ByteArray): ByteArray {
        val ivLen = blob[0].toInt()
        val c = Cipher.getInstance(TRANSFORM).apply {
            init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(128, blob, 1, ivLen))
        }
        return c.doFinal(blob, 1 + ivLen, blob.size - 1 - ivLen)
    }

    private companion object {
        val PROCESS_LOCKS = mutableMapOf<String, Mutex>()
        const val ALIAS = "kkotsin_vault"
        const val TRANSFORM = "AES/GCM/NoPadding"
    }
}
