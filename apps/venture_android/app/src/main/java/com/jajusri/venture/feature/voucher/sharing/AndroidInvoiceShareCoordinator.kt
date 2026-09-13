package com.jajusri.venture.feature.voucher.sharing

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.jajusri.venture.core.util.DispatcherProvider
import com.jajusri.venture.feature.voucher.domain.model.VoucherDetails
import com.jajusri.venture.feature.voucher.presentation.formatVoucherDate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidInvoiceShareCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val cachePolicy: InvoiceShareCachePolicy,
) : InvoiceShareCoordinator {

    override suspend fun preparePdf(details: VoucherDetails): InvoiceShareResult<PreparedInvoicePdf> =
        withContext(dispatchers.io) {
            if (!details.isShareableInvoice()) {
                return@withContext InvoiceShareResult.Failure("This voucher is not an available complete sales invoice.")
            }
            runCatching {
                val directory = File(context.cacheDir, InvoiceShareCachePolicy.CACHE_DIRECTORY)
                check(cachePolicy.acceptsDirectory(directory))
                check(directory.exists() || directory.mkdirs())
                check(cachePolicy.acceptsDirectory(directory))
                val filename = "${UUID.randomUUID()}-${sanitizedInvoiceFilename(details.summary.number.orEmpty())}"
                val file = File(directory, filename)
                cachePolicy.acquire(file)
                try {
                    cachePolicy.cleanup(directory, System.currentTimeMillis())
                    InvoicePdfRenderer.render(details, file)
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.invoice-files", file)
                    PreparedInvoicePdf(uri.toString(), file.absolutePath, sanitizedInvoiceFilename(details.summary.number.orEmpty()))
                } catch (failure: Throwable) {
                    cachePolicy.release(file)
                    cachePolicy.discard(file)
                    throw failure
                }
            }.fold(
                onSuccess = { InvoiceShareResult.Success(it) },
                onFailure = { InvoiceShareResult.Failure("Invoice PDF could not be generated. Please try again.") },
            )
        }

    override fun createPdfShareIntent(pdf: PreparedInvoicePdf): InvoiceShareResult<Intent> {
        val contentUri = Uri.parse(pdf.contentUri)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = PDF_MIME
            putExtra(Intent.EXTRA_STREAM, contentUri)
            clipData = ClipData.newUri(context.contentResolver, pdf.suggestedFilename, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (send.resolveActivity(context.packageManager) == null) {
            releasePdf(pdf)
            return InvoiceShareResult.Failure("No app is available to share PDF files.")
        }
        val file = File(pdf.cacheFilePath)
        cachePolicy.protectShared(file, System.currentTimeMillis())
        cachePolicy.release(file)
        return InvoiceShareResult.Success(Intent.createChooser(send, "Share invoice PDF"))
    }

    override fun createSummaryShareIntent(
        details: VoucherDetails,
        companyName: String?,
    ): InvoiceShareResult<Intent> {
        if (!details.isShareableInvoice()) {
            return InvoiceShareResult.Failure("This voucher is not an available complete sales invoice.")
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, buildInvoiceSummary(details, companyName))
        }
        if (send.resolveActivity(context.packageManager) == null) {
            return InvoiceShareResult.Failure("No app is available to share text.")
        }
        return InvoiceShareResult.Success(Intent.createChooser(send, "Share invoice summary"))
    }

    override suspend fun savePdf(
        pdf: PreparedInvoicePdf,
        destination: Uri,
    ): InvoiceShareResult<Unit> = withContext(dispatchers.io) {
        val source = File(pdf.cacheFilePath)
        try {
            runCatching {
                require(cachePolicy.isManagedFile(source) && source.isFile)
                context.contentResolver.openOutputStream(destination, "w").use { output ->
                    requireNotNull(output)
                    source.inputStream().use { input -> input.copyTo(output) }
                }
            }.fold(
                onSuccess = { InvoiceShareResult.Success(Unit) },
                onFailure = { InvoiceShareResult.Failure("Invoice PDF could not be saved. Please choose another location.") },
            )
        } finally {
            cachePolicy.release(source)
        }
    }

    override fun releasePdf(pdf: PreparedInvoicePdf) {
        cachePolicy.release(File(pdf.cacheFilePath))
    }

    private companion object {
        const val PDF_MIME = "application/pdf"
    }
}

internal object InvoicePdfRenderer {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 36f
    private const val CONTENT_RIGHT = 559f
    private const val TABLE_BOTTOM = 758f
    private const val FOOTER_TOP = 782f
    private const val HEADER_HEIGHT = 24f
    private const val SUMMARY_HEIGHT = 28f

    private val columnEdges = floatArrayOf(MARGIN, 76f, 292f, 340f, 390f, 470f, CONTENT_RIGHT)

    fun render(details: VoucherDetails, output: File) {
        val document = PdfDocument()
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8f; color = android.graphics.Color.rgb(35, 35, 35) }
        val bold = Paint(body).apply { typeface = Typeface.DEFAULT_BOLD }
        val heading = Paint(bold).apply { textSize = 18f; textAlign = Paint.Align.CENTER }
        val total = Paint(bold).apply { textSize = 10f; textAlign = Paint.Align.RIGHT }
        val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(210, 210, 210)
            strokeWidth = 0.65f
        }

        val voucherLines = voucherHeaderLines(details)
        val buyerLines = wrapEstimateText("${EstimatePdfText.BUYER_LABEL}: ${details.summary.partyName.orEmpty()}", 52).take(2)
        val firstHeaderTop = 77f + maxOf(voucherLines.size, buyerLines.size).coerceAtLeast(1) * 11f
        val rows = planEstimateItems(
            details.inventoryEntries,
            firstHeaderTop + HEADER_HEIGHT,
            MARGIN + HEADER_HEIGHT,
            TABLE_BOTTOM,
        ) { description -> wrapEstimateTextToWidth(description, columnEdges[2] - columnEdges[1] - 10f, body) }
        val totalPages = rows.maxOfOrNull(EstimateItemLayout::page) ?: 1

        try {
            (1..totalPages).forEach { pageNumber ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                val canvas = page.canvas
                val headerTop = if (pageNumber == 1) firstHeaderTop else MARGIN

                if (pageNumber == 1) {
                    canvas.drawText(EstimatePdfText.HEADING, PAGE_WIDTH / 2f, 50f, heading)
                    voucherLines.forEachIndexed { index, text -> canvas.drawText(text, MARGIN, 75f + index * 11f, bold) }
                    buyerLines.forEachIndexed { index, text ->
                        val aligned = Paint(if (index == 0) bold else body).apply { textAlign = Paint.Align.RIGHT }
                        canvas.drawText(text, CONTENT_RIGHT, 75f + index * 11f, aligned)
                    }
                }

                drawTableHeader(canvas, headerTop, bold, rule)
                rows.filter { it.page == pageNumber }.forEach { row -> drawEstimateRow(canvas, row, body, rule) }

                if (pageNumber == totalPages) {
                    val lastBottom = rows.lastOrNull()?.bottom ?: (headerTop + HEADER_HEIGHT)
                    drawSummary(canvas, lastBottom + 8f, details.narration, details.summary.amount?.value, body, total, rule)
                }

                drawFooter(canvas, pageNumber, totalPages, body, rule)
                document.finishPage(page)
            }
            output.outputStream().use(document::writeTo)
        } finally {
            document.close()
        }
    }

    private fun drawTableHeader(canvas: android.graphics.Canvas, top: Float, bold: Paint, rule: Paint) {
        canvas.drawLine(MARGIN, top, CONTENT_RIGHT, top, rule)
        canvas.drawLine(MARGIN, top + HEADER_HEIGHT, CONTENT_RIGHT, top + HEADER_HEIGHT, rule)
        columnEdges.forEach { x -> canvas.drawLine(x, top, x, top + HEADER_HEIGHT, rule) }
        val baseline = top + 15f
        EstimatePdfText.COLUMNS.forEachIndexed { index, label ->
            val align = when (index) {
                0, 3 -> Paint.Align.CENTER
                1 -> Paint.Align.LEFT
                else -> Paint.Align.RIGHT
            }
            drawCell(canvas, label, index, baseline, bold, align)
        }
    }

    private fun drawEstimateRow(
        canvas: android.graphics.Canvas,
        row: EstimateItemLayout,
        body: Paint,
        rule: Paint,
    ) {
        columnEdges.forEach { x -> canvas.drawLine(x, row.top, x, row.bottom, rule) }
        canvas.drawLine(MARGIN, row.bottom, CONTENT_RIGHT, row.bottom, rule)
        val baseline = row.top + 13f
        drawCell(canvas, row.item.lineNumber.toString(), 0, baseline, body, Paint.Align.CENTER)
        row.descriptionLines.forEachIndexed { index, text -> drawCell(canvas, text, 1, baseline + index * 11f, body, Paint.Align.LEFT) }
        val quantity = splitEstimateQuantity(row.item.quantity)
        drawCell(canvas, quantity.first, 2, baseline, body, Paint.Align.RIGHT)
        drawCell(canvas, quantity.second, 3, baseline, body, Paint.Align.CENTER)
        drawCell(canvas, estimateRate(row.item.rate, quantity.second), 4, baseline, body, Paint.Align.RIGHT)
        drawCell(canvas, row.item.amount?.value.orEmpty(), 5, baseline, body, Paint.Align.RIGHT)
    }

    private fun drawCell(canvas: android.graphics.Canvas, text: String, column: Int, baseline: Float, paint: Paint, align: Paint.Align) {
        if (text.isBlank()) return
        val copy = Paint(paint).apply { textAlign = align }
        val left = columnEdges[column]
        val right = columnEdges[column + 1]
        val x = when (align) {
            Paint.Align.LEFT -> left + 5f
            Paint.Align.CENTER -> (left + right) / 2f
            Paint.Align.RIGHT -> right - 5f
        }
        canvas.drawText(text, x, baseline, copy)
    }

    private fun drawSummary(
        canvas: android.graphics.Canvas,
        top: Float,
        narration: String?,
        amount: String?,
        body: Paint,
        totalPaint: Paint,
        rule: Paint,
    ) {
        canvas.drawLine(MARGIN, top, CONTENT_RIGHT, top, rule)
        canvas.drawLine(MARGIN, top + SUMMARY_HEIGHT, CONTENT_RIGHT, top + SUMMARY_HEIGHT, rule)
        val summary = planEstimateSummary(narration, amount, top)
        val totalText = summary.totalText
        val totalWidth = if (totalText.isBlank()) 0f else totalPaint.measureText(totalText)
        val narrationRight = (CONTENT_RIGHT - totalWidth - 18f).coerceAtLeast(MARGIN + 120f)
        summary.narrationText?.let {
            val fitted = fitSingleLine(it, narrationRight - MARGIN - 8f, body)
            canvas.drawText(fitted.first, MARGIN + 4f, top + 18f, fitted.second)
        }
        if (totalText.isNotBlank()) canvas.drawText(totalText, CONTENT_RIGHT - 5f, top + 19f, totalPaint)
    }

    private fun drawFooter(canvas: android.graphics.Canvas, page: Int, pages: Int, body: Paint, rule: Paint) {
        canvas.drawLine(MARGIN, FOOTER_TOP, CONTENT_RIGHT, FOOTER_TOP, rule)
        val footer = Paint(body).apply { color = android.graphics.Color.rgb(90, 90, 90) }
        canvas.drawText(EstimatePdfText.FOOTER_REVIEW, MARGIN, FOOTER_TOP + 12f, footer)
        canvas.drawText(EstimatePdfText.FOOTER_SOURCE, MARGIN, FOOTER_TOP + 23f, footer)
        canvas.drawText(EstimatePdfText.pageLabel(page, pages), CONTENT_RIGHT, FOOTER_TOP + 23f, Paint(footer).apply { textAlign = Paint.Align.RIGHT })
    }

    private fun fitSingleLine(text: String, maxWidth: Float, source: Paint): Pair<String, Paint> {
        val paint = Paint(source)
        while (paint.textSize > 6.5f && paint.measureText(text) > maxWidth) paint.textSize -= 0.5f
        if (paint.measureText(text) <= maxWidth) return text to paint
        val ellipsis = "..."
        var end = text.length
        while (end > 0 && paint.measureText(text.substring(0, end).trimEnd() + ellipsis) > maxWidth) end--
        return (text.substring(0, end).trimEnd() + ellipsis) to paint
    }

    private fun wrapEstimateTextToWidth(text: String, maxWidth: Float, paint: Paint): List<String> {
        if (text.isBlank()) return listOf("")
        val lines = mutableListOf<String>()
        var remaining = text.trim()
        while (remaining.isNotEmpty()) {
            val count = paint.breakText(remaining, true, maxWidth, null).coerceAtLeast(1)
            if (count == remaining.length) {
                lines += remaining
                break
            }
            val proposed = remaining.substring(0, count)
            val breakAt = proposed.lastIndexOf(' ').takeIf { it > 0 } ?: count
            lines += remaining.substring(0, breakAt).trimEnd()
            remaining = remaining.substring(breakAt).trimStart()
        }
        return lines
    }
}

internal object EstimatePdfText {
    const val HEADING = "ESTIMATE"
    const val VOUCHER_LABEL = "Est. Voucher No."
    const val DATE_LABEL = "Date"
    const val BUYER_LABEL = "Est. To"
    const val NARRATION_LABEL = "Narration"
    const val TOTAL_LABEL = "TOTAL"
    const val FOOTER_REVIEW = "For review and reference only. Original invoice accompanies the goods."
    const val FOOTER_SOURCE = "Generated from synchronized Tally data in VENTURE."
    val COLUMNS = listOf("Sl. No.", "Item Description", "Qty", "Unit", "Rate", "Amount")
    fun pageLabel(page: Int, pages: Int): String = "Page $page of $pages"
}

internal data class EstimateSummaryLayout(
    val narrationText: String?,
    val totalText: String,
    val top: Float,
    val bottom: Float,
)

internal fun planEstimateSummary(narration: String?, amount: String?, top: Float): EstimateSummaryLayout =
    EstimateSummaryLayout(
        narrationText = narration?.trim()?.takeIf(String::isNotBlank)?.replace(Regex("\\s+"), " ")
            ?.let { "${EstimatePdfText.NARRATION_LABEL}: $it" },
        totalText = amount?.trim()?.takeIf(String::isNotBlank)?.let { "${EstimatePdfText.TOTAL_LABEL}: $it" }.orEmpty(),
        top = top,
        bottom = top + 28f,
    )

internal data class EstimateItemLayout(
    val page: Int,
    val top: Float,
    val bottom: Float,
    val descriptionLines: List<String>,
    val item: com.jajusri.venture.feature.voucher.domain.model.VoucherInventoryLine,
)

internal fun planEstimateItems(
    items: List<com.jajusri.venture.feature.voucher.domain.model.VoucherInventoryLine>,
    firstPageStartY: Float,
    continuationStartY: Float,
    bottom: Float,
    descriptionWrapper: (String) -> List<String> = { wrapEstimateText(it, 44) },
): List<EstimateItemLayout> {
    var page = 1
    var y = firstPageStartY
    return items.mapIndexed { index, item ->
        val description = descriptionWrapper(item.itemName)
        val height = maxOf(24f, description.size * 11f + 10f)
        val reserveSummary = if (index == items.lastIndex) 8f + 28f else 0f
        if (y + height + reserveSummary > bottom) {
            page++
            y = continuationStartY
        }
        EstimateItemLayout(page, y, y + height, description, item).also { y += height }
    }
}

/**
 * Voucher No. and Date lines for the PDF header. Date is this Voucher's own authoritative date
 * ([VoucherDetails.summary]`.date`, sourced from Room, never the device clock/PDF-creation/sync
 * timestamp) — a back-dated Voucher renders its own historical date here, unchanged by when the
 * PDF happens to be generated. A pure function of [details] so it's testable without a Canvas.
 */
internal fun voucherHeaderLines(details: VoucherDetails): List<String> = (
    wrapEstimateText("${EstimatePdfText.VOUCHER_LABEL}: ${details.summary.number.orEmpty()}", 52) +
        wrapEstimateText("${EstimatePdfText.DATE_LABEL}: ${formatVoucherDate(details.summary.date)}", 52)
    ).take(3)

internal fun wrapEstimateText(text: String, maxChars: Int): List<String> {
    if (text.isBlank()) return listOf("")
    val lines = mutableListOf<String>()
    var current = ""
    text.trim().split(Regex("\\s+")).forEach { word ->
        if (word.length > maxChars) {
            if (current.isNotBlank()) lines += current
            word.chunked(maxChars).let { chunks ->
                lines += chunks.dropLast(1)
                current = chunks.last()
            }
        } else {
            val candidate = if (current.isBlank()) word else "$current $word"
            if (candidate.length <= maxChars) current = candidate else {
                lines += current
                current = word
            }
        }
    }
    if (current.isNotBlank()) lines += current
    return lines.ifEmpty { listOf("") }
}

internal fun splitEstimateQuantity(value: String?): Pair<String, String> {
    val normalized = value?.trim().orEmpty()
    if (normalized.isBlank()) return "" to ""
    val split = normalized.lastIndexOf(' ')
    return if (split > 0 && split < normalized.lastIndex) {
        normalized.substring(0, split).trim() to normalized.substring(split + 1).trim()
    } else normalized to ""
}

internal fun estimateRate(value: String?, unit: String): String {
    val normalized = value?.trim().orEmpty()
    if (unit.isNotBlank() && normalized.endsWith("/$unit", ignoreCase = true)) {
        return normalized.dropLast(unit.length + 1).trim()
    }
    return normalized
}
