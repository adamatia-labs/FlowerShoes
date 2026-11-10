package com.sadturtleman.flowershoes.core.drawing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class DrawingLimitsTest {
    private fun document(elements: List<Element>) = DrawingDocument(canvas = CanvasSpec(WidgetShape.SQUARE), elements = elements, totalDurationMs = 0)

    @Test
    fun `요소 수 경계에서 저장한 문서는 읽히고 초과 문서는 저장하지 않는다`() {
        val element = TemplateGuide("t", 0, "heart")
        val doc = document(List(DrawingCodec.MAX_ELEMENTS) { element })
        assertEquals(doc, assertIs<DrawingCodec.Result.Ok>(DrawingCodec.decode(DrawingCodec.encodeForStorage(doc))).document)
        assertFailsWith<IllegalArgumentException> { DrawingCodec.encodeForStorage(doc.copy(elements = doc.elements + element)) }
        val extra = ByteWriter().apply { field(99) {} }.toByteArray()
        assertEquals(DrawingCodec.Result.Unreadable, DrawingCodec.decode(DrawingCodec.encode(doc) + extra))
    }

    @Test
    fun `여러 획의 점 배열 합계도 제한하며 저장과 읽기 경계는 같다`() {
        val points = PointBuffer(IntArray(100_000), IntArray(100_000), IntArray(100_000))
        val line = Stroke("line", 0, Tool.PEN, 0xff000000, BrushSize.M, points)
        val doc = document(List(10) { line })
        val raw = DrawingCodec.encode(doc)
        assertEquals(doc, assertIs<DrawingCodec.Result.Ok>(DrawingCodec.decode(raw)).document)
        assertFailsWith<DrawingFormatException> { DrawingCodec.encodeForStorage(doc.copy(elements = doc.elements + line)) }
        val single = DrawingCodec.encode(document(listOf(line)))
        val headerLength = DrawingCodec.encode(document(emptyList())).size
        assertEquals(DrawingCodec.Result.Unreadable, DrawingCodec.decode(raw + single.copyOfRange(headerLength, single.size)))
    }

    @Test
    fun `길이를 위조한 점 배열은 데이터 없이 메모리를 할당하지 않는다`() {
        val raw = ByteWriter().apply {
            bytes(DrawingCodec.encode(document(emptyList())))
            field(1) { field(6) { field(1) { varint(100_000) } } }
        }.toByteArray()
        assertEquals(DrawingCodec.Result.Unreadable, DrawingCodec.decode(raw))
    }

    @Test
    fun `인코더가 읽기 제한보다 큰 문서를 쓰지 않는다`() {
        val doc = document(listOf(TemplateGuide("t", 0, "x".repeat(DrawingCodec.MAX_DOCUMENT_BYTES))))
        assertFailsWith<DrawingFormatException> { DrawingCodec.encodeForStorage(doc) }
    }
}
