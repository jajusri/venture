package com.jajusri.venture.feature.masterdata.ledger.sharing

import com.jajusri.venture.feature.masterdata.ledger.domain.model.LedgerStatementItemDetail
import com.jajusri.venture.feature.masterdata.ledger.domain.model.LedgerStatementItemLine
import com.jajusri.venture.feature.masterdata.ledger.domain.model.LedgerStatementTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises [LedgerStatementPdfRenderer.planParticularsLines] directly — it only ever calls the
 * wrap lambdas passed to it, never touching Paint/Canvas/PdfDocument itself, so a plain identity
 * wrap function is enough to prove the pagination algorithm without an Android graphics runtime.
 * This is the strongest available deterministic proof of "no clipped/dropped item, never split
 * inside a single line, large-voucher continuation still shows identity" without instantiating
 * a real PDF.
 */
class LedgerStatementPdfPaginationTest {

    private val noWrap: (String) -> List<String> = { text -> listOf(text) }

    private fun transactionWithItems(voucherId: String, itemCount: Int) = LedgerStatementTransaction(
        voucherId = voucherId,
        date = "2026-08-12",
        voucherType = "Sales",
        voucherNumber = voucherId,
        referenceNumber = null,
        narration = null,
        debit = "100",
        credit = null,
        runningBalance = null,
        itemDetail = LedgerStatementItemDetail(
            items = (1..itemCount).map { n -> LedgerStatementItemLine("Item $n", "$n Nos", "₹10.00", "₹${n * 10}.00") },
            totalLabel = "₹100.00",
        ),
    )

    private fun plainTransaction(voucherId: String) = LedgerStatementTransaction(
        voucherId = voucherId,
        date = "2026-08-12",
        voucherType = "Receipt",
        voucherNumber = voucherId,
        referenceNumber = null,
        narration = null,
        debit = null,
        credit = "50",
        runningBalance = null,
        itemDetail = null,
    )

    @Test
    fun `a small statement fits entirely on one page`() {
        val positioned = LedgerStatementPdfRenderer.planParticularsLines(
            transactions = listOf(plainTransaction("r1"), plainTransaction("r2")),
            firstPageStartY = 0f,
            continuationStartY = 0f,
            bottom = 800f,
            headingWrap = noWrap,
            narrationWrap = noWrap,
            itemNameWrap = noWrap,
        )
        assertEquals(1, positioned.maxOf { it.page })
    }

    @Test
    fun `a whole voucher block moves to a fresh page rather than splitting when it does not fit where the cursor is`() {
        val transactions = listOf(transactionWithItems("v1", itemCount = 3), transactionWithItems("v2", itemCount = 3))
        // Tight enough that the second voucher's block cannot fit after the first on page 1, but
        // each individual block easily fits a full fresh page.
        val positioned = LedgerStatementPdfRenderer.planParticularsLines(
            transactions = transactions,
            firstPageStartY = 0f,
            continuationStartY = 0f,
            bottom = 60f,
            headingWrap = noWrap,
            narrationWrap = noWrap,
            itemNameWrap = noWrap,
        )
        val v1Pages = positioned.filter { it.transaction.voucherId == "v1" }.map { it.page }.toSet()
        val v2Pages = positioned.filter { it.transaction.voucherId == "v2" }.map { it.page }.toSet()
        assertEquals("v1's block must stay entirely on one page", 1, v1Pages.size)
        assertEquals("v2's block must stay entirely on one page", 1, v2Pages.size)
        assertTrue("v2 must have moved to a later page than v1, not split across both", v2Pages.first() > v1Pages.first())
    }

    @Test
    fun `all item lines are represented in the positioned output, none dropped`() {
        val transaction = transactionWithItems("v1", itemCount = 25)
        val positioned = LedgerStatementPdfRenderer.planParticularsLines(
            transactions = listOf(transaction),
            firstPageStartY = 0f,
            continuationStartY = 0f,
            bottom = 40f, // deliberately tiny — forces the rare line-split path
            headingWrap = noWrap,
            narrationWrap = noWrap,
            itemNameWrap = noWrap,
        )
        val itemLineCount = positioned.count { it.line is LedgerStatementPdfRenderer.ItemRowLine }
        assertEquals("every one of the 25 items must appear exactly once, never dropped", 25, itemLineCount)
    }

    @Test
    fun `an oversized single voucher splits across multiple pages and reprints its identity on continuation`() {
        val transaction = transactionWithItems("v1", itemCount = 25)
        val positioned = LedgerStatementPdfRenderer.planParticularsLines(
            transactions = listOf(transaction),
            firstPageStartY = 0f,
            continuationStartY = 0f,
            bottom = 40f,
            headingWrap = noWrap,
            narrationWrap = noWrap,
            itemNameWrap = noWrap,
        )
        val pagesUsed = positioned.map { it.page }.distinct()
        assertTrue("25 items at a 40pt page bound must not fit on one page", pagesUsed.size > 1)

        val contdHeadings = positioned.count {
            it.line is LedgerStatementPdfRenderer.HeadingLine && (it.line as LedgerStatementPdfRenderer.HeadingLine).text.endsWith("(contd.)")
        }
        assertEquals("one contd. heading per page break after the first", pagesUsed.size - 1, contdHeadings)
    }

    @Test
    fun `no line is ever split mid-content — every positioned line's text is one of the original planned lines`() {
        val transaction = transactionWithItems("v1", itemCount = 25)
        val block = buildParticularsBlock(transaction)
        val originalLines = LedgerStatementPdfRenderer.planLinesForBlock(block, noWrap, noWrap, noWrap)
        val originalNames = originalLines.filterIsInstance<LedgerStatementPdfRenderer.ItemRowLine>().map { it.name }.toSet()

        val positioned = LedgerStatementPdfRenderer.planParticularsLines(
            transactions = listOf(transaction),
            firstPageStartY = 0f,
            continuationStartY = 0f,
            bottom = 40f,
            headingWrap = noWrap,
            narrationWrap = noWrap,
            itemNameWrap = noWrap,
        )
        val positionedNames = positioned.mapNotNull { (it.line as? LedgerStatementPdfRenderer.ItemRowLine)?.name }.toSet()
        assertEquals(originalNames, positionedNames)
    }

    @Test
    fun `Date Debit Credit Balance are anchored exactly once per transaction, at its first line`() {
        val positioned = LedgerStatementPdfRenderer.planParticularsLines(
            transactions = listOf(transactionWithItems("v1", itemCount = 3)),
            firstPageStartY = 0f,
            continuationStartY = 0f,
            bottom = 800f,
            headingWrap = noWrap,
            narrationWrap = noWrap,
            itemNameWrap = noWrap,
        )
        assertEquals(1, positioned.count { it.isFirstOfTransaction })
    }
}
