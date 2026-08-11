package com.budcom.android.feature.masterdata.ledger.presentation

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
fun LedgerBrowserRoute(
    onOpenLedgerStatement: (String) -> Unit,
    viewModel: LedgerBrowserViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is LedgerBrowserEffect.OpenLedgerStatement -> onOpenLedgerStatement(effect.ledgerId)
            }
        }
    }
    LedgerBrowserScreen(
        state = state,
        onEvent = viewModel::onEvent,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun LedgerBrowserScreen(
    state: LedgerBrowserUiState,
    onEvent: (LedgerBrowserEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pullRefreshState = rememberPullRefreshState(
        refreshing = state.isRefreshing,
        onRefresh = { onEvent(LedgerBrowserEvent.Refresh) },
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
                    onEvent(LedgerBrowserEvent.LoadNextPage)
                }
            }
    }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("ledger_browser_screen"),
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.ledger_browser_title)) })
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
                    MasterDataOfflineBanner(testTag = "ledger_offline_banner")
                }

                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { onEvent(LedgerBrowserEvent.SearchChanged(it)) },
                    label = { Text(stringResource(R.string.ledger_search_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ledger_search")
                        .semantics { contentDescription = "Search ledgers" },
                )

                when {
                    state.isInitialLoading && !state.hasContent -> {
                        MasterDataLoadingIndicator(testTag = "ledger_loading")
                    }
                    state.error != null && !state.hasContent -> {
                        MasterDataErrorBlock(
                            error = state.error,
                            onRetry = { onEvent(LedgerBrowserEvent.Retry) },
                            errorTestTag = "ledger_error",
                            retryTestTag = "ledger_retry",
                        )
                    }
                    !state.hasContent -> {
                        MasterDataEmptyMessage(
                            message = stringResource(R.string.ledger_empty),
                            testTag = "ledger_empty",
                        )
                    }
                    else -> {
                        if (state.error != null) {
                            Text(
                                text = state.error.displayMessage(),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.testTag("ledger_inline_error"),
                            )
                        }
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("ledger_list"),
                        ) {
                            items(state.ledgers, key = { it.id }) { row ->
                                LedgerRowCard(
                                    row = row,
                                    onClick = { onEvent(LedgerBrowserEvent.LedgerTapped(row.id)) },
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
                    .testTag("ledger_refresh_indicator"),
            )
        }
    }

    state.selectedLedgerNotice?.let { notice ->
        AlertDialog(
            onDismissRequest = { onEvent(LedgerBrowserEvent.DismissLedgerNotice) },
            confirmButton = {
                TextButton(
                    onClick = { onEvent(LedgerBrowserEvent.DismissLedgerNotice) },
                    modifier = Modifier.testTag("ledger_notice_dismiss"),
                ) { Text("OK") }
            },
            text = { Text(notice, modifier = Modifier.testTag("ledger_notice_message")) },
            modifier = Modifier.testTag("ledger_notice_dialog"),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LedgerRowCard(row: LedgerRowUi, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ledger_row_${row.id}")
            .semantics {
                contentDescription = buildString {
                    append(row.primaryLabel)
                    row.secondaryLabel?.let { append(", ").append(it) }
                    append(", status ").append(row.statusLabel)
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
                text = stringResource(R.string.ledger_status_label, row.statusLabel),
                style = MaterialTheme.typography.bodySmall,
            )
            row.balanceLabel?.let {
                Text(
                    text = stringResource(R.string.ledger_balance_label, it),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
