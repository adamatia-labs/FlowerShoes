package com.sadturtleman.flowershoes.core.data.sync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.sadturtleman.flowershoes.core.crypto.CoupleKey
import com.sadturtleman.flowershoes.core.crypto.DeviceKeys
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
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
class AndroidKeyVault(context: Context) : KeyVault {

    private val file = File(context.filesDir, "crypto/vault.bin")
    private val lock = Mutex()
    private var cache: Stored? = null

    @Serializable
    private data class Stored(
        val signPub: String? = null,
        val boxPub: String? = null,
        val deviceSecret: String? = null,
        val coupleKeys: Map<Int, String> = emptyMap(),
    )

    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun load(): Stored = cache ?: withContext(Dispatchers.IO) {
        if (!file.exists()) Stored() else runCatching { json.decodeFromString(Stored.serializer(), decrypt(file.readBytes()).decodeToString()) }.getOrElse { Stored() }
    }.also { cache = it }

    private suspend fun save(s: Stored) = withContext(Dispatchers.IO) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "vault.bin.tmp")
        tmp.writeBytes(encrypt(json.encodeToString(Stored.serializer(), s).encodeToByteArray()))
        tmp.renameTo(file)
        cache = s
    }

    override suspend fun deviceKeys(): DeviceKeys? = lock.withLock {
        val s = load()
        if (s.signPub == null || s.boxPub == null || s.deviceSecret == null) null
        else DeviceKeys.import(s.signPub.unb64(), s.boxPub.unb64(), s.deviceSecret.unb64())
    }

    override suspend fun putDeviceKeys(keys: DeviceKeys) = lock.withLock {
        save(load().copy(signPub = keys.signPub.b64(), boxPub = keys.boxPub.b64(), deviceSecret = keys.exportSecret().b64()))
    }

    override suspend fun coupleKey(epoch: Int): CoupleKey? = lock.withLock {
        load().coupleKeys[epoch]?.let { CoupleKey(epoch, it.unb64()) }
    }

    override suspend fun currentCoupleKey(): CoupleKey? = lock.withLock {
        load().coupleKeys.maxByOrNull { it.key }?.let { CoupleKey(it.key, it.value.unb64()) }
    }

    override suspend fun putCoupleKey(key: CoupleKey) = lock.withLock {
        val s = load()
        save(s.copy(coupleKeys = s.coupleKeys + (key.epoch to key.export().b64())))
    }

    override suspend fun dropEpochsBefore(epoch: Int) = lock.withLock {
        val s = load()
        save(s.copy(coupleKeys = s.coupleKeys.filterKeys { it >= epoch }))
    }

    override suspend fun clearCoupleKeys() = lock.withLock { save(load().copy(coupleKeys = emptyMap())) }

    override suspend fun clearAll() = lock.withLock {
        withContext(Dispatchers.IO) { file.delete() }
        cache = Stored()
    }

    // ── Keystore ──

    private fun wrappingKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
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
        const val ALIAS = "kkotsin_vault"
        const val TRANSFORM = "AES/GCM/NoPadding"
    }
}
