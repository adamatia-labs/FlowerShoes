package com.sadturtleman.flowershoes.core.drawingui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sadturtleman.flowershoes.core.designsystem.KkRadius
import com.sadturtleman.flowershoes.core.designsystem.KkSpacing
import com.sadturtleman.flowershoes.core.designsystem.KkTheme
import com.sadturtleman.flowershoes.core.drawing.DrawingDocument
import com.sadturtleman.flowershoes.core.drawing.EraserStroke
import com.sadturtleman.flowershoes.core.drawing.PointBuffer
import com.sadturtleman.flowershoes.core.drawing.ReplayTimeline
import com.sadturtleman.flowershoes.core.drawing.StickerElement
import com.sadturtleman.flowershoes.core.drawing.Stroke
import com.sadturtleman.flowershoes.core.drawing.TemplateGuide
import com.sadturtleman.flowershoes.core.drawing.Tool
import com.sadturtleman.flowershoes.core.drawing.WidgetShape

/** 번들 스티커. 정식 세트(Fluent Emoji 등, 설계 11장)를 넣기 전까지 글리프로 그린다. */
val STICKERS: Map<String, String> = linkedMapOf(
    "heart" to "💗", "rose" to "🌹", "star" to "⭐", "kiss" to "😘", "hug" to "🤗",
    "boot" to "🥾", "tag" to "🏷️", "cap" to "🎓", "letter" to "💌", "calendar" to "📅",
)

/**
 * 완성본 그리기. 지우개는 실제로 지운다(BlendMode.Clear) — 리플레이 마지막의 완성본과 똑같다.
 * 틀(TemplateGuide)은 [showGuides] 일 때만(그리는 중) 보인다.
 */
fun DrawScope.drawDocument(doc: DrawingDocument, showGuides: Boolean = false) {
    val sx = size.width / doc.canvas.width
    drawRect(Color(doc.canvas.backgroundArgb.toInt()))
    if (showGuides) doc.elements.filterIsInstance<TemplateGuide>().forEach { drawGuide(it.templateId, doc.canvas.shape) }
    // 지우개가 배경까지 지우지 않도록 획만 따로 레이어에 그린다.
    drawContext.canvas.saveLayer(androidx.compose.ui.geometry.Rect(Offset.Zero, size), androidx.compose.ui.graphics.Paint())
    doc.elements.forEach { e ->
        when (e) {
            is Stroke -> drawPoints(e.points, e.points.size - 1, e.color, e.brush.width * sx, sx, e.tool == Tool.HIGHLIGHTER)
            is EraserStroke -> drawPoints(e.points, e.points.size - 1, 0xFF000000, e.brush.width * sx, sx, false, BlendMode.Clear)
            else -> Unit
        }
    }
    drawContext.canvas.restore()
}

internal fun DrawScope.drawPoints(
    p: PointBuffer,
    through: Int,
    color: Long,
    widthPx: Float,
    scale: Float,
    highlighter: Boolean,
    blend: BlendMode = BlendMode.SrcOver,
    segments: IntArray? = null,
) {
    if (through < 0 || p.size == 0) return
    val c = Color(color.toInt()).let { if (highlighter) it.copy(alpha = 0.35f) else it }
    val ranges = segments?.toList()?.chunked(2)?.map { it[0]..it[1] } ?: listOf(0..p.size - 1)
    ranges.forEach { range ->
        val end = minOf(range.last, through)
        if (end < range.first) return@forEach
        if (end == range.first) {
            drawCircle(c, widthPx / 2, Offset(p.xs[end] * scale, p.ys[end] * scale), blendMode = blend)
            return@forEach
        }
        val path = Path().apply {
            moveTo(p.xs[range.first] * scale, p.ys[range.first] * scale)
            for (i in range.first + 1..end) lineTo(p.xs[i] * scale, p.ys[i] * scale)
        }
        drawPath(path, c, style = DrawStroke(widthPx, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = blend)
    }
}

internal fun DrawScope.drawGuide(templateId: String, @Suppress("UNUSED_PARAMETER") shape: WidgetShape) {
    val guide = Color(0x33000000)
    val w = size.width
    val h = size.height
    when (templateId) {
        "heart" -> {
            val path = Path().apply {
                moveTo(w * 0.5f, h * 0.85f)
                cubicTo(w * 0.15f, h * 0.6f, w * 0.1f, h * 0.3f, w * 0.3f, h * 0.2f)
                cubicTo(w * 0.42f, h * 0.14f, w * 0.48f, h * 0.24f, w * 0.5f, h * 0.3f)
                cubicTo(w * 0.52f, h * 0.24f, w * 0.58f, h * 0.14f, w * 0.7f, h * 0.2f)
                cubicTo(w * 0.9f, h * 0.3f, w * 0.85f, h * 0.6f, w * 0.5f, h * 0.85f)
            }
            drawPath(path, guide, style = DrawStroke(3f))
        }
        else -> drawRect(guide, topLeft = Offset(w * 0.1f, h * 0.1f), size = androidx.compose.ui.geometry.Size(w * 0.8f, h * 0.8f), style = DrawStroke(3f))
    }
}

/**
 * 완성본 + 스티커. 스티커는 글자 측정이 필요해 Composable 안에서 그린다.
 */
@Composable
fun DrawingView(doc: DrawingDocument, modifier: Modifier = Modifier, showGuides: Boolean = false) {
    val measurer = rememberTextMeasurer()
    Canvas(
        modifier.fillMaxWidth().aspectRatio(doc.canvas.width.toFloat() / doc.canvas.height)
            .clip(RoundedCornerShape(KkRadius.lg)),
    ) {
        drawDocument(doc, showGuides)
        val sx = size.width / doc.canvas.width
        doc.elements.filterIsInstance<StickerElement>().forEach { drawSticker(it, sx, 1f, measurer) }
    }
}

/** PNG 내보내기(:core:media)용. 컴포지션 밖에서 만든 [measurer] 로 그린다. */
fun DrawScope.drawStickerForExport(s: StickerElement, sx: Float, measurer: androidx.compose.ui.text.TextMeasurer) = drawSticker(s, sx, 1f, measurer)

internal fun DrawScope.drawSticker(s: StickerElement, sx: Float, pop: Float, measurer: androidx.compose.ui.text.TextMeasurer) {
    val glyph = STICKERS[s.stickerId] ?: "❔"
    val layout = measurer.measure(glyph, TextStyle(fontSize = (64 * sx).sp))
    translate(s.transform.x * sx, s.transform.y * sx) {
        rotate(s.transform.rotationDeg, pivot = Offset.Zero) {
            scale(s.transform.scale * pop, pivot = Offset.Zero) {
                drawText(layout, topLeft = Offset(-layout.size.width / 2f, -layout.size.height / 2f))
            }
        }
    }
}

/**
 * 리플레이 플레이어. [autoPlay] 면 바로 재생한다(받은 그림을 처음 열 때).
 * 캔버스를 누르면 바로 완성본, 끝난 뒤 누르면 처음부터 다시.
 */
@Composable
fun ReplayPlayer(
    doc: DrawingDocument,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
    speed: Float = 1f,
    onFinished: () -> Unit = {},
) {
    val timeline = remember(doc) { ReplayTimeline.of(doc) }
    var playing by remember(doc) { mutableStateOf(autoPlay) }
    var elapsed by remember(doc) { mutableLongStateOf(if (autoPlay) 0L else timeline.durationMs) }
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        var last = withFrameMillis { it }
        while (elapsed < timeline.durationMs) {
            val now = withFrameMillis { it }
            elapsed = (elapsed + ((now - last) * speed).toLong()).coerceAtMost(timeline.durationMs)
            last = now
        }
        playing = false
        onFinished()
    }
    val frame = if (playing) timeline.frame(elapsed) else timeline.completeFrame()
    val measurer = rememberTextMeasurer()
    val c = KkTheme.colors
    Box(modifier) {
        Canvas(
            Modifier.fillMaxWidth().aspectRatio(doc.canvas.width.toFloat() / doc.canvas.height)
                .clip(RoundedCornerShape(KkRadius.lg))
                .clickable(interactionSource = null, indication = null, role = Role.Button) {
                    if (playing) {
                        elapsed = timeline.durationMs
                    } else {
                        elapsed = 0
                        playing = true
                    }
                },
        ) {
            val sx = size.width / doc.canvas.width
            drawRect(Color(doc.canvas.backgroundArgb.toInt()))
            frame.strokes.forEach { sp ->
                val s = sp.stroke
                drawPoints(s.points, sp.drawnThrough, s.color, s.brush.width * sx, sx, s.tool == Tool.HIGHLIGHTER, segments = s.replaySegments)
                sp.penTip?.let { (x, y) ->
                    val from = sp.drawnThrough
                    val alive = s.replaySegments?.toList()?.chunked(2)?.any { from >= it[0] && from < it[1] } ?: true
                    if (alive && from >= 0) {
                        val c = Color(s.color.toInt()).let { if (s.tool == Tool.HIGHLIGHTER) it.copy(alpha = 0.35f) else it }
                        drawLine(c, Offset(s.points.xs[from] * sx, s.points.ys[from] * sx), Offset(x * sx, y * sx), s.brush.width * sx, cap = StrokeCap.Round)
                    }
                    drawCircle(Color(s.color.toInt()), s.brush.width * sx * 0.8f, Offset(x * sx, y * sx))
                }
            }
            frame.stickers.forEach { drawSticker(it.sticker, sx, it.scale, measurer) }
            // 마무리: 완성본으로 크로스페이드. 지우개가 일부만 지운 부분까지 완성본과 똑같이 끝난다.
            if (frame.finishAlpha > 0f) {
                drawContext.canvas.saveLayer(
                    androidx.compose.ui.geometry.Rect(Offset.Zero, size),
                    androidx.compose.ui.graphics.Paint().apply { alpha = frame.finishAlpha },
                )
                drawDocument(doc)
                doc.elements.filterIsInstance<StickerElement>().forEach { drawSticker(it, sx, 1f, measurer) }
                drawContext.canvas.restore()
            }
        }
        if (!playing) {
            Text(
                "▶ 과정 · ${(timeline.durationMs + 999) / 1000}초",
                style = KkTheme.type.labelS,
                color = c.textOnBrand,
                modifier = Modifier.align(Alignment.BottomEnd).padding(KkSpacing.sm)
                    .clip(RoundedCornerShape(KkRadius.full))
                    .background(Color(0x99000000))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}
