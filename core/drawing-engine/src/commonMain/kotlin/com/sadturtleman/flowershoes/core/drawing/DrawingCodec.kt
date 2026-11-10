package com.sadturtleman.flowershoes.core.drawing

import com.sadturtleman.flowershoes.core.compression.Zstd

/**
 * 그림 문서 인코딩.
 *
 * 형식: `MAGIC` 다음에 헤더 필드 하나, 이어서 요소마다 `[typeTag][length][본문]`.
 * 요소 본문 안도 `[fieldTag][length][값]` 이라 필드를 늘려도 옛 앱이 읽을 수 있다.
 *
 * 읽는 쪽은 모르는 typeTag 를 건너뛴다 — 새 요소가 생겨도 옛 앱은 아는 요소만 재생하고
 * 마지막에 완성 PNG 로 마무리하므로 결과는 똑같이 보인다. kotlinx ProtoBuf 의 sealed 다형성을 쓰지 않는 이유다.
 *
 * 좌표는 앞 점과의 차이를 zigzag varint 로 담는다. 저장 · 전송할 때는 이 바이트를 zstd 프레임으로 감싼다([encodeForStorage]).
 */
object DrawingCodec {

    const val MAX_DOCUMENT_BYTES = Zstd.MAX_DECOMPRESSED_BYTES
    const val MAX_ELEMENTS = 10_000
    private const val MAX_TOTAL_INT_VALUES = 3_000_000

    private val MAGIC = byteArrayOf(0x4B, 0x4B, 0x44, 0x01) // "KKD\1"

    private const val HEADER = 1

    // 헤더 필드
    private const val H_VERSION = 1
    private const val H_MIN_READER = 2
    private const val H_SHAPE = 3
    private const val H_BACKGROUND = 4
    private const val H_TOTAL_DURATION = 5

    // 요소 typeTag
    internal const val T_STROKE = 1
    internal const val T_ERASER = 2
    internal const val T_STICKER = 3
    internal const val T_PHOTO = 4
    internal const val T_TEMPLATE = 5

    // 요소 공통 필드
    private const val F_ID = 1
    private const val F_START = 2

    // 요소별 필드(3번부터)
    private const val F_TOOL = 3
    private const val F_COLOR = 4
    private const val F_BRUSH = 5
    private const val F_POINTS = 6
    private const val F_SEGMENTS = 7
    private const val F_STICKER_ID = 3
    private const val F_TRANSFORM = 4
    private const val F_ATTACHMENT = 3
    private const val F_DIM = 4
    private const val F_CROP = 5
    private const val F_TEMPLATE_ID = 3

    // PointBuffer 필드
    private const val P_XS = 1
    private const val P_YS = 2
    private const val P_TS = 3
    private const val P_PRESSURE = 4

    fun encode(doc: DrawingDocument): ByteArray {
        require(doc.elements.size <= MAX_ELEMENTS) { "그림 요소가 너무 많아요" }
        val budget = DecodeBudget()
        doc.elements.forEach { element ->
            val points = when (element) { is Stroke -> element.points; is EraserStroke -> element.points; else -> null }
            points?.let { repeat(3) { _ -> budget.reserve(it.size) } }
            (element as? Stroke)?.replaySegments?.let { budget.reserve(it.size) }
        }
        val w = ByteWriter()
        w.bytes(MAGIC)
        w.field(HEADER) {
            field(H_VERSION) { varint(doc.version.toLong()) }
            field(H_MIN_READER) { varint(doc.minReaderVersion.toLong()) }
            field(H_SHAPE) { varint(doc.canvas.shape.ordinal.toLong()) }
            field(H_BACKGROUND) { varint(doc.canvas.backgroundArgb and 0xFFFFFFFFL) }
            field(H_TOTAL_DURATION) { varint(doc.totalDurationMs) }
        }
        doc.elements.forEach { w.element(it) }
        return w.toByteArray()
    }

    /** 저장 · 전송용: zstd 로 줄인다(작아질 때만). [decode] 는 두 형식을 모두 읽는다. */
    fun encodeForStorage(doc: DrawingDocument): ByteArray = Zstd.pack(encode(doc))

    sealed interface Result {
        data class Ok(val document: DrawingDocument, val skippedUnknown: Int) : Result

        /** 이 앱보다 새 문서라 리플레이할 수 없다. 완성 PNG 만 보여 준다. */
        data class TooNew(val minReaderVersion: Int) : Result
        /** 손상·크기 제한 초과. 원본은 건드리지 않고 호출부가 완성 PNG 또는 실패 안내를 보여 준다. */
        data object Unreadable : Result
    }

    fun decode(stored: ByteArray, readerVersion: Int = DrawingDocument.CURRENT_VERSION): Result = try {
        decodeDocument(Zstd.unpack(stored, MAX_DOCUMENT_BYTES), readerVersion)
    } catch (_: Exception) { Result.Unreadable }

    private fun decodeDocument(bytes: ByteArray, readerVersion: Int): Result {
        if (bytes.size < MAGIC.size || !bytes.copyOf(MAGIC.size).contentEquals(MAGIC)) {
            throw DrawingFormatException("그림 문서가 아니다")
        }
        val r = ByteReader(bytes, MAGIC.size)
        val (tag, header) = r.field()
        if (tag != HEADER) throw DrawingFormatException("헤더가 없다")

        var version = 1
        var minReader = 1
        var shape = WidgetShape.SQUARE
        var background = 0xFFFFFFFFL
        var total = 0L
        header.fields { t, v ->
            when (t) {
                H_VERSION -> version = v.nonNegativeInt()
                H_MIN_READER -> minReader = v.nonNegativeInt()
                H_SHAPE -> shape = WidgetShape.entries.getOrElse(v.nonNegativeInt()) { WidgetShape.SQUARE }
                H_BACKGROUND -> background = v.varint()
                H_TOTAL_DURATION -> total = v.varint()
            }
        }
        if (minReader > readerVersion) return Result.TooNew(minReader)

        val elements = mutableListOf<Element>()
        val budget = DecodeBudget()
        var skipped = 0
        while (r.hasMore) {
            if (elements.size + skipped >= MAX_ELEMENTS) throw DrawingFormatException("그림 요소가 너무 많다")
            val (type, body) = r.field()
            val element = readElement(type, body, budget)
            if (element == null) skipped++ else elements += element
        }
        return Result.Ok(
            DrawingDocument(version, minReader, CanvasSpec(shape, background), elements, total),
            skipped,
        )
    }

    private fun ByteWriter.element(e: Element) {
        val type = when (e) {
            is Stroke -> T_STROKE
            is EraserStroke -> T_ERASER
            is StickerElement -> T_STICKER
            is PhotoBackground -> T_PHOTO
            is TemplateGuide -> T_TEMPLATE
        }
        field(type) {
            field(F_ID) { bytes(e.id.encodeToByteArray()) }
            field(F_START) { varint(e.startMs) }
            when (e) {
                is Stroke -> {
                    field(F_TOOL) { varint(e.tool.ordinal.toLong()) }
                    field(F_COLOR) { varint(e.color and 0xFFFFFFFFL) }
                    field(F_BRUSH) { varint(e.brush.ordinal.toLong()) }
                    field(F_POINTS) { points(e.points) }
                    e.replaySegments?.let { seg -> field(F_SEGMENTS) { deltas(seg) } }
                }
                is EraserStroke -> {
                    field(F_BRUSH) { varint(e.brush.ordinal.toLong()) }
                    field(F_POINTS) { points(e.points) }
                }
                is StickerElement -> {
                    field(F_STICKER_ID) { bytes(e.stickerId.encodeToByteArray()) }
                    field(F_TRANSFORM) {
                        floatBits(e.transform.x); floatBits(e.transform.y)
                        floatBits(e.transform.scale); floatBits(e.transform.rotationDeg)
                    }
                }
                is PhotoBackground -> {
                    field(F_ATTACHMENT) { bytes(e.attachmentId.encodeToByteArray()) }
                    field(F_DIM) { floatBits(e.dim) }
                    field(F_CROP) {
                        zigzag(e.crop.left.toLong()); zigzag(e.crop.top.toLong())
                        zigzag(e.crop.right.toLong()); zigzag(e.crop.bottom.toLong())
                    }
                }
                is TemplateGuide -> field(F_TEMPLATE_ID) { bytes(e.templateId.encodeToByteArray()) }
            }
        }
    }

    private fun readElement(type: Int, body: ByteReader, budget: DecodeBudget): Element? {
        if (type !in T_STROKE..T_TEMPLATE) return null
        var id = ""
        var start = 0L
        var tool = Tool.PEN
        var color = 0xFF000000L
        var brush = BrushSize.M
        var points = PointBuffer(IntArray(0), IntArray(0), IntArray(0))
        var segments: IntArray? = null
        var text = ""
        var transform = Transform(0f, 0f, 1f, 0f)
        var dim = 0f
        var crop = Rect(0, 0, 0, 0)
        body.fields { tag, v ->
            when (tag) {
                F_ID -> id = v.rest().decodeToString()
                F_START -> start = v.varint()
                else -> when (type) {
                    T_STROKE, T_ERASER -> when (tag) {
                        F_TOOL -> tool = Tool.entries.getOrElse(v.nonNegativeInt()) { Tool.PEN }
                        F_COLOR -> color = v.varint()
                        F_BRUSH -> brush = BrushSize.entries.getOrElse(v.nonNegativeInt()) { BrushSize.M }
                        F_POINTS -> points = v.points(budget)
                        F_SEGMENTS -> segments = v.deltas(budget)
                    }
                    T_STICKER -> when (tag) {
                        F_STICKER_ID -> text = v.rest().decodeToString()
                        F_TRANSFORM -> transform = Transform(v.floatBits(), v.floatBits(), v.floatBits(), v.floatBits())
                    }
                    T_PHOTO -> when (tag) {
                        F_ATTACHMENT -> text = v.rest().decodeToString()
                        F_DIM -> dim = v.floatBits().coerceIn(0f, PhotoBackground.MAX_DIM)
                        F_CROP -> crop = Rect(v.signedInt(), v.signedInt(), v.signedInt(), v.signedInt())
                    }
                    T_TEMPLATE -> if (tag == F_TEMPLATE_ID) text = v.rest().decodeToString()
                }
            }
        }
        return when (type) {
            T_STROKE -> Stroke(id, start, tool, color, brush, points, segments)
            T_ERASER -> EraserStroke(id, start, brush, points)
            T_STICKER -> StickerElement(id, start, text, transform)
            T_PHOTO -> PhotoBackground(id, start, text, dim, crop)
            else -> TemplateGuide(id, start, text)
        }
    }

    private fun ByteWriter.points(p: PointBuffer) {
        field(P_XS) { deltas(p.xs) }
        field(P_YS) { deltas(p.ys) }
        field(P_TS) { deltas(p.ts) }
        p.pressure?.let { field(P_PRESSURE) { bytes(it) } }
    }

    private fun ByteReader.points(budget: DecodeBudget): PointBuffer {
        var xs = IntArray(0)
        var ys = IntArray(0)
        var ts = IntArray(0)
        var pressure: ByteArray? = null
        fields { tag, v ->
            when (tag) {
                P_XS -> xs = v.deltas(budget)
                P_YS -> ys = v.deltas(budget)
                P_TS -> ts = v.deltas(budget)
                P_PRESSURE -> pressure = v.rest()
            }
        }
        val n = minOf(xs.size, ys.size, ts.size)
        return PointBuffer(xs.copyOf(n), ys.copyOf(n), ts.copyOf(n), pressure?.takeIf { it.size == n })
    }

    /** 개수 다음에 앞 값과의 차이를 zigzag 로. 손으로 그린 선은 이웃 점이 가까워 대부분 1바이트가 된다. */
    private fun ByteWriter.deltas(values: IntArray) {
        varint(values.size.toLong())
        var prev = 0L
        values.forEach {
            zigzag(it - prev)
            prev = it.toLong()
        }
    }

    private fun ByteReader.deltas(budget: DecodeBudget): IntArray {
        val n = nonNegativeInt()
        budget.reserve(n)
        if (n > remaining) throw DrawingFormatException("점 데이터가 중간에 끝났다")
        var prev = 0L
        return IntArray(n) {
            val delta = zigzag()
            if (delta !in (Int.MIN_VALUE.toLong() - prev)..(Int.MAX_VALUE.toLong() - prev)) throw DrawingFormatException("점 좌표가 범위를 넘는다")
            prev += delta
            prev.toInt()
        }
    }

    private fun ByteWriter.floatBits(f: Float) {
        val bits = f.toRawBits()
        for (i in 0 until 4) byte((bits ushr (8 * i)) and 0xFF)
    }

    private fun ByteReader.floatBits(): Float {
        var bits = 0
        for (i in 0 until 4) bits = bits or (byte() shl (8 * i))
        return Float.fromBits(bits)
    }

    private fun ByteReader.rest(): ByteArray {
        val out = ByteWriter()
        while (hasMore) out.byte(byte())
        return out.toByteArray()
    }

    private inline fun ByteReader.fields(block: (Int, ByteReader) -> Unit) {
        while (hasMore) {
            val (tag, value) = field()
            block(tag, value)
        }
    }

    private const val MAX_POINTS = 100_000

    private class DecodeBudget {
        private var left = MAX_TOTAL_INT_VALUES
        fun reserve(count: Int) {
            if (count !in 0..MAX_POINTS || count > left) throw DrawingFormatException("그림의 점 데이터가 너무 많다")
            left -= count
        }
    }
}
