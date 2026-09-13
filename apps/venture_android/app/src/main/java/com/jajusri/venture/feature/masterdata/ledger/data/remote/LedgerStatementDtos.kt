package com.jajusri.venture.feature.masterdata.ledger.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class LedgerStatementEnvelopeDto(
    val schemaVersion: String? = null,
    val dataFreshnessAt: String? = null,
    val statement: LedgerStatementDto,
)

@Serializable
data class LedgerStatementDto(
    val ledgerId: String,
    val ledgerName: String,
    val parentGroup: String? = null,
    val period: LedgerStatementPeriodDto,
    val openingBalance: LedgerStatementAmountDto? = null,
    val closingBalance: LedgerStatementAmountDto? = null,
    val transactions: List<LedgerStatementTransactionDto> = emptyList(),
    val coverage: LedgerStatementCoverageDto,
)

@Serializable
data class LedgerStatementPeriodDto(val from: String, val to: String)

@Serializable
data class LedgerStatementAmountDto(val amount: String, val side: String)

@Serializable
data class LedgerStatementTransactionDto(
    val voucherId: String,
    val date: String,
    val voucherType: String,
    val voucherNumber: String? = null,
    val referenceNumber: String? = null,
    val narration: String? = null,
    val debit: String? = null,
    val credit: String? = null,
    val runningBalance: LedgerStatementAmountDto? = null,
)

@Serializable
data class LedgerStatementCoverageDto(
    val transactionsComplete: Boolean,
    val balanceAvailable: Boolean,
    val syncedFrom: String? = null,
    val syncedTo: String? = null,
    val message: String? = null,
)
