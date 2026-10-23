package com.sadturtleman.flowershoes.core.drawing

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DrawingEngineTest {

    private fun line(vararg pts: Int, stepMs: Int = 10): PointBuffer {
        val n = pts.size / 2
        return PointBuffer(
            xs = IntArray(n) { pts[it * 2] },
            ys = IntArray(n) { pts[it * 2 + 1] },
            ts = IntArray(n) { it * stepMs },
        )
    }

    private fun stroke(id: String, start: Long, points: PointBuffer) =
        Stroke(id, start, Tool.PEN, 0xFF222222, BrushSize.M, points)

    private val sample = DrawingDocument(
        canvas = CanvasSpec(WidgetShape.WIDE, backgroundArgb = 0xFFFFF8F0),
        elements = listOf(
            PhotoBackground("p", 0, "att-1", 0.3f, Rect(0, 0, 1000, 467)),
            TemplateGuide("t", 0, "heart"),
            stroke("s1", 100, line(10, 10, 20, 12, 35, 15)).copy(replaySegments = intArrayOf(0, 1)),
            EraserStroke("e", 900, BrushSize.L, line(20, 12)),
            StickerElement("k", 1200, "fluent:rose", Transform(500f, 200f, 1.2f, -15f)),
        ),
        totalDurationMs = 1500,
    )

    @Test
    fun `인코딩한 문서를 그대로 되읽는다`() {
        val decoded = assertIs<DrawingCodec.Result.Ok>(DrawingCodec.decode(DrawingCodec.encode(sample)))
        assertEquals(sample, decoded.document)
        assertEquals(0, decoded.skippedUnknown)
    }

    @Test
    fun `모르는 요소는 건너뛰고 아는 요소만 읽는다`() {
        val known = DrawingCodec.encode(sample)
        // 미래 버전이 추가한 typeTag 99 요소를 끝에 붙인다.
        val future = ByteWriter().apply {
            bytes(known)
            field(99) { varint(123); bytes("new".encodeToByteArray()) }
        }.toByteArray()
        val decoded = assertIs<DrawingCodec.Result.Ok>(DrawingCodec.decode(future))
        assertEquals(sample.elements, decoded.document.elements)
        assertEquals(1, decoded.skippedUnknown)
    }

    @Test
    fun `필요한 읽기 버전이 더 높으면 리플레이하지 않는다`() {
        val bytes = DrawingCodec.encode(sample.copy(version = 3, minReaderVersion = 2))
        assertEquals(DrawingCodec.Result.TooNew(2), DrawingCodec.decode(bytes))
    }

    @Test
    fun `그림 문서가 아니면 거부한다`() {
        assertFailsWith<DrawingFormatException> { DrawingCodec.decode(byteArrayOf(1, 2, 3, 4, 5)) }
        val truncated = DrawingCodec.encode(sample).let { it.copyOf(it.size - 3) }
        assertFailsWith<DrawingFormatException> { DrawingCodec.decode(truncated) }
    }

    @Test
    fun `직선 위의 점은 간소화로 사라진다`() {
        val simplified = StrokeProcessing.simplify(line(0, 0, 10, 0, 20, 0, 30, 0, 40, 10), tolerance = 0.5)
        assertContentEquals(intArrayOf(0, 30, 40), simplified.xs)
        assertContentEquals(intArrayOf(0, 30, 40), simplified.ts)
    }

    @Test
    fun `뒤에 그은 지우개에 덮인 점만 리플레이에서 뺀다`() {
        val s = stroke("s", 0, line(0, 0, 100, 0, 200, 0, 300, 0, 400, 0))
        val eraser = EraserStroke("e", 10, BrushSize.L, line(200, 5))
        assertContentEquals(intArrayOf(0, 1, 3, 4), StrokeProcessing.replaySegments(s, listOf(eraser)))
        assertNull(StrokeProcessing.replaySegments(s, emptyList()))
    }

    @Test
    fun `앞에 그은 지우개는 뒤의 획에 영향이 없다`() {
        val doc = DrawingDocument(
            canvas = CanvasSpec(WidgetShape.SQUARE),
            elements = listOf(
                EraserStroke("e", 0, BrushSize.L, line(200, 0)),
                stroke("s", 50, line(0, 0, 100, 0, 200, 0)),
            ),
            totalDurationMs = 100,
        )
        val prepared = StrokeProcessing.prepareForSave(doc)
        assertNull((prepared.elements[1] as Stroke).replaySegments)
    }

    @Test
    fun `빈 시간은 300ms 로 자른다`() {
        val doc = DrawingDocument(
            canvas = CanvasSpec(WidgetShape.SQUARE),
            elements = listOf(
                stroke("a", 0, line(0, 0, 10, 10, stepMs = 100)), // 0~100
                stroke("b", 5_000, line(0, 0, 10, 10, stepMs = 100)), // 4.9초 쉼 → 300ms
            ),
            totalDurationMs = 5_100,
        )
        val timeline = ReplayTimeline.of(doc)
        assertEquals(100 + 300 + 100 + ReplayTimeline.FINISH_MS, timeline.durationMs)
    }

    @Test
    fun `길면 8초 안에 들어오도록 배속한다`() {
        val long = DrawingDocument(
            canvas = CanvasSpec(WidgetShape.SQUARE),
            elements = (0 until 40).map { stroke("s$it", it * 400L, line(0, 0, 50, 50, 100, 0, stepMs = 200)) },
            totalDurationMs = 16_400,
        )
        val timeline = ReplayTimeline.of(long)
        assertEquals(ReplayTimeline.MAX_TOTAL_MS, timeline.durationMs)
        assertTrue(timeline.frame(timeline.durationMs).isComplete)
    }

    @Test
    fun `프레임은 시각에 맞춰 점을 하나씩 늘리고 끝에 PNG 로 덮는다`() {
        val timeline = ReplayTimeline.of(sample)
        val early = timeline.frame(5)
        assertEquals(0, early.strokes.single().drawnThrough)
        // 첫 점(10,10 · 0ms)과 둘째 점(20,12 · 10ms)의 한가운데
        assertEquals(15 to 11, early.strokes.single().penTip)
        assertTrue(early.stickers.isEmpty())
        assertEquals(0f, early.finishAlpha)

        // 획(100~120ms) 뒤 스티커(1200ms)까지 1080ms 쉼 → 300ms 로 잘려 320ms 에 나온다.
        val mid = timeline.frame(25)
        assertTrue(mid.strokes.single().finished)
        assertTrue(mid.stickers.isEmpty())
        assertEquals(1, timeline.frame(320).stickers.size)
        assertEquals(20 + 300 + ReplayTimeline.POP_MS.toLong() + ReplayTimeline.FINISH_MS, timeline.durationMs)

        val end = timeline.frame(timeline.durationMs - 1)
        assertTrue(end.finishAlpha > 0.9f)
        assertTrue(timeline.completeFrame().isComplete)
    }

    @Test
    fun `간소화로 점이 둘만 남아도 펜은 시간에 맞춰 나아간다`() {
        val doc = DrawingDocument(
            canvas = CanvasSpec(WidgetShape.SQUARE),
            elements = listOf(stroke("s", 0, PointBuffer(intArrayOf(0, 1000), intArrayOf(0, 0), intArrayOf(0, 600)))),
            totalDurationMs = 600,
        )
        val half = ReplayTimeline.of(doc).frame(300).strokes.single()
        assertEquals(0, half.drawnThrough)
        assertEquals(0.5f, half.toward)
        assertEquals(500 to 0, half.penTip)
    }
}
