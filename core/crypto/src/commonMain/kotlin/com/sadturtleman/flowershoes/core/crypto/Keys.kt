@file:OptIn(ExperimentalUnsignedTypes::class)

package com.sadturtleman.flowershoes.core.crypto

import com.ionspin.kotlin.crypto.LibsodiumInitializer
import com.ionspin.kotlin.crypto.aead.AuthenticatedEncryptionWithAssociatedData
import com.ionspin.kotlin.crypto.box.Box
import com.ionspin.kotlin.crypto.generichash.GenericHash
import com.ionspin.kotlin.crypto.signature.Signature
import com.ionspin.kotlin.crypto.util.LibsodiumRandom

/** libsodium 초기화. 어떤 연산보다 먼저, 앱 시작 시 한 번 부른다(여러 번 불러도 된다). */
object Sodium {
    fun init() {
        if (!LibsodiumInitializer.isInitialized()) LibsodiumInitializer.initializeWithCallback {}
    }

    fun random(size: Int): ByteArray = LibsodiumRandom.buf(size).toByteArray()
}

class CryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 기기 키쌍. 서명(Ed25519)과 상자(X25519) 두 쌍이다.
 *
 * 비밀키는 이 기기 Keychain/Keystore 에만 둔다(ThisDeviceOnly). 백업 · 동기화하지 않는다.
 */
class DeviceKeys(
    val signPub: ByteArray,
    internal val signSec: ByteArray,
    val boxPub: ByteArray,
    internal val boxSec: ByteArray,
) {
    fun sign(message: ByteArray): ByteArray =
        Signature.detached(message.toUByteArray(), signSec.toUByteArray()).toByteArray()

    /** 다른 기기가 [Box.seal] 로 나에게 보낸 것(커플 키 전달 등)을 푼다. */
    fun openSealed(sealed: ByteArray): ByteArray = try {
        Box.sealOpen(sealed.toUByteArray(), boxPub.toUByteArray(), boxSec.toUByteArray()).toByteArray()
    } catch (e: Exception) {
        throw CryptoException("봉인을 풀 수 없다", e)
    }

    /** Keychain/Keystore 에 넣을 비밀 부분. */
    fun exportSecret(): ByteArray = signSec + boxSec

    companion object {
        fun generate(): DeviceKeys {
            val s = Signature.keypair()
            val b = Box.keypair()
            return DeviceKeys(s.publicKey.toByteArray(), s.secretKey.toByteArray(), b.publicKey.toByteArray(), b.secretKey.toByteArray())
        }

        fun import(signPub: ByteArray, boxPub: ByteArray, secret: ByteArray): DeviceKeys {
            require(secret.size == SIGN_SECRET + BOX_SECRET)
            return DeviceKeys(signPub, secret.copyOf(SIGN_SECRET), boxPub, secret.copyOfRange(SIGN_SECRET, secret.size))
        }

        private const val SIGN_SECRET = 64
        private const val BOX_SECRET = 32
    }
}

object Signatures {
    fun verify(message: ByteArray, signature: ByteArray, signPub: ByteArray): Boolean = try {
        Signature.verifyDetached(signature.toUByteArray(), message.toUByteArray(), signPub.toUByteArray())
        true
    } catch (_: Exception) {
        false
    }
}

/** [recipientBoxPub] 만 풀 수 있게 봉인한다. 커플 키를 기기마다 나눠 줄 때 쓴다. */
fun sealTo(recipientBoxPub: ByteArray, plaintext: ByteArray): ByteArray =
    Box.seal(plaintext.toUByteArray(), recipientBoxPub.toUByteArray()).toByteArray()

/**
 * 대칭 키 하나로 하는 인증 암호(XChaCha20-Poly1305). 출력은 `nonce(24) ‖ 암호문+태그`.
 * nonce 가 24바이트라 무작위로 뽑아도 겹칠 걱정이 없다.
 */
object Aead {
    const val KEY_BYTES = 32
    const val NONCE_BYTES = 24

    fun seal(key: ByteArray, plaintext: ByteArray, associatedData: ByteArray = ByteArray(0)): ByteArray {
        require(key.size == KEY_BYTES)
        val nonce = Sodium.random(NONCE_BYTES)
        val ct = AuthenticatedEncryptionWithAssociatedData.xChaCha20Poly1305IetfEncrypt(
            plaintext.toUByteArray(), associatedData.toUByteArray(), nonce.toUByteArray(), key.toUByteArray(),
        )
        return nonce + ct.toByteArray()
    }

    fun open(key: ByteArray, sealed: ByteArray, associatedData: ByteArray = ByteArray(0)): ByteArray {
        require(key.size == KEY_BYTES)
        if (sealed.size < NONCE_BYTES) throw CryptoException("암호문이 너무 짧다")
        return try {
            AuthenticatedEncryptionWithAssociatedData.xChaCha20Poly1305IetfDecrypt(
                sealed.copyOfRange(NONCE_BYTES, sealed.size).toUByteArray(),
                associatedData.toUByteArray(),
                sealed.copyOf(NONCE_BYTES).toUByteArray(),
                key.toUByteArray(),
            ).toByteArray()
        } catch (e: Exception) {
            throw CryptoException("복호화 실패(키가 다르거나 변조됨)", e)
        }
    }
}

/** BLAKE2b. [key] 를 주면 MAC 이 된다. */
object Hash {
    fun of(vararg parts: ByteArray, length: Int = 32, key: ByteArray? = null): ByteArray {
        // 이어 붙인 경계가 모호해지지 않도록 각 조각 앞에 길이를 붙인다.
        val joined = parts.fold(ByteArray(0)) { acc, p -> acc + lengthPrefix(p.size) + p }
        return GenericHash.genericHash(joined.toUByteArray(), length, key?.toUByteArray()).toByteArray()
    }

    private fun lengthPrefix(n: Int) = byteArrayOf((n ushr 24).toByte(), (n ushr 16).toByte(), (n ushr 8).toByte(), n.toByte())
}

/**
 * 커플 키. 기기가 바뀔 때마다 [epoch] 가 1씩 오르고, 모든 Op 에 그 번호가 실린다.
 * 옛 epoch 키는 아직 안 받은 Op 을 풀기 위해 31일 보관한 뒤 지운다.
 */
class CoupleKey(val epoch: Int, internal val bytes: ByteArray) {
    init {
        require(bytes.size == Aead.KEY_BYTES)
    }

    fun seal(plaintext: ByteArray, associatedData: ByteArray): ByteArray = Aead.seal(bytes, plaintext, associatedData)

    fun open(sealed: ByteArray, associatedData: ByteArray): ByteArray = Aead.open(bytes, sealed, associatedData)

    /** 활성 기기 하나에게 이 키를 전달할 봉투. */
    fun wrapFor(recipientBoxPub: ByteArray): ByteArray = sealTo(recipientBoxPub, byteArrayOf(*intBytes(epoch)) + bytes)

    fun export(): ByteArray = bytes.copyOf()

    companion object {
        fun generate(epoch: Int): CoupleKey = CoupleKey(epoch, Sodium.random(Aead.KEY_BYTES))

        fun unwrap(keys: DeviceKeys, wrapped: ByteArray): CoupleKey {
            val plain = keys.openSealed(wrapped)
            if (plain.size != 4 + Aead.KEY_BYTES) throw CryptoException("커플 키 봉투 형식이 아니다")
            val epoch = (plain[0].toInt() and 0xFF shl 24) or (plain[1].toInt() and 0xFF shl 16) or
                (plain[2].toInt() and 0xFF shl 8) or (plain[3].toInt() and 0xFF)
            return CoupleKey(epoch, plain.copyOfRange(4, plain.size))
        }
    }
}

internal fun intBytes(n: Int) = byteArrayOf((n ushr 24).toByte(), (n ushr 16).toByte(), (n ushr 8).toByte(), n.toByte())

internal fun longBytes(n: Long) = ByteArray(8) { (n ushr (56 - 8 * it)).toByte() }
