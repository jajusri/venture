package com.jajusri.venture.feature.dashboard.presentation

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.jajusri.venture.BuildConfig
import com.jajusri.venture.R
import com.jajusri.venture.feature.dashboard.domain.model.DashboardOperationalMode
import com.jajusri.venture.feature.dashboard.domain.model.DashboardSessionValidity
import com.jajusri.venture.ui.theme.VentureTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun DashboardRoute(
    onOpenServerConfig: () -> Unit,
    onOpenCompanySelection: () -> Unit,
    onOpenMasterData: () -> Unit,
    onOpenVouchers: () -> Unit,
    onOpenLedgers: () -> Unit,
    onOpenConnect: () -> Unit,
    onOpenDincharya: () -> Unit,
    onOpenBusinessProfile: () -> Unit,
    onOpenCatalogue: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenSettings: () -> Unit,
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
                DashboardNavigation.Ledgers -> onOpenLedgers()
                DashboardNavigation.Connect -> onOpenConnect()
                DashboardNavigation.Dincharya -> onOpenDincharya()
                DashboardNavigation.BusinessProfile -> onOpenBusinessProfile()
                DashboardNavigation.Catalogue -> onOpenCatalogue()
                DashboardNavigation.Search -> onOpenSearch()
                DashboardNavigation.Sync -> onOpenSync()
                DashboardNavigation.Diagnostics -> onOpenDiagnostics()
                DashboardNavigation.Settings -> onOpenSettings()
            }
        }
    }
    // Foreground reconciliation backstop (TD-network-loss-callback-unreliable): re-derives
    // Connector reachability whenever the Dashboard is actively resumed, and on a bounded
    // interval for as long as it stays resumed, so a missed/delayed OS network callback can
    // never leave a stale "Fully operational" state displayed indefinitely. Automatically
    // starts/stops with the RESUMED lifecycle state via repeatOnLifecycle's own cancellation.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.reconcileWhileActive()
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
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = state.selectedCompanyName?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.dashboard_title),
                    )
                },
                actions = {
                    // Always reachable — first-time / setup / loading must not lock recovery out.
                    val syncNowDescription = stringResource(R.string.dashboard_sync_now_description)
                    IconButton(
                        onClick = { onEvent(DashboardEvent.Refresh) },
                        enabled = !state.isBusy,
                        modifier = Modifier
                            .testTag("dashboard_topbar_sync")
                            .semantics { contentDescription = syncNowDescription },
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                    }
                    IconButton(
                        onClick = { onEvent(DashboardEvent.OpenSettings) },
                        modifier = Modifier
                            .testTag("dashboard_topbar_settings")
                            .semantics { contentDescription = "Open settings" },
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = null)
                    }
                },
            )
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
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .testTag("dashboard_loading"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.semantics { contentDescription = "Loading dashboard" },
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = stringResource(R.string.dashboard_refreshing),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        if (BuildConfig.DEBUG) {
                            Button(
                                onClick = { onEvent(DashboardEvent.OpenServerConfig) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("dashboard_loading_server_config")
                                    .semantics { contentDescription = "Open server configuration" },
                            ) {
                                Text(stringResource(R.string.dashboard_action_server_config))
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { onEvent(DashboardEvent.OpenCompanySelection) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dashboard_loading_company")
                                .semantics { contentDescription = "Open company selection" },
                        ) {
                            Text(stringResource(R.string.dashboard_action_company))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { onEvent(DashboardEvent.OpenSettings) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dashboard_loading_settings")
                                .semantics { contentDescription = "Open settings" },
                        ) {
                            Text(stringResource(R.string.dashboard_action_settings))
                        }
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
                        HomeSearchEntry(onEvent = onEvent)
                        HomeCompactStatusRow(state = state, onEvent = onEvent)
                        HomePrimaryEntries(onEvent = onEvent)

                        if (state.isRefreshing || state.isTestingConnection || state.isValidatingSession) {
                            LinearBusyHint(state = state)
                        }

                        // The approved Home is a compact accounting launcher, not the former
                        // diagnostics dashboard. Keep technical recovery detail available only
                        // when it is actionable; healthy operation gets a quiet secondary-tools
                        // row so all existing destinations survive without dominating the screen.
                        if (state.operationalMode == DashboardOperationalMode.FullyOperational) {
                            HomeSecondaryActions(onEvent)
                        } else {
                            HorizontalDivider()
                            OperationalBanner(state = state)
                            ConnectorStatusCard(state = state, onEvent = onEvent)
                            CompanySessionCard(state = state, onEvent = onEvent)
                            SyncStatusCard(state = state, onEvent = onEvent)
                            QuickActionsCard(onEvent = onEvent)
                        }

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

/**
 * Permanent Universal Search entry directly below the company header (VENTURE-UI-DESIGN-DECISIONS
 * §3). This is a navigation affordance, not an inline search field — it opens the existing
 * [UniversalSearchScreen][com.jajusri.venture.feature.search.presentation.UniversalSearchScreen]
 * via the same [DashboardEvent.OpenSearch] event the Quick Actions button already uses.
 */
@Composable
private fun HomeSearchEntry(onEvent: (DashboardEvent) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_search_entry")
            .clickable { onEvent(DashboardEvent.OpenSearch) }
            .semantics { contentDescription = "Search" },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.search_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Compact operational / last-sync / Tally-connected state with a one-tap Sync action
 * (VENTURE-UI-DESIGN-DECISIONS §3). Ready means Connector session is usable — not that
 * account data is freshly synced. [DashboardUiState.syncStatusLabel] is the freshness line.
 */
@Composable
private fun HomeCompactStatusRow(state: DashboardUiState, onEvent: (DashboardEvent) -> Unit) {
    val (dotColor, freshnessWord) = when (state.operationalMode) {
        DashboardOperationalMode.FullyOperational -> MaterialTheme.colorScheme.tertiary to "Ready"
        DashboardOperationalMode.PartiallyAvailable -> MaterialTheme.colorScheme.tertiary to "Partial"
        DashboardOperationalMode.Offline -> MaterialTheme.colorScheme.error to "Offline"
        DashboardOperationalMode.ConnectorUnavailable -> MaterialTheme.colorScheme.error to "Unavailable"
        DashboardOperationalMode.NoServerConfiguration -> MaterialTheme.colorScheme.error to "Not configured"
        DashboardOperationalMode.NotReady -> MaterialTheme.colorScheme.error to "Not ready"
        DashboardOperationalMode.NoCompanySelected -> MaterialTheme.colorScheme.error to "No company"
        DashboardOperationalMode.SessionInvalid -> MaterialTheme.colorScheme.error to "Session invalid"
    }
    val tallyWord = when (state.connectorConnected) {
        true -> stringResource(R.string.dashboard_tally_connected)
        false -> stringResource(R.string.dashboard_tally_unavailable)
        null -> stringResource(R.string.dashboard_tally_unknown)
    }
    Row(
        modifier = Modifier.fillMaxWidth().testTag("dashboard_compact_status"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(8.dp)
                    .background(color = dotColor, shape = CircleShape),
            )
            Text(
                text = "$freshnessWord · ${state.syncStatusLabel} · $tallyWord",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("dashboard_compact_status_text"),
            )
        }
        TextButton(
            onClick = { onEvent(DashboardEvent.Refresh) },
            enabled = !state.isBusy,
            modifier = Modifier.testTag("dashboard_compact_sync"),
        ) {
            Text(stringResource(R.string.dashboard_sync_now))
        }
    }
}

/**
 * Vouchers, Ledgers, Connect, (MVP-1.2-D) Dincharya, and (MVP-1.3-A) Business Profile as the
 * primary Home entries, Stock Items deliberately absent from prime space (VENTURE-UI-DESIGN-
 * DECISIONS §3 — Stock Items remains reachable via Master Data below). Each addition follows the
 * exact precedent Connect itself set in MVP-1.1-B — one more [HomePrimaryEntryRow], one more
 * `DashboardEvent.OpenX`, no new navigation paradigm invented.
 */
@Composable
private fun HomePrimaryEntries(onEvent: (DashboardEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HomePrimaryEntryRow(
            icon = Icons.AutoMirrored.Filled.List,
            title = stringResource(R.string.dashboard_action_vouchers),
            subtitle = stringResource(R.string.dashboard_primary_vouchers_subtitle),
            testTag = "dashboard_primary_vouchers",
            onClick = { onEvent(DashboardEvent.OpenVouchers) },
        )
        HomePrimaryEntryRow(
            icon = Icons.Filled.AccountBox,
            title = stringResource(R.string.dashboard_action_ledgers),
            subtitle = stringResource(R.string.dashboard_primary_ledgers_subtitle),
            testTag = "dashboard_primary_ledgers",
            onClick = { onEvent(DashboardEvent.OpenLedgers) },
        )
        HomePrimaryEntryRow(
            icon = Icons.Filled.Person,
            title = stringResource(R.string.dashboard_action_connect),
            subtitle = stringResource(R.string.dashboard_primary_connect_subtitle),
            testTag = "dashboard_primary_connect",
            onClick = { onEvent(DashboardEvent.OpenConnect) },
        )
        HomePrimaryEntryRow(
            icon = Icons.Filled.CheckCircle,
            title = stringResource(R.string.dashboard_action_dincharya),
            subtitle = stringResource(R.string.dashboard_primary_dincharya_subtitle),
            testTag = "dashboard_primary_dincharya",
            onClick = { onEvent(DashboardEvent.OpenDincharya) },
        )
        HomePrimaryEntryRow(
            icon = Icons.Filled.Info,
            title = stringResource(R.string.dashboard_action_business_profile),
            subtitle = stringResource(R.string.dashboard_primary_business_profile_subtitle),
            testTag = "dashboard_primary_business_profile",
            onClick = { onEvent(DashboardEvent.OpenBusinessProfile) },
        )
        HomePrimaryEntryRow(
            icon = Icons.Filled.ShoppingCart,
            title = stringResource(R.string.dashboard_action_catalogue),
            subtitle = stringResource(R.string.dashboard_primary_catalogue_subtitle),
            testTag = "dashboard_primary_catalogue",
            onClick = { onEvent(DashboardEvent.OpenCatalogue) },
        )
    }
}

@Composable
private fun HomeSecondaryActions(onEvent: (DashboardEvent) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag("dashboard_secondary_actions"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        HorizontalDivider(modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
        Text(
            text = "More",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onEvent(DashboardEvent.OpenMasterData) }, modifier = Modifier.weight(1f).testTag("dashboard_open_master_data")) {
                Text("Stock items")
            }
            TextButton(onClick = { onEvent(DashboardEvent.OpenCompanySelection) }, modifier = Modifier.weight(1f).testTag("dashboard_open_company")) {
                Text("Company")
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onEvent(DashboardEvent.OpenSync) }, modifier = Modifier.weight(1f).testTag("dashboard_open_sync")) {
                Text("Sync")
            }
            TextButton(onClick = { onEvent(DashboardEvent.OpenDiagnostics) }, modifier = Modifier.weight(1f).testTag("dashboard_open_diagnostics")) {
                Text("Diagnostics")
            }
        }
    }
}

@Composable
private fun HomePrimaryEntryRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
                enabled = !state.isProbeBusy,
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
                    enabled = !state.selectedCompanyId.isNullOrBlank() && !state.isProbeBusy,
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
private fun SyncStatusCard(state: DashboardUiState, onEvent: (DashboardEvent) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_sync_card"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.dashboard_sync_heading),
                style = MaterialTheme.typography.titleMedium,
            )
            StatusRow(
                label = stringResource(R.string.dashboard_sync_status_label),
                value = state.syncStatusLabel,
                testTag = "dashboard_sync_status",
            )
            OutlinedButton(
                onClick = { onEvent(DashboardEvent.OpenSync) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_open_sync_card")
                    .semantics { contentDescription = "Open sync" },
            ) {
                Text(stringResource(R.string.dashboard_action_sync))
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
        if (BuildConfig.DEBUG) {
            Button(
                onClick = { onEvent(DashboardEvent.OpenServerConfig) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_open_server_config")
                    .semantics { contentDescription = "Open server configuration" },
            ) {
                Text(stringResource(R.string.dashboard_action_server_config))
            }
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
            Button(
                onClick = { onEvent(DashboardEvent.OpenSearch) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_open_search")
                    .semantics { contentDescription = "Open search" },
            ) {
                Text(stringResource(R.string.dashboard_action_search))
            }
            Button(
                onClick = { onEvent(DashboardEvent.OpenSync) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_open_sync")
                    .semantics { contentDescription = "Open sync" },
            ) {
                Text(stringResource(R.string.dashboard_action_sync))
            }
            Button(
                onClick = { onEvent(DashboardEvent.OpenDiagnostics) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_open_diagnostics")
                    .semantics { contentDescription = "Open diagnostics" },
            ) {
                Text(stringResource(R.string.dashboard_action_diagnostics))
            }
            Button(
                onClick = { onEvent(DashboardEvent.OpenSettings) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_open_settings")
                    .semantics { contentDescription = "Open settings" },
            ) {
                Text(stringResource(R.string.dashboard_action_settings))
            }
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
    VentureTheme {
        DashboardScreen(
            state = DashboardUiState(
                isInitialLoading = false,
                isOnline = true,
                baseUrl = "http://192.168.1.10:8080/",
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
