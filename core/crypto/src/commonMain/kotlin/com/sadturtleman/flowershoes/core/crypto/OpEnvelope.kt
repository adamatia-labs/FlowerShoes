package com.sadturtleman.flowershoes.core.crypto

/**
 * 우편함에 올라가는 Op 봉투(spec/op-format.md).
 *
 * 머리([header])는 평문이지만 암호문의 AAD 로 묶이고 서명에도 들어간다 — 서버가 stream · seq · epoch 를
 * 바꿔치기하면 복호화나 서명 확인에서 걸린다.
 *
 * 서명 대상 바이트 = `"kkotsin-op-v1" ‖ header ‖ ciphertext` (각 조각 길이 접두).
 */
data class OpHeader(
    val opId: String,
    val stream: String,
    val seq: Long,
    val keyEpoch: Int,
) {
    fun toBytes(): ByteArray =
        lengthPrefixed(opId.encodeToByteArray()) + lengthPrefixed(stream.encodeToByteArray()) + longBytes(seq) + intBytes(keyEpoch)

    private fun lengthPrefixed(b: ByteArray) = intBytes(b.size) + b
}

class OpEnvelope(
    val header: OpHeader,
    val ciphertext: ByteArray,
    val signature: ByteArray,
) {
    companion object {
        private val CONTEXT = "kkotsin-op-v1".encodeToByteArray()

        private fun signedBytes(header: OpHeader, ciphertext: ByteArray): ByteArray =
            Hash.of(CONTEXT, header.toBytes(), ciphertext, length = 64)

        fun seal(header: OpHeader, plaintext: ByteArray, key: CoupleKey, signer: DeviceKeys): OpEnvelope {
            require(header.keyEpoch == key.epoch) { "Op 의 epoch 와 키의 epoch 가 다르다" }
            val ct = key.seal(plaintext, header.toBytes())
            return OpEnvelope(header, ct, signer.sign(signedBytes(header, ct)))
        }

        /**
         * 서명을 먼저 확인하고 푼다. [keyFor] 는 epoch 로 키를 찾는다(옛 epoch 는 31일 보관분).
         * 서명한 기기 공개키는 couples/{c}/devices 에 등록된 값이어야 한다.
         */
        fun open(envelope: OpEnvelope, senderSignPub: ByteArray, keyFor: (Int) -> CoupleKey?): ByteArray {
            if (!Signatures.verify(signedBytes(envelope.header, envelope.ciphertext), envelope.signature, senderSignPub)) {
                throw CryptoException("서명이 맞지 않는다")
            }
            val key = keyFor(envelope.header.keyEpoch) ?: throw CryptoException("epoch ${envelope.header.keyEpoch} 키가 없다")
            return key.open(envelope.ciphertext, envelope.header.toBytes())
        }
    }
}
