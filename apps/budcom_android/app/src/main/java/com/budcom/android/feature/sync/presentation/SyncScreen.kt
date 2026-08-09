package com.budcom.android.feature.sync.presentation

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
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
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
import com.budcom.android.feature.sync.domain.model.SyncTarget

@Composable
fun SyncRoute(
    onOpenCompanySelection: () -> Unit,
    onOpenServerConfig: () -> Unit,
    viewModel: SyncViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.navigation.collect { target ->
            when (target) {
                SyncNavigation.CompanySelection -> onOpenCompanySelection()
                SyncNavigation.ServerConfig -> onOpenServerConfig()
            }
        }
    }
    SyncScreen(state = state, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    state: SyncUiState,
    onEvent: (SyncEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("sync_screen"),
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.sync_title)) })
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
                MasterDataOfflineBanner(testTag = "sync_offline")
            }

            SummaryCard(state = state, onEvent = onEvent)

            if (state.bannerError != null) {
                Text(
                    text = state.bannerError.displayMessage(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("sync_error"),
                )
                Button(
                    onClick = { onEvent(SyncEvent.Retry) },
                    modifier = Modifier.testTag("sync_retry"),
                ) {
                    Text(stringResource(R.string.sync_retry))
                }
            }

            if (!state.aggregateMessage.isNullOrBlank()) {
                Text(
                    text = state.aggregateMessage,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("sync_aggregate_message"),
                )
            }

            ActiveRunCard(state = state, onEvent = onEvent)

            Text(
                text = stringResource(R.string.sync_targets_heading),
                style = MaterialTheme.typography.titleMedium,
            )
            state.targets.forEach { card ->
                TargetCard(card = card, onEvent = onEvent)
            }

            Button(
                onClick = { onEvent(SyncEvent.RunAvailableSyncs) },
                enabled = state.canStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sync_run_available"),
            ) {
                Text(stringResource(R.string.sync_run_available))
            }
            Text(
                text = stringResource(R.string.sync_run_available_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedButton(
                onClick = { onEvent(SyncEvent.RefreshOverview) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sync_refresh"),
            ) {
                Text(stringResource(R.string.sync_refresh))
            }
        }
    }
}

@Composable
private fun SummaryCard(state: SyncUiState, onEvent: (SyncEvent) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sync_summary"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.sync_summary_heading),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = if (state.hasCompany) {
                    stringResource(R.string.sync_company_value, state.companyId.orEmpty())
                } else {
                    stringResource(R.string.sync_no_company)
                },
                modifier = Modifier.testTag("sync_company"),
            )
            Text(
                text = stringResource(R.string.sync_phase_label, state.phase.name),
                modifier = Modifier.testTag("sync_phase"),
            )
            if (!state.hasCompany) {
                Button(
                    onClick = { onEvent(SyncEvent.OpenCompanySelection) },
                    modifier = Modifier.testTag("sync_open_company"),
                ) {
                    Text(stringResource(R.string.sync_select_company))
                }
            }
            if (BuildConfig.DEBUG && !state.isOnline) {
                OutlinedButton(
                    onClick = { onEvent(SyncEvent.OpenServerConfig) },
                    modifier = Modifier.testTag("sync_open_server"),
                ) {
                    Text(stringResource(R.string.sync_open_server_config))
                }
            }
        }
    }
}

@Composable
private fun ActiveRunCard(state: SyncUiState, onEvent: (SyncEvent) -> Unit) {
    val progress = state.activeProgress ?: return
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sync_active_run"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.sync_active_heading),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = state.activeTarget?.let { targetTitle(it) } ?: "—",
                modifier = Modifier.testTag("sync_active_target"),
            )
            Text(
                text = progress.statusLabel,
                modifier = Modifier.testTag("sync_active_status"),
            )
            progress.processedLabel?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall)
            }
            if (progress.determinateFraction != null) {
                LinearProgressIndicator(
                    progress = { progress.determinateFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sync_progress_determinate"),
                )
            } else if (state.isBusy) {
                Text(
                    text = stringResource(R.string.sync_progress_not_reported),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("sync_progress_indeterminate_hint"),
                )
                CircularProgressIndicator(
                    modifier = Modifier.testTag("sync_progress_indeterminate"),
                )
            }
            if (state.activeTarget != null &&
                state.targets.any { it.target == state.activeTarget && it.canCancel }
            ) {
                OutlinedButton(
                    onClick = { onEvent(SyncEvent.CancelTarget(state.activeTarget)) },
                    modifier = Modifier.testTag("sync_cancel"),
                ) {
                    Text(stringResource(R.string.sync_cancel))
                }
            }
        }
    }
}

@Composable
private fun TargetCard(card: SyncTargetCardUi, onEvent: (SyncEvent) -> Unit) {
    val tag = "sync_target_${card.target.name.lowercase()}"
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
            .semantics { contentDescription = "${card.title}. ${card.statusLine}" },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = card.title, style = MaterialTheme.typography.titleSmall)
            Text(text = card.statusLine, style = MaterialTheme.typography.bodyMedium)
            card.lastSyncedLine?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!card.available) {
                Text(
                    text = card.unavailableReason.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("${tag}_unavailable"),
                )
            } else {
                Button(
                    onClick = { onEvent(SyncEvent.StartTarget(card.target)) },
                    enabled = card.canSync,
                    modifier = Modifier.testTag("${tag}_start"),
                ) {
                    Text(stringResource(R.string.sync_start))
                }
                if (card.canCancel) {
                    OutlinedButton(
                        onClick = { onEvent(SyncEvent.CancelTarget(card.target)) },
                        modifier = Modifier.testTag("${tag}_cancel"),
                    ) {
                        Text(stringResource(R.string.sync_cancel))
                    }
                }
            }
        }
    }
}

private fun targetTitle(target: SyncTarget): String = when (target) {
    SyncTarget.Ledgers -> "Ledgers"
    SyncTarget.StockItems -> "Stock items"
    SyncTarget.Vouchers -> "Vouchers"
}
