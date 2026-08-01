package com.budcom.android.feature.voucher.sharing

import com.budcom.android.feature.voucher.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InvoiceShareContentTest {
    @Test
    fun `filename sanitization prevents path traversal and invalid characters`() {
        assertEquals("BUDCOM-Invoice-2026-INV-1.pdf", sanitizedInvoiceFilename("../../2026:INV/1"))
        assertFalse(sanitizedInvoiceFilename("../../x").contains(".."))
        assertFalse(sanitizedInvoiceFilename("../../x").contains('/'))
    }

    @Test
    fun `summary contains only available canonical fields and excludes internals`() {
        val text = details().let { buildInvoiceSummary(it, "Budcom-Test-01") }
        assertTrue(text.contains("Budcom-Test-01"))
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
        assertTrue(text.endsWith("Shared from BUDCOM"))
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
    fun `long invoice render plan preserves every item exactly once and in order`() {
        val items = (1..120).map {
            VoucherInventoryLine(it, "Item $it with a deterministic synchronized description", "$it PCS", "10/PCS", VoucherMoney("$it.00", null))
        }
        val plan = planInvoiceItems(items, startingY = 180f, bottom = 800f, continuationStartY = 60f)
        assertEquals((1..120).toList(), plan.map { it.item.lineNumber })
        assertEquals(120, plan.map { it.item.lineNumber }.distinct().size)
        assertTrue(plan.zipWithNext().all { (first, second) -> second.page >= first.page })
        assertTrue(plan.filter { it.startsNewPage }.all { it.page > 1 })
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
