package com.budcom.android.feature.serverconfig.presentation

import com.budcom.android.feature.serverconfig.domain.model.ConnectorConnectionProbe

/**
 * Immutable UI state for the server configuration + health vertical slice.
 */
data class ServerConfigUiState(
    val urlInput: String = "",
    val savedUrl: String = "",
    val urlValidationError: String? = null,
    val isSaving: Boolean = false,
    val isTesting: Boolean = false,
    val saveFeedback: String? = null,
    val connection: ConnectionUiState = ConnectionUiState.Idle,
) {
    val isBusy: Boolean get() = isSaving || isTesting
}

sealed interface ConnectionUiState {
    data object Idle : ConnectionUiState
    data object Loading : ConnectionUiState
    data class Success(val probe: ConnectorConnectionProbe) : ConnectionUiState
    data class Error(
        val kind: ConnectionErrorKind,
        val message: String,
    ) : ConnectionUiState
}

enum class ConnectionErrorKind {
    Offline,
    Timeout,
    Http,
    Serialization,
    Unknown,
}

sealed interface ServerConfigEvent {
    data class UrlChanged(val value: String) : ServerConfigEvent
    data object SaveClicked : ServerConfigEvent
    data object TestConnectionClicked : ServerConfigEvent
    data object RetryClicked : ServerConfigEvent
    data object SaveFeedbackConsumed : ServerConfigEvent
}
