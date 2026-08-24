package com.budcom.android.feature.catalogue.presentation

import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItem

data class CatalogueStockItemPickerUiState(
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val allItems: List<StockItem> = emptyList(),
    val isLinking: Boolean = false,
) {
    val filteredItems: List<StockItem>
        get() = searchQuery.trim().takeIf { it.isNotEmpty() }?.let { query ->
            allItems.filter { it.name.contains(query, ignoreCase = true) }
        } ?: allItems

    val isEmpty: Boolean get() = !isLoading && allItems.isEmpty()
}

sealed interface CatalogueStockItemPickerEvent {
    data class SearchChanged(val query: String) : CatalogueStockItemPickerEvent
    data class Pick(val stockItemId: String) : CatalogueStockItemPickerEvent
}
