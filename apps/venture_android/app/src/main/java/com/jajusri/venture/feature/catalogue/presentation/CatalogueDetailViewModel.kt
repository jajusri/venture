package com.jajusri.venture.feature.catalogue.presentation

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jajusri.venture.feature.catalogue.domain.model.CatalogueProduct
import com.jajusri.venture.feature.catalogue.domain.port.CatalogueClock
import com.jajusri.venture.feature.catalogue.domain.repository.CatalogueEnrichmentUpdate
import com.jajusri.venture.feature.catalogue.domain.repository.CatalogueLifecycleResult
import com.jajusri.venture.feature.catalogue.domain.repository.CatalogueRepository
import com.jajusri.venture.feature.catalogue.storage.CatalogueAssetFailureReason
import com.jajusri.venture.feature.catalogue.storage.CatalogueAssetResult
import com.jajusri.venture.feature.company.domain.port.CompanySessionPort
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CatalogueDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CatalogueRepository,
    private val companySession: CompanySessionPort,
    private val clock: CatalogueClock,
) : ViewModel() {

    private val productId: String = requireNotNull(savedStateHandle.get<String>(PRODUCT_ID_ARG)) { "productId is required" }

    private val _uiState = MutableStateFlow(CatalogueDetailUiState())
    val uiState: StateFlow<CatalogueDetailUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<CatalogueDetailEffect>(extraBufferCapacity = 1)
    val effects = _effects.asSharedFlow()

    /**
     * Owner-only enforcement is structurally wired end-to-end (architecture §7/§18:
     * [com.jajusri.venture.feature.catalogue.domain.model.CatalogueLifecycleTransitions],
     * [CatalogueRepository.transitionLifecycle]'s `isOwner` parameter), but this codebase has no
     * existing user/role/authentication concept anywhere to source a real signal from — a
     * pre-existing, whole-app characteristic Catalogue cannot and should not invent unilaterally.
     * Hardcoded `true` here (single-device-per-business assumption, consistent with every other
     * screen in this app today) is the smallest architecture-consistent choice; flagged as a named
     * limitation for explicit product-owner attention (see the MVP-1.4 implementation report,
     * technical debt registry).
     */
    private val isOwner = true

    init {
        viewModelScope.launch {
            val companyId = companySession.observeSelectedCompanyId().first()
            if (companyId == null) {
                _uiState.update { it.copy(isLoading = false, notFound = true) }
                return@launch
            }
            reload(companyId)
        }
    }

    fun onEvent(event: CatalogueDetailEvent) {
        when (event) {
            is CatalogueDetailEvent.DescriptionChanged -> _uiState.update { it.copy(descriptionDraft = event.value, isDirty = true) }
            is CatalogueDetailEvent.SpecificationsChanged -> _uiState.update { it.copy(specificationsDraft = event.value, isDirty = true) }
            is CatalogueDetailEvent.UnitChanged -> _uiState.update { it.copy(unitDraft = event.value, isDirty = true) }
            is CatalogueDetailEvent.CategoryChanged -> _uiState.update { it.copy(categoryDraft = event.value, isDirty = true) }
            is CatalogueDetailEvent.PriceDisplayModeChanged -> _uiState.update { it.copy(priceDisplayMode = event.mode, isDirty = true) }
            is CatalogueDetailEvent.ManualPriceChanged -> _uiState.update { it.copy(manualPriceDraft = event.value, isDirty = true) }
            CatalogueDetailEvent.SaveEnrichment -> saveEnrichment()
            is CatalogueDetailEvent.Transition -> transition(event.action)
            CatalogueDetailEvent.DismissMessage -> _uiState.update { it.copy(message = null) }
            CatalogueDetailEvent.PickPhotoFromGallery -> viewModelScope.launch { _effects.emit(CatalogueDetailEffect.RequestGalleryPick) }
            CatalogueDetailEvent.TakePhoto -> viewModelScope.launch { _effects.emit(CatalogueDetailEffect.RequestCameraCapture) }
            is CatalogueDetailEvent.PhotoSelected -> addPhoto(event.uri)
            is CatalogueDetailEvent.SetPrimaryAsset -> setPrimaryAsset(event.assetId)
            is CatalogueDetailEvent.DeleteAsset -> deleteAsset(event.assetId)
        }
    }

    private suspend fun reload(companyId: String) {
        val product = repository.findProduct(companyId, productId)
        if (product == null) {
            _uiState.update { it.copy(isLoading = false, notFound = true) }
            return
        }
        _uiState.update { it.applyProduct(product) }
        loadAssets(companyId, product.productId)
    }

    private suspend fun loadAssets(companyId: String, productId: String, message: String? = null) {
        val assets = repository.listAssets(companyId, productId).map { asset ->
            CatalogueAssetUi(
                assetId = asset.assetId,
                isPrimary = asset.isPrimary,
                file = repository.resolveAssetFile(companyId, productId, asset.filePath),
            )
        }
        _uiState.update { it.copy(assets = assets, message = message ?: it.message) }
    }

    private fun addPhoto(uri: Uri) {
        val product = _uiState.value.product ?: return
        viewModelScope.launch {
            when (val result = repository.addAsset(product.companyId, product.productId, uri, clock.now())) {
                is CatalogueAssetResult.Success -> loadAssets(product.companyId, product.productId, message = "Photo added")
                is CatalogueAssetResult.Failure -> _uiState.update { it.copy(message = result.reason.toMessage()) }
            }
        }
    }

    private fun setPrimaryAsset(assetId: String) {
        val product = _uiState.value.product ?: return
        viewModelScope.launch {
            repository.setPrimaryAsset(product.companyId, product.productId, assetId, clock.now())
            loadAssets(product.companyId, product.productId)
        }
    }

    private fun deleteAsset(assetId: String) {
        val product = _uiState.value.product ?: return
        viewModelScope.launch {
            repository.deleteAsset(product.companyId, product.productId, assetId)
            loadAssets(product.companyId, product.productId, message = "Photo removed")
        }
    }

    private fun CatalogueAssetFailureReason.toMessage(): String = when (this) {
        CatalogueAssetFailureReason.UnsupportedFileType -> "Only JPG, PNG, or WEBP photos are supported"
        CatalogueAssetFailureReason.FileTooLarge -> "That photo is too large"
        CatalogueAssetFailureReason.UnreadableSource -> "Could not read that photo"
        CatalogueAssetFailureReason.StorageError -> "Could not save that photo. Please try again"
    }

    private fun saveEnrichment() {
        val product = _uiState.value.product ?: return
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val updated = repository.updateEnrichment(
                product.companyId,
                product.productId,
                CatalogueEnrichmentUpdate(
                    // TD-047: harmless to always pass -- CatalogueRepository ignores this for a
                    // Tally-linked product, so this is never actually applied outside the Manual case.
                    unit = state.unitDraft.ifBlank { null },
                    description = state.descriptionDraft,
                    specifications = state.specificationsDraft,
                    customerFacingCategory = state.categoryDraft.ifBlank { null },
                    priceDisplayMode = state.priceDisplayMode,
                    manualPriceAmount = state.manualPriceDraft.ifBlank { null },
                ),
                clock.now(),
            )
            _uiState.update {
                if (updated != null) it.applyProduct(updated).copy(isSaving = false, isDirty = false, message = "Saved")
                else it.copy(isSaving = false, message = "Could not save changes")
            }
        }
    }

    private fun transition(action: com.jajusri.venture.feature.catalogue.domain.model.CatalogueLifecycleAction) {
        val product = _uiState.value.product ?: return
        viewModelScope.launch {
            when (val result = repository.transitionLifecycle(product.companyId, product.productId, action, isOwner, clock.now())) {
                is CatalogueLifecycleResult.Success -> _uiState.update { it.applyProduct(result.product).copy(message = "Updated") }
                CatalogueLifecycleResult.Rejected -> _uiState.update { it.copy(message = "That action isn't allowed right now") }
                CatalogueLifecycleResult.ProductNotFound -> _uiState.update { it.copy(notFound = true) }
            }
        }
    }

    private fun CatalogueDetailUiState.applyProduct(product: CatalogueProduct): CatalogueDetailUiState = copy(
        isLoading = false,
        notFound = false,
        product = product,
        descriptionDraft = product.description.orEmpty(),
        specificationsDraft = product.specifications.orEmpty(),
        unitDraft = product.unit.orEmpty(),
        categoryDraft = product.customerFacingCategory.orEmpty(),
        priceDisplayMode = product.priceDisplayMode,
        manualPriceDraft = product.manualPriceAmount.orEmpty(),
    )

    companion object {
        const val PRODUCT_ID_ARG = "productId"
    }
}
