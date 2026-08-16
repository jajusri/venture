package com.budcom.android.feature.diagnostics.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budcom.android.BuildConfig
import com.budcom.android.R
import com.budcom.android.feature.masterdata.presentation.MasterDataOfflineBanner
import com.budcom.android.feature.masterdata.presentation.displayMessage
import com.budcom.android.feature.serverconfig.domain.model.ConnectorHealth
import com.budcom.android.feature.serverconfig.domain.model.ConnectorReadiness

@Composable
fun DiagnosticsRoute(
    onOpenServerConfig: () -> Unit,
    onOpenCompanySelection: () -> Unit,
    viewModel: DiagnosticsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.navigation.collect { target ->
            when (target) {
                DiagnosticsNavigation.ServerConfig -> onOpenServerConfig()
                DiagnosticsNavigation.CompanySelection -> onOpenCompanySelection()
            }
        }
    }
    DiagnosticsScreen(state = state, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    state: DiagnosticsUiState,
    onEvent: (DiagnosticsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("diagnostics_screen"),
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.diagnostics_title)) })
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!state.isOnline) {
                MasterDataOfflineBanner(testTag = "diagnostics_offline")
            }

            if (state.isInitialLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.testTag("diagnostics_loading"),
                )
                return@Column
            }

            if (state.bannerError != null) {
                Text(
                    text = state.bannerError.displayMessage(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("diagnostics_banner_error"),
                )
                Button(
                    onClick = { onEvent(DiagnosticsEvent.Retry) },
                    modifier = Modifier.testTag("diagnostics_retry"),
                ) {
                    Text(stringResource(R.string.diagnostics_retry))
                }
            }

            state.loadedAtLabel?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("diagnostics_captured_at"),
                )
            }

            ToolsCard(state = state, onEvent = onEvent)
            ApplicationCard(state.application)
            ConnectorCard(state.connector)
            CompanyCard(state.company, onEvent)
            SyncCard(state.sync)
            NoteCard(
                title = stringResource(R.string.diagnostics_search_heading),
                body = state.searchNote,
                testTag = "diagnostics_search",
            )
            NoteCard(
                title = stringResource(R.string.diagnostics_master_data_heading),
                body = state.masterDataNote,
                testTag = "diagnostics_master_data",
            )
            NoteCard(
                title = stringResource(R.string.diagnostics_voucher_heading),
                body = state.voucherNote,
                testTag = "diagnostics_voucher",
            )
        }
    }
}

@Composable
private fun ToolsCard(
    state: DiagnosticsUiState,
    onEvent: (DiagnosticsEvent) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("diagnostics_tools"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.diagnostics_tools_heading),
                style = MaterialTheme.typography.titleMedium,
            )
            Button(
                onClick = { onEvent(DiagnosticsEvent.Refresh) },
                enabled = !state.isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("diagnostics_refresh"),
            ) {
                Text(stringResource(R.string.diagnostics_refresh))
            }
            OutlinedButton(
                onClick = { onEvent(DiagnosticsEvent.RecheckHealth) },
                enabled = !state.isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("diagnostics_recheck_health"),
            ) {
                Text(stringResource(R.string.diagnostics_recheck_health))
            }
            OutlinedButton(
                onClick = { onEvent(DiagnosticsEvent.RecheckReadiness) },
                enabled = !state.isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("diagnostics_recheck_readiness"),
            ) {
                Text(stringResource(R.string.diagnostics_recheck_readiness))
            }
            if (BuildConfig.DEBUG) {
                OutlinedButton(
                    onClick = { onEvent(DiagnosticsEvent.OpenServerConfig) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("diagnostics_open_server_config"),
                ) {
                    Text(stringResource(R.string.diagnostics_open_server_config))
                }
            }
        }
    }
}

@Composable
private fun ApplicationCard(application: ApplicationSectionUi?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("diagnostics_application"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.diagnostics_application_heading),
                style = MaterialTheme.typography.titleMedium,
            )
            if (application == null) {
                Text(stringResource(R.string.diagnostics_unavailable))
            } else {
                StatusRow("App", application.appName, "diagnostics_app_name")
                StatusRow("Version", application.versionName, "diagnostics_app_version")
                StatusRow("Version code", application.versionCode.toString(), "diagnostics_app_version_code")
                StatusRow("Build", application.buildTypeLabel, "diagnostics_app_build")
            }
        }
    }
}

@Composable
private fun ConnectorCard(connector: ConnectorSectionUi) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("diagnostics_connector"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.diagnostics_connector_heading),
                style = MaterialTheme.typography.titleMedium,
            )
            StatusRow(
                label = "Base URL",
                value = connector.baseUrl.ifBlank { stringResource(R.string.diagnostics_unavailable) },
                testTag = "diagnostics_base_url",
            )
            HealthBlock(connector.health, connector.healthError?.displayMessage())
            ReadinessBlock(connector.readiness, connector.readinessError?.displayMessage())
            Text(
                text = stringResource(R.string.diagnostics_connection_heading),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (connector.connectionError != null && connector.connectionState == null) {
                Text(
                    text = connector.connectionError.displayMessage(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("diagnostics_connection_error"),
                )
            } else if (connector.connectionState == null) {
                Text(
                    text = stringResource(R.string.diagnostics_unavailable),
                    modifier = Modifier.testTag("diagnostics_connection_unknown"),
                )
            } else {
                StatusRow("State", connector.connectionState, "diagnostics_connection_state")
                connector.connectionHostPort?.let {
                    StatusRow("Host", it, "diagnostics_connection_host")
                }
                connector.connectionCircuit?.let {
                    StatusRow("Circuit", it, "diagnostics_connection_circuit")
                }
                connector.connectionSafeMode?.let {
                    StatusRow("Safe mode", it.toYesNo(), "diagnostics_connection_safe_mode")
                }
                connector.connectionLatency?.let {
                    StatusRow("Avg latency", it, "diagnostics_connection_latency")
                }
                connector.connectionLastPing?.let {
                    StatusRow("Last successful ping", it, "diagnostics_connection_last_ping")
                }
                connector.connectionLastError?.let {
                    StatusRow("Last error", it, "diagnostics_connection_last_error")
                }
            }
        }
    }
}

@Composable
private fun HealthBlock(health: ConnectorHealth?, error: String?) {
    Text(
        text = stringResource(R.string.diagnostics_health_heading),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 4.dp),
    )
    when {
        health != null -> {
            StatusRow("Status", health.status, "diagnostics_health_status")
            StatusRow("Connector version", health.connectorVersion, "diagnostics_health_version")
            StatusRow("Tally reachable", health.tallyReachable.toYesNo(), "diagnostics_health_tally")
            StatusRow(
                "Repository",
                health.repositoryAvailable.toYesNo(),
                "diagnostics_health_repository",
            )
            StatusRow(
                "Database",
                health.databaseAccessible.toYesNo(),
                "diagnostics_health_database",
            )
        }
        error != null -> Text(
            text = error,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("diagnostics_health_error"),
        )
        else -> Text(
            text = stringResource(R.string.diagnostics_unavailable),
            modifier = Modifier.testTag("diagnostics_health_unknown"),
        )
    }
}

@Composable
private fun ReadinessBlock(readiness: ConnectorReadiness?, error: String?) {
    Text(
        text = stringResource(R.string.diagnostics_readiness_heading),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 4.dp),
    )
    when {
        readiness != null -> {
            StatusRow("Status", readiness.status, "diagnostics_ready_status")
            StatusRow("HTTP", readiness.httpStatus.toString(), "diagnostics_ready_http")
            StatusRow(
                "Repository",
                readiness.repositoryAvailable.toYesNo(),
                "diagnostics_ready_repository",
            )
            StatusRow(
                "Database",
                readiness.databaseAccessible.toYesNo(),
                "diagnostics_ready_database",
            )
        }
        error != null -> Text(
            text = error,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("diagnostics_ready_error"),
        )
        else -> Text(
            text = stringResource(R.string.diagnostics_unavailable),
            modifier = Modifier.testTag("diagnostics_ready_unknown"),
        )
    }
}

@Composable
private fun CompanyCard(
    company: CompanySectionUi,
    onEvent: (DiagnosticsEvent) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("diagnostics_company"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.diagnostics_company_heading),
                style = MaterialTheme.typography.titleMedium,
            )
            StatusRow(
                label = "Company",
                value = company.companyName
                    ?: company.companyId
                    ?: stringResource(R.string.diagnostics_no_company),
                testTag = "diagnostics_company_name",
            )
            StatusRow("Session", company.sessionLabel, "diagnostics_session")
            company.sessionError?.let {
                Text(
                    text = it.displayMessage(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("diagnostics_session_error"),
                )
            }
            OutlinedButton(
                onClick = { onEvent(DiagnosticsEvent.OpenCompanySelection) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("diagnostics_open_company"),
            ) {
                Text(stringResource(R.string.diagnostics_open_company))
            }
        }
    }
}

@Composable
private fun SyncCard(sync: SyncSectionUi) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("diagnostics_sync"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.diagnostics_sync_heading),
                style = MaterialTheme.typography.titleMedium,
            )
            StatusRow("State", sync.activeLabel, "diagnostics_sync_state")
            StatusRow(
                label = "Last successful sync",
                value = sync.lastSuccess ?: stringResource(R.string.diagnostics_never),
                testTag = "diagnostics_sync_last_success",
            )
            StatusRow(
                label = "Last failure",
                value = sync.lastFailure ?: stringResource(R.string.diagnostics_none),
                testTag = "diagnostics_sync_last_failure",
            )
        }
    }
}

@Composable
private fun NoteCard(
    title: String,
    body: String,
    testTag: String,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = body.ifBlank { stringResource(R.string.diagnostics_unavailable) },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun Boolean.toYesNo(): String = if (this) "Yes" else "No"

@Composable
private fun StatusRow(
    label: String,
    value: String,
    testTag: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "$label: $value" }
            .testTag(testTag),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
