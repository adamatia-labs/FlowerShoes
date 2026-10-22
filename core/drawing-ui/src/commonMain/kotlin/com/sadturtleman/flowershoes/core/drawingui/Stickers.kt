package com.sadturtleman.flowershoes.core.drawingui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.sadturtleman.flowershoes.core.drawing.StickerElement

/**
 * 스티커(Figma 그림 / 스티커). id 하나가 모양 하나다 — 받는 폰도 같은 id 로 똑같이 그린다.
 *
 * - 벡터: 48 뷰박스 경로([VECTOR_STICKERS]).
 * - `text:보고싶어`: 로즈 테두리 도장(-8°).
 * - `dday:D-547`: 올리브 알약. 붙인 순간의 글자가 그대로 남는다(받는 날 다시 세지 않는다).
 * - 그 밖(예전 앱의 heart · rose …): 글리프([STICKERS]).
 */
object StickerIds {
    const val TEXT = "text:"
    const val DDAY = "dday:"
    fun text(s: String) = TEXT + s
    fun dday(s: String) = DDAY + s
}

/** 스티커 서랍 분류(하트·꽃 · 군대 · 글자 · D-day). D-day 는 그림 화면이 지금 숫자로 채운다. */
val STICKER_TABS: List<Pair<String, List<String>>> = listOf(
    "하트·꽃" to listOf("v.heart", "v.flower", "v.star", "v.boot", "v.mail"),
    "군대" to listOf("v.helmet", "v.dogtag", "v.medal", "v.boot", "v.star"),
    "글자" to listOf("보고싶어", "사랑해", "힘내", "고생했어", "잘 자").map(StickerIds::text),
    "D-day" to emptyList(),
)

private class VectorPart(val d: String, val fill: Color?, val stroke: Color? = null, val strokeWidth: Float = 0f) {
    val path: Path by lazy { PathParser().parsePathString(d).toPath() }
}

private val ROSE = Color(0xFFED4773)
private val PINK = Color(0xFFFA6E98)
private val AMBER = Color(0xFFF2A93B)
private val OLIVE = Color(0xFF566B24)
private val OLIVE_DARK = Color(0xFF3E5218)
private val SKY = Color(0xFF3B9BF5)
private val BRAND = Color(0xFFC72C4D)

private val VECTOR_STICKERS: Map<String, List<VectorPart>> = mapOf(
    "v.heart" to listOf(VectorPart("M24 40C6 28 0 16 7 9C14 2 21 6 24 12C27 6 34 2 41 9C48 16 42 28 24 40Z", ROSE)),
    "v.flower" to listOf(
        VectorPart("M24 20A7 7 0 1 0 24 6A7 7 0 1 0 24 20Z", PINK),
        VectorPart("M35 28A7 7 0 1 0 35 14A7 7 0 1 0 35 28Z", PINK),
        VectorPart("M31 41A7 7 0 1 0 31 27A7 7 0 1 0 31 41Z", PINK),
        VectorPart("M17 41A7 7 0 1 0 17 27A7 7 0 1 0 17 41Z", PINK),
        VectorPart("M13 28A7 7 0 1 0 13 14A7 7 0 1 0 13 28Z", PINK),
        VectorPart("M24 29A5 5 0 1 0 24 19A5 5 0 1 0 24 29Z", AMBER),
    ),
    "v.star" to listOf(VectorPart("M24 6L29 17L41 18L32 26L35 38L24 32L13 38L16 26L7 18L19 17L24 6Z", AMBER)),
    "v.boot" to listOf(VectorPart("M10 30H38V38H10V30ZM14 10H28V30H14V10Z", OLIVE), VectorPart("M28 22H38V30H28V22Z", OLIVE_DARK)),
    "v.mail" to listOf(VectorPart("M8 16H40V34H8V16Z", SKY), VectorPart("M8 16L24 27L40 16", null, Color.White, 2.5f)),
    "v.helmet" to listOf(VectorPart("M8 30C8 18 15 10 24 10C33 10 40 18 40 30Z", OLIVE), VectorPart("M5 30H43V35H5Z", OLIVE_DARK)),
    "v.dogtag" to listOf(
        VectorPart("M17 8H31A4 4 0 0 1 35 12V36A4 4 0 0 1 31 40H17A4 4 0 0 1 13 36V12A4 4 0 0 1 17 8Z", Color(0xFF9AA0A8)),
        VectorPart("M24 16.5A2.5 2.5 0 1 0 24 11.5A2.5 2.5 0 1 0 24 16.5Z", Color.White),
        VectorPart("M18 22H30M18 27H30M18 32H26", null, Color(0xFF656A74), 2f),
    ),
    "v.medal" to listOf(
        VectorPart("M16 5H32L27 21H21Z", BRAND),
        VectorPart("M24 42A11 11 0 1 0 24 20A11 11 0 1 0 24 42Z", AMBER),
        VectorPart("M24 25L26 29.5L31 30L27.2 33.3L28.3 38L24 35.5L19.7 38L20.8 33.3L17 30L22 29.5Z", Color.White),
    ),
)

/** 캔버스 논리 단위(가로 1000)로 본 스티커 기본 크기. */
private const val VECTOR_SIZE = 180f
private const val STAMP_FONT = 48f
private const val DDAY_FONT = 66f

/** 스티커 하나를 [s] 의 위치 · 크기 · 회전으로. [pop] 은 리플레이의 톡 튀는 크기. */
internal fun DrawScope.drawStickerArt(s: StickerElement, sx: Float, pop: Float, measurer: TextMeasurer) {
    translate(s.transform.x * sx, s.transform.y * sx) {
        rotate(s.transform.rotationDeg, pivot = Offset.Zero) {
            scale(s.transform.scale * pop, pivot = Offset.Zero) { drawStickerAt(s.stickerId, sx, measurer) }
        }
    }
}

/** 원점(0,0) 가운데에 스티커를 그린다. [sx] = 화면 px / 논리 단위. */
private fun DrawScope.drawStickerAt(id: String, sx: Float, measurer: TextMeasurer) {
    val vector = VECTOR_STICKERS[id]
    when {
        vector != null -> {
            val k = VECTOR_SIZE * sx / 48f
            translate(-24f * k, -24f * k) {
                scale(k, pivot = Offset.Zero) {
                    vector.forEach { part ->
                        part.fill?.let { drawPath(part.path, it) }
                        part.stroke?.let { drawPath(part.path, it, style = DrawStroke(part.strokeWidth, cap = StrokeCap.Round)) }
                    }
                }
            }
        }
        id.startsWith(StickerIds.TEXT) -> {
            val layout = measurer.measure(id.removePrefix(StickerIds.TEXT), TextStyle(color = BRAND, fontSize = (STAMP_FONT * sx).toSp(), fontWeight = FontWeight.Bold))
            val padH = 26f * sx
            val padV = 14f * sx
            val w = layout.size.width + padH * 2
            val h = layout.size.height + padV * 2
            rotate(-8f, pivot = Offset.Zero) {
                drawRoundRect(Color.White, Offset(-w / 2, -h / 2), Size(w, h), CornerRadius(h / 2))
                drawRoundRect(ROSE, Offset(-w / 2, -h / 2), Size(w, h), CornerRadius(h / 2), style = DrawStroke(6f * sx))
                drawText(layout, topLeft = Offset(-layout.size.width / 2f, -layout.size.height / 2f))
            }
        }
        id.startsWith(StickerIds.DDAY) -> {
            val layout = measurer.measure(id.removePrefix(StickerIds.DDAY), TextStyle(color = Color.White, fontSize = (DDAY_FONT * sx).toSp(), fontWeight = FontWeight.Bold))
            val padH = 40f * sx
            val padV = 18f * sx
            val w = layout.size.width + padH * 2
            val h = layout.size.height + padV * 2
            drawRoundRect(OLIVE, Offset(-w / 2, -h / 2), Size(w, h), CornerRadius(h / 2))
            drawText(layout, topLeft = Offset(-layout.size.width / 2f, -layout.size.height / 2f))
        }
        else -> {
            val glyph = STICKERS[id] ?: "❔"
            val layout = measurer.measure(glyph, TextStyle(fontSize = (64 * sx).toSp()))
            drawText(layout, topLeft = Offset(-layout.size.width / 2f, -layout.size.height / 2f))
        }
    }
}

/** 스티커 서랍 한 칸의 그림(56 칸 안 48). */
@Composable
fun StickerPreview(id: String, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        // 미리보기 칸에 스티커가 꽉 차도록 논리 단위를 맞춘다. 글자 스티커는 길이를 재서 줄인다.
        val base = when {
            id.startsWith(StickerIds.TEXT) || id.startsWith(StickerIds.DDAY) -> {
                val dday = id.startsWith(StickerIds.DDAY)
                val font = if (dday) DDAY_FONT else STAMP_FONT
                val w = measurer.measure(id.substringAfter(':'), TextStyle(fontSize = font.toSp(), fontWeight = FontWeight.Bold)).size.width +
                    (if (dday) 80f else 52f)
                minOf(size.width / 300f, size.width * 0.92f / w)
            }
            else -> size.width / VECTOR_SIZE
        }
        translate(size.width / 2, size.height / 2) { drawStickerAt(id, base, measurer) }
    }
}

// ── 틀(따라 그리기 안내선) ────────────────────────────────────────────────

/** 틀 고르기(Figma 그림 / 틀): 하트 · D-day · 꽃신 · 글씨 줄. */
val TEMPLATES: List<Pair<String, String>> = listOf("heart" to "하트", "dday" to "D-day", "kkotsin" to "꽃신", "lines" to "글씨 줄")

private val GUIDE = Color(0xFFC9CBD0)

/** 점선 안내선. 캔버스 크기에 맞춰 그린다. */
internal fun DrawScope.drawTemplate(templateId: String, dot: Float) {
    val w = size.width
    val h = size.height
    val style = DrawStroke(dot, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.01f, dot * 2.2f)))
    fun p(x: Float, y: Float) = Offset(w * x, h * y)
    when (templateId) {
        "heart" -> drawPath(
            Path().apply {
                moveTo(w * 0.5f, h * 0.82f)
                cubicTo(w * 0.16f, h * 0.6f, w * 0.12f, h * 0.32f, w * 0.3f, h * 0.22f)
                cubicTo(w * 0.42f, h * 0.16f, w * 0.48f, h * 0.24f, w * 0.5f, h * 0.31f)
                cubicTo(w * 0.52f, h * 0.24f, w * 0.58f, h * 0.16f, w * 0.7f, h * 0.22f)
                cubicTo(w * 0.88f, h * 0.32f, w * 0.84f, h * 0.6f, w * 0.5f, h * 0.82f)
            },
            GUIDE, style = style,
        )
        "dday" -> {
            drawRoundRect(GUIDE, p(0.2f, 0.38f), Size(w * 0.6f, h * 0.24f), CornerRadius(h * 0.12f), style = style)
            drawCircle(GUIDE, h * 0.035f, p(0.36f, 0.5f), style = style)
            drawLine(GUIDE, p(0.46f, 0.5f), p(0.68f, 0.5f), dot, cap = StrokeCap.Round, pathEffect = style.pathEffect)
        }
        "kkotsin" -> {
            drawPath(
                Path().apply {
                    moveTo(w * 0.18f, h * 0.44f)
                    lineTo(w * 0.17f, h * 0.68f)
                    quadraticTo(w * 0.5f, h * 0.76f, w * 0.84f, h * 0.68f)
                    quadraticTo(w * 0.9f, h * 0.62f, w * 0.82f, h * 0.56f)
                    quadraticTo(w * 0.66f, h * 0.48f, w * 0.52f, h * 0.5f)
                    quadraticTo(w * 0.38f, h * 0.56f, w * 0.18f, h * 0.44f)
                },
                GUIDE, style = style,
            )
            listOf(0f, 72f, 144f, 216f, 288f).forEach { deg ->
                val r = h * 0.045f
                val a = deg * kotlin.math.PI.toFloat() / 180f
                drawCircle(GUIDE, r * 0.7f, Offset(w * 0.68f + kotlin.math.cos(a) * r, h * 0.36f + kotlin.math.sin(a) * r), style = style)
            }
        }
        "lines" -> listOf(0.34f, 0.52f, 0.7f).forEach { y -> drawLine(GUIDE, p(0.1f, y), p(0.9f, y), dot, cap = StrokeCap.Round, pathEffect = style.pathEffect) }
        else -> drawRoundRect(GUIDE, p(0.1f, 0.1f), Size(w * 0.8f, h * 0.8f), CornerRadius(w * 0.04f), style = style)
    }
}

/** 틀 칸의 그림. */
@Composable
fun TemplatePreview(id: String, modifier: Modifier = Modifier) {
    Canvas(modifier) { drawTemplate(id, dot = size.minDimension / 26f) }
}
