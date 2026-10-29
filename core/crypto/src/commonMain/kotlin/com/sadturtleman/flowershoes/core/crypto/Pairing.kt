@file:OptIn(ExperimentalUnsignedTypes::class)

package com.sadturtleman.flowershoes.core.crypto

import com.ionspin.kotlin.crypto.scalarmult.ScalarMultiplication
import kotlin.io.encoding.Base64

/**
 * 첫 연결(초대 링크). 확인 숫자(SAS)는 없다 — "서버가 키를 바꿔치기하지 않는다"는 신뢰를 감수한다(기획서 §4).
 *
 * 순서: 보낸 쪽이 공개키를 초대 문서에 둔다 → 받는 쪽이 링크로 그 문서를 읽고 자기 공개키로 수락한다
 * → 양쪽이 [coupleKey] 로 같은 커플 키(epoch 1)를 얻는다. 커플 키 자체는 서버를 지나지 않는다.
 *
 * [coupleKey] 는 역할(보낸 쪽 · 받는 쪽)을 모른다 — 공개키 두 쌍만 있으면 어느 기기에서든 같은 값이 나온다.
 */
object Pairing {

    private val COUPLE_KEY_CONTEXT = "kkotsin-pair-v1".encodeToByteArray()

    /** 한 기기의 공개키 두 개. 초대 · 수락 문서에는 [encode] 한 문자열 하나(`pubKey`)로 오간다. */
    class PublicKeys(val signPub: ByteArray, val boxPub: ByteArray) {
        /** base64(signPub(32) ‖ boxPub(32)). */
        fun encode(): String = Base64.encode(signPub + boxPub)

        companion object {
            private const val KEY_BYTES = 32

            fun of(keys: DeviceKeys) = PublicKeys(keys.signPub, keys.boxPub)

            fun decode(text: String): PublicKeys? {
                val raw = runCatching { Base64.decode(text) }.getOrNull() ?: return null
                if (raw.size != 2 * KEY_BYTES) return null
                return PublicKeys(raw.copyOf(KEY_BYTES), raw.copyOfRange(KEY_BYTES, raw.size))
            }
        }
    }

    /**
     * 내 비밀키와 상대 공개키로 커플 키(epoch 1)를 만든다.
     * `BLAKE2b-256( P(ctx) ‖ P(X25519(내 boxSec, 상대 boxPub)) ‖ P(token) ‖ 두 기기 P(signPub) ‖ P(boxPub) — boxPub 오름차순 )`
     */
    fun coupleKey(mine: DeviceKeys, theirs: PublicKeys, token: String): CoupleKey {
        val shared = mine.agree(theirs.boxPub)
        val (first, second) = listOf(PublicKeys.of(mine), theirs).sortedWith { a, b -> compareBytes(a.boxPub, b.boxPub) }
        val key = Hash.of(
            COUPLE_KEY_CONTEXT, shared, token.encodeToByteArray(),
            first.signPub, first.boxPub, second.signPub, second.boxPub,
        )
        shared.fill(0)
        return CoupleKey(epoch = 1, bytes = key)
    }

    private fun compareBytes(a: ByteArray, b: ByteArray): Int {
        for (i in 0 until minOf(a.size, b.size)) {
            val d = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
            if (d != 0) return d
        }
        return a.size - b.size
    }
}

/** X25519 공유 비밀. 이 모듈 안에서 바로 해시해 쓰고 버린다. */
internal fun DeviceKeys.agree(theirBoxPub: ByteArray): ByteArray =
    try {
        ScalarMultiplication.scalarMultiplication(boxSec.toUByteArray(), theirBoxPub.toUByteArray()).toByteArray()
    } catch (e: Exception) {
        throw CryptoException("상대 공개키로 키를 맞출 수 없다", e)
    }

/**
 * 초대 토큰: 128비트 무작위, base64url(패딩 없음) 22자. 링크(`/i/{token}` · `kakaolink?invite=`)로만 오가는 1회용 비밀이다.
 * 문서 id 를 알면 초대 문서를 읽을 수 있으므로(firestore.rules) 추측 공간이 곧 보안이다.
 */
object InviteToken {
    const val LENGTH = 22
    private const val BYTES = 16
    private val URL_SAFE = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
    private val FORMAT = Regex("^[A-Za-z0-9_-]{$LENGTH}$")

    fun random(): String = URL_SAFE.encode(Sodium.random(BYTES))

    fun isValid(token: String): Boolean = FORMAT.matches(token)
}
