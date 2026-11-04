package com.sadturtleman.flowershoes

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sadturtleman.flowershoes.feature.memory.BookPdf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookPdfPlatformTest {
    private fun writer(): BookPdf = Class.forName("com.sadturtleman.flowershoes.app.Photos_androidKt")
        .getDeclaredMethod("newBookPdf").invoke(null) as BookPdf

    @Test fun createsTwoReadablePdfsWithAllPages() = runBlocking {
        val pdf = writer()
        for (color in listOf(Color.RED, Color.BLUE)) {
            pdf.add(Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }.asImageBitmap())
        }
        val files = pdf.finish()
        assertEquals(2, files.size)
        files.forEachIndexed { index, (_, bytes) ->
            val file = java.io.File.createTempFile("book-test-", ".pdf", InstrumentationRegistry.getInstrumentation().targetContext.cacheDir)
            try {
                file.writeBytes(bytes)
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        assertEquals(2, renderer.pageCount)
                        repeat(2) { pageIndex ->
                            renderer.openPage(pageIndex).use { page ->
                                assertEquals(if (index == 0) 576 else 593, page.width)
                                assertEquals(page.width, page.height)
                                val rendered = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
                                page.render(rendered, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                assertEquals(if (pageIndex == 0) Color.RED else Color.BLUE, rendered.getPixel(20, 20))
                                rendered.recycle()
                            }
                        }
                    }
                }
            } finally { file.delete() }
        }
    }

    @Test fun abortIsIdempotentAndPreventsFurtherWrites() {
        val pdf = writer()
        pdf.abort()
        pdf.abort()
        assertTrue(runCatching { pdf.add(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).asImageBitmap()) }.isFailure)
    }
}
