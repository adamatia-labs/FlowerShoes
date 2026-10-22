package com.sadturtleman.flowershoes.core.drawing

/** 위젯 모양 캔버스. 논리 좌표계는 가로 1000 기준이다. */
enum class WidgetShape(val width: Int, val height: Int, val pngWidth: Int, val pngHeight: Int) {
    /** 2x2 위젯. */
    SQUARE(1000, 1000, 680, 680),

    /** 4x2 위젯. */
    WIDE(1000, 467, 1456, 680),
}

enum class Tool { PEN, HIGHLIGHTER }

/** 붓 굵기(논리 단위). */
enum class BrushSize(val width: Int) { S(6), M(12), L(20) }

/**
 * 캔버스. [backgroundArgb] 는 고정 ARGB 다 — 테마 토큰을 쓰면 받는 폰이 다크 모드일 때 배경이 바뀌어
 * 검은 펜 선이 안 보이게 된다.
 */
data class CanvasSpec(
    val shape: WidgetShape,
    val backgroundArgb: Long = 0xFFFFFFFF,
) {
    val width: Int get() = shape.width
    val height: Int get() = shape.height
}

/**
 * 점 묶음. 좌표는 손떨림 보정 "후" 값이다 — 보정 알고리즘이 기기마다 달라도 받는 쪽은 같은 모양을 그린다.
 * [ts] 는 획 시작 기준 ms 다.
 */
class PointBuffer(
    val xs: IntArray,
    val ys: IntArray,
    val ts: IntArray,
    val pressure: ByteArray? = null,
) {
    init {
        require(xs.size == ys.size && ys.size == ts.size) { "좌표 · 시각 개수가 같아야 한다" }
        require(pressure == null || pressure.size == xs.size)
    }

    val size: Int get() = xs.size

    /** 획이 그려지는 데 걸린 시간. */
    val durationMs: Int get() = if (ts.isEmpty()) 0 else ts.last()

    override fun equals(other: Any?): Boolean = other is PointBuffer &&
        xs.contentEquals(other.xs) && ys.contentEquals(other.ys) && ts.contentEquals(other.ts) &&
        (pressure?.contentEquals(other.pressure) ?: (other.pressure == null))

    override fun hashCode(): Int = xs.contentHashCode() * 31 + ys.contentHashCode()
}

data class Transform(val x: Float, val y: Float, val scale: Float, val rotationDeg: Float)

data class Rect(val left: Int, val top: Int, val right: Int, val bottom: Int)

/** 캔버스 위 요소 하나. 목록 순서 = 추가된 순서 = 재생 순서. [startMs] 는 편집 시작 기준 오프셋이다. */
sealed interface Element {
    val id: String
    val startMs: Long
}

/**
 * 펜 · 형광펜 획.
 *
 * [replaySegments] 는 리플레이 때 그릴 점 인덱스 구간 [시작, 끝, 시작, 끝, …](끝 포함)이다.
 * 뒤에 그은 지우개로 지워진 부분을 뺀 것이고, null 이면 전체를 그린다.
 */
data class Stroke(
    override val id: String,
    override val startMs: Long,
    val tool: Tool,
    val color: Long,
    val brush: BrushSize,
    val points: PointBuffer,
    val replaySegments: IntArray? = null,
) : Element {
    override fun equals(other: Any?): Boolean = other is Stroke && other.id == id && other.startMs == startMs &&
        other.tool == tool && other.color == color && other.brush == brush && other.points == points &&
        (replaySegments?.contentEquals(other.replaySegments) ?: (other.replaySegments == null))

    override fun hashCode(): Int = id.hashCode()
}

/** 지우개 획. 저장은 하지만 기본 리플레이에서는 재생하지 않는다. */
data class EraserStroke(
    override val id: String,
    override val startMs: Long,
    val brush: BrushSize,
    val points: PointBuffer,
) : Element

data class StickerElement(
    override val id: String,
    override val startMs: Long,
    val stickerId: String,
    val transform: Transform,
) : Element

/** 배경 사진. [dim] 은 "사진 흐리게" 0..0.6. */
data class PhotoBackground(
    override val id: String,
    override val startMs: Long,
    val attachmentId: String,
    val dim: Float,
    val crop: Rect,
) : Element {
    init {
        require(dim in 0f..MAX_DIM)
    }

    companion object {
        const val MAX_DIM = 0.6f
    }
}

/** "틀". 그릴 때만 보이는 안내선이라 리플레이에서는 표시하지 않는다. */
data class TemplateGuide(
    override val id: String,
    override val startMs: Long,
    val templateId: String,
) : Element

/**
 * 그림 문서. 리플레이 · 재편집용 원본이다.
 *
 * [version] 은 쓴 앱의 문서 버전, [minReaderVersion] 은 이 문서를 리플레이하려면 필요한 최소 버전이다.
 * 그보다 낮은 앱은 리플레이 대신 완성 PNG 만 보여 준다.
 */
data class DrawingDocument(
    val version: Int = CURRENT_VERSION,
    val minReaderVersion: Int = 1,
    val canvas: CanvasSpec,
    val elements: List<Element>,
    val totalDurationMs: Long,
) {
    companion object {
        /** 이 앱이 쓰는 문서 버전이자 읽을 수 있는 최고 버전. */
        const val CURRENT_VERSION = 1
    }
}

/** 문서 → 완성 PNG. 플랫폼 렌더러(:core:media)를 앱 셸이 꽂는다. 위젯 · 알림 · 목록이 이 PNG 를 쓴다. */
fun interface DrawingRasterizer {
    /** [photos] = 배경 사진 첨부 id → 이미지 바이트(JPEG). */
    fun png(doc: DrawingDocument, photos: Map<String, ByteArray>): ByteArray
}
