package com.budcom.android.feature.catalogue.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budcom.android.feature.catalogue.domain.port.CatalogueClock
import com.budcom.android.feature.catalogue.domain.repository.CatalogueRepository
import com.budcom.android.feature.company.domain.port.CompanySessionPort
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

/**
 * "Link from Tally stock items" (architecture §21 Milestone 1's own named "create Drafts from
 * Stock Items" action, never built until now). A one-shot picker, not a long-lived list screen —
 * closes itself (via [linked]) the moment a Stock Item is picked and its Draft created.
 */
@HiltViewModel
class CatalogueStockItemPickerViewModel @Inject constructor(
    private val repository: CatalogueRepository,
    private val companySession: CompanySessionPort,
    private val clock: CatalogueClock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CatalogueStockItemPickerUiState())
    val uiState: StateFlow<CatalogueStockItemPickerUiState> = _uiState.asStateFlow()

    /** Emits the newly-created product's id once a pick succeeds — the Route navigates straight
     * into that product's detail screen so the owner can enrich/publish it immediately. */
    private val _linked = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val linked = _linked.asSharedFlow()

    private var companyId: String? = null

    init {
        viewModelScope.launch {
            val id = companySession.observeSelectedCompanyId().first()
            companyId = id
            if (id == null) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }
            val items = repository.listUnlinkedStockItems(id)
            _uiState.update { it.copy(isLoading = false, allItems = items) }
        }
    }

    fun onEvent(event: CatalogueStockItemPickerEvent) {
        when (event) {
            is CatalogueStockItemPickerEvent.SearchChanged -> _uiState.update { it.copy(searchQuery = event.query) }
            is CatalogueStockItemPickerEvent.Pick -> pick(event.stockItemId)
        }
    }

    private fun pick(stockItemId: String) {
        val id = companyId ?: return
        if (_uiState.value.isLinking) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLinking = true) }
            val product = repository.createDraftFromStockItem(id, stockItemId, clock.now())
            _uiState.update { it.copy(isLinking = false) }
            product?.let { _linked.emit(it.productId) }
        }
    }
}
