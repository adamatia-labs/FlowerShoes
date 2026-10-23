package com.sadturtleman.flowershoes.core.drawing

import com.sadturtleman.flowershoes.core.compression.Zstd
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

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
    fun `저장 형식은 zstd 프레임이고 decode 는 두 형식을 모두 읽는다`() {
        val raw = DrawingCodec.encode(doc)
        val stored = DrawingCodec.encodeForStorage(doc)
        assertTrue(Zstd.isFrame(stored) && stored.size < raw.size, "raw=${raw.size} stored=${stored.size}")
        assertEquals(doc, assertIs<DrawingCodec.Result.Ok>(DrawingCodec.decode(stored)).document)
        assertEquals(doc, assertIs<DrawingCodec.Result.Ok>(DrawingCodec.decode(raw)).document)
    }
}
