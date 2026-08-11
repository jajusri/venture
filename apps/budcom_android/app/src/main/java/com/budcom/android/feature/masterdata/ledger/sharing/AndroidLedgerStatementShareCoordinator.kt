package com.budcom.android.feature.masterdata.ledger.sharing

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.budcom.android.core.util.DispatcherProvider
import com.budcom.android.core.util.TimeProvider
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatement
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatementTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import java.io.File

@Singleton
class AndroidLedgerStatementShareCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val cachePolicy: LedgerStatementShareCachePolicy,
    private val timeProvider: TimeProvider,
) : LedgerStatementShareCoordinator {

    override suspend fun preparePdf(
        statement: LedgerStatement,
        companyName: String?,
    ): LedgerStatementShareResult<PreparedLedgerStatementPdf> = withContext(dispatchers.io) {
        runCatching {
            val directory = File(context.cacheDir, LedgerStatementShareCachePolicy.CACHE_DIRECTORY)
            check(cachePolicy.acceptsDirectory(directory))
            check(directory.exists() || directory.mkdirs())
            check(cachePolicy.acceptsDirectory(directory))
            val suggestedName = sanitizedLedgerStatementFilename(statement.ledgerName, statement.period.from, statement.period.to)
            val filename = "${UUID.randomUUID()}-$suggestedName"
            val file = File(directory, filename)
            cachePolicy.acquire(file)
            try {
                cachePolicy.cleanup(directory, System.currentTimeMillis())
                LedgerStatementPdfRenderer.render(statement, companyName, timeProvider.nowEpochMillis(), file)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.invoice-files", file)
                PreparedLedgerStatementPdf(uri.toString(), file.absolutePath, suggestedName)
            } catch (failure: Throwable) {
                cachePolicy.release(file)
                cachePolicy.discard(file)
                throw failure
            }
        }.fold(
            onSuccess = { LedgerStatementShareResult.Success(it) },
            onFailure = { LedgerStatementShareResult.Failure("Ledger statement PDF could not be generated. Please try again.") },
        )
    }

    override fun createPdfShareIntent(pdf: PreparedLedgerStatementPdf): LedgerStatementShareResult<Intent> {
        val contentUri = Uri.parse(pdf.contentUri)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = PDF_MIME
            putExtra(Intent.EXTRA_STREAM, contentUri)
            clipData = ClipData.newUri(context.contentResolver, pdf.suggestedFilename, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (send.resolveActivity(context.packageManager) == null) {
            releasePdf(pdf)
            return LedgerStatementShareResult.Failure("No app is available to share PDF files.")
        }
        val file = File(pdf.cacheFilePath)
        cachePolicy.protectShared(file, System.currentTimeMillis())
        cachePolicy.release(file)
        return LedgerStatementShareResult.Success(Intent.createChooser(send, "Share ledger statement PDF"))
    }

    override suspend fun savePdf(
        pdf: PreparedLedgerStatementPdf,
        destination: Uri,
    ): LedgerStatementShareResult<Unit> = withContext(dispatchers.io) {
        val source = File(pdf.cacheFilePath)
        try {
            runCatching {
                require(cachePolicy.isManagedFile(source) && source.isFile)
                context.contentResolver.openOutputStream(destination, "w").use { output ->
                    requireNotNull(output)
                    source.inputStream().use { input -> input.copyTo(output) }
                }
            }.fold(
                onSuccess = { LedgerStatementShareResult.Success(Unit) },
                onFailure = { LedgerStatementShareResult.Failure("Ledger statement PDF could not be saved. Please choose another location.") },
            )
        } finally {
            cachePolicy.release(source)
        }
    }

    override fun releasePdf(pdf: PreparedLedgerStatementPdf) {
        cachePolicy.release(File(pdf.cacheFilePath))
    }

    private companion object {
        const val PDF_MIME = "application/pdf"
    }
}

/**
 * Compact, professional Ledger statement PDF: party/period header, opening balance, a
 * Date/Particulars/Vch Type-No/Debit/Credit/Balance table in accounting order, closing balance,
 * and an honest coverage note when the statement is not fully reconciled. Every transaction row
 * uses the statement's own [LedgerStatementTransaction.date] — never the device clock or PDF
 * generation time, which is shown separately and labeled explicitly as a generation timestamp.
 */
internal object LedgerStatementPdfRenderer {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 36f
    private const val CONTENT_RIGHT = 559f
    private const val TABLE_BOTTOM = 770f
    private const val FOOTER_TOP = 792f
    private const val HEADER_ROW_HEIGHT = 20f

    // Date | Particulars | Debit | Credit | Balance
    private val columnEdges = floatArrayOf(MARGIN, 96f, 360f, 430f, 495f, CONTENT_RIGHT)

    fun render(statement: LedgerStatement, companyName: String?, generatedAtEpochMillis: Long, output: File) {
        val document = PdfDocument()
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8f; color = android.graphics.Color.rgb(35, 35, 35) }
        val bold = Paint(body).apply { typeface = Typeface.DEFAULT_BOLD }
        val heading = Paint(bold).apply { textSize = 16f; textAlign = Paint.Align.CENTER }
        val subHeading = Paint(bold).apply { textSize = 11f; textAlign = Paint.Align.CENTER }
        val totalPaint = Paint(bold).apply { textSize = 9f; textAlign = Paint.Align.RIGHT }
        val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(210, 210, 210)
            strokeWidth = 0.65f
        }

        val headerLines = buildHeaderLines(companyName, statement) { text -> wrapToWidth(text, CONTENT_RIGHT - MARGIN, subHeading) }
        val firstHeaderTop = 40f + headerLines.size * 14f + 10f
        val rows = planStatementRows(statement.transactions, firstHeaderTop + HEADER_ROW_HEIGHT, MARGIN + HEADER_ROW_HEIGHT, TABLE_BOTTOM) { text ->
            wrapToWidth(text, columnEdges[2] - columnEdges[1] - 10f, body)
        }
        val totalPages = rows.maxOfOrNull(StatementRowLayout::page) ?: 1

        try {
            (1..totalPages).forEach { pageNumber ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                val canvas = page.canvas
                val headerTop = if (pageNumber == 1) firstHeaderTop else MARGIN

                if (pageNumber == 1) {
                    canvas.drawText("BUDCOM LEDGER STATEMENT", PAGE_WIDTH / 2f, 24f, heading)
                    headerLines.forEachIndexed { index, line ->
                        canvas.drawText(line, PAGE_WIDTH / 2f, 40f + index * 14f, subHeading)
                    }
                }

                drawTableHeader(canvas, headerTop, bold, rule)
                rows.filter { it.page == pageNumber }.forEach { row -> drawRow(canvas, row, body, rule) }

                if (pageNumber == totalPages) {
                    val lastBottom = rows.lastOrNull()?.bottom ?: (headerTop + HEADER_ROW_HEIGHT)
                    drawClosingSummary(canvas, lastBottom + 6f, statement, totalPaint, body, rule)
                }

                drawFooter(canvas, pageNumber, totalPages, generatedAtEpochMillis, body, rule)
                document.finishPage(page)
            }
            output.outputStream().use(document::writeTo)
        } finally {
            document.close()
        }
    }

    private fun buildHeaderLines(
        companyName: String?,
        statement: LedgerStatement,
        wrap: (String) -> List<String>,
    ): List<String> = buildList {
        companyName?.trim()?.takeIf(String::isNotBlank)?.let { addAll(wrap(it)) }
        addAll(wrap(statement.ledgerName + (statement.parentGroup?.let { " ($it)" } ?: "")))
        add("Statement period: ${statement.period.from} to ${statement.period.to}")
        add("Opening balance: ${statement.openingBalance.toLabel() ?: "Not available"}")
    }

    private fun drawTableHeader(canvas: android.graphics.Canvas, top: Float, bold: Paint, rule: Paint) {
        canvas.drawLine(MARGIN, top, CONTENT_RIGHT, top, rule)
        canvas.drawLine(MARGIN, top + HEADER_ROW_HEIGHT, CONTENT_RIGHT, top + HEADER_ROW_HEIGHT, rule)
        columnEdges.forEach { x -> canvas.drawLine(x, top, x, top + HEADER_ROW_HEIGHT, rule) }
        val baseline = top + 13f
        val labels = listOf("Date", "Particulars", "Debit", "Credit", "Balance")
        labels.forEachIndexed { index, label ->
            val align = if (index == 1) Paint.Align.LEFT else if (index == 0) Paint.Align.CENTER else Paint.Align.RIGHT
            drawCell(canvas, label, index, baseline, bold, align)
        }
    }

    private fun drawRow(canvas: android.graphics.Canvas, row: StatementRowLayout, body: Paint, rule: Paint) {
        columnEdges.forEach { x -> canvas.drawLine(x, row.top, x, row.bottom, rule) }
        canvas.drawLine(MARGIN, row.bottom, CONTENT_RIGHT, row.bottom, rule)
        val baseline = row.top + 12f
        drawCell(canvas, row.transaction.date, 0, baseline, body, Paint.Align.CENTER)
        row.particularsLines.forEachIndexed { index, text -> drawCell(canvas, text, 1, baseline + index * 10.5f, body, Paint.Align.LEFT) }
        drawCell(canvas, row.transaction.debit.orEmpty(), 2, baseline, body, Paint.Align.RIGHT)
        drawCell(canvas, row.transaction.credit.orEmpty(), 3, baseline, body, Paint.Align.RIGHT)
        drawCell(canvas, row.transaction.runningBalance.toLabel().orEmpty(), 4, baseline, body, Paint.Align.RIGHT)
    }

    private fun drawCell(canvas: android.graphics.Canvas, text: String, column: Int, baseline: Float, paint: Paint, align: Paint.Align) {
        if (text.isBlank()) return
        val copy = Paint(paint).apply { textAlign = align }
        val left = columnEdges[column]
        val right = columnEdges[column + 1]
        val x = when (align) {
            Paint.Align.LEFT -> left + 4f
            Paint.Align.CENTER -> (left + right) / 2f
            Paint.Align.RIGHT -> right - 4f
        }
        canvas.drawText(text, x, baseline, copy)
    }

    private fun drawClosingSummary(
        canvas: android.graphics.Canvas,
        top: Float,
        statement: LedgerStatement,
        totalPaint: Paint,
        body: Paint,
        rule: Paint,
    ) {
        canvas.drawLine(MARGIN, top, CONTENT_RIGHT, top, rule)
        canvas.drawText(
            "Closing balance: ${statement.closingBalance.toLabel() ?: "Not available"}",
            CONTENT_RIGHT,
            top + 14f,
            totalPaint,
        )
        val message = statement.coverage.message
        if (!message.isNullOrBlank()) {
            val note = Paint(body).apply { textSize = 7f; color = android.graphics.Color.rgb(140, 90, 20) }
            wrapToWidth(message, CONTENT_RIGHT - MARGIN, note).take(3).forEachIndexed { index, line ->
                canvas.drawText(line, MARGIN, top + 28f + index * 9f, note)
            }
        }
    }

    private fun drawFooter(
        canvas: android.graphics.Canvas,
        page: Int,
        pages: Int,
        generatedAtEpochMillis: Long,
        body: Paint,
        rule: Paint,
    ) {
        canvas.drawLine(MARGIN, FOOTER_TOP, CONTENT_RIGHT, FOOTER_TOP, rule)
        val footer = Paint(body).apply { color = android.graphics.Color.rgb(90, 90, 90); textSize = 7f }
        canvas.drawText(
            "Generated ${formatGeneratedAt(generatedAtEpochMillis)} from synchronized Tally data in BUDCOM.",
            MARGIN,
            FOOTER_TOP + 12f,
            footer,
        )
        canvas.drawText(
            "Page $page of $pages",
            CONTENT_RIGHT,
            FOOTER_TOP + 12f,
            Paint(footer).apply { textAlign = Paint.Align.RIGHT },
        )
    }

    private fun formatGeneratedAt(epochMillis: Long): String {
        val formatter = SimpleDateFormat("dd-MMM-yyyy HH:mm 'IST'", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        return formatter.format(Date(epochMillis))
    }

    private fun com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatementAmount?.toLabel(): String? {
        val value = this ?: return null
        return "${value.amount} ${value.side.name}"
    }

    private fun wrapToWidth(text: String, maxWidth: Float, paint: Paint): List<String> {
        if (text.isBlank()) return listOf("")
        val lines = mutableListOf<String>()
        var remaining = text.trim().replace(Regex("\\s+"), " ")
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

    private data class StatementRowLayout(
        val page: Int,
        val top: Float,
        val bottom: Float,
        val particularsLines: List<String>,
        val transaction: LedgerStatementTransaction,
    )

    private fun planStatementRows(
        transactions: List<LedgerStatementTransaction>,
        firstPageStartY: Float,
        continuationStartY: Float,
        bottom: Float,
        particularsWrapper: (String) -> List<String>,
    ): List<StatementRowLayout> {
        var page = 1
        var y = firstPageStartY
        return transactions.map { transaction ->
            val particulars = particularsWrapper(transaction.toParticulars())
            val height = maxOf(20f, particulars.size * 10.5f + 6f)
            if (y + height > bottom) {
                page++
                y = continuationStartY
            }
            StatementRowLayout(page, y, y + height, particulars, transaction).also { y += height }
        }
    }

    private fun LedgerStatementTransaction.toParticulars(): String {
        val head = listOfNotNull(voucherType, voucherNumber?.takeIf(String::isNotBlank)?.let { "No. $it" }).joinToString(" · ")
        val ref = referenceNumber?.takeIf(String::isNotBlank)?.let { "Ref: $it" }
        val note = narration?.takeIf(String::isNotBlank)
        return listOfNotNull(head.takeIf(String::isNotBlank), ref, note).joinToString(" — ")
    }
}
