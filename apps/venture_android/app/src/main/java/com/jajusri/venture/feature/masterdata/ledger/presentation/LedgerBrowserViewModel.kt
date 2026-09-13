package com.jajusri.venture.feature.masterdata.ledger.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jajusri.venture.core.common.AppResult
import com.jajusri.venture.core.network.NetworkConnectivityObserver
import com.jajusri.venture.feature.masterdata.domain.MasterDataBrowserDefaults
import com.jajusri.venture.feature.masterdata.ledger.domain.model.LedgerQuery
import com.jajusri.venture.feature.masterdata.ledger.domain.usecase.LoadLedgersUseCase
import com.jajusri.venture.feature.masterdata.ledger.domain.usecase.RefreshLedgersUseCase
import com.jajusri.venture.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LedgerBrowserViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val loadLedgers: LoadLedgersUseCase,
    private val refreshLedgers: RefreshLedgersUseCase,
    private val connectivityObserver: NetworkConnectivityObserver,
) : ViewModel() {

    private val initialQuery = savedStateHandle.get<String>(Routes.QUERY_ARG).orEmpty()

    private val _uiState = MutableStateFlow(LedgerBrowserUiState(searchQuery = initialQuery))
    val uiState: StateFlow<LedgerBrowserUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<LedgerBrowserEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<LedgerBrowserEffect> = _effects.asSharedFlow()

    private var searchJob: Job? = null
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            connectivityObserver.isOnline.collect { online ->
                _uiState.update { it.copy(isOnline = online) }
            }
        }
        onEvent(LedgerBrowserEvent.Load)
    }

    fun onEvent(event: LedgerBrowserEvent) {
        when (event) {
            LedgerBrowserEvent.Load -> load(page = 1, append = false, refreshing = false)
            LedgerBrowserEvent.Refresh -> load(page = 1, append = false, refreshing = true)
            // Retry's error block is only ever shown when there is no cached content to fall
            // back to (see LedgerBrowserScreen's `state.error != null && !state.hasContent`
            // branch) — for a company that has never synced, a cache-only load fails
            // identically forever, so this specific case must go through the network-backed
            // refresh. When content already exists, preserve the existing cache-first Retry
            // behavior (offline-first invariant covered by
            // `Load, search, retry, and pagination never touch the network path`).
            LedgerBrowserEvent.Retry -> load(page = 1, append = false, refreshing = !_uiState.value.hasContent)
            LedgerBrowserEvent.LoadNextPage -> {
                val state = _uiState.value
                if (!state.canLoadMore || state.isBusy) return
                load(page = state.page + 1, append = true, refreshing = false)
            }
            is LedgerBrowserEvent.SearchChanged -> {
                savedStateHandle[Routes.QUERY_ARG] = event.query
                _uiState.update { it.copy(searchQuery = event.query) }
                searchJob?.cancel()
                searchJob = viewModelScope.launch {
                    delay(MasterDataBrowserDefaults.SEARCH_DEBOUNCE_MS)
                    load(page = 1, append = false, refreshing = false)
                }
            }
            is LedgerBrowserEvent.LedgerTapped -> {
                val notice = when {
                    event.ledgerId.isBlank() -> "Unable to open this ledger: its identifier is missing."
                    _uiState.value.ledgers.none { it.id == event.ledgerId } ->
                        "Unable to open this ledger: it is no longer in the current list."
                    else -> null
                }
                if (notice != null) {
                    _uiState.update { it.copy(selectedLedgerNotice = notice) }
                } else {
                    viewModelScope.launch { _effects.emit(LedgerBrowserEffect.OpenLedgerStatement(event.ledgerId)) }
                }
            }
            LedgerBrowserEvent.DismissLedgerNotice -> {
                _uiState.update { it.copy(selectedLedgerNotice = null) }
            }
        }
    }

    private fun load(page: Int, append: Boolean, refreshing: Boolean) {
        // Allow refresh/search/retry to cancel in-flight work; skip only concurrent appends.
        if (append && loadJob?.isActive == true) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val queryText = _uiState.value.searchQuery
            _uiState.update {
                when {
                    refreshing -> it.copy(isRefreshing = true, error = null)
                    append -> it.copy(isLoadingMore = true, error = null)
                    it.hasContent -> it.copy(isRefreshing = true, error = null)
                    else -> it.copy(isInitialLoading = true, error = null)
                }
            }

            val query = LedgerQuery(
                text = queryText.trim().ifEmpty { null },
                page = page,
                pageSize = _uiState.value.pageSize,
            )
            val result = if (refreshing) refreshLedgers(query) else loadLedgers(query)
            when (result) {
                is AppResult.Success -> {
                    val pageData = result.value
                    _uiState.update { state ->
                        val rows = if (append) {
                            state.ledgers + pageData.toRows()
                        } else {
                            pageData.toRows()
                        }
                        state.copy(
                            isInitialLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            ledgers = rows,
                            page = pageData.page,
                            pageSize = pageData.pageSize,
                            totalItems = pageData.totalItems,
                            totalPages = pageData.totalPages,
                            canLoadMore = pageData.page < pageData.totalPages,
                            dataFreshnessAt = pageData.dataFreshnessAt,
                            error = null,
                        )
                    }
                }
                is AppResult.Failure -> {
                    _uiState.update { state ->
                        state.copy(
                            isInitialLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            error = result.error.toLedgerUiError(),
                        )
                    }
                }
            }
        }
    }
}
