package com.sadturtleman.flowershoes.core.crypto

/**
 * 상대 폰이 넘겨주기(Relink)에만 남은 확인 코드(SAS) · 커밋. 첫 연결은 초대 링크([Pairing])로 바뀌었고,
 * 기기 교체 통합 흐름(#40)이 Relink 와 함께 이 파일을 지운다.
 *
 * 순서: 받는 쪽이 [commit] 만 올린다 → 넘겨주는 쪽이 공개키를 공개한다 → 받는 쪽이 공개키와 nonce 를 공개한다
 * → 넘겨주는 쪽이 [verifyCommitment] 로 확인한다 → 양쪽이 [sas] 를 화면에 띄워 서로 맞는지 본다.
 */
object RelinkSas {

    const val NONCE_BYTES = 32

    private val COMMIT_CONTEXT = "kkotsin-commit-v1".encodeToByteArray()
    private val SAS_CONTEXT = "kkotsin-sas-v1".encodeToByteArray()

    fun newNonce(): ByteArray = Sodium.random(NONCE_BYTES)

    fun commit(keys: Pairing.PublicKeys, nonce: ByteArray): ByteArray =
        Hash.of(COMMIT_CONTEXT, keys.boxPub, keys.signPub, nonce)

    fun verifyCommitment(commitment: ByteArray, revealed: Pairing.PublicKeys, nonce: ByteArray): Boolean =
        constantTimeEquals(commitment, commit(revealed, nonce))

    /** 6자리 숫자 문자열. 양쪽이 같은 인자(넘겨주는 쪽 = A, 받는 쪽 = B)로 계산해야 같아진다. */
    fun sas(code: String, inviter: Pairing.PublicKeys, joiner: Pairing.PublicKeys, joinerNonce: ByteArray): String {
        val h = Hash.of(
            SAS_CONTEXT, code.encodeToByteArray(),
            inviter.signPub, inviter.boxPub, joiner.signPub, joiner.boxPub, joinerNonce,
            length = 16,
        )
        var v = 0L
        for (i in 0 until 8) v = (v shl 8) or (h[i].toLong() and 0xFF)
        // 64비트를 10^6 으로 나눈 나머지 — 치우침은 2^-44 수준이라 무시해도 된다.
        val n = (v ushr 1) % 1_000_000
        return n.toString().padStart(6, '0')
    }
}

/**
 * Crockford Base32 코드. 0/O, 1/I/L 처럼 헷갈리는 글자를 같은 것으로 읽어 손으로 옮겨 적기 쉽다.
 *
 * 넘겨주기 코드: 8자리(40비트), 1회용 · 24시간. (#40 에서 Relink 와 함께 지운다)
 */
object CrockfordCode {
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    fun random(length: Int): String {
        val bytes = Sodium.random(length) // 바이트마다 하위 5비트만 쓴다 → 글자당 정확히 5비트, 치우침 없음
        return buildString { bytes.forEach { append(ALPHABET[it.toInt() and 0x1F]) } }
    }

    /** 사용자가 입력한 값을 정규화한다. 대소문자 · 하이픈 · 공백을 무시하고 헷갈리는 글자를 맞춘다. 형식이 아니면 null. */
    fun normalize(input: String): String? {
        val out = StringBuilder()
        for (c in input.uppercase()) {
            when (c) {
                '-', ' ' -> continue
                'O' -> out.append('0')
                'I', 'L' -> out.append('1')
                else -> if (c in ALPHABET) out.append(c) else return null
            }
        }
        return out.toString()
    }

    /** 4자리씩 하이픈으로 끊어 보여 준다. */
    fun format(code: String): String = code.chunked(4).joinToString("-")
}
