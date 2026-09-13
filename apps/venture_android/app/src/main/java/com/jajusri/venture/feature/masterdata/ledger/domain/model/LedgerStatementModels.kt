package com.jajusri.venture.feature.masterdata.ledger.domain.model

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
    /**
     * Commercial item detail from the voucher's own inventory lines — present only in
     * [LedgerStatementMode.Detailed] and only for a voucher that actually has inventory lines
     * (never fabricated for a non-item voucher such as Receipt/Payment/Contra/Journal). This is
     * presentation-only commercial detail: [LedgerStatementItemDetail.totalLabel] is a sum of the
     * item lines shown purely for display inside Particulars — it never feeds back into or
     * overrides [debit]/[credit]/[runningBalance], which remain the sole authoritative
     * voucher-level accounting values from the existing ledger-line data.
     */
    val itemDetail: LedgerStatementItemDetail? = null,
)

/** Statement rendering mode — same underlying [LedgerStatement] data; Detailed only additionally
 * requests each item-bearing transaction's [LedgerStatementItemDetail]. */
enum class LedgerStatementMode {
    Summary,
    Detailed,
}

/** One inventory line from the voucher, formatted for display. [quantityLabel] is the Connector's
 * own already-unit-inclusive quantity string (e.g. "20 Nos") — never split or re-parsed. */
data class LedgerStatementItemLine(
    val itemName: String,
    val quantityLabel: String?,
    val rateLabel: String?,
    val amountLabel: String?,
)

/** [totalLabel] is the sum of [items]' amounts — commercial-detail display total, not an
 * accounting value (see [LedgerStatementTransaction.itemDetail] doc comment). */
data class LedgerStatementItemDetail(
    val items: List<LedgerStatementItemLine>,
    val totalLabel: String?,
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
    val ledgerAlias: String?,
    val parentGroup: String?,
    val period: LedgerStatementDateRange,
    val openingBalance: LedgerStatementAmount?,
    val closingBalance: LedgerStatementAmount?,
    val transactions: List<LedgerStatementTransaction>,
    val coverage: LedgerStatementCoverage,
)
