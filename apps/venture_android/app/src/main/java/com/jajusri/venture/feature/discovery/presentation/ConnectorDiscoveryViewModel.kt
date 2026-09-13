package com.jajusri.venture.feature.discovery.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jajusri.venture.core.connection.ConnectorEnrolmentService
import com.jajusri.venture.core.connection.EnrolmentResult
import com.jajusri.venture.core.discovery.DiscoveredConnector
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

/**
 * Drives first-install Connector discovery and user-confirmed identity enrolment.
 *
 * Never pairs without an explicit [ConnectorDiscoveryEvent.ConfirmPairing] following an
 * explicit [ConnectorDiscoveryEvent.Select] — see [ConnectorEnrolmentService.pair] for the
 * independent health/identity re-verification performed before anything is persisted.
 */
@HiltViewModel
class ConnectorDiscoveryViewModel @Inject constructor(
    private val enrolmentService: ConnectorEnrolmentService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConnectorDiscoveryUiState())
    val uiState: StateFlow<ConnectorDiscoveryUiState> = _uiState.asStateFlow()

    private val _navigation = MutableSharedFlow<ConnectorDiscoveryNavigation>(extraBufferCapacity = 4)
    val navigation: SharedFlow<ConnectorDiscoveryNavigation> = _navigation.asSharedFlow()

    init {
        startDiscovery()
    }

    fun onEvent(event: ConnectorDiscoveryEvent) {
        when (event) {
            ConnectorDiscoveryEvent.Retry -> startDiscovery()
            is ConnectorDiscoveryEvent.Select -> selectCandidate(event.candidate)
            ConnectorDiscoveryEvent.ConfirmPairing -> confirmPairing()
            ConnectorDiscoveryEvent.CancelSelection -> cancelSelection()
            ConnectorDiscoveryEvent.OpenManualConfig -> emitNav(ConnectorDiscoveryNavigation.OpenServerConfig)
        }
    }

    private fun startDiscovery() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    phase = ConnectorDiscoveryPhase.Discovering,
                    selected = null,
                    errorMessage = null,
                )
            }
            val results = enrolmentService.discover(DISCOVERY_TIMEOUT_MS)
            _uiState.update {
                it.copy(
                    phase = if (results.isEmpty()) ConnectorDiscoveryPhase.Empty else ConnectorDiscoveryPhase.Found,
                    discovered = results,
                )
            }
        }
    }

    private fun selectCandidate(candidate: DiscoveredConnector) {
        if (_uiState.value.phase != ConnectorDiscoveryPhase.Found) return
        _uiState.update {
            it.copy(phase = ConnectorDiscoveryPhase.Confirming, selected = candidate, errorMessage = null)
        }
    }

    private fun cancelSelection() {
        if (_uiState.value.phase != ConnectorDiscoveryPhase.Confirming) return
        _uiState.update { it.copy(phase = ConnectorDiscoveryPhase.Found, selected = null) }
    }

    private fun confirmPairing() {
        val state = _uiState.value
        if (state.phase != ConnectorDiscoveryPhase.Confirming) return
        val candidate = state.selected ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(phase = ConnectorDiscoveryPhase.Pairing) }
            when (val result = enrolmentService.pair(candidate)) {
                is EnrolmentResult.Paired -> {
                    emitNav(ConnectorDiscoveryNavigation.EnrolmentComplete)
                }
                EnrolmentResult.Unreachable -> {
                    _uiState.update {
                        it.copy(
                            phase = ConnectorDiscoveryPhase.Found,
                            selected = null,
                            errorMessage = "This Connector is no longer reachable. Try again.",
                        )
                    }
                }
                is EnrolmentResult.IdentityMismatch -> {
                    _uiState.update {
                        it.copy(
                            phase = ConnectorDiscoveryPhase.Found,
                            selected = null,
                            errorMessage = "The Connector's identity changed since it was discovered. Refusing to pair.",
                        )
                    }
                }
            }
        }
    }

    private fun emitNav(target: ConnectorDiscoveryNavigation) {
        viewModelScope.launch { _navigation.emit(target) }
    }

    private companion object {
        const val DISCOVERY_TIMEOUT_MS = 6_000L
    }
}
