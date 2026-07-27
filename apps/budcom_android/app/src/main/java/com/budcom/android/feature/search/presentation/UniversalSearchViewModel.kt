package com.budcom.android.feature.search.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budcom.android.core.network.NetworkConnectivityObserver
import com.budcom.android.feature.company.domain.port.CompanySessionPort
import com.budcom.android.feature.search.domain.UniversalSearchDefaults
import com.budcom.android.feature.search.domain.model.SearchQuery
import com.budcom.android.feature.search.domain.model.SearchSection
import com.budcom.android.feature.search.domain.usecase.ExecuteUniversalSearchUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UniversalSearchViewModel @Inject constructor(
    private val executeSearch: ExecuteUniversalSearchUseCase,
    private val companySession: CompanySessionPort,
    private val connectivityObserver: NetworkConnectivityObserver,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UniversalSearchUiState())
    val uiState: StateFlow<UniversalSearchUiState> = _uiState.asStateFlow()

    private val _navigation = MutableSharedFlow<UniversalSearchNavigation>(extraBufferCapacity = 8)
    val navigation: SharedFlow<UniversalSearchNavigation> = _navigation.asSharedFlow()

    private var debounceJob: Job? = null
    private var searchJob: Job? = null
    private var companyId: String? = null

    init {
        viewModelScope.launch {
            connectivityObserver.isOnline.collect { online ->
                _uiState.update { it.copy(isOnline = online) }
            }
        }
        viewModelScope.launch {
            companySession.observeSelectedCompanyId().collect { id ->
                companyId = id
            }
        }
    }

    fun onEvent(event: UniversalSearchEvent) {
        when (event) {
            is UniversalSearchEvent.QueryChanged -> onQueryChanged(event.query)
            UniversalSearchEvent.ClearQuery -> clearQuery()
            UniversalSearchEvent.Retry -> retryActiveQuery()
            is UniversalSearchEvent.RetrySection -> retryActiveQuery()
            is UniversalSearchEvent.ResultClicked -> onResultClicked(event.row)
            is UniversalSearchEvent.SeeAll -> onSeeAll(event.section)
        }
    }

    private fun onQueryChanged(raw: String) {
        val capped = raw.take(UniversalSearchDefaults.MAX_QUERY_LENGTH)
        _uiState.update { it.copy(query = capped) }
        debounceJob?.cancel()
        searchJob?.cancel()

        val normalized = SearchQuery(capped).normalized()
        if (normalized == null) {
            _uiState.update {
                it.copy(
                    isSearching = false,
                    hasSearched = false,
                    activeQuery = null,
                    voucherDateFrom = null,
                    voucherDateTo = null,
                    sections = emptyList(),
                )
            }
            return
        }

        debounceJob = viewModelScope.launch {
            delay(UniversalSearchDefaults.SEARCH_DEBOUNCE_MS)
            runSearch(normalized)
        }
    }

    private fun clearQuery() {
        debounceJob?.cancel()
        searchJob?.cancel()
        _uiState.update {
            UniversalSearchUiState(isOnline = it.isOnline)
        }
    }

    private fun retryActiveQuery() {
        val query = _uiState.value.activeQuery ?: SearchQuery(_uiState.value.query).normalized()
        if (query == null) return
        debounceJob?.cancel()
        runSearch(query)
    }

    private fun runSearch(normalized: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSearching = true,
                    hasSearched = true,
                    activeQuery = normalized,
                    sections = loadingSections(),
                )
            }
            val selectedCompany = companyId
                ?: companySession.observeSelectedCompanyId().first()
            val result = executeSearch(
                query = SearchQuery(normalized),
                companyId = selectedCompany,
            )
            if (result == null) {
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        hasSearched = false,
                        activeQuery = null,
                        sections = emptyList(),
                    )
                }
                return@launch
            }
            _uiState.update {
                it.copy(
                    isSearching = false,
                    hasSearched = true,
                    activeQuery = result.query,
                    voucherDateFrom = result.voucherDateRange.from,
                    voucherDateTo = result.voucherDateRange.to,
                    sections = result.toUiSections(),
                )
            }
        }
    }

    private fun onResultClicked(row: SearchResultRowUi) {
        val query = _uiState.value.activeQuery.orEmpty()
        when (row.section) {
            SearchSection.Ledgers ->
                _navigation.tryEmit(UniversalSearchNavigation.LedgerBrowser(query))
            SearchSection.StockItems ->
                _navigation.tryEmit(UniversalSearchNavigation.StockItemBrowser(query))
            SearchSection.Vouchers ->
                _navigation.tryEmit(UniversalSearchNavigation.VoucherDetails(row.id))
        }
    }

    private fun onSeeAll(section: SearchSection) {
        val query = _uiState.value.activeQuery.orEmpty()
        if (query.isBlank()) return
        when (section) {
            SearchSection.Ledgers ->
                _navigation.tryEmit(UniversalSearchNavigation.LedgerBrowser(query))
            SearchSection.StockItems ->
                _navigation.tryEmit(UniversalSearchNavigation.StockItemBrowser(query))
            SearchSection.Vouchers ->
                _navigation.tryEmit(UniversalSearchNavigation.VoucherBrowser(query))
        }
    }
}
