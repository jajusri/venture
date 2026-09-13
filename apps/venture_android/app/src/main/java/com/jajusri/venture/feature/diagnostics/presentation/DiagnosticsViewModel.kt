package com.jajusri.venture.feature.diagnostics.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jajusri.venture.core.network.NetworkConnectivityObserver
import com.jajusri.venture.feature.diagnostics.domain.usecase.LoadDiagnosticsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
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
class DiagnosticsViewModel @Inject constructor(
    private val loadDiagnostics: LoadDiagnosticsUseCase,
    private val connectivityObserver: NetworkConnectivityObserver,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiagnosticsUiState())
    val uiState: StateFlow<DiagnosticsUiState> = _uiState.asStateFlow()

    private val _navigation = MutableSharedFlow<DiagnosticsNavigation>(extraBufferCapacity = 4)
    val navigation: SharedFlow<DiagnosticsNavigation> = _navigation.asSharedFlow()

    @Volatile
    private var refreshInFlight = false

    init {
        viewModelScope.launch {
            connectivityObserver.isOnline.collect { online ->
                _uiState.update { it.copy(isOnline = online) }
            }
        }
        refresh(isInitial = true, refreshSync = true)
    }

    fun onEvent(event: DiagnosticsEvent) {
        when (event) {
            DiagnosticsEvent.Refresh,
            DiagnosticsEvent.Retry,
            -> refresh(isInitial = false, refreshSync = true)
            DiagnosticsEvent.RecheckHealth,
            DiagnosticsEvent.RecheckReadiness,
            -> recheckHealthAndReadiness()
            DiagnosticsEvent.OpenServerConfig ->
                viewModelScope.launch { _navigation.emit(DiagnosticsNavigation.ServerConfig) }
            DiagnosticsEvent.OpenCompanySelection ->
                viewModelScope.launch { _navigation.emit(DiagnosticsNavigation.CompanySelection) }
        }
    }

    private fun refresh(isInitial: Boolean, refreshSync: Boolean) {
        if (refreshInFlight) return
        refreshInFlight = true
        viewModelScope.launch {
            _uiState.update {
                if (isInitial) {
                    it.copy(isInitialLoading = true, bannerError = null)
                } else {
                    it.copy(isRefreshing = true, bannerError = null)
                }
            }
            val snapshot = loadDiagnostics(refreshSync = refreshSync)
            _uiState.update {
                snapshot.toUiState(prior = it, isOnline = _uiState.value.isOnline)
            }
            refreshInFlight = false
        }
    }

    private fun recheckHealthAndReadiness() {
        if (refreshInFlight) return
        refreshInFlight = true
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, bannerError = null) }
            val snapshot = loadDiagnostics(refreshSync = false)
            _uiState.update { snapshot.toUiState(prior = it, isOnline = _uiState.value.isOnline) }
            refreshInFlight = false
        }
    }
}
