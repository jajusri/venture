package com.budcom.android.feature.catalogue.presentation

import com.budcom.android.feature.catalogue.domain.model.CatalogueLifecycleState
import com.budcom.android.feature.catalogue.domain.model.CatalogueProductSource

data class CatalogueProductRowUi(
    val productId: String,
    val displayName: String,
    val lifecycleState: CatalogueLifecycleState,
    val sourceAvailable: Boolean,
    val source: CatalogueProductSource,
)

data class CatalogueUiState(
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val products: List<CatalogueProductRowUi> = emptyList(),
    val showAddManualDialog: Boolean = false,
    val addManualName: String = "",
    /** Catalogue-level Public/Private toggle (Brainstorm Outcome §5). Defaults `false` (Private)
     * — never presumed Public before the company's own setting has loaded. */
    val isPublic: Boolean = false,
    val shareMessage: String? = null,
    val error: String? = null,
) {
    val isEmpty: Boolean get() = !isInitialLoading && products.isEmpty()
}

sealed interface CatalogueEvent {
    data object Refresh : CatalogueEvent
    data object OpenAddManualDialog : CatalogueEvent
    data object DismissAddManualDialog : CatalogueEvent
    data class AddManualNameChanged(val name: String) : CatalogueEvent
    data object ConfirmAddManual : CatalogueEvent
    data class SetPublic(val isPublic: Boolean) : CatalogueEvent
    data object ShareFullCatalogue : CatalogueEvent
    data object DismissShareMessage : CatalogueEvent
}
