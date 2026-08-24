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
    val showAddChoiceDialog: Boolean = false,
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
    /** FAB tap — offers a choice between manual entry and linking from Tally stock, rather than
     * assuming one (architecture: Catalogue supports both a Manual and a Tally
     * [com.budcom.android.feature.catalogue.domain.model.CatalogueProductSource] from day one). */
    data object OpenAddChoiceDialog : CatalogueEvent
    data object DismissAddChoiceDialog : CatalogueEvent
    data object ChooseManualEntry : CatalogueEvent
    data object ChooseLinkFromStock : CatalogueEvent
    data object OpenAddManualDialog : CatalogueEvent
    data object DismissAddManualDialog : CatalogueEvent
    data class AddManualNameChanged(val name: String) : CatalogueEvent
    data object ConfirmAddManual : CatalogueEvent
    data class SetPublic(val isPublic: Boolean) : CatalogueEvent
    data object ShareFullCatalogue : CatalogueEvent
    data object DismissShareMessage : CatalogueEvent
}

/** One-shot navigation effect — mirrors [CatalogueDetailEffect]'s own pattern. */
sealed interface CatalogueEffect {
    data object NavigateToStockItemPicker : CatalogueEffect
}
