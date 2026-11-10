package com.sadturtleman.flowershoes.core.drawing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TlvBoundsTest {
    @Test
    fun `필드 길이가 정수 범위를 넘어도 빈 필드로 해석하지 않는다`() {
        val input = ByteWriter().apply { varint(1); varint(1L shl 32) }.toByteArray()
        assertFailsWith<DrawingFormatException> { ByteReader(input).field() }
    }

    @Test
    fun `필드 태그가 정수 범위를 넘어도 다른 태그로 해석하지 않는다`() {
        val input = ByteWriter().apply { varint((1L shl 32) + 1); varint(0) }.toByteArray()
        assertFailsWith<DrawingFormatException> { ByteReader(input).field() }
    }

    @Test
    fun `요청 크기 덧셈이 넘쳐도 명시적으로 거부한다`() {
        val reader = ByteReader(byteArrayOf(0, 1))
        reader.byte()
        assertFailsWith<DrawingFormatException> { reader.bytes(Int.MAX_VALUE) }
    }

    @Test
    fun `64비트 범위를 넘는 varint를 잘린 값으로 읽지 않는다`() {
        val invalid = ByteArray(10) { 0x80.toByte() }.also { it[9] = 0x02 }
        assertFailsWith<DrawingFormatException> { ByteReader(invalid).varint() }
    }

    @Test
    fun `정상 최대 부호 없는 비트 패턴은 보존한다`() {
        val input = ByteWriter().apply { varint(-1L) }.toByteArray()
        assertEquals(-1L, ByteReader(input).varint())
    }

    @Test
    fun `미래 리더 버전이 32비트로 잘려 현재 버전이 되지 않는다`() {
        val input = ByteWriter().apply {
            bytes(byteArrayOf(0x4b, 0x4b, 0x44, 1))
            field(1) { field(2) { varint((1L shl 32) + 1) } }
        }.toByteArray()
        assertEquals(DrawingCodec.Result.Unreadable, DrawingCodec.decode(input))
    }

    @Test
    fun `점 개수가 32비트로 잘려 빈 선으로 변하지 않는다`() {
        val input = ByteWriter().apply {
            bytes(byteArrayOf(0x4b, 0x4b, 0x44, 1))
            field(1) { }
            field(1) { field(6) { field(1) { varint(1L shl 32) } } }
        }.toByteArray()
        assertEquals(DrawingCodec.Result.Unreadable, DrawingCodec.decode(input))
    }

    @Test
    fun `점 좌표 누적 값이 정수 범위를 넘으면 거부한다`() {
        val input = ByteWriter().apply {
            bytes(byteArrayOf(0x4b, 0x4b, 0x44, 1))
            field(1) { }
            field(1) { field(6) { field(1) { varint(1); zigzag(Int.MAX_VALUE.toLong() + 1) } } }
        }.toByteArray()
        assertEquals(DrawingCodec.Result.Unreadable, DrawingCodec.decode(input))
    }

    @Test
    fun `부호 있는 경계 좌표는 손실 없이 왕복한다`() {
        val points = PointBuffer(intArrayOf(Int.MIN_VALUE, Int.MAX_VALUE), intArrayOf(0, 1), intArrayOf(0, 1))
        val document = DrawingDocument(canvas = CanvasSpec(WidgetShape.SQUARE), elements = listOf(Stroke("line", 0, Tool.PEN, 0xff000000, BrushSize.M, points)), totalDurationMs = 1)
        assertEquals(document, (DrawingCodec.decode(DrawingCodec.encode(document)) as DrawingCodec.Result.Ok).document)
    }

    @Test
    fun `두 번째 좌표의 누적 결과가 정수 범위 아래로 내려가면 거부한다`() {
        val input = ByteWriter().apply {
            bytes(byteArrayOf(0x4b, 0x4b, 0x44, 1))
            field(1) { }
            field(1) { field(6) { field(1) { varint(2); zigzag(Int.MIN_VALUE.toLong()); zigzag(-1) } } }
        }.toByteArray()
        assertEquals(DrawingCodec.Result.Unreadable, DrawingCodec.decode(input))
    }

    @Test
    fun `사진 자르기 좌표가 정수 범위를 넘으면 거부한다`() {
        val input = ByteWriter().apply {
            bytes(byteArrayOf(0x4b, 0x4b, 0x44, 1))
            field(1) { }
            field(4) { field(5) { zigzag(Int.MAX_VALUE.toLong() + 1); zigzag(0); zigzag(0); zigzag(0) } }
        }.toByteArray()
        assertEquals(DrawingCodec.Result.Unreadable, DrawingCodec.decode(input))
    }

    @Test
    fun `잘린 varint와 열 번째 바이트의 연속 표시는 거부한다`() {
        assertFailsWith<DrawingFormatException> { ByteReader(byteArrayOf(0x80.toByte())).varint() }
        assertFailsWith<DrawingFormatException> { ByteReader(ByteArray(11) { 0x80.toByte() }).varint() }
    }
}
