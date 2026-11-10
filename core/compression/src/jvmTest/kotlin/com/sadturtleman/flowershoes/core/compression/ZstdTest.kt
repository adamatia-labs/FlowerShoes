package com.sadturtleman.flowershoes.core.compression

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull

class ZstdTest {
    private val text = "보고싶어 ".repeat(500).encodeToByteArray()

    @Test
    fun `출력 크기 경계와 크기 미상 프레임을 검사한다`() {
        val frame = Zstd.codec!!.compress(text, 3)
        assertContentEquals(text, Zstd.codec!!.decompress(frame, text.size))
        assertNull(Zstd.codec!!.decompress(frame, text.size - 1))
        assertNull(Zstd.codec!!.decompress(frame.copyOf(frame.size - 1)))
        assertNull(Zstd.codec!!.decompress(Zstd.MAGIC + byteArrayOf(0, 0, 1, 0, 0)))
        assertNull(Zstd.codec!!.decompress(frame, -1))
    }

    @Test
    fun `잘못된 프레임은 네이티브 오류를 밖으로 내보내지 않는다`() {
        assertNull(Zstd.codec!!.decompress(Zstd.MAGIC + byteArrayOf(0x7f, 1, 2)))
    }

    @Test
    fun `해제 크기가 16MiB를 넘으면 네이티브 버퍼를 만들지 않는다`() {
        val frame = Zstd.codec!!.compress(ByteArray(16 * 1024 * 1024 + 1), 3)
        assertNull(Zstd.codec!!.decompress(frame))
    }

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
