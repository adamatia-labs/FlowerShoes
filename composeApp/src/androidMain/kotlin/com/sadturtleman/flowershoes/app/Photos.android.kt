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
