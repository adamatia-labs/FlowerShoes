package com.sadturtleman.flowershoes.core.drawingui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.sadturtleman.flowershoes.core.drawing.DrawingDocument
import com.sadturtleman.flowershoes.core.drawing.PhotoBackground
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * 배경 사진 첨부를 읽는 곳(앱 셸이 첨부 저장소로 채운다). 그림 문서는 첨부 id 만 갖고 있다.
 * 없으면 사진 없이 그린다(그림은 그대로 보인다).
 */
val LocalDrawingPhotoLoader = staticCompositionLocalOf<suspend (attachmentId: String) -> ByteArray?> { { null } }

/** 최근에 푼 사진 몇 장(목록 · 리플레이를 오갈 때 다시 풀지 않게). */
private object PhotoCache {
    private val map = LinkedHashMap<String, ImageBitmap>()
    fun get(id: String): ImageBitmap? = map[id]
    fun put(id: String, bitmap: ImageBitmap) {
        map[id] = bitmap
        while (map.size > 8) map.remove(map.keys.first())
    }
}

/** 문서의 배경 사진을 풀어 둔다. 다 풀기 전에는 비어 있다. */
@Composable
fun rememberDrawingPhotos(doc: DrawingDocument): Map<String, ImageBitmap> {
    val ids = doc.elements.filterIsInstance<PhotoBackground>().map { it.attachmentId }
    if (ids.isEmpty()) return emptyMap()
    val loader = LocalDrawingPhotoLoader.current
    val photos by produceState(ids.mapNotNull { id -> PhotoCache.get(id)?.let { id to it } }.toMap(), ids) {
        value = ids.mapNotNull { id ->
            (PhotoCache.get(id) ?: loader(id)?.let { bytes -> runCatching { bytes.decodeToImageBitmap() }.getOrNull()?.also { PhotoCache.put(id, it) } })
                ?.let { id to it }
        }.toMap()
    }
    return photos
}

/** 배경 사진을 캔버스에 꽉 차게(가운데 잘라) 그리고, [dim] 만큼 흰색으로 흐리게 덮는다. */
fun DrawScope.drawPhoto(image: ImageBitmap, dim: Float, alpha: Float) {
    val canvasRatio = size.width / size.height
    val imageRatio = image.width.toFloat() / image.height
    val (srcW, srcH) = if (imageRatio > canvasRatio) (image.height * canvasRatio).toInt() to image.height else image.width to (image.width / canvasRatio).toInt()
    drawImage(
        image,
        srcOffset = IntOffset((image.width - srcW) / 2, (image.height - srcH) / 2),
        srcSize = IntSize(srcW, srcH),
        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
        alpha = alpha,
    )
    if (dim > 0f) drawRect(Color.White.copy(alpha = dim * alpha))
}
