package com.budcom.android.feature.voucher.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import com.budcom.android.R
import com.budcom.android.core.pdf.PdfPreviewScreen
import com.budcom.android.feature.masterdata.presentation.MasterDataErrorBlock
import com.budcom.android.feature.masterdata.presentation.MasterDataLoadingIndicator
import com.budcom.android.feature.masterdata.presentation.MasterDataOfflineBanner
import com.budcom.android.feature.masterdata.presentation.displayMessage

@Composable
fun VoucherDetailsRoute(
    viewModel: VoucherDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val shareLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        viewModel.onEvent(VoucherDetailsEvent.ShareActivityFinished(result.resultCode == Activity.RESULT_CANCELED))
    }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        viewModel.onEvent(VoucherDetailsEvent.SaveDestinationSelected(uri))
    }
    LaunchedEffect(viewModel) {
        viewModel.shareEffects.collect { effect ->
            when (effect) {
                is VoucherDetailsShareEffect.LaunchShare -> {
                    shareLauncher.launch(effect.intent)
                }
                is VoucherDetailsShareEffect.CreatePdfDocument -> {
                    saveLauncher.launch(effect.suggestedFilename)
                }
            }
        }
    }
    VoucherDetailsScreen(
        state = state,
        onEvent = viewModel::onEvent,
        pdfPageRenderer = viewModel.pdfPageRenderer,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun VoucherDetailsScreen(
    state: VoucherDetailsUiState,
    onEvent: (VoucherDetailsEvent) -> Unit,
    pdfPageRenderer: com.budcom.android.core.pdf.PdfPageRenderer,
    modifier: Modifier = Modifier,
) {
    state.previewPdf?.let { pdf ->
        PdfPreviewScreen(
            filePath = pdf.cacheFilePath,
            title = pdf.suggestedFilename,
            renderer = pdfPageRenderer,
            onBack = { onEvent(VoucherDetailsEvent.DismissPreview) },
            onSave = { onEvent(VoucherDetailsEvent.SaveFromPreview) },
            onShare = { onEvent(VoucherDetailsEvent.ShareFromPreview) },
            isActionBusy = state.isShareBusy,
        )
        return
    }
    val pullRefreshState = rememberPullRefreshState(
        refreshing = state.isRefreshing,
        onRefresh = { onEvent(VoucherDetailsEvent.Refresh) },
    )

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("voucher_details_screen"),
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.voucher_details_title)) })
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
                    MasterDataLoadingIndicator(testTag = "voucher_details_loading")
                }
                state.detailsNotStored -> {
                    VoucherDetailsNotStoredContent(state = state, onEvent = onEvent)
                }
                state.error != null && !state.hasContent -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            state.cacheState.statusText(state.lastSyncedAt),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.testTag("voucher_details_data_status"),
                        )
                        if (!state.isOnline) {
                            MasterDataOfflineBanner(testTag = "voucher_details_offline_banner")
                        }
                        MasterDataErrorBlock(
                            error = state.error,
                            onRetry = { onEvent(VoucherDetailsEvent.Retry) },
                            errorTestTag = "voucher_details_error",
                            retryTestTag = "voucher_details_retry",
                        )
                    }
                }
                state.details != null -> {
                    val details = state.details
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("voucher_details_content"),
                    ) {
                        item {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(state.cacheState.statusText(state.lastSyncedAt), style = MaterialTheme.typography.labelMedium, modifier = Modifier.testTag("voucher_details_data_status"))
                                Button(onClick = { onEvent(VoucherDetailsEvent.Refresh) }, enabled = !state.isBusy, modifier = Modifier.testTag("voucher_details_status_retry")) { Text("Refresh") }
                            }
                        }
                        if (!state.isOnline) {
                            item {
                                MasterDataOfflineBanner(testTag = "voucher_details_offline_banner")
                            }
                        }
                        if (state.refreshError != null) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = state.refreshError,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f).testTag("voucher_details_refresh_error"),
                                    )
                                    Button(
                                        onClick = { onEvent(VoucherDetailsEvent.Refresh) },
                                        enabled = !state.isBusy,
                                        modifier = Modifier.testTag("voucher_details_refresh_error_retry"),
                                    ) { Text("Retry") }
                                }
                            }
                        } else if (state.error != null) {
                            item {
                                Text(
                                    text = state.error.displayMessage(),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.testTag("voucher_details_inline_error"),
                                )
                            }
                        }
                        item {
                            HeaderCard(details = details)
                        }
                        state.shareError?.let { message ->
                            item { Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("invoice_share_error")) }
                        }
                        state.shareMessage?.let { message ->
                            item { Text(message, modifier = Modifier.testTag("invoice_share_message")) }
                        }
                        if (state.canShareInvoice) {
                            item {
                                Button(
                                    onClick = { onEvent(VoucherDetailsEvent.OpenShareOptions) },
                                    enabled = !state.isShareBusy,
                                    modifier = Modifier.fillMaxWidth().testTag("share_invoice"),
                                ) { Text("Share invoice") }
                            }
                        } else if (state.shareUnavailableReason != null) {
                            item {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Button(
                                        onClick = {},
                                        enabled = false,
                                        modifier = Modifier.fillMaxWidth().testTag("share_invoice_unavailable"),
                                    ) { Text("Share invoice") }
                                    Text(
                                        text = state.shareUnavailableReason,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.testTag("share_invoice_unavailable_reason"),
                                    )
                                }
                            }
                        }
                        item {
                            PartyCard(details = details)
                        }
                        if (details.inventoryLines.isNotEmpty()) {
                            item {
                                InvoiceItemsCard(details.inventoryLines)
                            }
                        }
                        details.amountLabel?.let { amount ->
                            item {
                                TotalCard(amount)
                            }
                        }
                        if (!details.narration.isNullOrBlank()) {
                            item {
                                SectionCard(
                                    title = stringResource(R.string.voucher_details_narration),
                                    testTag = "voucher_details_narration",
                                ) {
                                    Text(text = details.narration, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                        item {
                            SectionCard(
                                title = stringResource(
                                    R.string.voucher_details_ledger_heading,
                                    details.ledgerLines.size,
                                ),
                                testTag = "voucher_details_ledger_section",
                            ) {
                                if (details.ledgerLines.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.voucher_details_no_ledger_lines),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    details.ledgerLines.forEach { line ->
                                        LedgerLineRow(line = line)
                                    }
                                }
                            }
                        }
                        item {
                            MetadataCard(details = details)
                        }
                    }
                }
            }

            PullRefreshIndicator(
                refreshing = state.isRefreshing,
                state = pullRefreshState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .testTag("voucher_details_refresh_indicator"),
            )
        }
    }
    ShareInvoiceOptions(state, onEvent)
}

@Composable
private fun VoucherDetailsNotStoredContent(
    state: VoucherDetailsUiState,
    onEvent: (VoucherDetailsEvent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("voucher_details_not_stored"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!state.isOnline) {
            MasterDataOfflineBanner(testTag = "voucher_details_offline_banner")
        }
        state.knownSummary?.let { summary ->
            Card(modifier = Modifier.fillMaxWidth().testTag("voucher_details_known_summary")) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "${summary.typeLabel} ${summary.primaryLabel}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(text = summary.dateLabel, style = MaterialTheme.typography.bodyMedium)
                    summary.partyName?.let {
                        Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Text(
            text = if (state.isOnline) {
                "Voucher details are not stored on this device."
            } else {
                "Voucher details are not stored on this device. Connect to BUDCOM Desktop to download them."
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("voucher_details_not_stored_message"),
        )
        state.downloadError?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("voucher_details_download_error"),
            )
        }
        Button(
            onClick = { onEvent(VoucherDetailsEvent.DownloadDetails) },
            enabled = !state.isDownloadingDetails,
            modifier = Modifier.testTag("voucher_details_download_action"),
        ) {
            if (state.isDownloadingDetails) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Downloading…")
            } else {
                Text("Download details")
            }
        }
    }
}

@Composable
private fun HeaderCard(details: VoucherDetailsContentUi) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("voucher_details_header")
            .semantics {
                contentDescription = "${details.typeLabel} ${details.numberLabel}"
            },
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = details.documentTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.voucher_details_number, details.numberLabel),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = details.dateLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End,
                )
            }
            details.referenceLabel?.let {
                Text(
                    text = stringResource(R.string.voucher_details_reference_value, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

}

@Composable
private fun PartyCard(details: VoucherDetailsContentUi) {
    SectionCard(
        title = details.partyHeading,
        testTag = "voucher_details_party_card",
    ) {
        Text(
            text = details.partyLabel ?: stringResource(R.string.voucher_details_unknown),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun TotalCard(amount: String) {
    Card(modifier = Modifier.fillMaxWidth().testTag("voucher_details_total")) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = stringResource(R.string.voucher_details_total),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = amount,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareInvoiceOptions(
    state: VoucherDetailsUiState,
    onEvent: (VoucherDetailsEvent) -> Unit,
) {
    if (!state.showShareOptions) return
    ModalBottomSheet(onDismissRequest = { onEvent(VoucherDetailsEvent.DismissShareOptions) }) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Share invoice", style = MaterialTheme.typography.titleLarge)
            // TD-028: PDF actions now go through in-app Preview first ("generate -> preview ->
            // save/share") rather than launching the OS chooser directly; Share summary is plain
            // text, not a PDF, so it has no preview step and is unaffected.
            Button(onClick = { onEvent(VoucherDetailsEvent.PreviewPdf) }, modifier = Modifier.fillMaxWidth().testTag("preview_invoice_pdf")) { Text("View invoice PDF") }
            Button(onClick = { onEvent(VoucherDetailsEvent.ShareSummary) }, modifier = Modifier.fillMaxWidth().testTag("share_invoice_summary")) { Text("Share summary") }
        }
    }
}

@Composable
private fun InvoiceItemsCard(lines: List<VoucherInventoryLineUi>) {
    SectionCard(
        title = stringResource(R.string.voucher_details_items),
        testTag = "voucher_details_inventory_section",
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.voucher_details_item),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.voucher_details_item_amount),
                modifier = Modifier.width(96.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
            )
        }
        HorizontalDivider()
        lines.forEachIndexed { index, line ->
            InvoiceItemRow(line)
            if (index != lines.lastIndex) HorizontalDivider()
        }
    }
}

@Composable
private fun MetadataCard(details: VoucherDetailsContentUi) {
    SectionCard(
        title = stringResource(R.string.voucher_details_record_information),
        testTag = "voucher_details_metadata",
    ) {
        MetaRow(
            label = stringResource(R.string.voucher_details_status),
            value = details.statusLabel,
        )
        MetaRow(
            label = stringResource(R.string.voucher_details_data_quality_label),
            value = details.dataQualityLabel,
        )
        details.effectiveDateLabel?.let {
            MetaRow(label = stringResource(R.string.voucher_details_effective_date_label), value = it)
        }
        MetaRow(label = stringResource(R.string.voucher_details_id), value = details.id)
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SectionCard(
    title: String,
    testTag: String,
    content: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().testTag(testTag)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun LedgerLineRow(line: VoucherLedgerLineUi) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("voucher_ledger_line_${line.lineNumber}")
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = stringResource(R.string.voucher_details_line_title, line.lineNumber, line.ledgerName),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(text = line.amountLabel, style = MaterialTheme.typography.bodyMedium)
        line.deemedPositiveLabel?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InventoryLineRow(line: VoucherInventoryLineUi) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("voucher_inventory_line_${line.lineNumber}")
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = stringResource(R.string.voucher_details_line_title, line.lineNumber, line.itemName),
            style = MaterialTheme.typography.bodyLarge,
        )
        line.quantityLabel?.let {
            Text(
                text = stringResource(R.string.voucher_details_quantity, it),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        line.rateLabel?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = stringResource(R.string.voucher_details_rate, it),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        line.amountLabel?.let {
            Text(text = it, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun InvoiceItemRow(line: VoucherInventoryLineUi) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("voucher_inventory_line_${line.lineNumber}")
            .padding(vertical = 8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = line.itemName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            val quantityAndRate = listOfNotNull(
                line.quantityLabel?.let { stringResource(R.string.voucher_details_quantity_short, it) },
                line.rateLabel?.let { stringResource(R.string.voucher_details_rate_short, it) },
            ).joinToString("  ·  ")
            if (quantityAndRate.isNotBlank()) {
                Text(
                    text = quantityAndRate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = line.amountLabel ?: stringResource(R.string.voucher_details_unknown),
            modifier = Modifier.width(96.dp),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.End,
        )
    }
}
