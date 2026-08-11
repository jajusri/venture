package com.budcom.android.feature.masterdata.ledger.presentation

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budcom.android.feature.masterdata.presentation.MasterDataEmptyMessage
import com.budcom.android.feature.masterdata.presentation.MasterDataErrorBlock
import com.budcom.android.feature.masterdata.presentation.MasterDataLoadingIndicator
import com.budcom.android.feature.masterdata.presentation.MasterDataOfflineBanner

@Composable
fun LedgerStatementRoute(
    onBack: () -> Unit,
    onOpenVoucherDetails: (String) -> Unit,
    viewModel: LedgerStatementViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val shareLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        viewModel.onEvent(LedgerStatementEvent.ShareActivityFinished(result.resultCode == Activity.RESULT_CANCELED))
    }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        viewModel.onEvent(LedgerStatementEvent.SaveDestinationSelected(uri))
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is LedgerStatementEffect.OpenVoucherDetails -> onOpenVoucherDetails(effect.voucherId)
            }
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.shareEffects.collect { effect ->
            when (effect) {
                is LedgerStatementShareEffect.LaunchShare -> shareLauncher.launch(effect.intent)
                is LedgerStatementShareEffect.CreatePdfDocument -> saveLauncher.launch(effect.suggestedFilename)
            }
        }
    }
    LedgerStatementScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun LedgerStatementScreen(
    state: LedgerStatementUiState,
    onEvent: (LedgerStatementEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pullRefreshState = rememberPullRefreshState(
        refreshing = state.isRefreshing,
        onRefresh = { onEvent(LedgerStatementEvent.Refresh) },
    )
    var showPeriodDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(state.content?.ledgerName ?: "Ledger Statement") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("ledger_statement_back")) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onEvent(LedgerStatementEvent.OpenShareOptions) },
                        enabled = state.hasContent,
                        modifier = Modifier.testTag("ledger_statement_share_button"),
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = "Share ledger statement")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).pullRefresh(pullRefreshState)) {
            when {
                state.isInitialLoading -> MasterDataLoadingIndicator(testTag = "ledger_statement_loading")
                state.error != null -> MasterDataErrorBlock(
                    error = state.error,
                    onRetry = { onEvent(LedgerStatementEvent.Retry) },
                    modifier = Modifier.padding(16.dp),
                    errorTestTag = "ledger_statement_error",
                    retryTestTag = "ledger_statement_retry",
                )
                state.content != null -> LedgerStatementContent(
                    content = state.content,
                    isOnline = state.isOnline,
                    refreshError = state.refreshError,
                    onTransactionTapped = { voucherId -> onEvent(LedgerStatementEvent.TransactionTapped(voucherId)) },
                    onChangePeriod = { showPeriodDialog = true },
                )
                else -> MasterDataEmptyMessage("No ledger statement is available yet.")
            }
            PullRefreshIndicator(
                refreshing = state.isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }

    if (showPeriodDialog) {
        LedgerStatementPeriodDialog(
            fromDate = state.fromDate,
            toDate = state.toDate,
            onConfirm = { from, to ->
                showPeriodDialog = false
                onEvent(LedgerStatementEvent.PeriodChanged(from, to))
            },
            onDismiss = { showPeriodDialog = false },
        )
    }

    if (state.showShareOptions) {
        ModalBottomSheet(onDismissRequest = { onEvent(LedgerStatementEvent.DismissShareOptions) }) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { onEvent(LedgerStatementEvent.SharePdf) },
                    enabled = !state.isShareBusy,
                    modifier = Modifier.fillMaxWidth().testTag("ledger_statement_share_pdf"),
                ) { Text("Share Ledger PDF") }
                TextButton(
                    onClick = { onEvent(LedgerStatementEvent.SavePdf) },
                    enabled = !state.isShareBusy,
                    modifier = Modifier.fillMaxWidth().testTag("ledger_statement_save_pdf"),
                ) { Text("Save Ledger PDF") }
                state.shareError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("ledger_statement_share_error"))
                }
                state.shareMessage?.let {
                    Text(it, modifier = Modifier.testTag("ledger_statement_share_message"))
                }
            }
        }
    }
}

@Composable
private fun LedgerStatementContent(
    content: LedgerStatementContentUi,
    isOnline: Boolean,
    refreshError: String?,
    onTransactionTapped: (String) -> Unit,
    onChangePeriod: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("ledger_statement_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column {
                if (!isOnline) MasterDataOfflineBanner(modifier = Modifier.padding(bottom = 8.dp))
                refreshError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp).testTag("ledger_statement_refresh_error"))
                }
                Text(content.ledgerName, style = MaterialTheme.typography.titleLarge)
                content.parentGroup?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).clickable(onClick = onChangePeriod),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Period: ${content.periodLabel}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("ledger_statement_period"))
                    Text("Change", style = MaterialTheme.typography.labelMedium)
                }
                Text(
                    "Opening balance: ${content.openingLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp).testTag("ledger_statement_opening"),
                )
                content.coverageMessage?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp).testTag("ledger_statement_coverage_message"),
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
        }

        if (content.rows.isEmpty()) {
            item { MasterDataEmptyMessage("No transactions for this ledger in the selected period.") }
        } else {
            items(content.rows, key = { "${it.voucherId}-${it.dateLabel}-${it.voucherNumberLabel}" }) { row ->
                LedgerStatementRow(row = row, onClick = { onTransactionTapped(row.voucherId) })
            }
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                "Closing balance: ${content.closingLabel}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag("ledger_statement_closing"),
            )
        }
    }
}

@Composable
private fun LedgerStatementRow(row: LedgerStatementRowUi, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag("ledger_statement_row_${row.voucherId}"),
        colors = CardDefaults.cardColors(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(row.dateLabel, style = MaterialTheme.typography.bodyMedium)
                Text("${row.voucherTypeLabel} · ${row.voucherNumberLabel}", style = MaterialTheme.typography.bodyMedium)
            }
            row.particularsLabel?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Dr: ${row.debitLabel ?: "—"}", style = MaterialTheme.typography.bodySmall)
                Text("Cr: ${row.creditLabel ?: "—"}", style = MaterialTheme.typography.bodySmall)
                Text(row.runningBalanceLabel ?: "—", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun LedgerStatementPeriodDialog(
    fromDate: String,
    toDate: String,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var from by remember { mutableStateOf(fromDate) }
    var to by remember { mutableStateOf(toDate) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Statement period") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = from,
                    onValueChange = { from = it },
                    label = { Text("From (YYYY-MM-DD)") },
                    modifier = Modifier.testTag("ledger_statement_from_field"),
                )
                OutlinedTextField(
                    value = to,
                    onValueChange = { to = it },
                    label = { Text("To (YYYY-MM-DD)") },
                    modifier = Modifier.testTag("ledger_statement_to_field"),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(from.trim(), to.trim()) },
                enabled = from.isNotBlank() && to.isNotBlank(),
                modifier = Modifier.testTag("ledger_statement_period_confirm"),
            ) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
