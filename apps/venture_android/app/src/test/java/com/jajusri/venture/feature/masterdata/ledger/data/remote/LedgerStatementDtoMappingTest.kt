package com.jajusri.venture.feature.masterdata.ledger.data.remote

import com.jajusri.venture.feature.masterdata.ledger.domain.model.AmountSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LedgerStatementDtoMappingTest {

    private fun sampleDto() = LedgerStatementEnvelopeDto(
        schemaVersion = "1.0.0",
        dataFreshnessAt = "2026-08-10T09:00:00.000Z",
        statement = LedgerStatementDto(
            ledgerId = "ledger-1",
            ledgerName = "Acme Traders",
            parentGroup = "Sundry Debtors",
            period = LedgerStatementPeriodDto(from = "2026-07-01", to = "2026-07-31"),
            openingBalance = LedgerStatementAmountDto("0", "debit"),
            closingBalance = LedgerStatementAmountDto("500", "debit"),
            transactions = listOf(
                LedgerStatementTransactionDto(
                    voucherId = "v-1",
                    date = "2026-07-05",
                    voucherType = "Sales",
                    voucherNumber = "S-001",
                    referenceNumber = "PO-9",
                    narration = "Sale of widgets",
                    debit = "2000",
                    credit = null,
                    runningBalance = LedgerStatementAmountDto("2000", "debit"),
                ),
                LedgerStatementTransactionDto(
                    voucherId = "v-2",
                    date = "2026-07-15",
                    voucherType = "Receipt",
                    debit = null,
                    credit = "1500",
                    runningBalance = LedgerStatementAmountDto("500", "debit"),
                ),
            ),
            coverage = LedgerStatementCoverageDto(
                transactionsComplete = true,
                balanceAvailable = true,
                syncedFrom = "2026-07-01",
                syncedTo = "2026-08-10",
                message = null,
            ),
        ),
    )

    @Test
    fun `maps a complete statement envelope to the domain model field-for-field`() {
        val domain = sampleDto().toDomain()
        assertEquals("ledger-1", domain.ledgerId)
        assertEquals("Acme Traders", domain.ledgerName)
        assertEquals("Sundry Debtors", domain.parentGroup)
        assertEquals("2026-07-01", domain.period.from)
        assertEquals("2026-07-31", domain.period.to)
        assertEquals(AmountSide.Dr, domain.openingBalance?.side)
        assertEquals("500", domain.closingBalance?.amount)
        assertEquals(2, domain.transactions.size)
        assertEquals("v-1", domain.transactions[0].voucherId)
        assertEquals("2000", domain.transactions[0].debit)
        assertNull(domain.transactions[0].credit)
        assertEquals("1500", domain.transactions[1].credit)
        assertEquals(true, domain.coverage.transactionsComplete)
        assertEquals(true, domain.coverage.balanceAvailable)
    }

    @Test
    fun `maps a null opening and closing balance and null coverage message honestly, never fabricating a value`() {
        val dto = sampleDto()
        val incomplete = dto.copy(
            statement = dto.statement.copy(
                openingBalance = null,
                closingBalance = null,
                coverage = dto.statement.coverage.copy(
                    balanceAvailable = false,
                    message = "Opening/closing balance unavailable — sync vouchers for this date range.",
                ),
            ),
        )
        val domain = incomplete.toDomain()
        assertNull(domain.openingBalance)
        assertNull(domain.closingBalance)
        assertEquals(false, domain.coverage.balanceAvailable)
        assertEquals(
            "Opening/closing balance unavailable — sync vouchers for this date range.",
            domain.coverage.message,
        )
    }

    @Test
    fun `treats a running balance transaction with no side text defensively as debit-cr aliases`() {
        assertEquals(AmountSide.Cr, "credit".toStatementAmountSide())
        assertEquals(AmountSide.Cr, "Cr".toStatementAmountSide())
        assertEquals(AmountSide.Dr, "debit".toStatementAmountSide())
        assertEquals(AmountSide.Dr, "anything-else".toStatementAmountSide())
    }
}
