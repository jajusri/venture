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
                if (!state.isOnline) {
                    MasterDataOfflineBanner(testTag = "voucher_offline_banner")
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
                        if (state.error != null) {
                            Text(
                                text = state.error.displayMessage(),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.testTag("voucher_inline_error"),
                            )
                        }
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("voucher_list"),
                        ) {
                            items(state.vouchers, key = { it.id }) { row ->
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
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.voucher_row_title, row.typeLabel, row.primaryLabel),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.voucher_date_label, row.dateLabel),
                style = MaterialTheme.typography.bodyMedium,
            )
            row.secondaryLabel?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(R.string.voucher_status_label, row.statusLabel),
                style = MaterialTheme.typography.bodySmall,
            )
            row.amountLabel?.let {
                Text(
                    text = stringResource(R.string.voucher_amount_label, it),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
