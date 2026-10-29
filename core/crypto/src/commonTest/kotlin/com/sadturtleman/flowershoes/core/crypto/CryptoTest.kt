package com.sadturtleman.flowershoes.core.crypto

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CryptoTest {

    @BeforeTest
    fun init() = Sodium.init()

    private fun DeviceKeys.publicKeys() = Pairing.PublicKeys(signPub, boxPub)

    @Test
    fun `Op 봉투는 서명을 확인하고 epoch 로 키를 찾아 푼다`() {
        val sender = DeviceKeys.generate()
        val key = CoupleKey.generate(epoch = 2)
        val header = OpHeader("op-1", "dev-a:app", 7, 2)
        val env = OpEnvelope.seal(header, "보고싶어".encodeToByteArray(), key, sender)

        val plain = OpEnvelope.open(env, sender.signPub) { if (it == 2) key else null }
        assertEquals("보고싶어", plain.decodeToString())
    }

    @Test
    fun `서버가 머리의 seq 를 바꾸면 열리지 않는다`() {
        val sender = DeviceKeys.generate()
        val key = CoupleKey.generate(1)
        val env = OpEnvelope.seal(OpHeader("op-1", "dev-a:app", 7, 1), byteArrayOf(1), key, sender)
        val tampered = OpEnvelope(env.header.copy(seq = 8), env.ciphertext, env.signature)
        assertFailsWith<CryptoException> { OpEnvelope.open(tampered, sender.signPub) { key } }
        // 다른 기기 공개키로는 서명이 맞지 않는다.
        assertFailsWith<CryptoException> { OpEnvelope.open(env, DeviceKeys.generate().signPub) { key } }
    }

    @Test
    fun `커플 키는 받는 기기 공개키로만 풀린다`() {
        val partner = DeviceKeys.generate()
        val key = CoupleKey.generate(3)
        val restored = CoupleKey.unwrap(partner, key.wrapFor(partner.boxPub))
        assertEquals(3, restored.epoch)
        assertContentEquals(key.export(), restored.export())
        assertFailsWith<CryptoException> { CoupleKey.unwrap(DeviceKeys.generate(), key.wrapFor(partner.boxPub)) }
    }

    @Test
    fun `기기 교체 MAC 은 같은 커플 키로만 만들 수 있다`() {
        val key = CoupleKey.generate(1)
        val request = "rotate:dev-new:epoch2".encodeToByteArray()
        val tag = key.mac(request)
        assertTrue(key.verifyMac(request, tag))
        assertFalse(CoupleKey.generate(1).verifyMac(request, tag))
        assertFalse(key.verifyMac("rotate:dev-evil:epoch2".encodeToByteArray(), tag))
    }

    @Test
    fun `커밋한 키와 다른 키를 공개하면 들킨다`() {
        val joiner = DeviceKeys.generate()
        val nonce = Pairing.newNonce()
        val commitment = Pairing.commit(joiner.publicKeys(), nonce)
        assertTrue(Pairing.verifyCommitment(commitment, joiner.publicKeys(), nonce))
        assertFalse(Pairing.verifyCommitment(commitment, DeviceKeys.generate().publicKeys(), nonce))
    }

    @Test
    fun `양쪽 SAS 는 같은 키일 때만 같다`() {
        val inviter = DeviceKeys.generate().publicKeys()
        val joiner = DeviceKeys.generate().publicKeys()
        val nonce = Pairing.newNonce()
        val a = Pairing.sas("K7M2Q9XA", inviter, joiner, nonce)
        val b = Pairing.sas("K7M2Q9XA", inviter, joiner, nonce)
        assertEquals(a, b)
        assertEquals(6, a.length)
        assertTrue(a.all { it.isDigit() })
        // 중간에서 한쪽 키를 바꿔치기하면 SAS 가 달라진다.
        assertNotEquals(a, Pairing.sas("K7M2Q9XA", inviter, DeviceKeys.generate().publicKeys(), nonce))
    }

    @Test
    fun `Crockford 코드는 헷갈리는 글자를 맞춰 읽는다`() {
        assertEquals("01123", CrockfordCode.normalize("o-il 23"))
        assertNull(CrockfordCode.normalize("ABCU"))
        val code = CrockfordCode.random(8)
        assertEquals(8, code.length)
        assertEquals(code, CrockfordCode.normalize(code.lowercase()))
        assertEquals("ABCD-EFGH", CrockfordCode.format("ABCDEFGH"))
    }
}
