package com.jajusri.venture.core.connection.presentation

/**
 * Additive connection-status presentation model. Not wired into any existing screen this
 * pass — exposed for a future screen to consume.
 */
sealed class ConnectorConnectionUiState {
    data object Resolving : ConnectorConnectionUiState()

    data class Connected(
        val connectorId: String,
        val pairedName: String,
        val lastSuccessfulConnectionAtEpochMillis: Long?,
    ) : ConnectorConnectionUiState()

    data object Reconnecting : ConnectorConnectionUiState()

    data object Offline : ConnectorConnectionUiState()
}
