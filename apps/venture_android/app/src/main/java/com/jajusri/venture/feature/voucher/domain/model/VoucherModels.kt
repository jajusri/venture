package com.jajusri.venture.feature.voucher.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Inclusive voucher date range matching Connector `from` / `to` (`YYYY-MM-DD`).
 *
 * TD-022: the automatic background reconciliation path (`VoucherWindowPlanner`) already bounds
 * every individual window to `windowSizeDays` (30 days). This manual/typed-input path had no
 * equivalent bound, so a user could request an arbitrarily wide span (years) and
 * `fetchCompleteWindow` would accumulate full ledger/inventory details for the entire span in
 * memory before a single Room commit. [MAX_SPAN_DAYS] closes that gap with the same one-year
 * ceiling used elsewhere as a reasonable ad-hoc lookup bound.
 */
data class VoucherDateRange(
    val from: String,
    val to: String,
) {
    init {
        require(ISO_DATE.matches(from)) { "from must be YYYY-MM-DD" }
        require(ISO_DATE.matches(to)) { "to must be YYYY-MM-DD" }
        require(from <= to) { "to must not precede from" }
        val spanDays = ChronoUnit.DAYS.between(LocalDate.parse(from), LocalDate.parse(to))
        require(spanDays <= MAX_SPAN_DAYS) {
            "Date range cannot exceed $MAX_SPAN_DAYS days (requested $spanDays days)"
        }
    }

    companion object {
        private val ISO_DATE = Regex("""^\d{4}-\d{2}-\d{2}$""")

        /** One year, inclusive of leap years — TD-022's manual-refresh span bound. */
        const val MAX_SPAN_DAYS = 366L
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
 *
 * [includeDetails] requests the complete per-item payload (ledger/inventory entries, narration,
 * effectiveDate) in the same bounded response, instead of a second per-voucher round trip.
 * Defaults to `false` (ordinary interactive list/browse/search traffic) — set only by the
 * offline-complete sync path, which needs every synchronized Voucher to be immediately usable
 * without a follow-up download.
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
    val includeDetails: Boolean = false,
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

/**
 * [fullDetails], when non-null, is the complete per-item detail payload for this same page —
 * present only when the query that produced this page set [VoucherQuery.includeDetails]. `null`
 * means "not requested," not "empty": callers must not infer completeness/absence of detail data
 * from an empty list here, only from `null` vs non-null.
 */
data class VoucherPage(
    val companyId: String,
    val items: List<VoucherSummary>,
    val page: Int,
    val pageSize: Int,
    val totalItems: Int,
    val totalPages: Int,
    val cacheState: VoucherCacheState = VoucherCacheState.Live,
    val lastSyncedAt: Long? = null,
    val fullDetails: List<VoucherDetails>? = null,
) {
    val canLoadMore: Boolean get() = page < totalPages
}

enum class VoucherCacheState { Live, Offline, NoCache }

/**
 * Detail model for voucher details screens (Connector VoucherPublicDetails).
 */
data class VoucherDetails(
    val summary: VoucherSummary,
    val effectiveDate: String?,
    val narration: String?,
    val ledgerEntries: List<VoucherLedgerLine>,
    val inventoryEntries: List<VoucherInventoryLine>,
    val cacheState: VoucherCacheState = VoucherCacheState.Live,
    val lastSyncedAt: Long? = null,
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
    val rate: String? = null,
    val amount: VoucherMoney?,
)
