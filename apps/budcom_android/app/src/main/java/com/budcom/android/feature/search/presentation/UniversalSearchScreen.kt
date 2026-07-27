package com.budcom.android.feature.search.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budcom.android.R
import com.budcom.android.feature.masterdata.presentation.MasterDataOfflineBanner
import com.budcom.android.feature.masterdata.presentation.displayMessage
import com.budcom.android.feature.search.domain.model.SearchSection

@Composable
fun UniversalSearchRoute(
    onOpenLedgerBrowser: (query: String) -> Unit,
    onOpenStockItemBrowser: (query: String) -> Unit,
    onOpenVoucherBrowser: (query: String) -> Unit,
    onOpenVoucherDetails: (voucherId: String) -> Unit,
    viewModel: UniversalSearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.navigation.collect { target ->
            when (target) {
                is UniversalSearchNavigation.LedgerBrowser -> onOpenLedgerBrowser(target.query)
                is UniversalSearchNavigation.StockItemBrowser -> onOpenStockItemBrowser(target.query)
                is UniversalSearchNavigation.VoucherBrowser -> onOpenVoucherBrowser(target.query)
                is UniversalSearchNavigation.VoucherDetails -> onOpenVoucherDetails(target.voucherId)
            }
        }
    }
    UniversalSearchScreen(
        state = state,
        onEvent = viewModel::onEvent,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniversalSearchScreen(
    state: UniversalSearchUiState,
    onEvent: (UniversalSearchEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("universal_search_screen"),
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.search_title)) })
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (!state.isOnline) {
                MasterDataOfflineBanner(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    testTag = "universal_search_offline",
                )
            }

            SearchField(
                query = state.query,
                onQueryChanged = { onEvent(UniversalSearchEvent.QueryChanged(it)) },
                onClear = { onEvent(UniversalSearchEvent.ClearQuery) },
            )

            Text(
                text = stringResource(R.string.search_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("universal_search_subtitle"),
            )

            when {
                state.showIdleHint -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("universal_search_idle"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.search_idle_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                state.allFailed -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                            .testTag("universal_search_all_failed"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(stringResource(R.string.search_all_failed))
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { onEvent(UniversalSearchEvent.Retry) },
                            modifier = Modifier.testTag("universal_search_retry"),
                        ) {
                            Text(stringResource(R.string.search_retry))
                        }
                    }
                }
                state.showEmpty -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("universal_search_empty"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.search_empty),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("universal_search_content"),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (state.isPartialFailure) {
                            item(key = "partial_banner") {
                                Text(
                                    text = stringResource(R.string.search_partial_failure),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.testTag("universal_search_partial"),
                                )
                            }
                        }
                        items(
                            items = state.sections,
                            key = { it.section.name },
                        ) { section ->
                            SearchSectionBlock(
                                sectionUi = section,
                                voucherDateFrom = state.voucherDateFrom,
                                voucherDateTo = state.voucherDateTo,
                                onEvent = onEvent,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChanged: (String) -> Unit,
    onClear: () -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChanged,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("universal_search_field")
            .semantics { contentDescription = "Search ledgers, stock items, and vouchers" },
        singleLine = true,
        label = { Text(stringResource(R.string.search_field_label)) },
        placeholder = { Text(stringResource(R.string.search_field_hint)) },
        leadingIcon = {
            Icon(Icons.Filled.Search, contentDescription = null)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.testTag("universal_search_clear"),
                ) {
                    Icon(
                        Icons.Filled.Clear,
                        contentDescription = stringResource(R.string.search_clear),
                    )
                }
            }
        },
    )
}

@Composable
private fun SearchSectionBlock(
    sectionUi: SearchSectionUi,
    voucherDateFrom: String?,
    voucherDateTo: String?,
    onEvent: (UniversalSearchEvent) -> Unit,
) {
    val title = when (sectionUi.section) {
        SearchSection.Ledgers -> stringResource(R.string.search_section_ledgers)
        SearchSection.StockItems -> stringResource(R.string.search_section_stock_items)
        SearchSection.Vouchers -> stringResource(R.string.search_section_vouchers)
    }
    val tagPrefix = "universal_search_section_${sectionUi.section.name.lowercase()}"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tagPrefix),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag("${tagPrefix}_title"),
        )
        if (sectionUi.section == SearchSection.Vouchers &&
            !voucherDateFrom.isNullOrBlank() &&
            !voucherDateTo.isNullOrBlank()
        ) {
            Text(
                text = stringResource(
                    R.string.search_voucher_window,
                    voucherDateFrom,
                    voucherDateTo,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("universal_search_voucher_window"),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        when (sectionUi) {
            is SearchSectionUi.Loading -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("${tagPrefix}_loading"),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            is SearchSectionUi.Empty -> {
                Text(
                    text = stringResource(R.string.search_section_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("${tagPrefix}_empty"),
                )
            }
            is SearchSectionUi.Failure -> {
                Column(modifier = Modifier.testTag("${tagPrefix}_error")) {
                    Text(
                        text = sectionUi.error.displayMessage(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(
                        onClick = { onEvent(UniversalSearchEvent.RetrySection(sectionUi.section)) },
                        modifier = Modifier.testTag("${tagPrefix}_retry"),
                    ) {
                        Text(stringResource(R.string.search_retry))
                    }
                }
            }
            is SearchSectionUi.Success -> {
                sectionUi.rows.forEach { row ->
                    SearchResultRow(
                        row = row,
                        onClick = { onEvent(UniversalSearchEvent.ResultClicked(row)) },
                    )
                    HorizontalDivider()
                }
                if (sectionUi.showSeeAll) {
                    TextButton(
                        onClick = { onEvent(UniversalSearchEvent.SeeAll(sectionUi.section)) },
                        modifier = Modifier.testTag("${tagPrefix}_see_all"),
                    ) {
                        Text(
                            text = stringResource(
                                R.string.search_see_all,
                                sectionUi.totalItems,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    row: SearchResultRowUi,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
            .testTag("universal_search_result_${row.section.name.lowercase()}_${row.id}")
            .semantics { contentDescription = "${row.primary}. ${row.secondary.orEmpty()}" },
    ) {
        Text(
            text = row.primary,
            style = MaterialTheme.typography.bodyLarge,
        )
        if (!row.secondary.isNullOrBlank()) {
            Text(
                text = row.secondary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
