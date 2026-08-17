package com.budcom.android.feature.connect.presentation

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budcom.android.R
import com.budcom.android.feature.masterdata.presentation.MasterDataErrorBlock
import com.budcom.android.feature.masterdata.presentation.MasterDataLoadingIndicator
import com.budcom.android.feature.masterdata.presentation.MasterDataOfflineBanner
import com.budcom.android.feature.masterdata.presentation.displayMessage
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun ConnectRoute(
    onOpenLedgerStatement: (String) -> Unit,
    onOpenVouchers: (String) -> Unit,
    onOpenPartyDetail: (String) -> Unit,
    onOpenProspectCreate: () -> Unit,
    viewModel: ConnectViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var noticeMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ConnectEffect.OpenLedgerStatement -> onOpenLedgerStatement(effect.ledgerId)
                is ConnectEffect.OpenVouchers -> onOpenVouchers(effect.query)
                is ConnectEffect.OpenPartyDetail -> onOpenPartyDetail(effect.partyId)
                ConnectEffect.OpenProspectCreate -> onOpenProspectCreate()
                is ConnectEffect.LaunchCall -> {
                    runCatching { context.startActivity(ConnectContactActions.callIntent(effect.phoneE164)) }
                        .onFailure { noticeMessage = "No dialer app is available." }
                }
                is ConnectEffect.LaunchWhatsApp -> {
                    runCatching { context.startActivity(ConnectContactActions.whatsAppChatIntent(effect.phoneE164)) }
                        .onFailure { noticeMessage = "No app is available to open WhatsApp." }
                }
                is ConnectEffect.ShowMessage -> noticeMessage = effect.message
            }
        }
    }

    ConnectScreen(state = state, onEvent = viewModel::onEvent)

    noticeMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { noticeMessage = null },
            confirmButton = {
                TextButton(onClick = { noticeMessage = null }, modifier = Modifier.testTag("connect_notice_dismiss")) {
                    Text("OK")
                }
            },
            text = { Text(message, modifier = Modifier.testTag("connect_notice_message")) },
            modifier = Modifier.testTag("connect_notice_dialog"),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun ConnectScreen(
    state: ConnectUiState,
    onEvent: (ConnectEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pullRefreshState = rememberPullRefreshState(
        refreshing = state.isRefreshing,
        onRefresh = { onEvent(ConnectEvent.Refresh) },
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
                    onEvent(ConnectEvent.LoadNextPage)
                }
            }
    }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("connect_screen"),
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.connect_title)) })
        },
        floatingActionButton = {
            if (state.selectedTab == ConnectTab.Prospects) {
                FloatingActionButton(
                    onClick = { onEvent(ConnectEvent.AddProspectTapped) },
                    modifier = Modifier.testTag("connect_add_prospect"),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add prospect")
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .pullRefresh(pullRefreshState),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                SecondaryTabRow(selectedTabIndex = state.selectedTab.ordinal) {
                    Tab(
                        selected = state.selectedTab == ConnectTab.Customers,
                        onClick = { onEvent(ConnectEvent.TabChanged(ConnectTab.Customers)) },
                        text = { Text(stringResource(R.string.connect_tab_customers)) },
                        modifier = Modifier.testTag("connect_tab_customers"),
                    )
                    Tab(
                        selected = state.selectedTab == ConnectTab.Prospects,
                        onClick = { onEvent(ConnectEvent.TabChanged(ConnectTab.Prospects)) },
                        text = { Text(stringResource(R.string.connect_tab_prospects)) },
                        modifier = Modifier.testTag("connect_tab_prospects"),
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (!state.isOnline) {
                        MasterDataOfflineBanner(testTag = "connect_offline_banner")
                    }

                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = { onEvent(ConnectEvent.SearchChanged(it)) },
                        label = { Text(stringResource(R.string.connect_search_hint)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("connect_search")
                            .semantics { contentDescription = "Search name or phone" },
                    )

                    val emptyMessage = when {
                        state.isSearching -> stringResource(R.string.connect_empty_search)
                        state.selectedTab == ConnectTab.Customers -> stringResource(R.string.connect_empty_customers)
                        else -> stringResource(R.string.connect_empty_prospects)
                    }

                    when {
                        state.isInitialLoading && !state.hasContent -> {
                            MasterDataLoadingIndicator(testTag = "connect_loading")
                        }
                        state.error != null && !state.hasContent -> {
                            MasterDataErrorBlock(
                                error = state.error,
                                onRetry = { onEvent(ConnectEvent.Retry) },
                                errorTestTag = "connect_error",
                                retryTestTag = "connect_retry",
                            )
                        }
                        !state.hasContent -> {
                            Text(
                                text = emptyMessage,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.fillMaxWidth().testTag("connect_empty"),
                            )
                        }
                        else -> {
                            if (state.error != null) {
                                Text(
                                    text = state.error.displayMessage(),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.testTag("connect_inline_error"),
                                )
                            }
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(bottom = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize().testTag("connect_list"),
                            ) {
                                items(state.rows, key = { it.partyId }) { row ->
                                    ConnectRowCard(row = row, onEvent = onEvent)
                                }
                                if (state.isLoadingMore) {
                                    item {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().padding(12.dp),
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
                modifier = Modifier.align(Alignment.TopCenter).testTag("connect_refresh_indicator"),
            )
        }
    }
}

@Composable
private fun ConnectRowCard(row: ConnectRowUi, onEvent: (ConnectEvent) -> Unit) {
    Card(
        onClick = { onEvent(ConnectEvent.RowTapped(row.partyId)) },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("connect_row_${row.partyId}")
            .semantics {
                contentDescription = buildString {
                    append(row.displayName)
                    row.phoneDisplay?.let { append(", phone ").append(it) }
                    row.balanceLabel?.let { append(", balance ").append(it) }
                    if (row.tagNames.isNotEmpty()) append(", tags ").append(row.tagNames.joinToString(", "))
                }
            },
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = row.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                row.balanceLabel?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            if (row.phoneDisplay != null || row.tagNames.isNotEmpty()) {
                Text(
                    text = listOfNotNull(
                        row.phoneDisplay,
                        row.tagNames.takeIf { it.isNotEmpty() }?.joinToString(" · "),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { onEvent(ConnectEvent.CallTapped(row.phoneE164)) },
                    label = { Text("Call") },
                    modifier = Modifier
                        .testTag("connect_call_${row.partyId}")
                        .semantics { contentDescription = "Call ${row.displayName}" },
                )
                AssistChip(
                    onClick = { onEvent(ConnectEvent.WhatsAppTapped(row.phoneE164)) },
                    label = { Text("WhatsApp") },
                    modifier = Modifier
                        .testTag("connect_whatsapp_${row.partyId}")
                        .semantics { contentDescription = "Message ${row.displayName} on WhatsApp" },
                )
            }

            if (row.hasAccountingLink) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onEvent(ConnectEvent.ViewLedgerTapped(row.linkedLedgerId)) },
                        modifier = Modifier.testTag("connect_view_ledger_${row.partyId}"),
                    ) {
                        Text(stringResource(R.string.connect_view_ledger))
                    }
                    OutlinedButton(
                        onClick = {
                            row.linkedLedgerName?.let { onEvent(ConnectEvent.ViewVouchersTapped(it)) }
                        },
                        modifier = Modifier.testTag("connect_view_vouchers_${row.partyId}"),
                    ) {
                        Text(stringResource(R.string.connect_view_vouchers))
                    }
                }
            }
        }
    }
}
