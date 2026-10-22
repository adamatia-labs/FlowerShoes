package com.sadturtleman.flowershoes.core.compression

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ZstdTest {
    private val text = "보고싶어 ".repeat(500).encodeToByteArray()

    @Test
    fun `반복이 많은 데이터는 작아지고 그대로 돌아온다`() {
        val z = Zstd.pack(text)
        assertTrue(Zstd.isFrame(z) && z.size < text.size / 10)
        assertContentEquals(text, Zstd.unpack(z))
    }

    @Test
    fun `작아지지 않으면 그대로 두고, 풀 때도 그대로`() {
        val noise = Random(1).nextBytes(2000)
        assertContentEquals(noise, Zstd.pack(noise))
        assertContentEquals(noise, Zstd.unpack(noise))
    }

    @Test
    fun `표시 바이트 형식은 어떤 바이트로 시작해도 구별된다`() {
        val looksLikeFrame = Zstd.MAGIC + Random(2).nextBytes(100)
        assertContentEquals(looksLikeFrame, Zstd.unpackFlagged(Zstd.packFlagged(looksLikeFrame)))
        val packed = Zstd.packFlagged(text)
        assertEquals(1, packed[0].toInt())
        assertContentEquals(text, Zstd.unpackFlagged(packed))
    }
}
