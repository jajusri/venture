package com.jajusri.venture.core.pdf

import android.graphics.pdf.PdfDocument
import androidx.test.platform.app.InstrumentationRegistry
import com.jajusri.venture.core.util.DefaultDispatcherProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * TD-028: exercises [AndroidPdfPageRenderer] against real, locally-generated PDF files using the
 * platform's own `android.graphics.pdf.PdfRenderer` — the read-side counterpart to the
 * `PdfDocument` writer this test uses to build its fixtures, matching what the app's own Voucher/
 * Ledger PDF generation already produces.
 */
class PdfPageRendererTest {
    private val renderer = AndroidPdfPageRenderer(DefaultDispatcherProvider())
    private val cacheDir = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir

    private fun writePdf(name: String, pageCount: Int): File {
        val document = PdfDocument()
        repeat(pageCount) { index ->
            val page = document.startPage(PdfDocument.PageInfo.Builder(200, 300, index + 1).create())
            page.canvas.drawText("Page ${index + 1}", 20f, 20f, android.graphics.Paint())
            document.finishPage(page)
        }
        val file = File(cacheDir, name)
        file.outputStream().use(document::writeTo)
        document.close()
        return file
    }

    @Test
    fun opensARealSinglePagePdfAndReportsCorrectPageCount(): Unit = runBlocking {
        val file = writePdf("single-page.pdf", pageCount = 1)

        val document = renderer.open(file.absolutePath)

        assertNotNull(document)
        assertEquals(1, document!!.pageCount)
        document.close()
        file.delete()
    }

    // I — multipage document path.
    @Test
    fun opensARealMultiPagePdfAndRendersEveryPage(): Unit = runBlocking {
        val file = writePdf("multi-page.pdf", pageCount = 3)

        val document = renderer.open(file.absolutePath)!!
        assertEquals(3, document.pageCount)
        for (index in 0 until document.pageCount) {
            val bitmap = document.renderPage(index, targetWidthPx = 100)
            assertNotNull("page $index should render", bitmap)
            assertEquals(100, bitmap!!.width)
            assertTrue(bitmap.height > 0)
        }
        document.close()
        file.delete()
    }

    // K — missing/unreadable file handled, never throws.
    @Test
    fun openReturnsNullForAMissingFileRatherThanThrowing(): Unit = runBlocking {
        val document = renderer.open(File(cacheDir, "does-not-exist-${System.nanoTime()}.pdf").absolutePath)

        assertNull(document)
    }

    @Test
    fun openReturnsNullForAFileThatIsNotAValidPdf(): Unit = runBlocking {
        val file = File(cacheDir, "not-a-pdf.pdf").apply { writeText("this is not a pdf") }

        val document = renderer.open(file.absolutePath)

        assertNull(document)
        file.delete()
    }

    @Test
    fun renderPageReturnsNullForAnOutOfRangeIndexRatherThanThrowing(): Unit = runBlocking {
        val file = writePdf("bounds-check.pdf", pageCount = 1)
        val document = renderer.open(file.absolutePath)!!

        val bitmap = document.renderPage(5, targetWidthPx = 100)

        assertNull(bitmap)
        document.close()
        file.delete()
    }

    @Test
    fun closeIsSafeToCallMoreThanOnce(): Unit = runBlocking {
        val file = writePdf("double-close.pdf", pageCount = 1)
        val document = renderer.open(file.absolutePath)!!

        document.close()
        document.close()

        file.delete()
    }

    @Test
    fun renderingAfterCloseReturnsNullRatherThanCrashing(): Unit = runBlocking {
        val file = writePdf("render-after-close.pdf", pageCount = 1)
        val document = renderer.open(file.absolutePath)!!
        document.close()

        val bitmap = document.renderPage(0, targetWidthPx = 100)

        assertNull(bitmap)
        file.delete()
    }
}
