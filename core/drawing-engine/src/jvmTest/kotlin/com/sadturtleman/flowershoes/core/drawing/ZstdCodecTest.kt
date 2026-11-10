package com.sadturtleman.flowershoes.core.drawing

import com.sadturtleman.flowershoes.core.compression.Zstd
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertContentEquals

/** jvm 에만 둔다 — iOS 테스트에는 Swift zstd 가 꽂히지 않는다. */
class ZstdCodecTest {
    private val doc = DrawingDocument(
        canvas = CanvasSpec(WidgetShape.SQUARE),
        totalDurationMs = 4_000,
        elements = List(40) { i ->
            Stroke(
                id = "s$i", startMs = i * 100L, tool = Tool.entries.first(), color = 0xFF222222, brush = BrushSize.entries.first(),
                points = PointBuffer(IntArray(50) { it * 2 }, IntArray(50) { it * 2 + i }, IntArray(50) { it * 16 }),
            )
        },
    )

    @Test
    fun `손상된 압축 프레임은 예외 대신 읽을 수 없는 결과가 된다`() {
        assertFalse(DrawingCodec.decode(Zstd.MAGIC + byteArrayOf(0x7f, 1, 2)) is DrawingCodec.Result.Ok)
    }

    @Test
    fun `잘린 압축 프레임도 호출부로 예외를 내보내지 않는다`() {
        val stored = DrawingCodec.encodeForStorage(doc)
        assertFalse(DrawingCodec.decode(stored.copyOf(stored.size - 1)) is DrawingCodec.Result.Ok)
    }

    @Test
    fun `큰 기존 압축 그림은 읽기를 거부하고 원본 바이트를 보존한다`() {
        val base = DrawingCodec.encode(doc)
        val extra = 16 * 1024 * 1024
        val field = ByteWriter().apply { varint(99); varint(extra.toLong()) }.toByteArray()
        val stored = Zstd.pack(base + field + ByteArray(extra))
        val original = stored.copyOf()
        assertTrue(Zstd.isFrame(stored))
        assertFalse(DrawingCodec.decode(stored) is DrawingCodec.Result.Ok)
        assertContentEquals(original, stored)
    }

    @Test
    fun `저장 형식은 zstd 프레임이고 decode 는 두 형식을 모두 읽는다`() {
        val raw = DrawingCodec.encode(doc)
        val stored = DrawingCodec.encodeForStorage(doc)
        assertTrue(Zstd.isFrame(stored) && stored.size < raw.size, "raw=${raw.size} stored=${stored.size}")
        assertEquals(doc, assertIs<DrawingCodec.Result.Ok>(DrawingCodec.decode(stored)).document)
        assertEquals(doc, assertIs<DrawingCodec.Result.Ok>(DrawingCodec.decode(raw)).document)
    }
}
