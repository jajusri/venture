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
        val send = buildSendIntent(pdf)
        if (send.resolveActivity(context.packageManager) == null) {
            releasePdf(pdf)
            return LedgerStatementShareResult.Failure("No app is available to share PDF files.")
        }
        protectSharedFile(pdf)
        return LedgerStatementShareResult.Success(Intent.createChooser(send, "Share ledger statement PDF"))
    }

    override fun createWhatsAppShareIntent(pdf: PreparedLedgerStatementPdf): LedgerStatementShareResult<Intent> {
        val send = buildSendIntent(pdf).apply { setPackage(WHATSAPP_PACKAGE) }
        if (send.resolveActivity(context.packageManager) == null) {
            releasePdf(pdf)
            return LedgerStatementShareResult.Failure("WhatsApp is not installed on this device.")
        }
        protectSharedFile(pdf)
        return LedgerStatementShareResult.Success(send)
    }

    override fun createPreviewIntent(pdf: PreparedLedgerStatementPdf): LedgerStatementShareResult<Intent> {
        val contentUri = Uri.parse(pdf.contentUri)
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, PDF_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (view.resolveActivity(context.packageManager) == null) {
            releasePdf(pdf)
            return LedgerStatementShareResult.Failure("No app is available to preview PDF files.")
        }
        protectSharedFile(pdf)
        return LedgerStatementShareResult.Success(view)
    }

    private fun buildSendIntent(pdf: PreparedLedgerStatementPdf): Intent {
        val contentUri = Uri.parse(pdf.contentUri)
        return Intent(Intent.ACTION_SEND).apply {
            type = PDF_MIME
            putExtra(Intent.EXTRA_STREAM, contentUri)
            clipData = ClipData.newUri(context.contentResolver, pdf.suggestedFilename, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun protectSharedFile(pdf: PreparedLedgerStatementPdf) {
        val file = File(pdf.cacheFilePath)
        cachePolicy.protectShared(file, System.currentTimeMillis())
        cachePolicy.release(file)
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
        const val WHATSAPP_PACKAGE = "com.whatsapp"
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
    private const val HEADING_LINE_HEIGHT = 10.5f
    private const val ITEM_LINE_HEIGHT = 9f
    private const val TOTAL_LINE_HEIGHT = 10f
    private const val NARRATION_LINE_HEIGHT = 9f

    // Date | Particulars | Debit | Credit | Balance
    private val columnEdges = floatArrayOf(MARGIN, 96f, 360f, 430f, 495f, CONTENT_RIGHT)

    // Item Name | Qty | Rate | Amount — sub-columns inside the Particulars column only.
    private val itemColumnEdges = floatArrayOf(96f, 210f, 250f, 300f, 360f)

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
        // Smaller than voucher-identity text, per the locked visual rule.
        val item = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 6.5f; color = android.graphics.Color.rgb(60, 60, 60) }
        val itemBold = Paint(item).apply { typeface = Typeface.DEFAULT_BOLD }
        val itemTotal = Paint(itemBold).apply { textSize = 7.5f; textAlign = Paint.Align.RIGHT }
        val narrationPaint = Paint(item).apply { textSize = 7f; color = android.graphics.Color.rgb(90, 90, 90) }

        val headerLines = buildHeaderLines(companyName, statement) { text -> wrapToWidth(text, CONTENT_RIGHT - MARGIN, subHeading) }
        val firstHeaderTop = 40f + headerLines.size * 14f + 10f
        val positioned = planParticularsLines(
            transactions = statement.transactions,
            firstPageStartY = firstHeaderTop + HEADER_ROW_HEIGHT,
            continuationStartY = MARGIN + HEADER_ROW_HEIGHT,
            bottom = TABLE_BOTTOM,
            headingWrap = { text -> wrapToWidth(text, columnEdges[2] - columnEdges[1] - 10f, bold) },
            narrationWrap = { text -> wrapToWidth(text, columnEdges[2] - columnEdges[1] - 10f, narrationPaint) },
            itemNameWrap = { text -> wrapToWidth(text, itemColumnEdges[1] - itemColumnEdges[0] - 6f, item) },
        )
        val totalPages = positioned.maxOfOrNull(PositionedLine::page) ?: 1
        // (transaction, page) -> the vertical extent of that transaction's content on that page,
        // for drawing the bounding column dividers and, on the transaction's very first segment
        // only, the voucher-level Date/Debit/Credit/Balance values aligned with the whole block.
        val segments = positioned.groupBy { it.transaction to it.page }
            .mapValues { (_, lines) -> lines.minOf(PositionedLine::top) to lines.maxOf(PositionedLine::bottom) }

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
                val onThisPage = positioned.filter { it.page == pageNumber }
                onThisPage.groupBy { it.transaction }.forEach { (transaction, lines) ->
                    val (segTop, segBottom) = segments.getValue(transaction to pageNumber)
                    drawTransactionSegment(canvas, transaction, lines, segTop, segBottom, body, item, itemBold, itemTotal, narrationPaint, rule)
                }

                if (pageNumber == totalPages) {
                    val lastBottom = onThisPage.maxOfOrNull(PositionedLine::bottom) ?: (headerTop + HEADER_ROW_HEIGHT)
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

    /**
     * Draws one transaction's content for its extent on one page: the outer column dividers and
     * bottom rule bound the whole [segTop]..[segBottom] block, but Date/Debit/Credit/Balance are
     * only ever drawn once per transaction — at [PositionedLine.isFirstOfTransaction] — so they
     * stay aligned with the complete voucher block rather than repeating per Particulars line
     * (never per item, per the locked rule).
     */
    private fun drawTransactionSegment(
        canvas: android.graphics.Canvas,
        transaction: LedgerStatementTransaction,
        lines: List<PositionedLine>,
        segTop: Float,
        segBottom: Float,
        body: Paint,
        item: Paint,
        itemBold: Paint,
        itemTotal: Paint,
        narrationPaint: Paint,
        rule: Paint,
    ) {
        columnEdges.forEach { x -> canvas.drawLine(x, segTop, x, segBottom, rule) }
        canvas.drawLine(MARGIN, segBottom, CONTENT_RIGHT, segBottom, rule)

        val accountingBaseline = lines.firstOrNull { it.isFirstOfTransaction }?.let { it.top + 8f }
        if (accountingBaseline != null) {
            drawCell(canvas, transaction.date, 0, accountingBaseline, body, Paint.Align.CENTER)
            drawCell(canvas, transaction.debit.orEmpty(), 2, accountingBaseline, body, Paint.Align.RIGHT)
            drawCell(canvas, transaction.credit.orEmpty(), 3, accountingBaseline, body, Paint.Align.RIGHT)
            drawCell(canvas, transaction.runningBalance.toLabel().orEmpty(), 4, accountingBaseline, body, Paint.Align.RIGHT)
        }

        lines.forEach { positioned -> drawParticularsLine(canvas, positioned, body, item, itemBold, itemTotal, narrationPaint) }
    }

    private fun drawParticularsLine(
        canvas: android.graphics.Canvas,
        positioned: PositionedLine,
        body: Paint,
        item: Paint,
        itemBold: Paint,
        itemTotal: Paint,
        narrationPaint: Paint,
    ) {
        val baseline = positioned.top + positioned.line.height - 2f
        when (val line = positioned.line) {
            is HeadingLine -> canvas.drawText(line.text, columnEdges[1] + 4f, baseline, Paint(body).apply { typeface = Typeface.DEFAULT_BOLD })
            is ItemHeaderLine -> line.labels.forEachIndexed { index, label -> drawItemCell(canvas, label, index, baseline, itemBold) }
            is ItemRowLine -> {
                drawItemCell(canvas, line.name, 0, baseline, item)
                line.quantity?.let { drawItemCell(canvas, it, 1, baseline, item) }
                line.rate?.let { drawItemCell(canvas, it, 2, baseline, item) }
                line.amount?.let { drawItemCell(canvas, it, 3, baseline, item) }
            }
            is TotalTextLine -> canvas.drawText(line.text, columnEdges[2] - 4f, baseline, itemTotal)
            is NarrationTextLine -> canvas.drawText(line.text, columnEdges[1] + 4f, baseline, narrationPaint)
        }
    }

    private fun drawItemCell(canvas: android.graphics.Canvas, text: String, subColumn: Int, baseline: Float, paint: Paint) {
        if (text.isBlank()) return
        val left = itemColumnEdges[subColumn]
        val right = itemColumnEdges[subColumn + 1]
        val align = if (subColumn == 0) Paint.Align.LEFT else Paint.Align.RIGHT
        val copy = Paint(paint).apply { textAlign = align }
        val x = if (align == Paint.Align.LEFT) left + 2f else right - 2f
        canvas.drawText(text, x, baseline, copy)
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

    // internal (not private): lets a JVM unit test exercise the pagination algorithm directly
    // with fake wrap functions — these types/functions never touch Paint/Canvas/PdfDocument
    // themselves, only the wrap lambdas render() supplies, so nothing here requires an Android
    // graphics runtime to test.
    internal sealed interface PlannedLine { val height: Float }
    internal data class HeadingLine(val text: String) : PlannedLine { override val height = HEADING_LINE_HEIGHT }
    internal data class ItemHeaderLine(val labels: List<String>) : PlannedLine { override val height = ITEM_LINE_HEIGHT }
    internal data class ItemRowLine(val name: String, val quantity: String?, val rate: String?, val amount: String?) :
        PlannedLine { override val height = ITEM_LINE_HEIGHT }
    internal data class TotalTextLine(val text: String) : PlannedLine { override val height = TOTAL_LINE_HEIGHT }
    internal data class NarrationTextLine(val text: String) : PlannedLine { override val height = NARRATION_LINE_HEIGHT }

    internal data class PositionedLine(
        val page: Int,
        val top: Float,
        val bottom: Float,
        val line: PlannedLine,
        val transaction: LedgerStatementTransaction,
        val isFirstOfTransaction: Boolean,
    )

    /** Wraps [block]'s content into concrete, still-unpositioned [PlannedLine]s. Item rows whose
     * name wraps to multiple lines only carry qty/rate/amount on the first of those lines. */
    internal fun planLinesForBlock(
        block: ParticularsBlock,
        headingWrap: (String) -> List<String>,
        narrationWrap: (String) -> List<String>,
        itemNameWrap: (String) -> List<String>,
    ): List<PlannedLine> = buildList {
        headingWrap(block.headingText).forEach { add(HeadingLine(it)) }
        block.itemHeaderLabels?.let { add(ItemHeaderLine(it)) }
        block.itemRows.forEach { row ->
            val nameLines = itemNameWrap(row.name).ifEmpty { listOf("") }
            nameLines.forEachIndexed { index, nameLine ->
                add(
                    ItemRowLine(
                        name = nameLine,
                        quantity = if (index == 0) row.quantity else null,
                        rate = if (index == 0) row.rate else null,
                        amount = if (index == 0) row.amount else null,
                    ),
                )
            }
        }
        block.totalText?.let { add(TotalTextLine(it)) }
        block.narrationText?.let { text -> narrationWrap(text).forEach { add(NarrationTextLine(it)) } }
    }

    /**
     * Places every transaction's [ParticularsBlock] lines on the page grid. A whole block is kept
     * together whenever it fits either where the cursor already is or on a fresh page — this is
     * what "avoid splitting an individual item row awkwardly" means in practice, and it is the
     * only path taken for any statement this task's tests exercise. Only a single voucher block
     * too tall to fit even one entire fresh page (an extreme case — dozens of item lines) ever
     * falls into line-by-line placement, which never splits inside a single line and reprints the
     * voucher identity as "(contd.)" at the top of each continuation page so context is never
     * lost — see "allow a large voucher to continue across pages if unavoidable."
     */
    internal fun planParticularsLines(
        transactions: List<LedgerStatementTransaction>,
        firstPageStartY: Float,
        continuationStartY: Float,
        bottom: Float,
        headingWrap: (String) -> List<String>,
        narrationWrap: (String) -> List<String>,
        itemNameWrap: (String) -> List<String>,
    ): List<PositionedLine> {
        var page = 1
        var y = firstPageStartY
        val positioned = mutableListOf<PositionedLine>()

        for (transaction in transactions) {
            val block = buildParticularsBlock(transaction)
            val lines = planLinesForBlock(block, headingWrap, narrationWrap, itemNameWrap)
            val blockHeight = lines.sumOf { it.height.toDouble() }.toFloat()
            val freshPageCapacity = bottom - continuationStartY
            if (y + blockHeight > bottom && blockHeight <= freshPageCapacity) {
                page++
                y = continuationStartY
            }

            var isFirstLineOfTransaction = true
            for (line in lines) {
                if (y + line.height > bottom) {
                    page++
                    y = continuationStartY
                    // Mid-block page break (the extreme-case path): reprint voucher identity so
                    // the continuation is never orphaned from its context.
                    positioned += PositionedLine(page, y, y + HEADING_LINE_HEIGHT, HeadingLine("${block.headingText} (contd.)"), transaction, isFirstOfTransaction = false)
                    y += HEADING_LINE_HEIGHT
                }
                positioned += PositionedLine(page, y, y + line.height, line, transaction, isFirstOfTransaction = isFirstLineOfTransaction)
                y += line.height
                isFirstLineOfTransaction = false
            }
        }
        return positioned
    }
}
