package com.budcom.android.feature.voucher.sharing

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.budcom.android.core.util.DispatcherProvider
import com.budcom.android.feature.voucher.domain.model.VoucherDetails
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
    private const val MARGIN = 42f
    private const val BOTTOM = 800f

    fun render(details: VoucherDetails, output: File) {
        val document = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = android.graphics.Color.BLACK }
        val bold = Paint(paint).apply { typeface = Typeface.DEFAULT_BOLD }
        var pageNumber = 0
        var page: PdfDocument.Page? = null
        var y = MARGIN

        fun startPage(repeatHeader: Boolean) {
            page?.let { current ->
                current.canvas.drawText("Page $pageNumber", PAGE_WIDTH - 82f, PAGE_HEIGHT - 24f, paint)
                document.finishPage(current)
            }
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            y = MARGIN
            if (repeatHeader) {
                page!!.canvas.drawText("Item", MARGIN, y, bold)
                page!!.canvas.drawText("Quantity / Rate", 300f, y, bold)
                page!!.canvas.drawText("Amount", 485f, y, bold)
                y += 18f
            }
        }

        fun line(text: String, x: Float = MARGIN, style: Paint = paint, spacing: Float = 16f) {
            if (y + spacing > BOTTOM) startPage(false)
            page!!.canvas.drawText(text.take(110), x, y, style)
            y += spacing
        }

        fun wrapped(text: String, x: Float, maxChars: Int, style: Paint = paint) {
            text.chunked(maxChars).forEach { line(it, x, style) }
        }

        try {
            startPage(false)
            line("SALES INVOICE", style = Paint(bold).apply { textSize = 18f }, spacing = 28f)
            line("Generated from synchronized Tally data; not the original statutory invoice.", spacing = 22f)
            line("Invoice number: ${details.summary.number}", style = bold)
            line("Invoice date: ${details.summary.date}")
            details.summary.partyName?.takeIf { it.isNotBlank() }?.let { wrapped("Party: $it", MARGIN, 85) }
            details.summary.referenceNumber?.takeIf { it.isNotBlank() }?.let { wrapped("Reference: $it", MARGIN, 85) }
            y += 10f
            line("Item", MARGIN, bold)
            page!!.canvas.drawText("Quantity / Rate", 300f, y - 16f, bold)
            page!!.canvas.drawText("Amount", 485f, y - 16f, bold)
            planInvoiceItems(details.inventoryEntries, y, BOTTOM, MARGIN + 18f).forEach { planned ->
                val item = planned.item
                if (planned.startsNewPage) startPage(true)
                wrapped("${item.lineNumber}. ${item.itemName}", MARGIN, 42)
                val quantityRate = listOfNotNull(item.quantity, item.rate).filter { it.isNotBlank() }.joinToString(" @ ")
                if (quantityRate.isNotBlank()) line(quantityRate, 300f)
                item.amount?.value?.takeIf { it.isNotBlank() }?.let { line(it, 485f) }
                y += 6f
            }
            y += 8f
            details.summary.amount?.value?.takeIf { it.isNotBlank() }?.let { line("Invoice total: $it", style = bold) }
            details.narration?.takeIf { it.isNotBlank() }?.let {
                y += 8f
                line("Narration", style = bold)
                wrapped(it, MARGIN, 90)
            }
            page?.let { current ->
                current.canvas.drawText("Page $pageNumber", PAGE_WIDTH - 82f, PAGE_HEIGHT - 24f, paint)
                document.finishPage(current)
            }
            output.outputStream().use(document::writeTo)
        } finally {
            document.close()
        }
    }
}

internal data class PlannedInvoiceItem(
    val page: Int,
    val startsNewPage: Boolean,
    val item: com.budcom.android.feature.voucher.domain.model.VoucherInventoryLine,
)

internal fun planInvoiceItems(
    items: List<com.budcom.android.feature.voucher.domain.model.VoucherInventoryLine>,
    startingY: Float,
    bottom: Float,
    continuationStartY: Float,
): List<PlannedInvoiceItem> {
    var page = 1
    var y = startingY
    return items.map { item ->
        val nameLines = (("${item.lineNumber}. ${item.itemName}".length + 41) / 42).coerceAtLeast(1)
        val quantityRateLines = if (listOfNotNull(item.quantity, item.rate).any { it.isNotBlank() }) 1 else 0
        val amountLines = if (item.amount?.value?.isNotBlank() == true) 1 else 0
        val height = (nameLines + quantityRateLines + amountLines) * 16f + 6f
        val newPage = y + height > bottom
        if (newPage) {
            page++
            y = continuationStartY
        }
        PlannedInvoiceItem(page, newPage, item).also { y += height }
    }
}
