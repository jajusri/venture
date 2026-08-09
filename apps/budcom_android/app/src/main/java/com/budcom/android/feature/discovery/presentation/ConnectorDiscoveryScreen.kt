package com.budcom.android.feature.discovery.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budcom.android.BuildConfig
import com.budcom.android.core.discovery.DiscoveredConnector
import com.budcom.android.ui.theme.BudcomTheme

@Composable
fun ConnectorDiscoveryRoute(
    onEnrolmentComplete: () -> Unit,
    onOpenServerConfig: () -> Unit,
    viewModel: ConnectorDiscoveryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.navigation.collect { target ->
            when (target) {
                ConnectorDiscoveryNavigation.EnrolmentComplete -> onEnrolmentComplete()
                ConnectorDiscoveryNavigation.OpenServerConfig -> onOpenServerConfig()
            }
        }
    }
    ConnectorDiscoveryScreen(state = state, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectorDiscoveryScreen(
    state: ConnectorDiscoveryUiState,
    onEvent: (ConnectorDiscoveryEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize().testTag("connector_discovery_screen"),
        topBar = {
            TopAppBar(title = { Text("Find your BUDCOM Connector") })
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (state.phase) {
                ConnectorDiscoveryPhase.Discovering -> DiscoveringContent()
                ConnectorDiscoveryPhase.Empty -> EmptyContent(
                    errorMessage = state.errorMessage,
                    onRetry = { onEvent(ConnectorDiscoveryEvent.Retry) },
                    onManualConfig = { onEvent(ConnectorDiscoveryEvent.OpenManualConfig) },
                )
                ConnectorDiscoveryPhase.Found -> FoundContent(
                    discovered = state.discovered,
                    errorMessage = state.errorMessage,
                    onSelect = { onEvent(ConnectorDiscoveryEvent.Select(it)) },
                    onRetry = { onEvent(ConnectorDiscoveryEvent.Retry) },
                    onManualConfig = { onEvent(ConnectorDiscoveryEvent.OpenManualConfig) },
                )
                ConnectorDiscoveryPhase.Confirming -> ConfirmingContent(
                    candidate = state.selected,
                    onConfirm = { onEvent(ConnectorDiscoveryEvent.ConfirmPairing) },
                    onCancel = { onEvent(ConnectorDiscoveryEvent.CancelSelection) },
                )
                ConnectorDiscoveryPhase.Pairing -> PairingContent()
            }
        }
    }
}

@Composable
private fun DiscoveringContent() {
    Column(
        modifier = Modifier.fillMaxSize().testTag("discovery_discovering"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(12.dp))
        Text("Searching for BUDCOM Connector on this network…")
    }
}

@Composable
private fun EmptyContent(
    errorMessage: String?,
    onRetry: () -> Unit,
    onManualConfig: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().testTag("discovery_empty"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = "No BUDCOM Connector found.",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "Make sure BUDCOM Desktop is running and this device is on the same network.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("discovery_error"),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().testTag("discovery_retry_button"),
        ) {
            Text("Retry")
        }
        if (BuildConfig.DEBUG) {
            OutlinedButton(
                onClick = onManualConfig,
                modifier = Modifier.fillMaxWidth().testTag("discovery_manual_config_button"),
            ) {
                Text("Enter server address manually")
            }
        }
    }
}

@Composable
private fun FoundContent(
    discovered: List<DiscoveredConnector>,
    errorMessage: String?,
    onSelect: (DiscoveredConnector) -> Unit,
    onRetry: () -> Unit,
    onManualConfig: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().testTag("discovery_found"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = if (discovered.size > 1) {
                "Multiple Connectors found. Choose the one to pair with:"
            } else {
                "Connector found. Confirm to pair:"
            },
            style = MaterialTheme.typography.titleMedium,
        )
        errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("discovery_error"),
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(discovered, key = { it.connectorId }) { candidate ->
                DiscoveredConnectorCard(candidate = candidate, onClick = { onSelect(candidate) })
            }
        }
        OutlinedButton(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().testTag("discovery_retry_button"),
        ) {
            Text("Search again")
        }
        if (BuildConfig.DEBUG) {
            OutlinedButton(
                onClick = onManualConfig,
                modifier = Modifier.fillMaxWidth().testTag("discovery_manual_config_button"),
            ) {
                Text("Enter server address manually")
            }
        }
    }
}

@Composable
private fun DiscoveredConnectorCard(
    candidate: DiscoveredConnector,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag("discovered_connector_${candidate.connectorId}"),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = candidate.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "ID: ${candidate.shortFingerprint()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Reachable",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ConfirmingContent(
    candidate: DiscoveredConnector?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().testTag("discovery_confirming"),
        verticalArrangement = Arrangement.Center,
    ) {
        if (candidate != null) {
            Text(
                text = "Pair with this Connector?",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = candidate.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "ID: ${candidate.shortFingerprint()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        Button(
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth().testTag("discovery_confirm_button"),
        ) {
            Text("Confirm and pair")
        }
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth().testTag("discovery_cancel_button"),
        ) {
            Text("Cancel")
        }
    }
}

@Composable
private fun PairingContent() {
    Column(
        modifier = Modifier.fillMaxSize().testTag("discovery_pairing"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(12.dp))
        Text("Pairing…")
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun ConnectorDiscoveryScreenFoundPreview() {
    BudcomTheme {
        ConnectorDiscoveryScreen(
            state = ConnectorDiscoveryUiState(
                phase = ConnectorDiscoveryPhase.Found,
                discovered = listOf(
                    DiscoveredConnector(
                        connectorId = "9c98ff3c-3b1c-4429-a1a9-4055ef4c95e4",
                        name = "Front Desk",
                        host = "192.168.29.34",
                        port = 8080,
                        apiVersion = "1.0.0",
                        authRequired = false,
                    ),
                ),
            ),
            onEvent = {},
        )
    }
}
