package com.budcom.android.feature.dashboard.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budcom.android.core.common.AppResult
import com.budcom.android.feature.dashboard.domain.model.DashboardSessionValidity
import com.budcom.android.feature.dashboard.domain.usecase.ObserveDashboardContextUseCase
import com.budcom.android.feature.dashboard.domain.usecase.ProbeConnectorConnectionUseCase
import com.budcom.android.feature.dashboard.domain.usecase.RefreshDashboardUseCase
import com.budcom.android.feature.dashboard.domain.usecase.ValidateDashboardSessionUseCase
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
class DashboardViewModel @Inject constructor(
    private val refreshDashboard: RefreshDashboardUseCase,
    private val probeConnectorConnection: ProbeConnectorConnectionUseCase,
    private val validateDashboardSession: ValidateDashboardSessionUseCase,
    private val observeDashboardContext: ObserveDashboardContextUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _navigation = MutableSharedFlow<DashboardNavigation>(extraBufferCapacity = 8)
    val navigation: SharedFlow<DashboardNavigation> = _navigation.asSharedFlow()

    @Volatile
    private var refreshInFlight = false

    init {
        viewModelScope.launch {
            observeDashboardContext().collect { context ->
                _uiState.update { state ->
                    state.copy(
                        isOnline = context.isOnline,
                        baseUrl = context.baseUrl,
                        selectedCompanyId = context.selectedCompanyId,
                        sessionValidity = if (context.selectedCompanyId.isNullOrBlank()) {
                            DashboardSessionValidity.NoCompany
                        } else if (state.sessionValidity == DashboardSessionValidity.NoCompany) {
                            DashboardSessionValidity.Unknown
                        } else {
                            state.sessionValidity
                        },
                        selectedCompanyName = if (context.selectedCompanyId.isNullOrBlank()) {
                            null
                        } else {
                            state.selectedCompanyName
                        },
                    ).withAuthoritativeMode()
                }
            }
        }
        refresh(isInitial = true)
    }

    fun onEvent(event: DashboardEvent) {
        when (event) {
            DashboardEvent.Refresh -> refresh(isInitial = false)
            DashboardEvent.TestConnection -> testConnectionOnly()
            DashboardEvent.ValidateSession -> validateSessionOnly()
            DashboardEvent.OpenServerConfig -> emitNav(DashboardNavigation.ServerConfig)
            DashboardEvent.OpenCompanySelection -> emitNav(DashboardNavigation.CompanySelection)
        }
    }

    private fun emitNav(target: DashboardNavigation) {
        viewModelScope.launch { _navigation.emit(target) }
    }

    private fun refresh(isInitial: Boolean) {
        if (refreshInFlight) return
        refreshInFlight = true
        viewModelScope.launch {
            _uiState.update {
                if (isInitial && !it.hasContent) {
                    it.copy(isInitialLoading = true)
                } else {
                    it.copy(isRefreshing = true)
                }
            }
            _uiState.update { mapSnapshotToUiState(refreshDashboard(), it) }
            refreshInFlight = false
        }
    }

    private fun testConnectionOnly() {
        if (refreshInFlight || _uiState.value.isTestingConnection) return
        refreshInFlight = true
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingConnection = true) }
            when (val result = probeConnectorConnection()) {
                is AppResult.Success -> {
                    val probe = result.value
                    _uiState.update { state ->
                        state.copy(
                            isTestingConnection = false,
                            isInitialLoading = false,
                            connectorConnected = true,
                            readinessLabel = readinessLabelFromStatus(probe.readiness?.status),
                            lastSuccessfulHealthCheckEpochMillis = probe.checkedAtEpochMillis,
                            connectorError = null,
                        ).withAuthoritativeMode()
                    }
                }
                is AppResult.Failure -> {
                    _uiState.update { state ->
                        state.copy(
                            isTestingConnection = false,
                            isInitialLoading = false,
                            connectorConnected = false,
                            connectorError = result.error.toDashboardUiError(),
                        ).withAuthoritativeMode()
                    }
                }
            }
            refreshInFlight = false
        }
    }

    private fun validateSessionOnly() {
        if (_uiState.value.isValidatingSession || refreshInFlight) return
        if (_uiState.value.selectedCompanyId.isNullOrBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isValidatingSession = true) }
            val result = validateDashboardSession()
            _uiState.update { state ->
                state.copy(
                    isValidatingSession = false,
                    selectedCompanyId = result.companyId ?: state.selectedCompanyId,
                    selectedCompanyName = result.companyName ?: state.selectedCompanyName,
                    sessionValidity = result.validity,
                    lastSuccessfulSessionValidationEpochMillis =
                        result.validatedAtEpochMillis
                            ?: state.lastSuccessfulSessionValidationEpochMillis,
                    sessionError = result.error?.toDashboardUiError(),
                ).withAuthoritativeMode()
            }
        }
    }
}

enum class DashboardNavigation {
    ServerConfig,
    CompanySelection,
}
