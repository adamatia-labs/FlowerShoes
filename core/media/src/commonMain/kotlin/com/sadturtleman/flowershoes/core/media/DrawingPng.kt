package com.sadturtleman.flowershoes.core.media

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.sadturtleman.flowershoes.core.drawing.DrawingDocument
import com.sadturtleman.flowershoes.core.drawing.StickerElement
import com.sadturtleman.flowershoes.core.drawingui.drawDocument
import com.sadturtleman.flowershoes.core.drawingui.drawStickerForExport
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * 완성 PNG(위젯 · 알림 · 사진첩 저장). 화면과 같은 Compose 렌더러(:core:drawing-ui)로 그린다 —
 * 스티커 · 배경 사진 · 지우개가 화면과 똑같이 나온다.
 */
expect object DrawingPng {
    fun render(doc: DrawingDocument, widthPx: Int = doc.canvas.shape.pngWidth, photos: Map<String, ByteArray> = emptyMap()): ByteArray
}

/** 글꼴 원본 바이트 → 이 플랫폼의 FontFamily(PNG 내보내기용). 만들 수 없으면 null(기본 글꼴). */
internal expect fun exportFont(bytes: ByteArray): FontFamily?

/** 문서를 [widthPx] 너비 비트맵으로. 글자(스티커) 측정에 플랫폼 글꼴 해석기가 필요하다. */
internal fun renderDrawing(doc: DrawingDocument, widthPx: Int, photos: Map<String, ByteArray>, fonts: FontFamily.Resolver): ImageBitmap {
    val heightPx = (doc.canvas.height * widthPx.toFloat() / doc.canvas.width).toInt()
    val bitmap = ImageBitmap(widthPx, heightPx)
    val measurer = TextMeasurer(fonts, Density(1f), LayoutDirection.Ltr)
    val images = photos.mapNotNull { (id, bytes) -> runCatching { bytes.decodeToImageBitmap() }.getOrNull()?.let { id to it } }.toMap()
    // 글자 스티커 글꼴(Jua): 화면의 글꼴 해석기 밖이라 원본 바이트로 만든 플랫폼 글꼴을 쓴다.
    com.sadturtleman.flowershoes.core.drawingui.StickerFont.exportFamily =
        com.sadturtleman.flowershoes.core.drawingui.StickerFont.bytes?.let { exportFont(it) }
    try {
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(widthPx.toFloat(), heightPx.toFloat())) {
            drawDocument(doc, photos = images)
            val sx = widthPx.toFloat() / doc.canvas.width
            doc.elements.filterIsInstance<StickerElement>().forEach { drawStickerForExport(it, sx, measurer) }
        }
    } finally {
        com.sadturtleman.flowershoes.core.drawingui.StickerFont.exportFamily = null
    }
    return bitmap
}
