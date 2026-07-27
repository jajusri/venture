package com.budcom.android.feature.masterdata.ledger.domain.model

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
