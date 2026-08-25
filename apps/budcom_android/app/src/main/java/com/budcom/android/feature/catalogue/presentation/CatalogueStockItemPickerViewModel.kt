package com.budcom.android.feature.catalogue.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budcom.android.feature.catalogue.domain.port.CatalogueClock
import com.budcom.android.feature.catalogue.domain.repository.CatalogueRepository
import com.budcom.android.feature.company.domain.port.CompanySessionPort
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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

    /** Emits the count of Drafts created once a "link all" run finishes — the Route returns to the
     * Catalogue list (there is no single resulting product to open, unlike [linked]). */
    private val _linkedAll = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val linkedAll = _linkedAll.asSharedFlow()

    private var companyId: String? = null

    init {
        viewModelScope.launch {
            val id = companySession.observeSelectedCompanyId().first()
            companyId = id
            if (id == null) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }
            // TD-050: a company whose Stock Items browser was never separately opened would
            // otherwise have an empty/stale local cache here even after a real "Sync Now" — this
            // guarantees freshness itself rather than depending on that unrelated screen having
            // been visited first. See CatalogueRepository.warmStockItemCache's own doc comment.
            repository.warmStockItemCache(id)
            val items = repository.listUnlinkedStockItems(id)
            _uiState.update { it.copy(isLoading = false, allItems = items) }
        }
    }

    fun onEvent(event: CatalogueStockItemPickerEvent) {
        when (event) {
            is CatalogueStockItemPickerEvent.SearchChanged -> _uiState.update { it.copy(searchQuery = event.query) }
            is CatalogueStockItemPickerEvent.Pick -> pick(event.stockItemId)
            is CatalogueStockItemPickerEvent.OpenLinkAllConfirmation -> _uiState.update { it.copy(showLinkAllConfirmation = true) }
            is CatalogueStockItemPickerEvent.DismissLinkAllConfirmation -> _uiState.update { it.copy(showLinkAllConfirmation = false) }
            is CatalogueStockItemPickerEvent.ConfirmLinkAll -> linkAll()
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

    /** Creates a Draft for every currently-unlinked Stock Item via the batched, chunked-
     * transactional repository call (TD-051) rather than one repository call per item — see
     * [CatalogueRepository.createDraftsFromStockItems]'s own doc comment for why. */
    private fun linkAll() {
        val id = companyId ?: return
        if (_uiState.value.isLinking) return
        val stockItemIds = _uiState.value.allItems.map { it.id }
        if (stockItemIds.isEmpty()) return
        viewModelScope.launch {
            val total = stockItemIds.size
            _uiState.update {
                it.copy(isLinking = true, showLinkAllConfirmation = false, linkAllProgress = LinkAllProgress(0, total))
            }
            // Tracks the last count the repository actually reported as persisted, so a failure
            // partway through still leaves [linkedCount] equal to what is truly on disk -- never
            // an overcount, since the repository's own onProgress contract only fires after a
            // chunk has already committed (see that method's doc comment).
            var lastReportedLinked = 0
            val linkedCount = try {
                repository.createDraftsFromStockItems(id, stockItemIds, clock.now()) { linked, linkedTotal ->
                    lastReportedLinked = linked
                    _uiState.update { it.copy(linkAllProgress = LinkAllProgress(linked, linkedTotal)) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastReportedLinked
            }
            val remaining = repository.listUnlinkedStockItems(id)
            _uiState.update { it.copy(isLinking = false, allItems = remaining, linkAllProgress = null) }
            _linkedAll.emit(linkedCount)
        }
    }
}
