package com.budcom.android.feature.masterdata.ledger.domain.model

/** Inclusive statement period, business dates (`YYYY-MM-DD`) — never device/sync timestamps. */
data class LedgerStatementDateRange(val from: String, val to: String)

/** A signed statement balance. No currency field: matches the Connector contract, which derives
 * this purely from already-stored voucher-ledger amounts (no currency column upstream either). */
data class LedgerStatementAmount(val amount: String, val side: AmountSide)

data class LedgerStatementTransaction(
    /** Stable, GUID-derived Voucher identity — the only safe key for a "View Voucher" deep link. */
    val voucherId: String,
    val date: String,
    val voucherType: String,
    val voucherNumber: String?,
    val referenceNumber: String?,
    val narration: String?,
    val debit: String?,
    val credit: String?,
    /** Null exactly when [LedgerStatementCoverage.balanceAvailable] is false. */
    val runningBalance: LedgerStatementAmount?,
)

/**
 * Honest reporting of whether synced data was sufficient to compute this statement
 * authoritatively — mirrors the Connector's own contract exactly. Never inferred locally;
 * always taken verbatim from the Connector response (or, for the fully-offline/no-cache
 * case, constructed as an explicit "not available" coverage by the repository).
 */
data class LedgerStatementCoverage(
    val transactionsComplete: Boolean,
    val balanceAvailable: Boolean,
    val syncedFrom: String?,
    val syncedTo: String?,
    val message: String?,
)

data class LedgerStatement(
    val ledgerId: String,
    val ledgerName: String,
    val parentGroup: String?,
    val period: LedgerStatementDateRange,
    val openingBalance: LedgerStatementAmount?,
    val closingBalance: LedgerStatementAmount?,
    val transactions: List<LedgerStatementTransaction>,
    val coverage: LedgerStatementCoverage,
)
