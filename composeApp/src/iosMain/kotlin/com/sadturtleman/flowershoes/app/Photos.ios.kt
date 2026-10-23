@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.sadturtleman.flowershoes.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.create
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UIImageWriteToSavedPhotosAlbum
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy

@Composable
internal actual fun rememberPhotoPicker(onPicked: (ByteArray) -> Unit): (Boolean) -> Unit {
    val handler = remember { PhotoPickHandler() }
    handler.onPicked = onPicked
    return remember(handler) { { fromCamera -> handler.present(fromCamera) } }
}

/** 앨범(PHPicker) · 카메라(UIImagePickerController) 대리자. 고른 사진을 줄여 JPEG 로 넘긴다. */
private class PhotoPickHandler :
    NSObject(),
    PHPickerViewControllerDelegateProtocol,
    UIImagePickerControllerDelegateProtocol,
    UINavigationControllerDelegateProtocol {

    var onPicked: (ByteArray) -> Unit = {}

    fun present(fromCamera: Boolean) {
        val top = topController() ?: return
        if (fromCamera) {
            val camera = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
            if (!UIImagePickerController.isSourceTypeAvailable(camera)) return
            val picker = UIImagePickerController()
            picker.sourceType = camera
            picker.delegate = this
            top.presentViewController(picker, animated = true, completion = null)
        } else {
            val config = PHPickerConfiguration()
            config.filter = PHPickerFilter.imagesFilter
            config.selectionLimit = 1
            val picker = PHPickerViewController(configuration = config)
            picker.delegate = this
            top.presentViewController(picker, animated = true, completion = null)
        }
    }

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val result = didFinishPicking.firstOrNull() as? PHPickerResult ?: return
        result.itemProvider.loadDataRepresentationForTypeIdentifier("public.image") { data, _ ->
            val image = data?.let { UIImage(data = it) } ?: return@loadDataRepresentationForTypeIdentifier
            val jpeg = shrink(image) ?: return@loadDataRepresentationForTypeIdentifier
            dispatch_async(dispatch_get_main_queue()) { onPicked(jpeg) }
        }
    }

    override fun imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo: Map<Any?, *>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage ?: return
        shrink(image)?.let(onPicked)
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
    }
}

private fun topController(): UIViewController? {
    var top = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (top?.presentedViewController != null) top = top.presentedViewController
    return top
}

/** 긴 변 [PHOTO_MAX_EDGE] 로 줄이고(방향 반영) JPEG 85. 렌더러 배율은 1(픽셀 = 포인트). */
private fun shrink(image: UIImage): ByteArray? {
    val (w, h) = image.size.useContents { width to height }
    if (w <= 0.0 || h <= 0.0) return null
    val k = minOf(1.0, PHOTO_MAX_EDGE / maxOf(w, h))
    val format = UIGraphicsImageRendererFormat.defaultFormat()
    format.scale = 1.0
    val renderer = UIGraphicsImageRenderer(size = CGSizeMake(w * k, h * k), format = format)
    val scaled = renderer.imageWithActions { image.drawInRect(CGRectMake(0.0, 0.0, w * k, h * k)) }
    return UIImageJPEGRepresentation(scaled, 0.85)?.toByteArray()
}

private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val out = ByteArray(size)
    if (size > 0) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}

@Composable
internal actual fun rememberImageShare(): (ByteArray) -> Unit = remember {
    { png ->
        val data = png.usePinned { NSData.create(bytes = it.addressOf(0), length = png.size.toULong()) }
        val controller = platform.UIKit.UIActivityViewController(activityItems = listOf(UIImage(data = data)), applicationActivities = null)
        topController()?.presentViewController(controller, animated = true, completion = null)
    }
}

@Composable
internal actual fun rememberImageSaver(): suspend (ByteArray) -> Boolean = remember {
    { png ->
        val data = png.usePinned { NSData.create(bytes = it.addressOf(0), length = png.size.toULong()) }
        val image = UIImage(data = data)
        UIImageWriteToSavedPhotosAlbum(image, null, null, null)
        true
    }
}
