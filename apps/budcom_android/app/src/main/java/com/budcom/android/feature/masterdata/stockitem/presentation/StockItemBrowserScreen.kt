package com.budcom.android.feature.masterdata.stockitem.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
fun StockItemBrowserRoute(
    viewModel: StockItemBrowserViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    StockItemBrowserScreen(
        state = state,
        onEvent = viewModel::onEvent,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun StockItemBrowserScreen(
    state: StockItemBrowserUiState,
    onEvent: (StockItemBrowserEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pullRefreshState = rememberPullRefreshState(
        refreshing = state.isRefreshing,
        onRefresh = { onEvent(StockItemBrowserEvent.Refresh) },
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
                    onEvent(StockItemBrowserEvent.LoadNextPage)
                }
            }
    }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("stock_item_browser_screen"),
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.stock_item_browser_title)) })
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
                    MasterDataOfflineBanner(testTag = "stock_item_offline_banner")
                }

                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { onEvent(StockItemBrowserEvent.SearchChanged(it)) },
                    label = { Text(stringResource(R.string.stock_item_search_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("stock_item_search")
                        .semantics { contentDescription = "Search stock items" },
                )

                when {
                    state.isInitialLoading && !state.hasContent -> {
                        MasterDataLoadingIndicator(testTag = "stock_item_loading")
                    }
                    state.error != null && !state.hasContent -> {
                        MasterDataErrorBlock(
                            error = state.error,
                            onRetry = { onEvent(StockItemBrowserEvent.Retry) },
                            errorTestTag = "stock_item_error",
                            retryTestTag = "stock_item_retry",
                        )
                    }
                    !state.hasContent -> {
                        MasterDataEmptyMessage(
                            message = stringResource(R.string.stock_item_empty),
                            testTag = "stock_item_empty",
                        )
                    }
                    else -> {
                        if (state.error != null) {
                            Text(
                                text = state.error.displayMessage(),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.testTag("stock_item_inline_error"),
                            )
                        }
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("stock_item_list"),
                        ) {
                            items(state.stockItems, key = { it.id }) { row ->
                                StockItemRowCard(row = row)
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
                    .testTag("stock_item_refresh_indicator"),
            )
        }
    }
}

@Composable
private fun StockItemRowCard(row: StockItemRowUi) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("stock_item_row_${row.id}")
            .semantics {
                contentDescription = buildString {
                    append(row.primaryLabel)
                    row.secondaryLabel?.let { append(", ").append(it) }
                    append(", status ").append(row.statusLabel)
                    row.unitLabel?.let { append(", unit ").append(it) }
                    row.balanceLabel?.let { append(", balance ").append(it) }
                }
            },
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = row.primaryLabel, style = MaterialTheme.typography.titleMedium)
            row.secondaryLabel?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(R.string.stock_item_status_label, row.statusLabel),
                style = MaterialTheme.typography.bodySmall,
            )
            row.unitLabel?.let {
                Text(
                    text = stringResource(R.string.stock_item_unit_label, it),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            row.balanceLabel?.let {
                Text(
                    text = stringResource(R.string.stock_item_balance_label, it),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
