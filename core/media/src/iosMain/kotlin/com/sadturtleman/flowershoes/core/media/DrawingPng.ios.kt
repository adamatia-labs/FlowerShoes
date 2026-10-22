package com.sadturtleman.flowershoes.core.media

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.sadturtleman.flowershoes.core.drawing.DrawingDocument
import com.sadturtleman.flowershoes.core.drawing.StickerElement
import com.sadturtleman.flowershoes.core.drawingui.drawDocument
import com.sadturtleman.flowershoes.core.drawingui.drawStickerForExport
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

/** iOS: Compose 렌더러(:core:drawing-ui)로 ImageBitmap 에 그리고 Skia 로 PNG 인코딩. macOS 에서 빌드 확인 필요. */
actual object DrawingPng {
    actual fun render(doc: DrawingDocument, widthPx: Int): ByteArray {
        val heightPx = (doc.canvas.height * widthPx.toFloat() / doc.canvas.width).toInt()
        val bitmap = ImageBitmap(widthPx, heightPx)
        val measurer = TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(widthPx.toFloat(), heightPx.toFloat())) {
            drawDocument(doc)
            val sx = widthPx.toFloat() / doc.canvas.width
            doc.elements.filterIsInstance<StickerElement>().forEach { drawStickerForExport(it, sx, measurer) }
        }
        return Image.makeFromBitmap(bitmap.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes
    }
}
