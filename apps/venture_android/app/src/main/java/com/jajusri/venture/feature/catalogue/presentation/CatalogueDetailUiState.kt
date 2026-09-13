package com.jajusri.venture.feature.catalogue.presentation

import android.net.Uri
import com.jajusri.venture.feature.catalogue.domain.model.CatalogueLifecycleAction
import com.jajusri.venture.feature.catalogue.domain.model.CatalogueLifecycleState
import com.jajusri.venture.feature.catalogue.domain.model.CatalogueProduct
import com.jajusri.venture.feature.catalogue.domain.model.CatalogueProductSource
import com.jajusri.venture.feature.catalogue.domain.model.PriceDisplayMode
import java.io.File

/** Display-ready projection of [com.jajusri.venture.feature.catalogue.domain.model.CatalogueAsset]
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
    /** TD-047: only meaningful (and only shown editable) for a [CatalogueProductSource.Manual]
     * product -- a Tally-linked product's Unit is shown read-only from [CatalogueProduct.unit]
     * elsewhere and never routes through this draft. */
    val unitDraft: String = "",
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

    /** TD-047: gates the editable Unit field -- a Tally-linked product's Unit is Tally-authoritative
     * and shown read-only in the existing "From Tally" card instead. */
    val isManualProduct: Boolean get() = product?.source == CatalogueProductSource.Manual

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
    /** TD-047: only meaningful for a Manual product; see [CatalogueDetailUiState.isManualProduct]. */
    data class UnitChanged(val value: String) : CatalogueDetailEvent
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
 * [com.jajusri.venture.feature.businessprofile.presentation.BusinessProfileEffect]'s own
 * `RequestLogoPick` pattern. Creating the camera's destination [Uri] needs a `Context`
 * (`FileProvider.getUriForFile`), which the ViewModel deliberately does not hold — the Route
 * composable (which already has `LocalContext.current`) creates it in response to
 * [RequestCameraCapture] and launches the picker/camera itself. */
sealed interface CatalogueDetailEffect {
    data object RequestGalleryPick : CatalogueDetailEffect
    data object RequestCameraCapture : CatalogueDetailEffect
}
