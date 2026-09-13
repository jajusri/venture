package com.jajusri.venture.core.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.jajusri.venture.core.util.DispatcherProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TD-028: renders a locally-generated PDF file to page bitmaps for in-app preview. Backed by the
 * platform's own `android.graphics.pdf.PdfRenderer` (API 21+, part of the Android SDK, already
 * the read-side counterpart to `android.graphics.pdf.PdfDocument` this app already uses to
 * generate PDFs) — no new dependency, no APK size impact, no network/cloud involvement.
 */
interface PdfPageRenderer {
    /** Opens [filePath] for rendering, or null if the file cannot be opened as a PDF. */
    suspend fun open(filePath: String): PdfPreviewDocument?
}

/** A single open PDF document. Callers must [close] it when done (e.g. leaving the preview
 * screen) — this holds a file descriptor for as long as it stays open. */
interface PdfPreviewDocument {
    val pageCount: Int

    /** Renders page [index] (0-based) scaled to [targetWidthPx] wide, preserving aspect ratio.
     * Pages are rendered one at a time internally even under concurrent calls — `PdfRenderer`
     * only ever allows one open page per document. Returns null if [index] is out of range or
     * rendering fails. */
    suspend fun renderPage(index: Int, targetWidthPx: Int): Bitmap?

    fun close()
}

@Singleton
class AndroidPdfPageRenderer @Inject constructor(
    private val dispatchers: DispatcherProvider,
) : PdfPageRenderer {

    override suspend fun open(filePath: String): PdfPreviewDocument? = withContext(dispatchers.io) {
        runCatching {
            val file = File(filePath)
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            AndroidPdfPreviewDocument(PdfRenderer(pfd), pfd, dispatchers)
        }.getOrNull()
    }
}

private class AndroidPdfPreviewDocument(
    private val renderer: PdfRenderer,
    private val pfd: ParcelFileDescriptor,
    private val dispatchers: DispatcherProvider,
) : PdfPreviewDocument {
    private val mutex = Mutex()
    private var closed = false

    override val pageCount: Int get() = renderer.pageCount

    override suspend fun renderPage(index: Int, targetWidthPx: Int): Bitmap? = withContext(dispatchers.default) {
        mutex.withLock {
            if (closed || index !in 0 until renderer.pageCount || targetWidthPx <= 0) return@withLock null
            runCatching {
                renderer.openPage(index).use { page ->
                    val scale = targetWidthPx.toFloat() / page.width
                    val height = (page.height * scale).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(targetWidthPx, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }.getOrNull()
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { renderer.close() }
        runCatching { pfd.close() }
    }
}
