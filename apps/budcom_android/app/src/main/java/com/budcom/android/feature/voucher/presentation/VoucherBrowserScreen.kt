package com.budcom.android.feature.voucher.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budcom.android.R
import com.budcom.android.feature.masterdata.presentation.MasterDataEmptyMessage
import com.budcom.android.feature.masterdata.presentation.MasterDataErrorBlock
import com.budcom.android.feature.masterdata.presentation.MasterDataLoadingIndicator
import com.budcom.android.feature.masterdata.presentation.MasterDataOfflineBanner
import com.budcom.android.feature.masterdata.presentation.displayMessage
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun VoucherBrowserRoute(
    onOpenVoucherDetails: (voucherId: String) -> Unit,
    viewModel: VoucherBrowserViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    VoucherBrowserScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onOpenVoucherDetails = onOpenVoucherDetails,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun VoucherBrowserScreen(
    state: VoucherBrowserUiState,
    onEvent: (VoucherBrowserEvent) -> Unit,
    onOpenVoucherDetails: (voucherId: String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val pullRefreshState = rememberPullRefreshState(
        refreshing = state.isRefreshing,
        onRefresh = { onEvent(VoucherBrowserEvent.Refresh) },
    )
    val listState = rememberLazyListState()

    LaunchedEffect(listState, state.canLoadMore, state.isBusy) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= (info.totalItemsCount - 3)
        }
            .distinctUntilChanged()
            .collect { nearEnd ->
                if (nearEnd && state.canLoadMore && !state.isBusy) {
                    onEvent(VoucherBrowserEvent.LoadNextPage)
                }
            }
    }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("voucher_browser_screen"),
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.voucher_browser_title)) })
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .pullRefresh(pullRefreshState),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                VoucherTypeFilterStrip(
                    availableTypes = state.availableTypeFilters,
                    selectedType = state.selectedTypeFilter,
                    onTypeSelected = { onEvent(VoucherBrowserEvent.TypeFilterChanged(it)) },
                )

                if (!state.isOnline) {
                    MasterDataOfflineBanner(testTag = "voucher_offline_banner")
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(state.cacheState.statusText(state.lastSyncedAt), style = MaterialTheme.typography.labelMedium, modifier = Modifier.testTag("voucher_data_status"))
                    Button(onClick = { onEvent(VoucherBrowserEvent.Refresh) }, enabled = !state.isBusy, modifier = Modifier.testTag("voucher_status_retry")) { Text("Refresh") }
                }
                state.reconciliationStatusLabel()?.let { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("voucher_reconciliation_status"),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = state.dateFrom,
                        onValueChange = { onEvent(VoucherBrowserEvent.DateFromChanged(it)) },
                        label = { Text(stringResource(R.string.voucher_date_from)) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("voucher_date_from"),
                    )
                    OutlinedTextField(
                        value = state.dateTo,
                        onValueChange = { onEvent(VoucherBrowserEvent.DateToChanged(it)) },
                        label = { Text(stringResource(R.string.voucher_date_to)) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("voucher_date_to"),
                    )
                }
                Button(
                    onClick = { onEvent(VoucherBrowserEvent.ApplyDateRange) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("voucher_apply_dates"),
                ) {
                    Text(stringResource(R.string.voucher_apply_dates))
                }

                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { onEvent(VoucherBrowserEvent.SearchChanged(it)) },
                    label = { Text(stringResource(R.string.voucher_search_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("voucher_search")
                        .semantics { contentDescription = "Search vouchers" },
                )

                when {
                    state.isInitialLoading && !state.hasContent -> {
                        MasterDataLoadingIndicator(testTag = "voucher_loading")
                    }
                    state.error != null && !state.hasContent -> {
                        MasterDataErrorBlock(
                            error = state.error,
                            onRetry = { onEvent(VoucherBrowserEvent.Retry) },
                            errorTestTag = "voucher_error",
                            retryTestTag = "voucher_retry",
                        )
                    }
                    !state.hasContent -> {
                        MasterDataEmptyMessage(
                            message = stringResource(R.string.voucher_empty),
                            testTag = "voucher_empty",
                        )
                    }
                    else -> {
                        if (state.refreshError != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = state.refreshError,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f).testTag("voucher_refresh_error"),
                                )
                                Button(
                                    onClick = { onEvent(VoucherBrowserEvent.Refresh) },
                                    enabled = !state.isBusy,
                                    modifier = Modifier.testTag("voucher_refresh_error_retry"),
                                ) { Text("Retry") }
                            }
                        } else if (state.error != null) {
                            Text(
                                text = state.error.displayMessage(),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.testTag("voucher_inline_error"),
                            )
                        }
                        if (state.filteredVouchers.isEmpty()) {
                            MasterDataEmptyMessage(
                                message = stringResource(
                                    R.string.voucher_filtered_empty,
                                    state.selectedTypeFilter.orEmpty(),
                                ),
                                testTag = "voucher_filtered_empty",
                            )
                        } else {
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(bottom = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("voucher_list"),
                            ) {
                                items(state.filteredVouchers, key = { it.id }) { row ->
                                    VoucherRowCard(
                                        row = row,
                                        onClick = { onOpenVoucherDetails(row.id) },
                                    )
                                }
                                if (state.isLoadingMore) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            CircularProgressIndicator()
                                        }
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
                    .testTag("voucher_refresh_indicator"),
            )
        }
    }
}

/**
 * Compact horizontal type filter strip directly under the Vouchers header (BUDCOM-UI-DESIGN-DECISIONS
 * §5): All is always first and selected by default; other chips are derived from the voucher types
 * actually present so far rather than a fixed taxonomy, so a company never sees a filter for a type
 * it has no vouchers of. Scrolls horizontally when it doesn't fit. Selecting a chip only changes what
 * [VoucherBrowserUiState.filteredVouchers] renders — the underlying fetched/cached collection never changes.
 */
@Composable
private fun VoucherTypeFilterStrip(
    availableTypes: List<String>,
    selectedType: String?,
    onTypeSelected: (String?) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("voucher_type_filter_strip"),
    ) {
        item {
            FilterChip(
                selected = selectedType == null,
                onClick = { onTypeSelected(null) },
                label = { Text(stringResource(R.string.voucher_type_filter_all)) },
                modifier = Modifier.testTag("voucher_type_filter_all"),
            )
        }
        items(availableTypes, key = { it }) { type ->
            FilterChip(
                selected = selectedType == type,
                onClick = { onTypeSelected(type) },
                label = { Text(type) },
                modifier = Modifier.testTag("voucher_type_filter_$type"),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoucherRowCard(
    row: VoucherRowUi,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("voucher_row_${row.id}")
            .semantics {
                contentDescription = buildString {
                    append(row.typeLabel)
                    append(", ")
                    append(row.primaryLabel)
                    append(", date ")
                    append(row.dateLabel)
                    row.secondaryLabel?.let { append(", ").append(it) }
                    append(", status ").append(row.statusLabel)
                    row.amountLabel?.let { append(", amount ").append(it) }
                    append(". Open voucher details")
                }
            },
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            // Primary line: LEFT voucher type/number, CENTER party name, RIGHT date. Left and
            // right anchors are fixed-position and single-line; only the center party-name area
            // may grow to a second line for a long name. Alignment.Top (not CenterVertically)
            // keeps the left/right anchors pinned to the row's first line so a wrapped party name
            // never pushes the date down or under the voucher number.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = "${row.typeLabel} · ${row.primaryLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Start,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = row.partyName?.takeIf { it.isNotBlank() } ?: "—",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Text(
                    text = row.dateLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            row.secondaryLabel?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
