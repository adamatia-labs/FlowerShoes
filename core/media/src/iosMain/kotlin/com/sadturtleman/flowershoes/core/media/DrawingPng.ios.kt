package com.sadturtleman.flowershoes.core.media

import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.text.font.createFontFamilyResolver
import com.sadturtleman.flowershoes.core.drawing.DrawingDocument
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

/** iOS: Compose 렌더러로 그리고 Skia 로 PNG 인코딩. */
actual object DrawingPng {
    actual fun render(doc: DrawingDocument, widthPx: Int, photos: Map<String, ByteArray>): ByteArray {
        val bitmap = renderDrawing(doc, widthPx, photos, createFontFamilyResolver())
        return Image.makeFromBitmap(bitmap.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes
    }
}

/** iOS(Skia): 바이트로 바로 글꼴을 만든다. */
internal actual fun exportFont(bytes: ByteArray): androidx.compose.ui.text.font.FontFamily? = runCatching {
    androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.platform.Font("sticker-jua", bytes))
}.getOrNull()
