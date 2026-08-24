package com.budcom.android.feature.catalogue.presentation

import com.budcom.android.feature.catalogue.domain.model.CatalogueLifecycleAction
import com.budcom.android.feature.catalogue.domain.model.CatalogueLifecycleState
import com.budcom.android.feature.catalogue.domain.model.CatalogueProduct
import com.budcom.android.feature.catalogue.domain.model.PriceDisplayMode

data class CatalogueDetailUiState(
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val product: CatalogueProduct? = null,
    val descriptionDraft: String = "",
    val specificationsDraft: String = "",
    val categoryDraft: String = "",
    val priceDisplayMode: PriceDisplayMode = PriceDisplayMode.ContactForPrice,
    val manualPriceDraft: String = "",
    val isSaving: Boolean = false,
    val isDirty: Boolean = false,
    val message: String? = null,
) {
    val canEdit: Boolean get() = product?.let {
        it.lifecycleState == CatalogueLifecycleState.Draft || it.lifecycleState == CatalogueLifecycleState.Review
    } ?: false

    val availableActions: List<CatalogueLifecycleAction> get() {
        val state = product?.lifecycleState ?: return emptyList()
        return when (state) {
            CatalogueLifecycleState.Draft -> listOf(CatalogueLifecycleAction.SubmitForReview, CatalogueLifecycleAction.Publish)
            CatalogueLifecycleState.Review -> listOf(CatalogueLifecycleAction.Publish)
            CatalogueLifecycleState.Published -> listOf(CatalogueLifecycleAction.Archive, CatalogueLifecycleAction.ReopenForEdit)
            CatalogueLifecycleState.Archived -> listOf(CatalogueLifecycleAction.Unarchive)
        }
    }
}

sealed interface CatalogueDetailEvent {
    data class DescriptionChanged(val value: String) : CatalogueDetailEvent
    data class SpecificationsChanged(val value: String) : CatalogueDetailEvent
    data class CategoryChanged(val value: String) : CatalogueDetailEvent
    data class PriceDisplayModeChanged(val mode: PriceDisplayMode) : CatalogueDetailEvent
    data class ManualPriceChanged(val value: String) : CatalogueDetailEvent
    data object SaveEnrichment : CatalogueDetailEvent
    data class Transition(val action: CatalogueLifecycleAction) : CatalogueDetailEvent
    data object DismissMessage : CatalogueDetailEvent
}
