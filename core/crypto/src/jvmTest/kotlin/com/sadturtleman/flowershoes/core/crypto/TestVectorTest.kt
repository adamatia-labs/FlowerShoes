package com.sadturtleman.flowershoes.core.crypto

import java.io.File
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 공유 테스트 벡터(설계 6-8). 파일이 없으면 지금 키로 만들어 두고, 있으면 그 값으로 검증한다.
 * 같은 파일을 서버(firebase/functions) · Swift(NSE · 위젯) 테스트가 읽는다.
 */
class TestVectorTest {

    private val file = File("../../spec/test-vectors/op-v1.json")
    private val b64 = Base64.getEncoder()
    private val unb64 = Base64.getDecoder()

    private fun field(json: String, name: String): String =
        Regex("\"$name\"\\s*:\\s*\"([^\"]*)\"").find(json)!!.groupValues[1]

    @Test
    fun `Op 봉투 테스트 벡터`() {
        Sodium.init()
        if (!file.exists()) {
            val keys = DeviceKeys.generate()
            val coupleKey = CoupleKey.generate(epoch = 2)
            val header = OpHeader("op-vector-1", "dev-a:widget", 7, 2)
            val env = OpEnvelope.seal(header, "{\"t\":\"missyou\"}".encodeToByteArray(), coupleKey, keys)
            file.parentFile.mkdirs()
            file.writeText(
                """
                {
                  "description": "Op 봉투 v1. header = OpHeader.toBytes(), ct = nonce‖ciphertext‖tag, sig = Ed25519(BLAKE2b-512(P(ctx)‖P(header)‖P(ct)))",
                  "signPub": "${b64.encodeToString(keys.signPub)}",
                  "coupleKey": "${b64.encodeToString(coupleKey.export())}",
                  "epoch": 2,
                  "header": "${b64.encodeToString(header.toBytes())}",
                  "ct": "${b64.encodeToString(env.ciphertext)}",
                  "sig": "${b64.encodeToString(env.signature)}",
                  "plaintext": "{\"t\":\"missyou\"}"
                }
                """.trimIndent() + "\n",
            )
        }
        val json = file.readText()
        val header = OpHeader("op-vector-1", "dev-a:widget", 7, 2)
        assertEquals(field(json, "header"), b64.encodeToString(header.toBytes()))
        val env = OpEnvelope(header, unb64.decode(field(json, "ct")), unb64.decode(field(json, "sig")))
        val key = CoupleKey(2, unb64.decode(field(json, "coupleKey")))
        val plain = OpEnvelope.open(env, unb64.decode(field(json, "signPub"))) { key }
        assertTrue(plain.decodeToString().contains("missyou"))
    }
}
