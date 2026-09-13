package com.jajusri.venture.feature.voucher.sharing

import com.jajusri.venture.feature.voucher.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InvoiceShareContentTest {
    @Test
    fun `filename sanitization prevents path traversal and invalid characters`() {
        assertEquals("VENTURE-Invoice-2026-INV-1.pdf", sanitizedInvoiceFilename("../../2026:INV/1"))
        assertFalse(sanitizedInvoiceFilename("../../x").contains(".."))
        assertFalse(sanitizedInvoiceFilename("../../x").contains('/'))
    }

    @Test
    fun `summary contains only available canonical fields and excludes internals`() {
        val text = details().let { buildInvoiceSummary(it, "Venture-Test-01") }
        assertTrue(text.contains("Venture-Test-01"))
        assertTrue(text.contains("Invoice: S-1"))
        assertTrue(text.contains("Party: Acme"))
        assertTrue(text.contains("Total: 100.00"))
        assertFalse(text.contains("v-internal"))
        assertFalse(text.contains("http"))
    }

    @Test
    fun `summary omits missing optional fields`() {
        val value = details().copy(summary = details().summary.copy(partyName = null, amount = null))
        val text = buildInvoiceSummary(value, null)
        assertFalse(text.contains("Party:"))
        assertFalse(text.contains("Total:"))
        assertTrue(text.endsWith("Shared from VENTURE"))
    }

    @Test
    fun `only complete active sales voucher with number is eligible`() {
        assertTrue(details().isShareableInvoice())
        assertFalse(details().copy(summary = details().summary.copy(type = "Payment")).isShareableInvoice())
        assertFalse(details().copy(summary = details().summary.copy(status = VoucherStatus.Cancelled)).isShareableInvoice())
        assertFalse(details().copy(summary = details().summary.copy(dataQuality = VoucherDataQuality.Incomplete)).isShareableInvoice())
        assertFalse(details().copy(summary = details().summary.copy(number = null)).isShareableInvoice())
    }

    @Test
    fun `ineligible vouchers expose a concise reason and eligible vouchers expose none`() {
        assertEquals(null, details().shareIneligibilityReason())
        assertTrue(details().copy(summary = details().summary.copy(type = "Payment")).shareIneligibilityReason()!!.isNotBlank())
        assertTrue(details().copy(summary = details().summary.copy(status = VoucherStatus.Cancelled)).shareIneligibilityReason()!!.isNotBlank())
        assertTrue(details().copy(summary = details().summary.copy(dataQuality = VoucherDataQuality.Incomplete)).shareIneligibilityReason()!!.isNotBlank())
        assertTrue(details().copy(summary = details().summary.copy(number = null)).shareIneligibilityReason()!!.isNotBlank())
    }

    @Test
    fun `voucher date is rendered in the PDF header`() {
        val lines = voucherHeaderLines(details())
        assertTrue(lines.any { it.contains("Date: 27 Jul 2026") })
    }

    @Test
    fun `historical back-dated voucher shows its own date, not any other`() {
        val backDated = details().copy(summary = details().summary.copy(date = "2020-01-15"))
        val lines = voucherHeaderLines(backDated)
        assertTrue(lines.any { it.contains("Date: 15 Jan 2020") })
        assertFalse(lines.any { it.contains("2026") })
    }

    @Test
    fun `voucher date rendering is independent of the current device date`() {
        // No clock/current-time input exists anywhere in voucherHeaderLines' signature — this
        // test documents that invariant: the only date-shaped input is the Voucher's own.
        val lines = voucherHeaderLines(details())
        assertEquals(lines, voucherHeaderLines(details()))
    }

    @Test
    fun `resynced voucher whose authoritative date changed renders the new date`() {
        val original = voucherHeaderLines(details())
        val resynced = voucherHeaderLines(details().copy(summary = details().summary.copy(date = "2026-10-01")))
        assertTrue(original.any { it.contains("Date: 27 Jul 2026") })
        assertTrue(resynced.any { it.contains("Date: 01 Oct 2026") })
        assertFalse(resynced.any { it.contains("27 Jul 2026") })
    }

    @Test
    fun `estimate uses exact heading labels columns and footer`() {
        assertEquals("ESTIMATE", EstimatePdfText.HEADING)
        assertEquals("Est. Voucher No.", EstimatePdfText.VOUCHER_LABEL)
        assertEquals("Date", EstimatePdfText.DATE_LABEL)
        assertEquals("Est. To", EstimatePdfText.BUYER_LABEL)
        assertEquals(listOf("Sl. No.", "Item Description", "Qty", "Unit", "Rate", "Amount"), EstimatePdfText.COLUMNS)
        assertEquals(6, EstimatePdfText.COLUMNS.size)
        assertFalse(EstimatePdfText.HEADING.contains("invoice", ignoreCase = true))
        assertFalse(EstimatePdfText.HEADING.contains("logo", ignoreCase = true))
        assertEquals("For review and reference only. Original invoice accompanies the goods.", EstimatePdfText.FOOTER_REVIEW)
        assertEquals("Generated from synchronized Tally data in VENTURE.", EstimatePdfText.FOOTER_SOURCE)
        assertEquals("Page 2 of 4", EstimatePdfText.pageLabel(2, 4))
    }

    @Test
    fun `quantity unit and rate come only from canonical item values`() {
        assertEquals("2.50" to "PCS", splitEstimateQuantity("2.50 PCS"))
        assertEquals("2.50" to "", splitEstimateQuantity("2.50"))
        assertEquals("50.00", estimateRate("50.00/PCS", "PCS"))
        assertEquals("50.00/BOX", estimateRate("50.00/BOX", "PCS"))
        assertEquals("" to "", splitEstimateQuantity(null))
    }

    @Test
    fun `long descriptions wrap without losing text`() {
        val description = "A deliberately long synchronized item description that must wrap safely across multiple compact lines"
        val lines = wrapEstimateText(description, 24)
        assertTrue(lines.size > 1)
        assertTrue(lines.all { it.length <= 24 })
        assertEquals(description.split(Regex("\\s+")).joinToString(""), lines.joinToString("").replace(" ", ""))
    }

    @Test
    fun `long estimate plan preserves every item once in order and reserves final summary`() {
        val items = (1..120).map {
            VoucherInventoryLine(it, "Item $it with a deterministic synchronized description", "$it PCS", "10/PCS", VoucherMoney("$it.00", null))
        }
        val plan = planEstimateItems(items, firstPageStartY = 125f, continuationStartY = 60f, bottom = 758f)
        assertEquals((1..120).toList(), plan.map { it.item.lineNumber })
        assertEquals(120, plan.map { it.item.lineNumber }.distinct().size)
        assertTrue(plan.zipWithNext().all { (first, second) -> second.page >= first.page })
        assertTrue(plan.maxOf { it.page } > 1)
        assertTrue(plan.all { it.top < it.bottom && it.bottom <= 758f })
        assertTrue(plan.last().bottom + 8f + 28f <= 758f)
    }

    @Test
    fun `summary keeps narration and total on one fixed-height row`() {
        val summary = planEstimateSummary("  Deliver   before noon\nHandle carefully  ", "100.00", 700f)
        assertEquals("Narration: Deliver before noon Handle carefully", summary.narrationText)
        assertFalse(summary.narrationText!!.contains('\n'))
        assertEquals("TOTAL: 100.00", summary.totalText)
        assertEquals(28f, summary.bottom - summary.top)
    }

    @Test
    fun `blank narration omits its label without affecting total`() {
        val summary = planEstimateSummary("  ", "100.00", 700f)
        assertEquals(null, summary.narrationText)
        assertEquals("TOTAL: 100.00", summary.totalText)
    }

    @Test
    fun `long narration remains a single logical line and cannot alter total`() {
        val narration = (1..80).joinToString(" ") { "canonical$it" }
        val summary = planEstimateSummary(narration, "1234567890.00", 700f)
        assertFalse(summary.narrationText!!.contains('\n'))
        assertEquals("TOTAL: 1234567890.00", summary.totalText)
        assertEquals(28f, summary.bottom - summary.top)
    }

    private fun details() = VoucherDetails(
        summary = VoucherSummary(
            VoucherIdentity("v-internal"), "2026-07-27", "Sales", "S-1", "Acme", null,
            VoucherMoney("100.00", VoucherMoneySide.Debit), VoucherStatus.Active, VoucherDataQuality.Complete,
        ),
        effectiveDate = null,
        narration = null,
        ledgerEntries = emptyList(),
        inventoryEntries = emptyList(),
    )
}
