package com.budcom.android.feature.masterdata.ledger.presentation

import com.budcom.android.core.common.AppError
import com.budcom.android.feature.masterdata.domain.MasterDataBrowserDefaults
import com.budcom.android.feature.masterdata.presentation.MasterDataUiError
import com.budcom.android.feature.masterdata.presentation.toMasterDataUiError
import com.budcom.android.feature.masterdata.ledger.domain.model.Ledger
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerPage

data class LedgerBrowserUiState(
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val searchQuery: String = "",
    val ledgers: List<LedgerRowUi> = emptyList(),
    val page: Int = 1,
    val pageSize: Int = MasterDataBrowserDefaults.DEFAULT_PAGE_SIZE,
    val totalItems: Int = 0,
    val totalPages: Int = 0,
    val canLoadMore: Boolean = false,
    val dataFreshnessAt: String? = null,
    val isOnline: Boolean = true,
    val error: MasterDataUiError? = null,
    /** Set only when a tap cannot be resolved to a real ledger (blank/stale id) — an explicit,
     * non-silent response instead of a dead tap. A resolvable tap instead emits
     * [LedgerBrowserEffect.OpenLedgerStatement]. Cleared on dismissal. */
    val selectedLedgerNotice: String? = null,
) {
    val isBusy: Boolean get() = isInitialLoading || isRefreshing || isLoadingMore
    val hasContent: Boolean get() = ledgers.isNotEmpty()
}

sealed interface LedgerBrowserEffect {
    data class OpenLedgerStatement(val ledgerId: String) : LedgerBrowserEffect
}

data class LedgerRowUi(
    val id: String,
    val primaryLabel: String,
    val secondaryLabel: String?,
    val statusLabel: String,
    val balanceLabel: String?,
)

sealed interface LedgerBrowserEvent {
    data object Load : LedgerBrowserEvent
    data object Refresh : LedgerBrowserEvent
    data object Retry : LedgerBrowserEvent
    data object LoadNextPage : LedgerBrowserEvent
    data class SearchChanged(val query: String) : LedgerBrowserEvent
    data class LedgerTapped(val ledgerId: String) : LedgerBrowserEvent
    data object DismissLedgerNotice : LedgerBrowserEvent
}

/** @deprecated Prefer [MasterDataUiError]; retained as alias for ledger call sites. */
typealias LedgerUiError = MasterDataUiError

internal fun AppError.toLedgerUiError(): MasterDataUiError = toMasterDataUiError()

internal fun Ledger.toRowUi(): LedgerRowUi {
    val secondary = listOfNotNull(parentGroup, alias?.let { "Alias: $it" })
        .joinToString(" · ")
        .ifBlank { null }
    val balance = closingBalance?.let { money ->
        "${money.amount} ${money.currencyCode} ${money.side.name}"
    }
    return LedgerRowUi(
        id = id,
        primaryLabel = name,
        secondaryLabel = secondary,
        statusLabel = status.name.lowercase().replaceFirstChar { it.titlecase() },
        balanceLabel = balance,
    )
}

internal fun LedgerPage.toRows(): List<LedgerRowUi> = items.map { it.toRowUi() }
