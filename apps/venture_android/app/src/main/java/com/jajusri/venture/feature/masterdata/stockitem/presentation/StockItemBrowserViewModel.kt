package com.jajusri.venture.feature.masterdata.stockitem.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jajusri.venture.core.common.AppResult
import com.jajusri.venture.core.network.NetworkConnectivityObserver
import com.jajusri.venture.feature.masterdata.domain.MasterDataBrowserDefaults
import com.jajusri.venture.feature.masterdata.stockitem.domain.model.StockItemQuery
import com.jajusri.venture.feature.masterdata.stockitem.domain.usecase.LoadStockItemsUseCase
import com.jajusri.venture.feature.masterdata.stockitem.domain.usecase.RefreshStockItemsUseCase
import com.jajusri.venture.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StockItemBrowserViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val loadStockItems: LoadStockItemsUseCase,
    private val refreshStockItems: RefreshStockItemsUseCase,
    private val connectivityObserver: NetworkConnectivityObserver,
) : ViewModel() {

    private val initialQuery = savedStateHandle.get<String>(Routes.QUERY_ARG).orEmpty()

    private val _uiState = MutableStateFlow(StockItemBrowserUiState(searchQuery = initialQuery))
    val uiState: StateFlow<StockItemBrowserUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            connectivityObserver.isOnline.collect { online ->
                _uiState.update { it.copy(isOnline = online) }
            }
        }
        onEvent(StockItemBrowserEvent.Load)
    }

    fun onEvent(event: StockItemBrowserEvent) {
        when (event) {
            StockItemBrowserEvent.Load -> load(page = 1, append = false, refreshing = false)
            StockItemBrowserEvent.Refresh -> load(page = 1, append = false, refreshing = true)
            StockItemBrowserEvent.Retry -> load(page = 1, append = false, refreshing = false)
            StockItemBrowserEvent.LoadNextPage -> {
                val state = _uiState.value
                if (!state.canLoadMore || state.isBusy) return
                load(page = state.page + 1, append = true, refreshing = false)
            }
            is StockItemBrowserEvent.SearchChanged -> {
                savedStateHandle[Routes.QUERY_ARG] = event.query
                _uiState.update { it.copy(searchQuery = event.query) }
                searchJob?.cancel()
                searchJob = viewModelScope.launch {
                    delay(MasterDataBrowserDefaults.SEARCH_DEBOUNCE_MS)
                    load(page = 1, append = false, refreshing = false)
                }
            }
        }
    }

    private fun load(page: Int, append: Boolean, refreshing: Boolean) {
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

            val query = StockItemQuery(
                text = queryText.trim().ifEmpty { null },
                page = page,
                pageSize = _uiState.value.pageSize,
            )
            val result = if (refreshing) refreshStockItems(query) else loadStockItems(query)
            when (result) {
                is AppResult.Success -> {
                    val pageData = result.value
                    _uiState.update { state ->
                        val rows = if (append) {
                            state.stockItems + pageData.toRows()
                        } else {
                            pageData.toRows()
                        }
                        state.copy(
                            isInitialLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            stockItems = rows,
                            page = pageData.pagination.page,
                            pageSize = pageData.pagination.pageSize,
                            totalItems = pageData.pagination.totalItems,
                            totalPages = pageData.pagination.totalPages,
                            canLoadMore = pageData.pagination.canLoadMore,
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
                            error = result.error.toStockItemUiError(),
                        )
                    }
                }
            }
        }
    }
}
