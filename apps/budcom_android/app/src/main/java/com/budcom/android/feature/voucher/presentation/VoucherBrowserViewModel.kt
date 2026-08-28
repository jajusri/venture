package com.budcom.android.feature.voucher.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.network.NetworkConnectivityObserver
import com.budcom.android.feature.company.domain.port.CompanySessionPort
import com.budcom.android.feature.masterdata.domain.MasterDataBrowserDefaults
import com.budcom.android.feature.masterdata.presentation.MasterDataUiError
import com.budcom.android.feature.voucher.domain.model.VoucherDateRangeDefaults
import com.budcom.android.feature.voucher.domain.model.VoucherQuery
import com.budcom.android.feature.voucher.domain.usecase.LoadVouchersUseCase
import com.budcom.android.feature.voucher.domain.usecase.ReconcileVoucherWindowsUseCase
import com.budcom.android.feature.voucher.domain.usecase.RefreshVouchersUseCase
import com.budcom.android.feature.voucher.domain.usecase.VoucherReconciliationOutcome
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
    private val savedStateHandle: SavedStateHandle,
    private val loadVouchers: LoadVouchersUseCase,
    private val refreshVouchers: RefreshVouchersUseCase,
    private val reconcileVoucherWindows: ReconcileVoucherWindowsUseCase,
    private val companySession: CompanySessionPort,
    private val connectivityObserver: NetworkConnectivityObserver,
) : ViewModel() {

    private val initialQuery = savedStateHandle.get<String>(Routes.QUERY_ARG).orEmpty()
    private val restoredDateFrom = savedStateHandle.get<String>(DATE_FROM_ARG)
    private val restoredDateTo = savedStateHandle.get<String>(DATE_TO_ARG)
    private val restoredTypeFilter = savedStateHandle.get<String>(TYPE_FILTER_ARG)

    private val fallbackRange = VoucherDateRangeDefaults.lastDaysInclusive()

    private val _uiState = MutableStateFlow(
        VoucherBrowserUiState(
            searchQuery = initialQuery,
            dateFrom = restoredDateFrom ?: fallbackRange.from,
            dateTo = restoredDateTo ?: fallbackRange.to,
            selectedTypeFilter = restoredTypeFilter,
        ),
    )
    val uiState: StateFlow<VoucherBrowserUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var loadJob: Job? = null
    private var reconcileJob: Job? = null

    init {
        viewModelScope.launch {
            connectivityObserver.isOnline.collect { online ->
                _uiState.update { it.copy(isOnline = online) }
            }
        }
        viewModelScope.launch {
            companySession.observeSelectedCompanyId().distinctUntilChanged().collect { companyId ->
                // A new company has no bearing on a reconciliation walk for the previous one.
                reconcileJob?.cancel()
                reconcileJob = null
                _uiState.update {
                    it.copy(
                        companyId = companyId,
                        historyReconciliationStatus = VoucherHistoryReconciliationStatus.NotStarted,
                        selectedTypeFilter = null,
                    )
                }
                savedStateHandle[TYPE_FILTER_ARG] = null
                onEvent(VoucherBrowserEvent.Load)
            }
        }
    }

    /**
     * USER EXPLICIT VOUCHER SYNC (BUDCOM MVP-1 Section 3): the fast recent-window refresh above
     * already committed and is what the visible list/[lastSyncedAt] reflect. This continues the
     * rest of the permanent snapshot architecture in the background — progressively older
     * windows, up to the company's authoritative history scope — without blocking or re-running
     * the foreground refresh. [ReconcileVoucherWindowsUseCase]'s own per-company single-flight
     * guard makes it safe to call opportunistically on every explicit sync: an already-running
     * walk just returns immediately and this is a no-op.
     */
    private fun continueBackgroundReconciliation(companyId: String) {
        if (reconcileJob?.isActive == true) return
        reconcileJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    historyReconciliationStatus = VoucherHistoryReconciliationStatus.InProgress,
                    historyReconciliationScopeIsAuthoritative = null,
                )
            }
            val outcome = reconcileVoucherWindows(companyId)
            val (status, scopeIsAuthoritative) = when (outcome) {
                is VoucherReconciliationOutcome.Completed ->
                    VoucherHistoryReconciliationStatus.Completed to outcome.scopeIsAuthoritative
                is VoucherReconciliationOutcome.PartiallyCompleted ->
                    VoucherHistoryReconciliationStatus.Failed to outcome.scopeIsAuthoritative
                VoucherReconciliationOutcome.AlreadyRunning -> return@launch
            }
            _uiState.update {
                it.copy(
                    historyReconciliationStatus = status,
                    historyReconciliationScopeIsAuthoritative = scopeIsAuthoritative,
                )
            }
        }
    }

    fun onEvent(event: VoucherBrowserEvent) {
        when (event) {
            VoucherBrowserEvent.Load -> load(page = 1, append = false, refreshing = false)
            VoucherBrowserEvent.Refresh -> load(page = 1, append = false, refreshing = true)
            // Retry's error block is only ever shown when there is no cached content to fall
            // back to (see VoucherBrowserScreen's `state.error != null && !state.hasContent`
            // branch) — for a company that has never synced, a cache-only load fails
            // identically forever, so this specific case must go through the network-backed
            // refresh. When content already exists, preserve the existing cache-first Retry
            // behavior.
            VoucherBrowserEvent.Retry -> load(page = 1, append = false, refreshing = !_uiState.value.hasContent)
            VoucherBrowserEvent.LoadNextPage -> {
                val state = _uiState.value
                if (!state.canLoadMore || state.isBusy) return
                load(page = state.page + 1, append = true, refreshing = false)
            }
            is VoucherBrowserEvent.SearchChanged -> {
                savedStateHandle[Routes.QUERY_ARG] = event.query
                _uiState.update { it.copy(searchQuery = event.query) }
                searchJob?.cancel()
                searchJob = viewModelScope.launch {
                    delay(MasterDataBrowserDefaults.SEARCH_DEBOUNCE_MS)
                    load(page = 1, append = false, refreshing = false)
                }
            }
            is VoucherBrowserEvent.DateFromChanged -> {
                savedStateHandle[DATE_FROM_ARG] = event.value
                _uiState.update { it.copy(dateFrom = event.value) }
            }
            is VoucherBrowserEvent.DateToChanged -> {
                savedStateHandle[DATE_TO_ARG] = event.value
                _uiState.update { it.copy(dateTo = event.value) }
            }
            VoucherBrowserEvent.ApplyDateRange -> {
                load(page = 1, append = false, refreshing = false)
            }
            is VoucherBrowserEvent.TypeFilterChanged -> {
                savedStateHandle[TYPE_FILTER_ARG] = event.type
                _uiState.update { it.copy(selectedTypeFilter = event.type) }
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
                    if (refreshing) continueBackgroundReconciliation(companyId)
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

    companion object {
        const val DATE_FROM_ARG = "voucherDateFrom"
        const val DATE_TO_ARG = "voucherDateTo"
        const val TYPE_FILTER_ARG = "voucherTypeFilter"
    }
}
