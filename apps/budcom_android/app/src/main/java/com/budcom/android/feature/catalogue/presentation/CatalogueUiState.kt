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
    val showShareMenu: Boolean = false,
    val showCategoryShareDialog: Boolean = false,
    /** Distinct customer-facing categories among this company's currently-Published products
     * (architecture §11: sharing reads exclusively from the published snapshot) -- a Draft/Review
     * product's category, if it hasn't been published yet, is never offered here. */
    val availableCategories: List<String> = emptyList(),
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
    data object OpenShareMenu : CatalogueEvent
    data object DismissShareMenu : CatalogueEvent
    /** Loads [CatalogueUiState.availableCategories] from the currently-Published snapshot and
     * opens the category picker. */
    data object OpenCategoryShareDialog : CatalogueEvent
    data object DismissCategoryShareDialog : CatalogueEvent
    data class ShareCategory(val category: String) : CatalogueEvent
}

/** One-shot navigation effect — mirrors [CatalogueDetailEffect]'s own pattern. */
sealed interface CatalogueEffect {
    data object NavigateToStockItemPicker : CatalogueEffect
}
