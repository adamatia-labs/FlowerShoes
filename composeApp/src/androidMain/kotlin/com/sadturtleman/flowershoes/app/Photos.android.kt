package com.sadturtleman.flowershoes.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal actual fun rememberPhotoPicker(onPicked: (ByteArray) -> Unit): (Boolean) -> Unit {
    val context = LocalContext.current
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { shrink(context, it) }?.let(onPicked)
    }
    // 카메라: 앱 캐시의 파일에 원본으로 찍게 한다(미리보기 비트맵은 너무 작다).
    val shot = remember(context) { File(context.cacheDir, "camera/shot.jpg") }
    val shotUri = remember(context) { FileProvider.getUriForFile(context, "${context.packageName}.files", shot.also { it.parentFile?.mkdirs() }) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) shrink(context, shotUri)?.let(onPicked)
    }
    return remember(gallery, camera) {
        { fromCamera ->
            if (fromCamera) camera.launch(shotUri)
            else gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }
}

/** 긴 변 [PHOTO_MAX_EDGE] 로 줄이고(회전 반영) JPEG 85. */
private fun shrink(context: Context, uri: Uri): ByteArray? = runCatching {
    val bitmap = if (Build.VERSION.SDK_INT >= 28) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val w = info.size.width
            val h = info.size.height
            val k = minOf(1f, PHOTO_MAX_EDGE.toFloat() / maxOf(w, h))
            decoder.setTargetSize((w * k).toInt().coerceAtLeast(1), (h * k).toInt().coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= PHOTO_MAX_EDGE) sample *= 2
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
    } ?: return null
    ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        bitmap.recycle()
        out.toByteArray()
    }
}.getOrNull()

@Composable
internal actual fun rememberImageShare(): (ByteArray) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { png ->
            val file = File(context.cacheDir, "share/kkotsin.png").also { it.parentFile?.mkdirs(); it.writeBytes(png) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("image/png")
                .putExtra(android.content.Intent.EXTRA_STREAM, uri)
                .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(android.content.Intent.createChooser(send, "그림 공유").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

@Composable
internal actual fun rememberImageSaver(): suspend (ByteArray) -> Boolean {
    val context = LocalContext.current
    return remember(context) {
        { png ->
            withContext(Dispatchers.IO) {
                runCatching {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, "kkotsin_${System.currentTimeMillis()}.png")
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        if (Build.VERSION.SDK_INT >= 29) {
                            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/꽃신")
                            put(MediaStore.Images.Media.IS_PENDING, 1)
                        }
                    }
                    val resolver = context.contentResolver
                    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@runCatching false
                    resolver.openOutputStream(uri)?.use { it.write(png) } ?: return@runCatching false
                    if (Build.VERSION.SDK_INT >= 29) {
                        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                    }
                    true
                }.getOrDefault(false)
            }
        }
    }
}

@Composable
internal actual fun rememberFileShare(): (List<Pair<String, ByteArray>>) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { files ->
            val dir = File(context.cacheDir, "share").also { it.mkdirs() }
            val uris = ArrayList(
                files.map { (name, bytes) ->
                    FileProvider.getUriForFile(context, "${context.packageName}.files", File(dir, name).also { it.writeBytes(bytes) })
                },
            )
            val mime = if (files.all { it.first.endsWith(".pdf") }) "application/pdf" else "*/*"
            val send = if (uris.size == 1) {
                android.content.Intent(android.content.Intent.ACTION_SEND).setType(mime).putExtra(android.content.Intent.EXTRA_STREAM, uris[0])
            } else {
                android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).setType(mime).putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, uris)
            }.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(android.content.Intent.createChooser(send, "저장 · 공유").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

/**
 * 추억 책 PDF(Android PdfDocument). 쪽은 8×8인치(576pt). 화면용은 그대로, 인쇄용은 사방 3mm(8.5pt) 재단 여백을 더하고
 * 그림을 여백까지 넓혀 깐다(재단하면 쪽 끝이 흰 줄 없이 잘린다).
 */
internal actual fun newBookPdf(): com.sadturtleman.flowershoes.feature.memory.BookPdf? = object : com.sadturtleman.flowershoes.feature.memory.BookPdf {
    private val screen = android.graphics.pdf.PdfDocument()
    private val print = android.graphics.pdf.PdfDocument()
    private var n = 0

    override fun add(page: androidx.compose.ui.graphics.ImageBitmap) {
        // 그래픽 레이어에서 받은 비트맵은 HARDWARE 라 PdfDocument(소프트웨어 캔버스)가 못 그린다 — 복사해서 쓴다.
        val captured = page.asAndroidBitmap()
        val bitmap = if (Build.VERSION.SDK_INT >= 26 && captured.config == Bitmap.Config.HARDWARE) captured.copy(Bitmap.Config.ARGB_8888, false) else captured
        n++
        draw(screen, bitmap, PAGE_PT, 0f)
        draw(print, bitmap, (PAGE_PT + 2 * BLEED_PT).toInt(), BLEED_PT)
        if (bitmap !== captured) bitmap.recycle()
    }

    @Suppress("UNUSED_PARAMETER")
    private fun draw(doc: android.graphics.pdf.PdfDocument, bitmap: Bitmap, size: Int, bleed: Float) {
        val p = doc.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(size, size, n).create())
        val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
        // 인쇄용은 재단 여백([bleed])까지 그림을 늘려 깐다(가장자리 색이 이어진다).
        p.canvas.drawBitmap(bitmap, null, android.graphics.RectF(0f, 0f, size.toFloat(), size.toFloat()), paint)
        doc.finishPage(p)
    }

    override suspend fun finish(): List<Pair<String, ByteArray>> = withContext(Dispatchers.IO) {
        fun bytes(doc: android.graphics.pdf.PdfDocument) = ByteArrayOutputStream().use { out -> doc.writeTo(out); doc.close(); out.toByteArray() }
        listOf("우리의_책_화면용.pdf" to bytes(screen), "우리의_책_인쇄용_8x8_재단3mm.pdf" to bytes(print))
    }
}

private const val PAGE_PT = 576
private const val BLEED_PT = 8.5f
