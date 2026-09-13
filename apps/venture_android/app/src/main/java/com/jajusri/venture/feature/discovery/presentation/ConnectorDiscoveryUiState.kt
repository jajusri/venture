package com.jajusri.venture.feature.discovery.presentation

import com.jajusri.venture.core.discovery.DiscoveredConnector

/** Immutable UI state for first-install Connector discovery/enrolment. */
data class ConnectorDiscoveryUiState(
    val phase: ConnectorDiscoveryPhase = ConnectorDiscoveryPhase.Discovering,
    val discovered: List<DiscoveredConnector> = emptyList(),
    val selected: DiscoveredConnector? = null,
    val errorMessage: String? = null,
)

enum class ConnectorDiscoveryPhase {
    Discovering,
    Found,
    Empty,
    Confirming,
    Pairing,
}

sealed interface ConnectorDiscoveryEvent {
    data object Retry : ConnectorDiscoveryEvent
    data class Select(val candidate: DiscoveredConnector) : ConnectorDiscoveryEvent
    data object ConfirmPairing : ConnectorDiscoveryEvent
    data object CancelSelection : ConnectorDiscoveryEvent
    data object OpenManualConfig : ConnectorDiscoveryEvent
}

sealed interface ConnectorDiscoveryNavigation {
    data object EnrolmentComplete : ConnectorDiscoveryNavigation
    data object OpenServerConfig : ConnectorDiscoveryNavigation
}

/** First 8 characters of the stable Connector ID, for a non-IP-emphasizing display fingerprint. */
fun DiscoveredConnector.shortFingerprint(): String = connectorId.take(8)
