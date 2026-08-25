package com.budcom.android.feature.catalogue.presentation

import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItem

data class CatalogueStockItemPickerUiState(
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val allItems: List<StockItem> = emptyList(),
    val isLinking: Boolean = false,
    val showLinkAllConfirmation: Boolean = false,
    /** Non-null only while a "Link all" run is in flight — see [LinkAllProgress]. */
    val linkAllProgress: LinkAllProgress? = null,
) {
    val filteredItems: List<StockItem>
        get() = searchQuery.trim().takeIf { it.isNotEmpty() }?.let { query ->
            allItems.filter { it.name.contains(query, ignoreCase = true) }
        } ?: allItems

    val isEmpty: Boolean get() = !isLoading && allItems.isEmpty()
}

/** Reported by [CatalogueStockItemPickerViewModel]'s "Link all" at a batch interval (once per
 * chunk, never per single row) so a large run — the live gate saw ~45 minutes for 1,208 items —
 * shows the user real progress instead of an indeterminate spinner. */
data class LinkAllProgress(val linked: Int, val total: Int) {
    val percent: Int get() = if (total == 0) 0 else (linked * 100) / total
}

sealed interface CatalogueStockItemPickerEvent {
    data class SearchChanged(val query: String) : CatalogueStockItemPickerEvent
    data class Pick(val stockItemId: String) : CatalogueStockItemPickerEvent
    data object OpenLinkAllConfirmation : CatalogueStockItemPickerEvent
    data object DismissLinkAllConfirmation : CatalogueStockItemPickerEvent
    data object ConfirmLinkAll : CatalogueStockItemPickerEvent
}
