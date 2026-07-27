package com.budcom.android.feature.voucher.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budcom.android.R
import com.budcom.android.feature.masterdata.presentation.MasterDataErrorBlock
import com.budcom.android.feature.masterdata.presentation.MasterDataLoadingIndicator
import com.budcom.android.feature.masterdata.presentation.MasterDataOfflineBanner
import com.budcom.android.feature.masterdata.presentation.displayMessage

@Composable
fun VoucherDetailsRoute(
    viewModel: VoucherDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    VoucherDetailsScreen(
        state = state,
        onEvent = viewModel::onEvent,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun VoucherDetailsScreen(
    state: VoucherDetailsUiState,
    onEvent: (VoucherDetailsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
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
                state.error != null && !state.hasContent -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
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
                        if (!state.isOnline) {
                            item {
                                MasterDataOfflineBanner(testTag = "voucher_details_offline_banner")
                            }
                        }
                        if (state.error != null) {
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
                        item {
                            MetadataCard(details = details)
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
                            SectionCard(
                                title = stringResource(
                                    R.string.voucher_details_inventory_heading,
                                    details.inventoryLines.size,
                                ),
                                testTag = "voucher_details_inventory_section",
                            ) {
                                if (details.inventoryLines.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.voucher_details_no_inventory_lines),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    details.inventoryLines.forEach { line ->
                                        InventoryLineRow(line = line)
                                    }
                                }
                            }
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
                text = stringResource(R.string.voucher_row_title, details.typeLabel, details.numberLabel),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.voucher_date_label, details.dateLabel),
                style = MaterialTheme.typography.bodyMedium,
            )
            details.effectiveDateLabel?.let {
                Text(
                    text = stringResource(R.string.voucher_details_effective_date, it),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            details.amountLabel?.let {
                Text(
                    text = stringResource(R.string.voucher_amount_label, it),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun MetadataCard(details: VoucherDetailsContentUi) {
    SectionCard(
        title = stringResource(R.string.voucher_details_metadata),
        testTag = "voucher_details_metadata",
    ) {
        MetaRow(label = stringResource(R.string.voucher_details_id), value = details.id)
        MetaRow(
            label = stringResource(R.string.voucher_details_party),
            value = details.partyLabel ?: stringResource(R.string.voucher_details_unknown),
        )
        MetaRow(
            label = stringResource(R.string.voucher_details_reference),
            value = details.referenceLabel ?: stringResource(R.string.voucher_details_unknown),
        )
        MetaRow(
            label = stringResource(R.string.voucher_details_status),
            value = details.statusLabel,
        )
        MetaRow(
            label = stringResource(R.string.voucher_details_data_quality_label),
            value = details.dataQualityLabel,
        )
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
        line.amountLabel?.let {
            Text(text = it, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
