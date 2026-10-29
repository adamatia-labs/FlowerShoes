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
    fun `초대 토큰은 128비트 base64url 22자이고 형식이 아니면 거른다`() {
        val token = InviteToken.random()
        assertEquals(22, token.length)
        assertTrue(InviteToken.isValid(token))
        assertNotEquals(token, InviteToken.random())
        assertFalse(InviteToken.isValid("K7M2Q9XA"))
        assertFalse(InviteToken.isValid(token.dropLast(1) + "="))
        assertFalse(InviteToken.isValid(token.dropLast(1) + "/"))
    }

    @Test
    fun `두 기기는 공개키만 주고받고 같은 커플 키를 얻는다`() {
        val inviter = DeviceKeys.generate()
        val joiner = DeviceKeys.generate()
        val token = InviteToken.random()
        val a = Pairing.coupleKey(inviter, joiner.publicKeys(), token)
        val b = Pairing.coupleKey(joiner, inviter.publicKeys(), token)
        assertEquals(1, a.epoch)
        assertContentEquals(a.export(), b.export())
        // 가운데서 한쪽 키를 바꿔치기하면 키가 갈린다 — 서로의 Op 이 열리지 않는다.
        assertFalse(a.export().contentEquals(Pairing.coupleKey(joiner, DeviceKeys.generate().publicKeys(), token).export()))
        // 다른 초대 링크로는 다른 키.
        assertFalse(a.export().contentEquals(Pairing.coupleKey(inviter, joiner.publicKeys(), InviteToken.random()).export()))
    }

    @Test
    fun `공개키 한 쌍은 문자열 하나로 오가고 형식이 틀리면 null`() {
        val keys = DeviceKeys.generate().publicKeys()
        val back = Pairing.PublicKeys.decode(keys.encode())!!
        assertContentEquals(keys.signPub, back.signPub)
        assertContentEquals(keys.boxPub, back.boxPub)
        assertNull(Pairing.PublicKeys.decode("AAAA"))
        assertNull(Pairing.PublicKeys.decode("not base64!"))
    }
}
