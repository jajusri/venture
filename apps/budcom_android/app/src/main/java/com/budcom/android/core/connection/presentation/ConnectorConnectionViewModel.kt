package com.budcom.android.core.connection.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budcom.android.core.connection.ConnectionResolution
import com.budcom.android.core.connection.ConnectorConnectionResolver
import com.budcom.android.core.util.TimeProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Additive connection-status view model. Not wired into any existing screen this pass —
 * a future screen composes [uiState] and calls [resolveConnection] on app start / retry.
 */
@HiltViewModel
class ConnectorConnectionViewModel @Inject constructor(
    private val resolver: ConnectorConnectionResolver,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ConnectorConnectionUiState>(ConnectorConnectionUiState.Resolving)
    val uiState: StateFlow<ConnectorConnectionUiState> = _uiState.asStateFlow()

    fun resolveConnection() {
        viewModelScope.launch {
            _uiState.value = ConnectorConnectionUiState.Reconnecting
            _uiState.value = when (val resolution = resolver.resolve()) {
                is ConnectionResolution.Connected -> ConnectorConnectionUiState.Connected(
                    connectorId = resolution.connectorId,
                    pairedName = resolution.friendlyName,
                    lastSuccessfulConnectionAtEpochMillis = timeProvider.nowEpochMillis(),
                )
                ConnectionResolution.Offline -> ConnectorConnectionUiState.Offline
            }
        }
    }
}
