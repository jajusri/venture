package com.jajusri.venture.feature.masterdata.ledger.domain.model

/**
 * Ledger list query matching Connector `GET /ledgers` search parameters.
 */
data class LedgerQuery(
    val text: String? = null,
    val page: Int = 1,
    val pageSize: Int = 50,
    val sortBy: LedgerSortBy = LedgerSortBy.Name,
    val sortDirection: LedgerSortDirection = LedgerSortDirection.Asc,
)

enum class LedgerSortBy {
    Name,
    ParentGroup,
    ClosingBalance,
    SyncedAt,
}

enum class LedgerSortDirection {
    Asc,
    Desc,
}

data class MoneyAmount(
    val amount: String,
    val currencyCode: String,
    val side: AmountSide,
)

enum class AmountSide {
    Dr,
    Cr,
}

enum class LedgerStatus {
    Active,
    Inactive,
    Reserved,
    Unknown,
}

enum class LedgerDataQuality {
    Complete,
    Partial,
    Invalid,
}

/**
 * Domain ledger row for browser lists (from Connector LedgerSummary).
 */
data class Ledger(
    val id: String,
    val name: String,
    val alias: String?,
    val parentGroup: String?,
    val status: LedgerStatus,
    val closingBalance: MoneyAmount?,
    val dataQuality: LedgerDataQuality,
    val syncedAt: String,
)

data class LedgerPage(
    val items: List<Ledger>,
    val page: Int,
    val pageSize: Int,
    val totalItems: Int,
    val totalPages: Int,
    val dataFreshnessAt: String?,
)

/**
 * A single ledger's contact-compatible fields (MVP-1.1-D) — reads the Connector's already-synced
 * local snapshot (`GET /ledgers/{id}`, not a live Tally query), fetched only on-demand for one
 * ledger at a time, never as part of the bulk [Ledger] list/browse path above.
 */
data class LedgerContactDetails(
    val ledgerId: String,
    val mobile: String?,
    val email: String?,
    val address: String?,
    val state: String?,
    val pincode: String?,
    val gstin: String?,
)

/**
 * Result of a manually-triggered, occasional bulk contact-details fetch — one Tally round-trip
 * covering every ledger, reusing [LedgerContactDetails] per item. Never populated as part of the
 * bulk [Ledger] list/browse path, and never automatic — see [LedgerBulkContactDetailPort][
 * com.jajusri.venture.feature.masterdata.ledger.domain.port.LedgerBulkContactDetailPort].
 */
data class LedgerContactDetailsBulkResult(
    val items: List<LedgerContactDetails>,
    val ledgerCount: Int,
    val durationMs: Long,
)
