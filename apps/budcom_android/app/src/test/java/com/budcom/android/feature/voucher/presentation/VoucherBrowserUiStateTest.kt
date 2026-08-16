package com.budcom.android.feature.voucher.presentation

import com.budcom.android.feature.voucher.domain.model.VoucherDataQuality
import com.budcom.android.feature.voucher.domain.model.VoucherIdentity
import com.budcom.android.feature.voucher.domain.model.VoucherMoney
import com.budcom.android.feature.voucher.domain.model.VoucherMoneySide
import com.budcom.android.feature.voucher.domain.model.VoucherStatus
import com.budcom.android.feature.voucher.domain.model.VoucherSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoucherBrowserUiStateTest {

    private fun state(
        status: VoucherHistoryReconciliationStatus,
        scopeIsAuthoritative: Boolean? = null,
    ) = VoucherBrowserUiState(
        historyReconciliationStatus = status,
        historyReconciliationScopeIsAuthoritative = scopeIsAuthoritative,
    )

    private fun summary(
        number: String? = "S-1",
        partyName: String? = "Acme Traders",
        referenceNumber: String? = "PO-99",
        amount: VoucherMoney? = VoucherMoney("100.00", VoucherMoneySide.Debit),
        status: VoucherStatus = VoucherStatus.Active,
    ) = VoucherSummary(
        identity = VoucherIdentity("v-1"),
        date = "2026-08-01",
        type = "Sales",
        number = number,
        partyName = partyName,
        referenceNumber = referenceNumber,
        amount = amount,
        status = status,
        dataQuality = VoucherDataQuality.Complete,
    )

    @Test
    fun `party name flows to its own dedicated row column, not the secondary line`() {
        val row = summary(partyName = "Acme Traders").toRowUi()
        assertEquals("Acme Traders", row.partyName)
    }

    @Test
    fun `secondary line carries reference, status and amount in order, never the party name`() {
        val row = summary(referenceNumber = "PO-99", status = VoucherStatus.Active, amount = VoucherMoney("100.00", VoucherMoneySide.Debit)).toRowUi()
        assertEquals("Ref: PO-99 · Active · 100.00 (debit)", row.secondaryLabel)
    }

    @Test
    fun `secondary line omits blank reference and missing amount rather than showing empty segments`() {
        val row = summary(referenceNumber = "", amount = null).toRowUi()
        assertEquals("Active", row.secondaryLabel)
    }

    @Test
    fun `blank voucher number falls back to an em dash instead of an empty primary label`() {
        val row = summary(number = "  ").toRowUi()
        assertEquals("—", row.primaryLabel)
    }

    @Test
    fun `shows nothing for NotStarted rather than a placeholder`() {
        assertNull(state(VoucherHistoryReconciliationStatus.NotStarted).reconciliationStatusLabel())
    }

    @Test
    fun `shows an in-progress label without claiming completion`() {
        val label = state(VoucherHistoryReconciliationStatus.InProgress).reconciliationStatusLabel()
        assertEquals("Recent vouchers updated · Historical reconciliation in progress", label)
    }

    @Test
    fun `shows authoritative completion only when scope is truly authoritative`() {
        val label = state(VoucherHistoryReconciliationStatus.Completed, scopeIsAuthoritative = true).reconciliationStatusLabel()
        assertEquals("History reconciled · Scope: authoritative", label)
    }

    @Test
    fun `never claims full history when BOOKSFROM scope was not authoritative`() {
        val label = state(VoucherHistoryReconciliationStatus.Completed, scopeIsAuthoritative = false).reconciliationStatusLabel()
        assertEquals("Available history reconciled · Complete historical scope unavailable", label)
    }

    @Test
    fun `treats a null scope flag on Completed the same as non-authoritative, never assuming authoritative`() {
        val label = state(VoucherHistoryReconciliationStatus.Completed, scopeIsAuthoritative = null).reconciliationStatusLabel()
        assertEquals("Available history reconciled · Complete historical scope unavailable", label)
    }

    @Test
    fun `shows a failure label without hiding that reconciliation did not complete`() {
        val label = state(VoucherHistoryReconciliationStatus.Failed).reconciliationStatusLabel()
        assertEquals("Historical reconciliation did not complete · Showing available data", label)
    }

    private fun row(id: String, typeLabel: String) = VoucherRowUi(
        id = id,
        primaryLabel = "N-$id",
        secondaryLabel = null,
        dateLabel = "2026-08-01",
        typeLabel = typeLabel,
        statusLabel = "Active",
        amountLabel = null,
    )

    @Test
    fun `available type filters are the distinct sorted types actually loaded, not a fixed taxonomy`() {
        val state = VoucherBrowserUiState(
            vouchers = listOf(row("1", "Sales"), row("2", "Payment"), row("3", "Sales")),
        )
        assertEquals(listOf("Payment", "Sales"), state.availableTypeFilters)
    }

    @Test
    fun `no selected filter means All, so filtered vouchers equal the full loaded list`() {
        val vouchers = listOf(row("1", "Sales"), row("2", "Payment"))
        val state = VoucherBrowserUiState(vouchers = vouchers, selectedTypeFilter = null)
        assertEquals(vouchers, state.filteredVouchers)
    }

    @Test
    fun `selecting a type filter narrows the rendered list without touching the underlying loaded vouchers`() {
        val vouchers = listOf(row("1", "Sales"), row("2", "Payment"), row("3", "Sales"))
        val state = VoucherBrowserUiState(vouchers = vouchers, selectedTypeFilter = "Sales")
        assertEquals(listOf(row("1", "Sales"), row("3", "Sales")), state.filteredVouchers)
        assertEquals(3, state.vouchers.size)
    }

    @Test
    fun `a filter matching nothing yields an empty filtered list while vouchers remain loaded`() {
        val vouchers = listOf(row("1", "Sales"))
        val state = VoucherBrowserUiState(vouchers = vouchers, selectedTypeFilter = "Receipt")
        assertEquals(emptyList<VoucherRowUi>(), state.filteredVouchers)
        assertEquals(1, state.vouchers.size)
    }
}
