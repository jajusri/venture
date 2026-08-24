package com.budcom.android.feature.catalogue.presentation

import android.net.Uri
import com.budcom.android.feature.catalogue.domain.model.CatalogueLifecycleAction
import com.budcom.android.feature.catalogue.domain.model.CatalogueLifecycleState
import com.budcom.android.feature.catalogue.domain.model.CatalogueProduct
import com.budcom.android.feature.catalogue.domain.model.PriceDisplayMode
import java.io.File

/** Display-ready projection of [com.budcom.android.feature.catalogue.domain.model.CatalogueAsset]
 * with its file already resolved (architecture §10 path-containment check applied), so the
 * Composable never needs to call back into the ViewModel just to render a thumbnail. */
data class CatalogueAssetUi(
    val assetId: String,
    val isPrimary: Boolean,
    val file: File?,
)

data class CatalogueDetailUiState(
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val product: CatalogueProduct? = null,
    val descriptionDraft: String = "",
    val specificationsDraft: String = "",
    val categoryDraft: String = "",
    val priceDisplayMode: PriceDisplayMode = PriceDisplayMode.ContactForPrice,
    val manualPriceDraft: String = "",
    val assets: List<CatalogueAssetUi> = emptyList(),
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

    data object PickPhotoFromGallery : CatalogueDetailEvent
    data object TakePhoto : CatalogueDetailEvent
    data class PhotoSelected(val uri: Uri) : CatalogueDetailEvent
    data class SetPrimaryAsset(val assetId: String) : CatalogueDetailEvent
    data class DeleteAsset(val assetId: String) : CatalogueDetailEvent
}

/** One-shot UI effects — mirrors
 * [com.budcom.android.feature.businessprofile.presentation.BusinessProfileEffect]'s own
 * `RequestLogoPick` pattern. Creating the camera's destination [Uri] needs a `Context`
 * (`FileProvider.getUriForFile`), which the ViewModel deliberately does not hold — the Route
 * composable (which already has `LocalContext.current`) creates it in response to
 * [RequestCameraCapture] and launches the picker/camera itself. */
sealed interface CatalogueDetailEffect {
    data object RequestGalleryPick : CatalogueDetailEffect
    data object RequestCameraCapture : CatalogueDetailEffect
}
