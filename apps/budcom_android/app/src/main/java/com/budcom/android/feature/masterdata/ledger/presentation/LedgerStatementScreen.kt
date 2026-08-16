package com.budcom.android.feature.masterdata.ledger.presentation

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerPeriodSelection
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatementMode
import com.budcom.android.feature.masterdata.ledger.sharing.LedgerShareDestination
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    LedgerStatementScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        pdfPageRenderer = viewModel.pdfPageRenderer,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun LedgerStatementScreen(
    state: LedgerStatementUiState,
    onEvent: (LedgerStatementEvent) -> Unit,
    onBack: () -> Unit,
    pdfPageRenderer: com.budcom.android.core.pdf.PdfPageRenderer,
    modifier: Modifier = Modifier,
) {
    state.previewPdf?.let { pdf ->
        com.budcom.android.core.pdf.PdfPreviewScreen(
            filePath = pdf.cacheFilePath,
            title = pdf.suggestedFilename,
            renderer = pdfPageRenderer,
            onBack = { onEvent(LedgerStatementEvent.DismissPreview) },
            onSave = { onEvent(LedgerStatementEvent.SaveFromPreview) },
            onShare = { onEvent(LedgerStatementEvent.ShareFromPreview) },
            isActionBusy = state.isShareBusy,
        )
        return
    }
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Parity with Voucher's "View invoice PDF": a direct, always-visible, one-tap
                    // Preview entry point, separate from the Share icon's long-press Advanced
                    // Options (where Preview PDF also remains available as one of several
                    // destinations, unchanged).
                    TextButton(
                        onClick = { onEvent(LedgerStatementEvent.PreviewLedgerFast) },
                        enabled = state.hasContent && !state.isShareBusy,
                        modifier = Modifier.testTag("ledger_statement_preview_button"),
                    ) { Text("Preview") }
                    // Tap: immediately shares using the remembered default (Settings -> Ledger
                    // Sharing) with no options screen — the locked ~3-tap fast path. Long-press:
                    // opens advanced/change options for a one-time override, reusing this same
                    // icon rather than adding new UI chrome (no separate overflow pattern existed
                    // on this screen to reuse instead).
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .combinedClickable(
                                enabled = state.hasContent && !state.isShareBusy,
                                onClick = { onEvent(LedgerStatementEvent.ShareLedgerFast) },
                                onLongClick = { onEvent(LedgerStatementEvent.OpenShareOptions) },
                            )
                            .testTag("ledger_statement_share_button")
                            .semantics { contentDescription = "Share ledger statement. Long-press for more options." },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null)
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
                    periodSelection = state.periodSelection,
                    onSelectPeriod = { period ->
                        if (period is LedgerPeriodSelection.Custom) showPeriodDialog = true
                        else onEvent(LedgerStatementEvent.PeriodSelected(period))
                    },
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
            LedgerShareAdvancedOptions(state = state, onEvent = onEvent)
        }
    }
}

private val ADVANCED_PERIOD_CHOICES: List<Pair<String, LedgerPeriodSelection>> = listOf(
    "Current FY" to LedgerPeriodSelection.CurrentFinancialYear,
    "Today" to LedgerPeriodSelection.Today,
    "This Month" to LedgerPeriodSelection.ThisMonth,
    "Last Month" to LedgerPeriodSelection.LastMonth,
)

/**
 * The one advanced/change-options surface, reached only by long-pressing Share Ledger. Every
 * choice here applies to exactly one share — see [LedgerStatementEvent.AdvancedShare]'s doc
 * comment — never the persisted default (Settings -> Ledger Sharing is the only place that
 * changes).
 */
@Composable
private fun LedgerShareAdvancedOptions(
    state: LedgerStatementUiState,
    onEvent: (LedgerStatementEvent) -> Unit,
) {
    var showCustomPeriodDialog by remember { mutableStateOf(false) }
    val effectivePeriod = state.advancedPeriod ?: state.periodSelection
    val effectiveMode = state.advancedStatementMode ?: state.sharingPreferences.statementMode

    Column(
        modifier = Modifier
            .padding(16.dp)
            .testTag("ledger_statement_advanced_options"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Period", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ADVANCED_PERIOD_CHOICES.forEach { (label, period) ->
                AdvancedOptionChip(
                    label = label,
                    selected = effectivePeriod == period,
                    onClick = { onEvent(LedgerStatementEvent.AdvancedPeriodChanged(period)) },
                    testTag = "ledger_statement_advanced_period_${period::class.simpleName}",
                )
            }
            AdvancedOptionChip(
                label = "Custom…",
                selected = effectivePeriod is LedgerPeriodSelection.Custom,
                onClick = { showCustomPeriodDialog = true },
                testTag = "ledger_statement_advanced_period_custom",
            )
        }

        Text("Statement", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatementModeOption(
                label = "Summary",
                selected = effectiveMode == LedgerStatementMode.Summary,
                onClick = { onEvent(LedgerStatementEvent.AdvancedStatementModeChanged(LedgerStatementMode.Summary)) },
                testTag = "ledger_statement_advanced_mode_summary",
            )
            StatementModeOption(
                label = "Detailed",
                selected = effectiveMode == LedgerStatementMode.Detailed,
                onClick = { onEvent(LedgerStatementEvent.AdvancedStatementModeChanged(LedgerStatementMode.Detailed)) },
                testTag = "ledger_statement_advanced_mode_detailed",
            )
        }

        Text("Share to", style = MaterialTheme.typography.labelLarge)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val whatsAppToPartyAvailable = state.content?.whatsAppToPartyAvailable == true
            TextButton(
                onClick = { onEvent(LedgerStatementEvent.AdvancedShare(LedgerShareDestination.WhatsAppToParty)) },
                enabled = whatsAppToPartyAvailable && !state.isShareBusy,
                modifier = Modifier.fillMaxWidth().testTag("ledger_statement_destination_whatsapp_party"),
            ) {
                Text(
                    if (whatsAppToPartyAvailable) "WhatsApp to Party" else "WhatsApp to Party — not available (no phone number linked)",
                )
            }
            TextButton(
                onClick = { onEvent(LedgerStatementEvent.AdvancedShare(LedgerShareDestination.WhatsAppSelect)) },
                enabled = !state.isShareBusy,
                modifier = Modifier.fillMaxWidth().testTag("ledger_statement_destination_whatsapp_select"),
            ) { Text("WhatsApp — choose recipient") }
            TextButton(
                onClick = { onEvent(LedgerStatementEvent.AdvancedShare(LedgerShareDestination.AndroidShare)) },
                enabled = !state.isShareBusy,
                modifier = Modifier.fillMaxWidth().testTag("ledger_statement_destination_android_share"),
            ) { Text("Share via…") }
            TextButton(
                onClick = { onEvent(LedgerStatementEvent.AdvancedShare(LedgerShareDestination.SavePdf)) },
                enabled = !state.isShareBusy,
                modifier = Modifier.fillMaxWidth().testTag("ledger_statement_destination_save_pdf"),
            ) { Text("Save PDF") }
            TextButton(
                onClick = { onEvent(LedgerStatementEvent.AdvancedShare(LedgerShareDestination.PreviewPdf)) },
                enabled = !state.isShareBusy,
                modifier = Modifier.fillMaxWidth().testTag("ledger_statement_destination_preview_pdf"),
            ) { Text("Preview PDF") }
        }

        state.shareError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("ledger_statement_share_error"))
        }
        state.shareMessage?.let {
            Text(it, modifier = Modifier.testTag("ledger_statement_share_message"))
        }
    }

    if (showCustomPeriodDialog) {
        LedgerStatementPeriodDialog(
            fromDate = state.fromDate,
            toDate = state.toDate,
            onConfirm = { from, to ->
                showCustomPeriodDialog = false
                onEvent(LedgerStatementEvent.AdvancedPeriodChanged(LedgerPeriodSelection.Custom(from, to)))
            },
            onDismiss = { showCustomPeriodDialog = false },
        )
    }
}

@Composable
private fun AdvancedOptionChip(label: String, selected: Boolean, onClick: () -> Unit, testTag: String) {
    val colors = if (selected) {
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(colors.first, shape = MaterialTheme.shapes.small)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag(testTag),
    ) {
        Text(label, color = colors.second, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun StatementModeOption(label: String, selected: Boolean, onClick: () -> Unit, testTag: String) {
    Row(
        modifier = Modifier
            .selectable(selected = selected, onClick = onClick)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}

private val PERIOD_QUICK_CHOICES: List<Pair<String, LedgerPeriodSelection>> = listOf(
    "Last 7 Sales" to LedgerPeriodSelection.Last7Sales,
    "This Month" to LedgerPeriodSelection.ThisMonth,
    "Current FY" to LedgerPeriodSelection.CurrentFinancialYear,
    "Previous FY" to LedgerPeriodSelection.PreviousFinancialYear,
    "Last 30 Days" to LedgerPeriodSelection.Last30Days,
    "Custom" to LedgerPeriodSelection.Custom("", ""),
)

/** Locked period selector (Phase 9/Date-period requirements): selecting any of these is a
 * Room-only read (see [LedgerStatementViewModel.onEvent]'s [LedgerStatementEvent.PeriodSelected]
 * handling) — no network call is triggered by a period change. */
@Composable
private fun LedgerPeriodSelectorRow(selected: LedgerPeriodSelection, onSelect: (LedgerPeriodSelection) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .testTag("ledger_statement_period_selector"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for ((label, period) in PERIOD_QUICK_CHOICES) {
            val isSelected = period::class == selected::class
            TextButton(
                onClick = { onSelect(period) },
                modifier = Modifier.testTag("ledger_statement_period_choice_$label"),
            ) {
                Text(
                    label,
                    style = if (isSelected) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LedgerStatementContent(
    content: LedgerStatementContentUi,
    isOnline: Boolean,
    refreshError: String?,
    periodSelection: LedgerPeriodSelection,
    onSelectPeriod: (LedgerPeriodSelection) -> Unit,
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
                LedgerPeriodSelectorRow(selected = periodSelection, onSelect = onSelectPeriod)
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
