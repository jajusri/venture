package com.budcom.android.feature.catalogue.presentation

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budcom.android.feature.businessprofile.domain.repository.BusinessProfileRepository
import com.budcom.android.feature.catalogue.domain.model.CatalogueProduct
import com.budcom.android.feature.catalogue.domain.port.CatalogueClock
import com.budcom.android.feature.catalogue.domain.repository.CatalogueRepository
import com.budcom.android.feature.catalogue.sharing.CatalogueShareCoordinator
import com.budcom.android.feature.catalogue.sharing.CatalogueShareResult
import com.budcom.android.feature.catalogue.sharing.CatalogueShareScope
import com.budcom.android.feature.company.domain.port.CompanySessionPort
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CatalogueViewModel @Inject constructor(
    private val repository: CatalogueRepository,
    private val companySession: CompanySessionPort,
    private val clock: CatalogueClock,
    private val shareCoordinator: CatalogueShareCoordinator,
    private val businessProfileRepository: BusinessProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CatalogueUiState())
    val uiState: StateFlow<CatalogueUiState> = _uiState.asStateFlow()

    private val _shareIntent = MutableSharedFlow<Intent>(extraBufferCapacity = 1)
    val shareIntent = _shareIntent.asSharedFlow()

    private val _effects = MutableSharedFlow<CatalogueEffect>(extraBufferCapacity = 1)
    val effects = _effects.asSharedFlow()

    private var companyId: String? = null

    init {
        // Mirrors ConnectViewModel's own company-switch discipline (TD-037 class): every company
        // change (not just the initial subscription) reloads this screen's list from scratch.
        viewModelScope.launch {
            companySession.observeSelectedCompanyId().distinctUntilChanged().collect { id ->
                companyId = id
                load(refreshing = false)
            }
        }
    }

    fun onEvent(event: CatalogueEvent) {
        when (event) {
            CatalogueEvent.Refresh -> load(refreshing = true)
            CatalogueEvent.OpenAddChoiceDialog -> _uiState.update { it.copy(showAddChoiceDialog = true) }
            CatalogueEvent.DismissAddChoiceDialog -> _uiState.update { it.copy(showAddChoiceDialog = false) }
            CatalogueEvent.ChooseManualEntry ->
                _uiState.update { it.copy(showAddChoiceDialog = false, showAddManualDialog = true, addManualName = "") }
            CatalogueEvent.ChooseLinkFromStock -> {
                _uiState.update { it.copy(showAddChoiceDialog = false) }
                viewModelScope.launch { _effects.emit(CatalogueEffect.NavigateToStockItemPicker) }
            }
            CatalogueEvent.OpenAddManualDialog -> _uiState.update { it.copy(showAddManualDialog = true, addManualName = "") }
            CatalogueEvent.DismissAddManualDialog -> _uiState.update { it.copy(showAddManualDialog = false) }
            is CatalogueEvent.AddManualNameChanged -> _uiState.update { it.copy(addManualName = event.name) }
            CatalogueEvent.ConfirmAddManual -> confirmAddManual()
            is CatalogueEvent.SetPublic -> setPublic(event.isPublic)
            CatalogueEvent.ShareFullCatalogue -> {
                _uiState.update { it.copy(showShareMenu = false) }
                shareFullCatalogue()
            }
            CatalogueEvent.DismissShareMessage -> _uiState.update { it.copy(shareMessage = null) }
            CatalogueEvent.OpenShareMenu -> _uiState.update { it.copy(showShareMenu = true) }
            CatalogueEvent.DismissShareMenu -> _uiState.update { it.copy(showShareMenu = false) }
            CatalogueEvent.OpenCategoryShareDialog -> openCategoryShareDialog()
            CatalogueEvent.DismissCategoryShareDialog -> _uiState.update { it.copy(showCategoryShareDialog = false) }
            is CatalogueEvent.ShareCategory -> {
                _uiState.update { it.copy(showCategoryShareDialog = false) }
                shareCategory(event.category)
            }
        }
    }

    private fun load(refreshing: Boolean) {
        val id = companyId ?: run {
            // A resume-triggered CatalogueEvent.Refresh (see CatalogueRoute) can race the
            // company-session subscription on a cold start, firing before companyId is known.
            // Only the real, company-driven load path (refreshing = false, from the init-block
            // subscription) is allowed to declare "no company" and clear the loading indicator --
            // a premature Refresh in this state must be a no-op, not a flash of the empty state
            // this screen isn't actually in yet.
            if (!refreshing) _uiState.update { it.copy(isInitialLoading = false, isRefreshing = false, products = emptyList()) }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                if (refreshing) it.copy(isRefreshing = true) else it.copy(isInitialLoading = true)
            }
            // Opportunistic reconciliation (architecture §6/§14/§21): cheap, local-only sweep
            // against the already-synced Stock Item cache -- never a network call of its own.
            runCatching { repository.reconcileStockItemLinks(id, clock.now()) }
            val products = repository.listProducts(id)
            val isPublic = repository.isPublic(id)
            _uiState.update {
                it.copy(
                    isInitialLoading = false,
                    isRefreshing = false,
                    products = products.map { p -> p.toRowUi() },
                    isPublic = isPublic,
                    error = null,
                )
            }
        }
    }

    private fun confirmAddManual() {
        val id = companyId ?: return
        val name = _uiState.value.addManualName.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            repository.createManualDraft(id, name, clock.now())
            _uiState.update { it.copy(showAddManualDialog = false, addManualName = "") }
            load(refreshing = false)
        }
    }

    private fun setPublic(isPublic: Boolean) {
        val id = companyId ?: return
        viewModelScope.launch {
            repository.setPublic(id, isPublic, clock.now())
            _uiState.update { it.copy(isPublic = isPublic) }
        }
    }

    private fun shareFullCatalogue() {
        val id = companyId ?: return
        viewModelScope.launch { performShare(id, CatalogueShareScope.FullCatalogue) }
    }

    private fun openCategoryShareDialog() {
        val id = companyId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(showShareMenu = false) }
            val categories = repository.listAllPublished(id).mapNotNull { it.customerFacingCategory }.distinct().sorted()
            _uiState.update { it.copy(showCategoryShareDialog = true, availableCategories = categories) }
        }
    }

    private fun shareCategory(category: String) {
        val id = companyId ?: return
        viewModelScope.launch { performShare(id, CatalogueShareScope.Category(category)) }
    }

    private suspend fun performShare(companyId: String, scope: CatalogueShareScope) {
        val businessName = businessProfileRepository.getProfile(companyId)?.tradingName
        when (val prepared = shareCoordinator.prepareShare(companyId, scope, businessName)) {
            is CatalogueShareResult.Failure -> _uiState.update { it.copy(shareMessage = prepared.message) }
            is CatalogueShareResult.Success -> when (val intentResult = shareCoordinator.createShareIntent(prepared.value)) {
                is CatalogueShareResult.Failure -> _uiState.update { it.copy(shareMessage = intentResult.message) }
                is CatalogueShareResult.Success -> _shareIntent.emit(intentResult.value)
            }
        }
    }
}

private fun CatalogueProduct.toRowUi() = CatalogueProductRowUi(
    productId = productId,
    displayName = displayName,
    lifecycleState = lifecycleState,
    sourceAvailable = sourceAvailable,
    source = source,
)
