package com.jajusri.venture.feature.settings.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jajusri.venture.BuildConfig
import com.jajusri.venture.R
import com.jajusri.venture.feature.masterdata.ledger.domain.model.LedgerStatementMode
import com.jajusri.venture.feature.masterdata.ledger.sharing.LedgerShareDefaultDestination
import com.jajusri.venture.feature.masterdata.ledger.sharing.LedgerSharingDefaultPeriod
import com.jajusri.venture.feature.masterdata.presentation.MasterDataOfflineBanner
import com.jajusri.venture.feature.masterdata.presentation.displayMessage
import com.jajusri.venture.feature.settings.domain.model.ThemePreference
import com.jajusri.venture.navigation.StartupRoutingState

@Composable
fun SettingsRoute(
    onOpenServerConfig: () -> Unit,
    onOpenCompanySelection: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenSecurePairing: () -> Unit,
    onOpenTrustStatus: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.navigation.collect { target ->
            when (target) {
                SettingsNavigation.ServerConfig -> onOpenServerConfig()
                SettingsNavigation.CompanySelection -> onOpenCompanySelection()
                SettingsNavigation.Sync -> onOpenSync()
                SettingsNavigation.Diagnostics -> onOpenDiagnostics()
                SettingsNavigation.SecurePairing -> onOpenSecurePairing()
                SettingsNavigation.TrustStatus -> onOpenTrustStatus()
            }
        }
    }
    SettingsScreen(state = state, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.settings_title)) })
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
                MasterDataOfflineBanner(testTag = "settings_offline")
            }

            if (state.isInitialLoading) {
                CircularProgressIndicator(modifier = Modifier.testTag("settings_loading"))
                return@Column
            }

            if (state.themeConfigurationError != null || state.themeSaveError != null) {
                Text(
                    text = state.themeConfigurationError
                        ?: state.themeSaveError!!.displayMessage(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("settings_theme_error"),
                )
                Button(
                    onClick = { onEvent(SettingsEvent.Retry) },
                    modifier = Modifier.testTag("settings_retry"),
                ) {
                    Text(stringResource(R.string.settings_retry))
                }
            }

            ConnectionCard(state = state, onEvent = onEvent)
            AppearanceCard(state = state, onEvent = onEvent)
            CompanyCard(state = state, onEvent = onEvent)
            LedgerSharingCard(state = state, onEvent = onEvent)
            SyncCard(state = state, onEvent = onEvent)
            DiagnosticsCard(onEvent = onEvent)
            TrustStatusCard(onEvent = onEvent)
            AboutCard(state = state, onEvent = onEvent)
        }
    }
}

@Composable
private fun ConnectionCard(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
) {
    SettingsSection(
        title = stringResource(R.string.settings_connection_heading),
        testTag = "settings_connection",
    ) {
        StatusRow(
            label = stringResource(R.string.settings_base_url_label),
            value = state.baseUrl.ifBlank { stringResource(R.string.settings_unavailable) },
            testTag = "settings_base_url",
        )
        StatusRow(
            label = stringResource(R.string.settings_connectivity_label),
            value = if (state.isOnline) {
                stringResource(R.string.settings_online)
            } else {
                stringResource(R.string.settings_offline)
            },
            testTag = "settings_connectivity",
        )
        if (BuildConfig.DEBUG && state.secureConnectionState == StartupRoutingState.LegacyEligible) {
            Button(
                onClick = { onEvent(SettingsEvent.OpenServerConfig) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_open_server_config"),
            ) {
                Text(stringResource(R.string.settings_open_server_config))
            }
        }
        SecureConnectionRow(state = state, onEvent = onEvent)
    }
}

/**
 * The one bounded, user-initiated entry point into secure pairing from an already-running app —
 * visible only when a real action is available: migrating a legacy-eligible installation, or
 * recovering a RE_PAIR_REQUIRED / credential-unavailable one. Never shown, and never creates a
 * credential record, merely by composing — see [SecurePairingScreen]'s own Idle phase for that
 * guarantee.
 */
@Composable
private fun SecureConnectionRow(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
) {
    when (state.secureConnectionState) {
        StartupRoutingState.LegacyEligible -> {
            Text(
                text = stringResource(R.string.settings_secure_connection_not_secured),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("settings_secure_connection_status"),
            )
            OutlinedButton(
                onClick = { onEvent(SettingsEvent.OpenSecurePairing) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_secure_this_connection"),
            ) {
                Text(stringResource(R.string.settings_secure_this_connection))
            }
        }
        StartupRoutingState.RePairRequired, StartupRoutingState.SecureCredentialUnavailable -> {
            Text(
                text = stringResource(R.string.settings_secure_connection_needs_attention),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("settings_secure_connection_status"),
            )
            OutlinedButton(
                onClick = { onEvent(SettingsEvent.OpenSecurePairing) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_re_pair"),
            ) {
                Text(stringResource(R.string.settings_re_pair))
            }
        }
        StartupRoutingState.SecureActive -> {
            Text(
                text = stringResource(R.string.settings_secure_pairing_stored),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("settings_secure_connection_status"),
            )
            OutlinedButton(
                onClick = { onEvent(SettingsEvent.OpenSecurePairing) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_manage_secure_pairing"),
            ) {
                Text(stringResource(R.string.settings_manage_secure_pairing))
            }
        }
        StartupRoutingState.PairingPending -> {
            Text(
                text = stringResource(R.string.settings_secure_pairing_pending),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("settings_secure_connection_status"),
            )
            OutlinedButton(
                onClick = { onEvent(SettingsEvent.OpenSecurePairing) },
                modifier = Modifier.fillMaxWidth().testTag("settings_manage_secure_pairing"),
            ) {
                Text(stringResource(R.string.settings_manage_secure_pairing))
            }
        }
        StartupRoutingState.PairingRequired, null -> Unit
    }
}

@Composable
private fun AppearanceCard(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
) {
    SettingsSection(
        title = stringResource(R.string.settings_appearance_heading),
        testTag = "settings_appearance",
    ) {
        ThemePreference.entries.forEach { preference ->
            val label = when (preference) {
                ThemePreference.System -> stringResource(R.string.settings_theme_system)
                ThemePreference.Light -> stringResource(R.string.settings_theme_light)
                ThemePreference.Dark -> stringResource(R.string.settings_theme_dark)
            }
            val selected = state.themePreference == preference
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selected,
                        onClick = { onEvent(SettingsEvent.SelectTheme(preference)) },
                        role = Role.RadioButton,
                    )
                    .testTag("settings_theme_${preference.name.lowercase()}")
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = selected,
                    onClick = { onEvent(SettingsEvent.SelectTheme(preference)) },
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun CompanyCard(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
) {
    SettingsSection(
        title = stringResource(R.string.settings_company_heading),
        testTag = "settings_company",
    ) {
        StatusRow(
            label = stringResource(R.string.settings_current_company_label),
            value = state.companyName
                ?: state.companyId
                ?: stringResource(R.string.settings_no_company),
            testTag = "settings_current_company",
        )
        OutlinedButton(
            onClick = { onEvent(SettingsEvent.OpenCompanySelection) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_open_company"),
        ) {
            Text(stringResource(R.string.settings_change_company))
        }
    }
}

@Composable
private fun LedgerSharingCard(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
) {
    SettingsSection(
        title = "Ledger Sharing",
        testTag = "settings_ledger_sharing",
    ) {
        Text(
            text = "Default statement",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 4.dp),
        )
        LedgerStatementMode.entries.forEach { mode ->
            val label = when (mode) {
                LedgerStatementMode.Summary -> "Summary"
                LedgerStatementMode.Detailed -> "Detailed"
            }
            val selected = state.ledgerSharingStatementMode == mode
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selected,
                        onClick = { onEvent(SettingsEvent.SelectLedgerSharingStatementMode(mode)) },
                        role = Role.RadioButton,
                    )
                    .testTag("settings_ledger_sharing_mode_${mode.name.lowercase()}")
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected, onClick = { onEvent(SettingsEvent.SelectLedgerSharingStatementMode(mode)) })
                Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
            }
        }

        Text(
            text = "Default period",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 8.dp),
        )
        LedgerSharingDefaultPeriod.entries.forEach { period ->
            // Same copy as the period-picker chips on LedgerStatementScreen — one name per period
            // across the app, not a second vocabulary invented here.
            val label = when (period) {
                LedgerSharingDefaultPeriod.Last7Sales -> "Last 7 Sales"
                LedgerSharingDefaultPeriod.Today -> "Today"
                LedgerSharingDefaultPeriod.ThisMonth -> "This Month"
                LedgerSharingDefaultPeriod.LastMonth -> "Last Month"
                LedgerSharingDefaultPeriod.CurrentFinancialYear -> "Current FY"
            }
            val selected = state.ledgerSharingDefaultPeriod == period
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selected,
                        onClick = { onEvent(SettingsEvent.SelectLedgerSharingDefaultPeriod(period)) },
                        role = Role.RadioButton,
                    )
                    .testTag("settings_ledger_sharing_period_${period.name.lowercase()}")
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected, onClick = { onEvent(SettingsEvent.SelectLedgerSharingDefaultPeriod(period)) })
                Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
            }
        }

        Text(
            text = "Default destination",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 8.dp),
        )
        LedgerShareDefaultDestination.entries.forEach { destination ->
            val label = when (destination) {
                LedgerShareDefaultDestination.WhatsAppSelect -> "WhatsApp — choose recipient"
                LedgerShareDefaultDestination.AndroidShare -> "Share via…"
                LedgerShareDefaultDestination.SavePdf -> "Save PDF"
            }
            val selected = state.ledgerSharingDefaultDestination == destination
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selected,
                        onClick = { onEvent(SettingsEvent.SelectLedgerSharingDefaultDestination(destination)) },
                        role = Role.RadioButton,
                    )
                    .testTag("settings_ledger_sharing_destination_${destination.name.lowercase()}")
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected, onClick = { onEvent(SettingsEvent.SelectLedgerSharingDefaultDestination(destination)) })
                Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun SyncCard(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
) {
    SettingsSection(
        title = stringResource(R.string.settings_sync_heading),
        testTag = "settings_sync",
    ) {
        StatusRow(
            label = stringResource(R.string.settings_sync_status_label),
            value = state.syncStatusLabel,
            testTag = "settings_sync_status",
        )
        Button(
            onClick = { onEvent(SettingsEvent.OpenSync) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_open_sync"),
        ) {
            Text(stringResource(R.string.settings_open_sync))
        }
    }
}

@Composable
private fun DiagnosticsCard(onEvent: (SettingsEvent) -> Unit) {
    SettingsSection(
        title = stringResource(R.string.settings_diagnostics_heading),
        testTag = "settings_diagnostics",
    ) {
        Button(
            onClick = { onEvent(SettingsEvent.OpenDiagnostics) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_open_diagnostics"),
        ) {
            Text(stringResource(R.string.settings_open_diagnostics))
        }
    }
}

@Composable
private fun TrustStatusCard(onEvent: (SettingsEvent) -> Unit) {
    SettingsSection(
        title = "Trust & Enrollment",
        testTag = "settings_trust_status",
    ) {
        Button(
            onClick = { onEvent(SettingsEvent.OpenTrustStatus) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_open_trust_status"),
        ) {
            Text("View enrollment status")
        }
    }
}

@Composable
private fun AboutCard(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
) {
    val app = state.application
    SettingsSection(
        title = stringResource(R.string.settings_about_heading),
        testTag = "settings_about",
    ) {
        if (app == null) {
            Text(stringResource(R.string.settings_unavailable))
        } else {
            StatusRow("App", app.appName, "settings_app_name")
            StatusRow("Package", app.packageName, "settings_package_name")
            StatusRow("Version", app.versionName, "settings_version_name")
            StatusRow("Version code", app.versionCode.toString(), "settings_version_code")
            StatusRow("Build", app.buildTypeLabel, "settings_build_type")
            StatusRow(
                label = stringResource(R.string.settings_connector_version_label),
                value = state.connectorVersion
                    ?: stringResource(R.string.settings_connector_version_unavailable),
                testTag = "settings_connector_version",
            )
        }
        OutlinedButton(
            onClick = { onEvent(SettingsEvent.Refresh) },
            enabled = !state.isBusy,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_refresh"),
        ) {
            Text(stringResource(R.string.settings_refresh))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    testTag: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            content()
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
            .semantics { contentDescription = "$label: $value" }
            .testTag(testTag),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
