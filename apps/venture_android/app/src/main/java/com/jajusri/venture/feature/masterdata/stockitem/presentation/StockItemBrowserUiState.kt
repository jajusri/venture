package com.jajusri.venture.feature.masterdata.stockitem.presentation

import com.jajusri.venture.core.common.AppError
import com.jajusri.venture.feature.masterdata.domain.MasterDataBrowserDefaults
import com.jajusri.venture.feature.masterdata.presentation.MasterDataUiError
import com.jajusri.venture.feature.masterdata.presentation.toMasterDataUiError
import com.jajusri.venture.feature.masterdata.stockitem.domain.model.StockItem
import com.jajusri.venture.feature.masterdata.stockitem.domain.model.StockItemPage

data class StockItemBrowserUiState(
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val searchQuery: String = "",
    val stockItems: List<StockItemRowUi> = emptyList(),
    val page: Int = 1,
    val pageSize: Int = MasterDataBrowserDefaults.DEFAULT_PAGE_SIZE,
    val totalItems: Int = 0,
    val totalPages: Int = 0,
    val canLoadMore: Boolean = false,
    val dataFreshnessAt: String? = null,
    val isOnline: Boolean = true,
    val error: MasterDataUiError? = null,
) {
    val isBusy: Boolean get() = isInitialLoading || isRefreshing || isLoadingMore
    val hasContent: Boolean get() = stockItems.isNotEmpty()
}

data class StockItemRowUi(
    val id: String,
    val primaryLabel: String,
    val secondaryLabel: String?,
    val statusLabel: String,
    val unitLabel: String?,
    val balanceLabel: String?,
)

sealed interface StockItemBrowserEvent {
    data object Load : StockItemBrowserEvent
    data object Refresh : StockItemBrowserEvent
    data object Retry : StockItemBrowserEvent
    data object LoadNextPage : StockItemBrowserEvent
    data class SearchChanged(val query: String) : StockItemBrowserEvent
}

internal fun AppError.toStockItemUiError(): MasterDataUiError = toMasterDataUiError()

internal fun StockItem.toRowUi(): StockItemRowUi {
    val secondary = listOfNotNull(
        parentGroup,
        category?.let { "Category: $it" },
        alias?.let { "Alias: $it" },
        partNumber?.let { "Part: $it" },
        hsnCode?.let { "HSN: $it" },
    ).joinToString(" · ").ifBlank { null }
    val balance = closingBalance?.let { money ->
        "${money.amount} ${money.currencyCode} ${money.side.name}"
    }
    return StockItemRowUi(
        id = id,
        primaryLabel = name,
        secondaryLabel = secondary,
        statusLabel = status.name.lowercase().replaceFirstChar { it.titlecase() },
        unitLabel = baseUnit,
        balanceLabel = balance,
    )
}

internal fun StockItemPage.toRows(): List<StockItemRowUi> = items.map { it.toRowUi() }
