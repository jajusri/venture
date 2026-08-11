package com.budcom.android.feature.voucher.presentation

import com.budcom.android.core.common.AppError
import com.budcom.android.feature.masterdata.presentation.MasterDataUiError
import com.budcom.android.feature.masterdata.presentation.toMasterDataUiError
import com.budcom.android.feature.voucher.domain.model.VoucherDateRange
import com.budcom.android.feature.voucher.domain.model.VoucherDateRangeDefaults
import com.budcom.android.feature.voucher.domain.model.VoucherPage
import com.budcom.android.feature.voucher.domain.model.VoucherSummary
import com.budcom.android.feature.voucher.domain.model.VoucherCacheState

data class VoucherBrowserUiState(
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val companyId: String? = null,
    val dateFrom: String = DefaultDateRange.from,
    val dateTo: String = DefaultDateRange.to,
    val searchQuery: String = "",
    val vouchers: List<VoucherRowUi> = emptyList(),
    val page: Int = 1,
    val pageSize: Int = 50,
    val totalItems: Int = 0,
    val totalPages: Int = 0,
    val canLoadMore: Boolean = false,
    val isOnline: Boolean = true,
    val error: MasterDataUiError? = null,
    /** Set only when a manual refresh fails while cached rows remain visible; cleared on the next successful refresh/load. */
    val refreshError: String? = null,
    val cacheState: VoucherCacheState = VoucherCacheState.NoCache,
    val lastSyncedAt: Long? = null,
    /**
     * [lastSyncedAt]/[cacheState] describe only the currently-viewed window's fast refresh —
     * they must never be read as "all voucher history is reconciled." This field is the
     * separate, honest signal for the background historical-reconciliation walk (BUDCOM MVP-1
     * Section 4): a recent-window refresh can complete and show current data while older windows
     * are still mid-reconciliation, e.g. a voucher moved from an older date into the current
     * window looks correct immediately, but the stale old-window representation isn't provably
     * gone until [VoucherHistoryReconciliationStatus.Completed].
     */
    val historyReconciliationStatus: VoucherHistoryReconciliationStatus = VoucherHistoryReconciliationStatus.NotStarted,
    /**
     * Meaningful only once [historyReconciliationStatus] is [VoucherHistoryReconciliationStatus.Completed]
     * or [VoucherHistoryReconciliationStatus.Failed]: true means the walk covered the company's
     * real BOOKSFROM history boundary; false means BOOKSFROM was unavailable and only the
     * default fallback window was covered — its actual coverage of this company's full history
     * is unknown. Never read `historyReconciliationStatus == Completed` alone as "full history
     * reconciled" — this flag must also be true.
     */
    val historyReconciliationScopeIsAuthoritative: Boolean? = null,
) {
    val isBusy: Boolean get() = isInitialLoading || isRefreshing || isLoadingMore
    val hasContent: Boolean get() = vouchers.isNotEmpty()
    val hasCompany: Boolean get() = !companyId.isNullOrBlank()

    private object DefaultDateRange {
        private val range = VoucherDateRangeDefaults.lastDaysInclusive()
        val from: String = range.from
        val to: String = range.to
    }
}

/** Distinct from [VoucherCacheState]: describes the background full-history walk, not the currently-viewed window. */
enum class VoucherHistoryReconciliationStatus { NotStarted, InProgress, Completed, Failed }

/**
 * Lightweight, truthful label for the background historical-reconciliation walk — deliberately
 * not a progress dashboard, just an honest one-line state. Returns null for [VoucherHistoryReconciliationStatus.NotStarted]
 * (nothing meaningful to say yet, so showing nothing is more honest than a placeholder). Never
 * claims "Full history" when [VoucherBrowserUiState.historyReconciliationScopeIsAuthoritative] is
 * false — that specifically means BOOKSFROM was unavailable and only a fallback window was
 * walked, so the label says "Available history" instead.
 */
fun VoucherBrowserUiState.reconciliationStatusLabel(): String? = when (historyReconciliationStatus) {
    VoucherHistoryReconciliationStatus.NotStarted -> null
    VoucherHistoryReconciliationStatus.InProgress -> "Recent vouchers updated · Historical reconciliation in progress"
    VoucherHistoryReconciliationStatus.Completed -> if (historyReconciliationScopeIsAuthoritative == true) {
        "History reconciled · Scope: authoritative"
    } else {
        "Available history reconciled · Complete historical scope unavailable"
    }
    VoucherHistoryReconciliationStatus.Failed -> "Historical reconciliation did not complete · Showing available data"
}

internal fun VoucherCacheState.statusText(lastSyncedAt: Long?): String = when (this) {
    VoucherCacheState.Live -> "Live"
    VoucherCacheState.Offline -> "Offline · Last synced ${lastSyncedAt?.let(::formatSyncTime) ?: "unknown"}"
    VoucherCacheState.NoCache -> "No offline data"
}

internal fun refreshFailedMessage(lastSyncedAt: Long?): String =
    "Could not refresh · Showing data last synced at ${lastSyncedAt?.let(::formatSyncTime) ?: "unknown"}"

private fun formatSyncTime(value: Long): String = java.text.DateFormat.getDateTimeInstance(
    java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT,
).format(java.util.Date(value))

data class VoucherRowUi(
    val id: String,
    val primaryLabel: String,
    val secondaryLabel: String?,
    val dateLabel: String,
    val typeLabel: String,
    val statusLabel: String,
    val amountLabel: String?,
    val partyName: String? = null,
)

sealed interface VoucherBrowserEvent {
    data object Load : VoucherBrowserEvent
    data object Refresh : VoucherBrowserEvent
    data object Retry : VoucherBrowserEvent
    data object LoadNextPage : VoucherBrowserEvent
    data class SearchChanged(val query: String) : VoucherBrowserEvent
    data class DateFromChanged(val value: String) : VoucherBrowserEvent
    data class DateToChanged(val value: String) : VoucherBrowserEvent
    data object ApplyDateRange : VoucherBrowserEvent
}

internal fun AppError.toVoucherUiError(): MasterDataUiError = toMasterDataUiError()

internal fun VoucherSummary.toRowUi(): VoucherRowUi {
    val numberLabel = number?.takeIf { it.isNotBlank() } ?: "—"
    val statusLabel = status.name.lowercase().replaceFirstChar { it.titlecase() }
    val amount = amount?.let { money ->
        val side = money.side?.name?.lowercase()
        if (side != null) "${money.value} ($side)" else money.value
    }
    // Party name now has its own dedicated primary-line column (see VoucherRowCard), so the
    // secondary line carries only what doesn't already have a place: reference, status, amount.
    val secondary = listOfNotNull(
        referenceNumber?.takeIf { it.isNotBlank() }?.let { "Ref: $it" },
        statusLabel,
        amount,
    ).joinToString(" · ").ifBlank { null }
    return VoucherRowUi(
        id = identity.id,
        primaryLabel = numberLabel,
        secondaryLabel = secondary,
        dateLabel = formatVoucherDate(date),
        typeLabel = type,
        statusLabel = statusLabel,
        amountLabel = amount,
        partyName = partyName,
    )
}

internal fun VoucherPage.toRows(): List<VoucherRowUi> = items.map { it.toRowUi() }

internal fun tryParseDateRange(from: String, to: String): VoucherDateRange? =
    runCatching { VoucherDateRange(from = from.trim(), to = to.trim()) }.getOrNull()
