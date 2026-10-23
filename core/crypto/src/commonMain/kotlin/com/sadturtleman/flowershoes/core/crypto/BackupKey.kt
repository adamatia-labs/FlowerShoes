@file:OptIn(ExperimentalUnsignedTypes::class)

package com.sadturtleman.flowershoes.core.crypto

import com.ionspin.kotlin.crypto.pwhash.PasswordHash
import com.ionspin.kotlin.crypto.pwhash.crypto_pwhash_argon2id_ALG_ARGON2ID13

/**
 * 백업 키(BK). 서버는 모른다.
 *
 * iCloud 키체인 / Block Store 로 동기화하고, 안 되면 [RecoveryCode] 로 감싼 사본(keywrap)을 서버에 둔다.
 * 실제 암호화에 쓰는 키는 BK 에서 용도별로 파생한다 — 같은 키를 두 용도에 쓰지 않기 위해서다.
 */
class BackupKey(internal val bytes: ByteArray) {
    init {
        require(bytes.size == Aead.KEY_BYTES)
    }

    /** DB 조각 · manifest 암호화 키. */
    fun dataKey(): ByteArray = derive("data")

    /** blob 이름 = HMAC(blobKey, sha256). 파일 이름에서 내용 해시가 드러나지 않게 한다. */
    fun blobName(contentSha256: ByteArray): String =
        Hash.of(contentSha256, key = derive("blob-name")).toHex()

    private fun derive(purpose: String): ByteArray = Hash.of("kkotsin-bk-v1".encodeToByteArray(), purpose.encodeToByteArray(), key = bytes)

    fun export(): ByteArray = bytes.copyOf()

    companion object {
        fun generate(): BackupKey = BackupKey(Sodium.random(Aead.KEY_BYTES))
    }
}

/**
 * 복구 코드로 감싼 BK. 서버의 backups/{uid}/keywrap 에 이 값이 저장된다.
 */
class KeyWrap(val salt: ByteArray, val sealed: ByteArray)

object RecoveryCode {
    const val LENGTH = 24
    private const val SALT_BYTES = 16

    // 휴대폰에서 1초 안쪽으로 끝나는 정도. 코드 자체가 120비트라 Argon2 비용은 보조 장치다.
    private const val OPS_LIMIT = 3uL
    private const val MEM_LIMIT = 64 * 1024 * 1024

    fun generate(): String = CrockfordCode.random(LENGTH)

    fun wrap(code: String, key: BackupKey): KeyWrap {
        val normalized = requireNotNull(CrockfordCode.normalize(code)) { "복구 코드 형식이 아니다" }
        val salt = Sodium.random(SALT_BYTES)
        return KeyWrap(salt, Aead.seal(stretch(normalized, salt), key.bytes, WRAP_AAD))
    }

    /** 코드가 틀리면 [CryptoException]. */
    fun unwrap(code: String, wrap: KeyWrap): BackupKey {
        val normalized = CrockfordCode.normalize(code)?.takeIf { it.length == LENGTH }
            ?: throw CryptoException("복구 코드 형식이 아니다")
        return BackupKey(Aead.open(stretch(normalized, wrap.salt), wrap.sealed, WRAP_AAD))
    }

    private fun stretch(code: String, salt: ByteArray): ByteArray =
        PasswordHash.pwhash(Aead.KEY_BYTES, code, salt.toUByteArray(), OPS_LIMIT, MEM_LIMIT, crypto_pwhash_argon2id_ALG_ARGON2ID13)
            .toByteArray()

    private val WRAP_AAD = "kkotsin-keywrap-v1".encodeToByteArray()
}

internal fun ByteArray.toHex(): String = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
