package com.sadturtleman.flowershoes.core.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import com.sadturtleman.flowershoes.core.drawing.DrawingCodec
import com.sadturtleman.flowershoes.core.drawing.DrawingDocument
import com.sadturtleman.flowershoes.core.drawing.EraserStroke
import com.sadturtleman.flowershoes.core.drawing.PointBuffer
import com.sadturtleman.flowershoes.core.drawing.StickerElement
import com.sadturtleman.flowershoes.core.drawing.Stroke
import com.sadturtleman.flowershoes.core.drawing.Tool
import java.io.File

/**
 * 그림 문서를 비트맵으로(위젯 · 알림용). Compose 렌더러(:core:drawing-ui)와 같은 규칙으로 그린다:
 * 배경은 고정 ARGB, 지우개는 실제로 지우고(획 레이어에서 CLEAR), 형광펜은 35% 불투명.
 *
 * [DrawingPng] 가 이것으로 PNG 를 만든다. 문서를 그 자리에서 그려야 할 때(옛 그림 등)도 쓴다.
 */
object DrawingBitmap {

    fun render(docPath: String, widthPx: Int): Bitmap? {
        val bytes = runCatching { File(docPath).readBytes() }.getOrNull() ?: return null
        val doc = (DrawingCodec.decode(bytes) as? DrawingCodec.Result.Ok)?.document ?: return null
        return render(doc, widthPx)
    }

    fun render(doc: DrawingDocument, widthPx: Int): Bitmap {
        val scale = widthPx.toFloat() / doc.canvas.width
        val heightPx = (doc.canvas.height * scale).toInt()
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(doc.canvas.backgroundArgb.toInt())

        // 획은 따로 레이어에 그려야 지우개가 배경까지 지우지 않는다.
        val layer = canvas.saveLayer(0f, 0f, widthPx.toFloat(), heightPx.toFloat(), null)
        doc.elements.forEach { e ->
            when (e) {
                is Stroke -> drawPoints(canvas, e.points, strokePaint(e.color, e.brush.width * scale, e.tool == Tool.HIGHLIGHTER), scale)
                is EraserStroke -> drawPoints(
                    canvas, e.points,
                    strokePaint(0xFF000000, e.brush.width * scale, false).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) },
                    scale,
                )
                else -> Unit
            }
        }
        canvas.restoreToCount(layer)

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
        doc.elements.filterIsInstance<StickerElement>().forEach { s ->
            text.textSize = 64 * scale * s.transform.scale
            canvas.save()
            canvas.translate(s.transform.x * scale, s.transform.y * scale)
            canvas.rotate(s.transform.rotationDeg)
            canvas.drawText(STICKER_GLYPHS[s.stickerId] ?: "❔", 0f, text.textSize / 3, text)
            canvas.restore()
        }
        return bitmap
    }

    private fun strokePaint(argb: Long, width: Float, highlighter: Boolean) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = argb.toInt()
        if (highlighter) alpha = (255 * 0.35f).toInt()
        style = Paint.Style.STROKE
        strokeWidth = width
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private fun drawPoints(canvas: Canvas, p: PointBuffer, paint: Paint, scale: Float) {
        if (p.size == 0) return
        if (p.size == 1) {
            canvas.drawPoint(p.xs[0] * scale, p.ys[0] * scale, paint)
            return
        }
        val path = Path().apply {
            moveTo(p.xs[0] * scale, p.ys[0] * scale)
            for (i in 1 until p.size) lineTo(p.xs[i] * scale, p.ys[i] * scale)
        }
        canvas.drawPath(path, paint)
    }

    // :core:drawing-ui 의 STICKERS 와 같은 목록(위젯은 Compose 런타임 없이 그린다).
    private val STICKER_GLYPHS = mapOf(
        "heart" to "💗", "rose" to "🌹", "star" to "⭐", "kiss" to "😘", "hug" to "🤗",
        "boot" to "🥾", "tag" to "🏷️", "cap" to "🎓", "letter" to "💌", "calendar" to "📅",
    )
}

actual object DrawingPng {
    actual fun render(doc: DrawingDocument, widthPx: Int): ByteArray {
        val bitmap = DrawingBitmap.render(doc, widthPx)
        return java.io.ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }
}
