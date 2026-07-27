package com.budcom.android.feature.dashboard.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budcom.android.R
import com.budcom.android.feature.dashboard.domain.model.DashboardOperationalMode
import com.budcom.android.feature.dashboard.domain.model.DashboardSessionValidity
import com.budcom.android.ui.theme.BudcomTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun DashboardRoute(
    onOpenServerConfig: () -> Unit,
    onOpenCompanySelection: () -> Unit,
    onOpenMasterData: () -> Unit,
    onOpenVouchers: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.navigation.collect { target ->
            when (target) {
                DashboardNavigation.ServerConfig -> onOpenServerConfig()
                DashboardNavigation.CompanySelection -> onOpenCompanySelection()
                DashboardNavigation.MasterData -> onOpenMasterData()
                DashboardNavigation.Vouchers -> onOpenVouchers()
            }
        }
    }
    DashboardScreen(
        state = state,
        onEvent = viewModel::onEvent,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onEvent: (DashboardEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pullRefreshState = rememberPullRefreshState(
        refreshing = state.isRefreshing,
        onRefresh = { onEvent(DashboardEvent.Refresh) },
    )

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("dashboard_screen"),
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.dashboard_title)) })
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .pullRefresh(pullRefreshState),
        ) {
            when {
                state.isInitialLoading && !state.hasContent -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("dashboard_loading"),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .testTag("dashboard_content"),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OperationalBanner(state = state)

                        if (state.isRefreshing || state.isTestingConnection || state.isValidatingSession) {
                            LinearBusyHint(state = state)
                        }

                        ConnectorStatusCard(state = state, onEvent = onEvent)
                        CompanySessionCard(state = state, onEvent = onEvent)
                        QuickActionsCard(onEvent = onEvent)

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }

            PullRefreshIndicator(
                refreshing = state.isRefreshing,
                state = pullRefreshState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .testTag("dashboard_refresh_indicator"),
            )
        }
    }
}

@Composable
private fun LinearBusyHint(state: DashboardUiState) {
    val label = when {
        state.isValidatingSession -> stringResource(R.string.dashboard_validating_session)
        state.isTestingConnection -> stringResource(R.string.dashboard_testing_connection)
        else -> stringResource(R.string.dashboard_refreshing)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_busy_hint"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OperationalBanner(state: DashboardUiState) {
    val (text, tag) = when (state.operationalMode) {
        DashboardOperationalMode.Offline ->
            stringResource(R.string.dashboard_mode_offline) to "dashboard_banner_offline"
        DashboardOperationalMode.NoServerConfiguration ->
            stringResource(R.string.dashboard_mode_no_server) to "dashboard_banner_no_server"
        DashboardOperationalMode.ConnectorUnavailable ->
            stringResource(R.string.dashboard_mode_unavailable) to "dashboard_banner_unavailable"
        DashboardOperationalMode.NotReady ->
            stringResource(R.string.dashboard_mode_not_ready) to "dashboard_banner_not_ready"
        DashboardOperationalMode.NoCompanySelected ->
            stringResource(R.string.dashboard_mode_no_company) to "dashboard_banner_no_company"
        DashboardOperationalMode.SessionInvalid ->
            stringResource(R.string.dashboard_mode_session_invalid) to "dashboard_banner_session_invalid"
        DashboardOperationalMode.PartiallyAvailable ->
            stringResource(R.string.dashboard_mode_partial) to "dashboard_banner_partial"
        DashboardOperationalMode.FullyOperational ->
            stringResource(R.string.dashboard_mode_operational) to "dashboard_banner_operational"
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
            .semantics { contentDescription = text },
        colors = CardDefaults.cardColors(
            containerColor = when (state.operationalMode) {
                DashboardOperationalMode.FullyOperational ->
                    MaterialTheme.colorScheme.tertiaryContainer
                DashboardOperationalMode.Offline,
                DashboardOperationalMode.ConnectorUnavailable,
                DashboardOperationalMode.SessionInvalid,
                -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.secondaryContainer
            },
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun ConnectorStatusCard(
    state: DashboardUiState,
    onEvent: (DashboardEvent) -> Unit,
) {
    val connectionLabel = when (state.connectorConnected) {
        true -> stringResource(R.string.dashboard_connector_connected)
        false -> stringResource(R.string.dashboard_connector_unavailable)
        null -> stringResource(R.string.dashboard_connector_unknown)
    }
    val readinessText = when (state.readinessLabel) {
        ReadinessLabel.Ready -> stringResource(R.string.dashboard_readiness_ready)
        ReadinessLabel.NotReady -> stringResource(R.string.dashboard_readiness_not_ready)
        ReadinessLabel.Unknown -> stringResource(R.string.dashboard_readiness_unknown)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_connector_card"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.dashboard_connector_heading),
                style = MaterialTheme.typography.titleMedium,
            )
            StatusRow(
                label = stringResource(R.string.dashboard_connection_label),
                value = connectionLabel,
                testTag = "dashboard_connection_status",
            )
            StatusRow(
                label = stringResource(R.string.dashboard_readiness_label),
                value = readinessText,
                testTag = "dashboard_readiness_status",
            )
            StatusRow(
                label = stringResource(R.string.dashboard_base_url_label),
                value = state.baseUrl.ifBlank { stringResource(R.string.dashboard_base_url_empty) },
                testTag = "dashboard_base_url",
            )
            StatusRow(
                label = stringResource(R.string.dashboard_last_health_label),
                value = formatEpoch(state.lastSuccessfulHealthCheckEpochMillis),
                testTag = "dashboard_last_health",
            )
            state.connectorError?.let { error ->
                Text(
                    text = errorMessage(error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("dashboard_connector_error"),
                )
            }
            OutlinedButton(
                onClick = { onEvent(DashboardEvent.TestConnection) },
                enabled = !state.isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_test_connection")
                    .semantics {
                        contentDescription = "Test Connector connection"
                    },
            ) {
                Text(stringResource(R.string.dashboard_test_connection))
            }
        }
    }
}

@Composable
private fun CompanySessionCard(
    state: DashboardUiState,
    onEvent: (DashboardEvent) -> Unit,
) {
    val companyValue = when {
        state.selectedCompanyId.isNullOrBlank() ->
            stringResource(R.string.dashboard_no_company_selected)
        !state.selectedCompanyName.isNullOrBlank() ->
            "${state.selectedCompanyName} (${state.selectedCompanyId})"
        else -> state.selectedCompanyId.orEmpty()
    }
    val sessionValue = when (state.sessionValidity) {
        DashboardSessionValidity.Valid -> stringResource(R.string.dashboard_session_valid)
        DashboardSessionValidity.Invalid -> stringResource(R.string.dashboard_session_invalid)
        DashboardSessionValidity.NoCompany -> stringResource(R.string.dashboard_session_no_company)
        DashboardSessionValidity.Unknown -> stringResource(R.string.dashboard_session_unknown)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_company_card"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.dashboard_company_heading),
                style = MaterialTheme.typography.titleMedium,
            )
            StatusRow(
                label = stringResource(R.string.dashboard_company_label),
                value = companyValue,
                testTag = "dashboard_company_value",
            )
            StatusRow(
                label = stringResource(R.string.dashboard_session_label),
                value = sessionValue,
                testTag = "dashboard_session_status",
            )
            StatusRow(
                label = stringResource(R.string.dashboard_last_session_label),
                value = formatEpoch(state.lastSuccessfulSessionValidationEpochMillis),
                testTag = "dashboard_last_session",
            )
            state.sessionError?.let { error ->
                Text(
                    text = errorMessage(error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("dashboard_session_error"),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { onEvent(DashboardEvent.OpenCompanySelection) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("dashboard_change_company")
                        .semantics { contentDescription = "Change company" },
                ) {
                    Text(stringResource(R.string.dashboard_change_company))
                }
                Button(
                    onClick = { onEvent(DashboardEvent.ValidateSession) },
                    enabled = !state.selectedCompanyId.isNullOrBlank() && !state.isBusy,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("dashboard_validate_session")
                        .semantics { contentDescription = "Validate session" },
                ) {
                    Text(stringResource(R.string.dashboard_validate_session))
                }
            }
        }
    }
}

@Composable
private fun QuickActionsCard(onEvent: (DashboardEvent) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_quick_actions"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.dashboard_actions_heading),
                style = MaterialTheme.typography.titleMedium,
            )
            Button(
                onClick = { onEvent(DashboardEvent.OpenServerConfig) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_open_server_config")
                    .semantics { contentDescription = "Open server configuration" },
            ) {
                Text(stringResource(R.string.dashboard_action_server_config))
            }
            OutlinedButton(
                onClick = { onEvent(DashboardEvent.OpenCompanySelection) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_open_company")
                    .semantics { contentDescription = "Open company selection" },
            ) {
                Text(stringResource(R.string.dashboard_action_company))
            }
            Button(
                onClick = { onEvent(DashboardEvent.OpenMasterData) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_open_master_data")
                    .semantics { contentDescription = "Open master data" },
            ) {
                Text(stringResource(R.string.dashboard_action_master_data))
            }
            Button(
                onClick = { onEvent(DashboardEvent.OpenVouchers) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_open_vouchers")
                    .semantics { contentDescription = "Open vouchers" },
            ) {
                Text(stringResource(R.string.dashboard_action_vouchers))
            }
            Text(
                text = stringResource(R.string.dashboard_future_features),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("dashboard_future_features"),
            )
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    value: String,
    testTag: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "$label: $value" },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.testTag(testTag),
        )
    }
}

@Composable
private fun errorMessage(error: DashboardUiError): String = when (error) {
    is DashboardUiError.Offline -> error.message
    is DashboardUiError.Timeout -> error.message
    is DashboardUiError.Remote -> error.message
    is DashboardUiError.Serialization -> error.message
    is DashboardUiError.Message -> error.message
    is DashboardUiError.Unexpected -> error.message
}

@Composable
private fun formatEpoch(epochMillis: Long?): String {
    if (epochMillis == null) return stringResource(R.string.dashboard_never)
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())
    return formatter.format(Instant.ofEpochMilli(epochMillis))
}

@Preview(showBackground = true)
@Composable
private fun DashboardScreenPreview() {
    BudcomTheme {
        DashboardScreen(
            state = DashboardUiState(
                isInitialLoading = false,
                isOnline = true,
                baseUrl = "http://10.0.2.2:8080/",
                connectorConnected = true,
                readinessLabel = ReadinessLabel.Ready,
                lastSuccessfulHealthCheckEpochMillis = 1_700_000_000_000L,
                selectedCompanyId = "estimation",
                selectedCompanyName = "ESTIMATION",
                sessionValidity = DashboardSessionValidity.Valid,
                lastSuccessfulSessionValidationEpochMillis = 1_700_000_000_000L,
                operationalMode = DashboardOperationalMode.FullyOperational,
            ),
            onEvent = {},
        )
    }
}
