package com.sadturtleman.flowershoes

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sadturtleman.flowershoes.core.compression.Zstd
import com.sadturtleman.flowershoes.core.drawing.CanvasSpec
import com.sadturtleman.flowershoes.core.drawing.DrawingCodec
import com.sadturtleman.flowershoes.core.drawing.DrawingDocument
import com.sadturtleman.flowershoes.core.drawing.WidgetShape
import com.sadturtleman.flowershoes.core.media.DrawingBitmap
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DrawingDecodeTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun malformedFramesReturnUnreadableAndKeepOriginalFile() {
        val valid = DrawingCodec.encodeForStorage(DrawingDocument(canvas = CanvasSpec(WidgetShape.SQUARE), elements = emptyList(), totalDurationMs = 0))
        val frames = listOf(
            Zstd.MAGIC + byteArrayOf(0x7f, 1, 2),
            Zstd.codec!!.compress(valid, 3).dropLast(1).toByteArray(),
            Zstd.codec!!.compress(ByteArray(DrawingCodec.MAX_DOCUMENT_BYTES + 1), 3),
            ByteArray(DrawingCodec.MAX_DOCUMENT_BYTES + 1),
        )
        for (bytes in frames) {
            val file = File.createTempFile("drawing-invalid-", ".kkd", context.cacheDir)
            try {
                file.writeBytes(bytes)
                assertEquals(DrawingCodec.Result.Unreadable, DrawingCodec.decode(bytes))
                assertNull(DrawingBitmap.render(file.absolutePath, 128))
                assertArrayEquals(bytes, file.readBytes())
            } finally { file.delete() }
        }
    }

    @Test fun normalSavedDocumentStillRendersAfterARejectedFrame() {
        val file = File.createTempFile("drawing-normal-", ".kkd", context.cacheDir)
        try {
            file.writeBytes(Zstd.MAGIC + byteArrayOf(0x7f, 1, 2))
            assertNull(DrawingBitmap.render(file.absolutePath, 128))
            val doc = DrawingDocument(canvas = CanvasSpec(WidgetShape.SQUARE, backgroundArgb = 0xffff0000), elements = emptyList(), totalDurationMs = 0)
            file.writeBytes(DrawingCodec.encodeForStorage(doc))
            DrawingBitmap.install(context)
            val bitmap = DrawingBitmap.render(file.absolutePath, 128)
            assertNotNull(bitmap)
            assertEquals(android.graphics.Color.RED, bitmap!!.getPixel(64, 64))
            bitmap.recycle()
        } finally { file.delete() }
    }
}
