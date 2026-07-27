package com.budcom.android.feature.masterdata.ledger.presentation

import com.budcom.android.core.common.AppError
import com.budcom.android.feature.masterdata.ledger.domain.model.Ledger
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerPage

data class LedgerBrowserUiState(
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val searchQuery: String = "",
    val ledgers: List<LedgerRowUi> = emptyList(),
    val page: Int = 1,
    val pageSize: Int = 50,
    val totalItems: Int = 0,
    val totalPages: Int = 0,
    val canLoadMore: Boolean = false,
    val dataFreshnessAt: String? = null,
    val isOnline: Boolean = true,
    val error: LedgerUiError? = null,
) {
    val isBusy: Boolean get() = isInitialLoading || isRefreshing || isLoadingMore
    val hasContent: Boolean get() = ledgers.isNotEmpty()
}

data class LedgerRowUi(
    val id: String,
    val primaryLabel: String,
    val secondaryLabel: String?,
    val statusLabel: String,
    val balanceLabel: String?,
)

sealed interface LedgerUiError {
    data class Offline(val message: String) : LedgerUiError
    data class Timeout(val message: String) : LedgerUiError
    data class Remote(val message: String, val httpStatus: Int?) : LedgerUiError
    data class Message(val message: String) : LedgerUiError
    data class Unexpected(val message: String) : LedgerUiError
}

sealed interface LedgerBrowserEvent {
    data object Load : LedgerBrowserEvent
    data object Refresh : LedgerBrowserEvent
    data object Retry : LedgerBrowserEvent
    data object LoadNextPage : LedgerBrowserEvent
    data class SearchChanged(val query: String) : LedgerBrowserEvent
}

internal fun AppError.toLedgerUiError(): LedgerUiError = when (this) {
    is AppError.Offline -> LedgerUiError.Offline("Device is offline.")
    is AppError.Timeout -> LedgerUiError.Timeout("The request timed out.")
    is AppError.Remote -> LedgerUiError.Remote(message, httpStatus)
    is AppError.Serialization -> LedgerUiError.Message(message)
    is AppError.Message -> LedgerUiError.Message(message)
    is AppError.Unexpected -> LedgerUiError.Unexpected(
        cause.message ?: "An unexpected error occurred.",
    )
}

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
