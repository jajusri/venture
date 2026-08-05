package com.budcom.android.feature.voucher.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.network.NetworkConnectivityObserver
import com.budcom.android.feature.company.domain.port.CompanySessionPort
import com.budcom.android.feature.masterdata.domain.MasterDataBrowserDefaults
import com.budcom.android.feature.masterdata.presentation.MasterDataUiError
import com.budcom.android.feature.voucher.domain.model.VoucherQuery
import com.budcom.android.feature.voucher.domain.usecase.LoadVouchersUseCase
import com.budcom.android.feature.voucher.domain.usecase.RefreshVouchersUseCase
import com.budcom.android.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VoucherBrowserViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val loadVouchers: LoadVouchersUseCase,
    private val refreshVouchers: RefreshVouchersUseCase,
    private val companySession: CompanySessionPort,
    private val connectivityObserver: NetworkConnectivityObserver,
) : ViewModel() {

    private val initialQuery = savedStateHandle.get<String>(Routes.QUERY_ARG).orEmpty()

    private val _uiState = MutableStateFlow(VoucherBrowserUiState(searchQuery = initialQuery))
    val uiState: StateFlow<VoucherBrowserUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            connectivityObserver.isOnline.collect { online ->
                _uiState.update { it.copy(isOnline = online) }
            }
        }
        viewModelScope.launch {
            companySession.observeSelectedCompanyId().distinctUntilChanged().collect { companyId ->
                _uiState.update { it.copy(companyId = companyId) }
                onEvent(VoucherBrowserEvent.Load)
            }
        }
    }

    fun onEvent(event: VoucherBrowserEvent) {
        when (event) {
            VoucherBrowserEvent.Load -> load(page = 1, append = false, refreshing = false)
            VoucherBrowserEvent.Refresh -> load(page = 1, append = false, refreshing = true)
            VoucherBrowserEvent.Retry -> load(page = 1, append = false, refreshing = false)
            VoucherBrowserEvent.LoadNextPage -> {
                val state = _uiState.value
                if (!state.canLoadMore || state.isBusy) return
                load(page = state.page + 1, append = true, refreshing = false)
            }
            is VoucherBrowserEvent.SearchChanged -> {
                _uiState.update { it.copy(searchQuery = event.query) }
                searchJob?.cancel()
                searchJob = viewModelScope.launch {
                    delay(MasterDataBrowserDefaults.SEARCH_DEBOUNCE_MS)
                    load(page = 1, append = false, refreshing = false)
                }
            }
            is VoucherBrowserEvent.DateFromChanged -> {
                _uiState.update { it.copy(dateFrom = event.value) }
            }
            is VoucherBrowserEvent.DateToChanged -> {
                _uiState.update { it.copy(dateTo = event.value) }
            }
            VoucherBrowserEvent.ApplyDateRange -> {
                load(page = 1, append = false, refreshing = false)
            }
        }
    }

    private fun load(page: Int, append: Boolean, refreshing: Boolean) {
        if (append && loadJob?.isActive == true) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val stateSnapshot = _uiState.value
            val companyId = stateSnapshot.companyId
                ?: companySession.observeSelectedCompanyId().first()
            if (companyId.isNullOrBlank()) {
                _uiState.update {
                    it.copy(
                        isInitialLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        companyId = null,
                        vouchers = emptyList(),
                        error = MasterDataUiError.Message("Select a company before browsing vouchers."),
                    )
                }
                return@launch
            }

            val dateRange = tryParseDateRange(stateSnapshot.dateFrom, stateSnapshot.dateTo)
            if (dateRange == null) {
                _uiState.update {
                    it.copy(
                        isInitialLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        error = MasterDataUiError.Message(
                            "Enter a valid date range as YYYY-MM-DD (from must not follow to).",
                        ),
                    )
                }
                return@launch
            }

            _uiState.update {
                when {
                    refreshing -> it.copy(isRefreshing = true, refreshError = null, companyId = companyId)
                    append -> it.copy(isLoadingMore = true, error = null, companyId = companyId)
                    it.hasContent -> it.copy(isRefreshing = true, refreshError = null, companyId = companyId)
                    else -> it.copy(isInitialLoading = true, error = null, companyId = companyId)
                }
            }

            val query = VoucherQuery(
                companyId = companyId,
                dateRange = dateRange,
                searchText = stateSnapshot.searchQuery.trim().ifEmpty { null },
                page = page,
                pageSize = stateSnapshot.pageSize,
            )
            val result = if (refreshing) refreshVouchers(query) else loadVouchers(query)
            when (result) {
                is AppResult.Success -> {
                    val pageData = result.value
                    _uiState.update { state ->
                        val rows = if (append) {
                            state.vouchers + pageData.toRows()
                        } else {
                            pageData.toRows()
                        }
                        state.copy(
                            isInitialLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            vouchers = rows,
                            page = pageData.page,
                            pageSize = pageData.pageSize,
                            totalItems = pageData.totalItems,
                            totalPages = pageData.totalPages,
                            canLoadMore = pageData.canLoadMore,
                            error = null,
                            refreshError = null,
                            cacheState = pageData.cacheState,
                            lastSyncedAt = pageData.lastSyncedAt,
                        )
                    }
                }
                is AppResult.Failure -> {
                    _uiState.update { state ->
                        if (refreshing && state.hasContent) {
                            // A failed refresh must never hide or replace valid cached rows.
                            state.copy(
                                isInitialLoading = false,
                                isRefreshing = false,
                                isLoadingMore = false,
                                refreshError = refreshFailedMessage(state.lastSyncedAt),
                                cacheState = com.budcom.android.feature.voucher.domain.model.VoucherCacheState.Offline,
                            )
                        } else {
                            state.copy(
                                isInitialLoading = false,
                                isRefreshing = false,
                                isLoadingMore = false,
                                error = result.error.toVoucherUiError(),
                                cacheState = if (state.hasContent) state.cacheState else com.budcom.android.feature.voucher.domain.model.VoucherCacheState.NoCache,
                            )
                        }
                    }
                }
            }
        }
    }
}
