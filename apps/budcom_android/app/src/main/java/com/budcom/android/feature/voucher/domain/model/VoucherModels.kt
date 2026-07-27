package com.budcom.android.feature.voucher.domain.model

/**
 * Inclusive voucher date range matching Connector `from` / `to` (`YYYY-MM-DD`).
 */
data class VoucherDateRange(
    val from: String,
    val to: String,
) {
    init {
        require(ISO_DATE.matches(from)) { "from must be YYYY-MM-DD" }
        require(ISO_DATE.matches(to)) { "to must be YYYY-MM-DD" }
        require(from <= to) { "to must not precede from" }
    }

    companion object {
        private val ISO_DATE = Regex("""^\d{4}-\d{2}-\d{2}$""")
    }
}

enum class VoucherSortField {
    Date,
    VoucherNumber,
    Amount,
}

enum class VoucherSortDirection {
    Asc,
    Desc,
}

data class VoucherSort(
    val field: VoucherSortField = VoucherSortField.Date,
    val direction: VoucherSortDirection = VoucherSortDirection.Asc,
)

/**
 * Typed list/search query for Connector `GET /api/v1/vouchers`.
 */
data class VoucherQuery(
    val companyId: String,
    val dateRange: VoucherDateRange,
    val searchText: String? = null,
    val voucherType: String? = null,
    val voucherNumber: String? = null,
    val partyName: String? = null,
    val page: Int = 1,
    val pageSize: Int = 50,
    val sort: VoucherSort = VoucherSort(),
)

enum class VoucherStatus {
    Active,
    Cancelled,
    Unknown,
}

enum class VoucherDataQuality {
    Complete,
    Incomplete,
}

enum class VoucherMoneySide {
    Debit,
    Credit,
}

data class VoucherMoney(
    val value: String,
    val side: VoucherMoneySide?,
)

/**
 * Stable voucher identity for list rows and future detail navigation.
 */
data class VoucherIdentity(
    val id: String,
)

/**
 * Domain voucher summary for browser lists (Connector VoucherPublicRecord).
 */
data class VoucherSummary(
    val identity: VoucherIdentity,
    val date: String,
    val type: String,
    val number: String?,
    val partyName: String?,
    val referenceNumber: String?,
    val amount: VoucherMoney?,
    val status: VoucherStatus,
    val dataQuality: VoucherDataQuality,
)

data class VoucherPage(
    val companyId: String,
    val items: List<VoucherSummary>,
    val page: Int,
    val pageSize: Int,
    val totalItems: Int,
    val totalPages: Int,
) {
    val canLoadMore: Boolean get() = page < totalPages
}

/**
 * Detail model for future Voucher Details milestone (foundation only).
 */
data class VoucherDetails(
    val summary: VoucherSummary,
    val effectiveDate: String?,
    val narration: String?,
    val ledgerEntries: List<VoucherLedgerLine>,
    val inventoryEntries: List<VoucherInventoryLine>,
)

data class VoucherLedgerLine(
    val lineNumber: Int,
    val ledgerName: String,
    val amount: VoucherMoney,
    val isDeemedPositive: Boolean?,
)

data class VoucherInventoryLine(
    val lineNumber: Int,
    val itemName: String,
    val quantity: String?,
    val amount: VoucherMoney?,
)
