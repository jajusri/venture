package com.jajusri.venture.feature.serverconfig.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jajusri.venture.R
import com.jajusri.venture.feature.serverconfig.domain.model.ConnectorConnectionProbe
import com.jajusri.venture.feature.serverconfig.domain.model.ConnectorHealth
import com.jajusri.venture.feature.serverconfig.domain.model.ConnectorReadiness
import com.jajusri.venture.feature.serverconfig.domain.model.ConnectorServiceStatus
import com.jajusri.venture.ui.theme.VentureTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Server configuration screen wired to [ServerConfigViewModel].
 */
@Composable
fun ServerConfigRoute(
    viewModel: ServerConfigViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ServerConfigScreen(
        state = state,
        onEvent = viewModel::onEvent,
    )
}

/**
 * Stateless server configuration + health UI for previews and Compose tests.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerConfigScreen(
    state: ServerConfigUiState,
    onEvent: (ServerConfigEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize().testTag("server_config_screen"),
        topBar = {
            TopAppBar(title = { Text(text = stringResource(R.string.server_config_title)) })
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.server_config_saved_label, state.savedUrl),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("saved_url"),
            )

            OutlinedTextField(
                value = state.urlInput,
                onValueChange = { onEvent(ServerConfigEvent.UrlChanged(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("url_input"),
                label = { Text(stringResource(R.string.server_config_url_label)) },
                isError = state.urlValidationError != null,
                supportingText = {
                    state.urlValidationError?.let {
                        Text(
                            text = it,
                            modifier = Modifier.testTag("url_validation_error"),
                        )
                    }
                },
                singleLine = true,
                enabled = !state.isBusy,
            )

            Button(
                onClick = { onEvent(ServerConfigEvent.SaveClicked) },
                enabled = !state.isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_button"),
            ) {
                Text(stringResource(R.string.server_config_save))
            }

            OutlinedButton(
                onClick = { onEvent(ServerConfigEvent.TestConnectionClicked) },
                enabled = !state.isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("test_button"),
            ) {
                Text(stringResource(R.string.server_config_test))
            }

            state.saveFeedback?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("save_feedback"),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.server_config_health_heading),
                style = MaterialTheme.typography.titleMedium,
            )
            ConnectionSection(
                connection = state.connection,
                onRetry = { onEvent(ServerConfigEvent.RetryClicked) },
            )
        }
    }
}

@Composable
private fun ConnectionSection(
    connection: ConnectionUiState,
    onRetry: () -> Unit,
) {
    when (connection) {
        ConnectionUiState.Idle -> {
            Text(
                text = stringResource(R.string.server_config_health_idle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("connection_idle"),
            )
        }
        ConnectionUiState.Loading -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("connection_loading"),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.semantics { contentDescription = "Testing Connector connection" },
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.server_config_health_loading))
            }
        }
        is ConnectionUiState.Success -> {
            HealthResultContent(
                probe = connection.probe,
                modifier = Modifier.testTag("connection_success"),
            )
        }
        is ConnectionUiState.Error -> {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.testTag("connection_error"),
            ) {
                Text(
                    text = stringResource(
                        R.string.server_config_health_error_kind,
                        connection.kind.name,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("connection_error_kind"),
                )
                Text(
                    text = connection.message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("connection_error_message"),
                )
                Button(
                    onClick = onRetry,
                    modifier = Modifier.testTag("retry_button"),
                ) {
                    Text(stringResource(R.string.server_config_retry))
                }
            }
        }
    }
}

@Composable
private fun HealthResultContent(
    probe: ConnectorConnectionProbe,
    modifier: Modifier = Modifier,
) {
    val health = probe.health
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = stringResource(R.string.health_status, health.status))
        Text(text = stringResource(R.string.health_schema_version, health.schemaVersion))
        Text(text = stringResource(R.string.health_connector_version, health.connectorVersion))
        Text(text = stringResource(R.string.health_tally_reachable, health.tallyReachable.toString()))
        Text(text = stringResource(R.string.health_read_only, health.readOnly.toString()))
        Text(text = stringResource(R.string.health_bind_host, health.bindHost))
        Text(text = stringResource(R.string.health_bind_port, health.bindPort))
        Text(text = stringResource(R.string.health_network_exposure, health.networkExposure))
        Text(
            text = stringResource(
                R.string.health_network_exposure_warning,
                health.networkExposureWarning ?: "null",
            ),
        )
        Text(
            text = stringResource(
                R.string.health_network_policy_satisfied,
                health.networkPolicySatisfied.toString(),
            ),
        )
        Text(
            text = stringResource(
                R.string.health_auth_lan,
                health.authenticatedLanAccessEnabled.toString(),
            ),
        )
        Text(
            text = stringResource(
                R.string.health_startup_correlation,
                health.startupCorrelationId ?: "null",
            ),
        )
        Text(
            text = stringResource(
                R.string.health_repository_available,
                health.repositoryAvailable.toString(),
            ),
        )
        Text(
            text = stringResource(
                R.string.health_database_accessible,
                health.databaseAccessible.toString(),
            ),
        )
        Text(text = stringResource(R.string.health_services_heading))
        health.services.forEach { service ->
            Text(
                text = stringResource(
                    R.string.health_service_row,
                    service.name,
                    service.running.toString(),
                    service.ready.toString(),
                    service.message ?: "null",
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        probe.readiness?.let { readiness ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = stringResource(R.string.ready_heading))
            Text(text = stringResource(R.string.ready_status, readiness.status))
            Text(text = stringResource(R.string.ready_http_status, readiness.httpStatus))
            Text(
                text = stringResource(
                    R.string.ready_repository_available,
                    readiness.repositoryAvailable.toString(),
                ),
            )
            Text(
                text = stringResource(
                    R.string.ready_database_accessible,
                    readiness.databaseAccessible.toString(),
                ),
            )
            Text(
                text = stringResource(
                    R.string.ready_voucher_sync,
                    readiness.voucherSynchronizationComposed.toString(),
                ),
            )
            Text(
                text = stringResource(
                    R.string.ready_voucher_app,
                    readiness.voucherApplicationComposed.toString(),
                ),
            )
        }
        Text(
            text = stringResource(
                R.string.health_checked_at,
                formatter.format(Instant.ofEpochMilli(probe.checkedAtEpochMillis)),
            ),
            modifier = Modifier.testTag("checked_at"),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ServerConfigScreenPreview() {
    VentureTheme {
        ServerConfigScreen(
            state = ServerConfigUiState(
                urlInput = "http://192.168.1.10:8080/",
                savedUrl = "http://192.168.1.10:8080/",
                connection = ConnectionUiState.Success(
                    ConnectorConnectionProbe(
                        health = sampleHealth(),
                        readiness = ConnectorReadiness(
                            status = "ready",
                            repositoryAvailable = true,
                            databaseAccessible = true,
                            voucherSynchronizationComposed = true,
                            voucherApplicationComposed = true,
                            httpStatus = 200,
                        ),
                        checkedAtEpochMillis = 1_700_000_000_000L,
                    ),
                ),
            ),
            onEvent = {},
        )
    }
}

private fun sampleHealth() = ConnectorHealth(
    status = "ok",
    schemaVersion = "1.0.0",
    connectorVersion = "0.4.0",
    tallyReachable = true,
    readOnly = true,
    bindHost = "127.0.0.1",
    bindPort = 8080,
    networkExposure = "loopback",
    networkExposureWarning = null,
    networkPolicySatisfied = true,
    authenticatedLanAccessEnabled = false,
    services = listOf(
        ConnectorServiceStatus("ApiServer", running = true, ready = true, message = null),
    ),
    startupCorrelationId = null,
    repositoryAvailable = true,
    databaseAccessible = true,
)
