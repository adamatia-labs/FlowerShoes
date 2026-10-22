package com.sadturtleman.flowershoes.core.media

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.text.font.createFontFamilyResolver
import com.sadturtleman.flowershoes.core.drawing.DrawingCodec
import com.sadturtleman.flowershoes.core.drawing.DrawingDocument
import com.sadturtleman.flowershoes.core.drawing.PhotoBackground
import java.io.File

/**
 * 그림 문서를 비트맵으로(위젯 · 알림용). 화면과 같은 Compose 렌더러로 그린다(스티커 · 사진 포함).
 * 앱이 시작할 때 [install] 로 Context 를 넣는다 — 글자 스티커를 재는 글꼴 해석기가 Context 를 쓴다.
 */
object DrawingBitmap {
    private var context: Context? = null

    /** 첨부 폴더(배경 사진을 문서 경로 옆에서 찾는다). */
    private var attachmentsDir: File? = null

    fun install(context: Context, attachmentsDir: File? = null) {
        this.context = context.applicationContext
        this.attachmentsDir = attachmentsDir
    }

    fun render(docPath: String, widthPx: Int): Bitmap? {
        val bytes = runCatching { File(docPath).readBytes() }.getOrNull() ?: return null
        val doc = (DrawingCodec.decode(bytes) as? DrawingCodec.Result.Ok)?.document ?: return null
        // 위젯: 배경 사진은 문서와 같은 첨부 폴더에 있다.
        val dir = attachmentsDir ?: File(docPath).parentFile
        val photos = doc.elements.filterIsInstance<PhotoBackground>()
            .mapNotNull { p -> runCatching { File(dir, p.attachmentId).readBytes() }.getOrNull()?.let { p.attachmentId to it } }.toMap()
        return render(doc, widthPx, photos)
    }

    fun render(doc: DrawingDocument, widthPx: Int, photos: Map<String, ByteArray> = emptyMap()): Bitmap {
        val ctx = checkNotNull(context) { "DrawingBitmap.install(context) 를 먼저 불러야 한다" }
        return renderDrawing(doc, widthPx, photos, createFontFamilyResolver(ctx)).asAndroidBitmap()
    }
}

actual object DrawingPng {
    actual fun render(doc: DrawingDocument, widthPx: Int, photos: Map<String, ByteArray>): ByteArray {
        val bitmap = DrawingBitmap.render(doc, widthPx, photos)
        return java.io.ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
    }
}
