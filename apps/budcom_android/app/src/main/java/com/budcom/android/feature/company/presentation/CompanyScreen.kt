package com.budcom.android.feature.company.presentation

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
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budcom.android.R
import com.budcom.android.feature.company.domain.model.ConnectorCompany
import com.budcom.android.ui.components.FullScreenLoading
import com.budcom.android.ui.theme.BudcomTheme

@Composable
fun CompanyRoute(
    viewModel: CompanyViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CompanyScreen(
        state = state,
        onEvent = viewModel::onEvent,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun CompanyScreen(
    state: CompanyUiState,
    onEvent: (CompanyEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize().testTag("company_screen"),
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.company_title)) })
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { onEvent(CompanyEvent.SearchChanged(it)) },
                label = { Text(stringResource(R.string.company_search_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("company_search")
                    .semantics { contentDescription = "Search companies" },
                singleLine = true,
                enabled = !state.isSelecting,
            )

            state.message?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("company_message"),
                )
            }

            when {
                state.isLoading -> LoadingState()
                state.error != null -> ErrorState(state.error, onRetry = { onEvent(CompanyEvent.Retry) })
                state.filteredCompanies.isEmpty() -> EmptyState()
                else -> {
                    val pullRefreshState = rememberPullRefreshState(
                        refreshing = state.isRefreshing,
                        onRefresh = { onEvent(CompanyEvent.Refresh) },
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .pullRefresh(pullRefreshState)
                            .testTag("company_pull_refresh"),
                    ) {
                        CompanyList(
                            companies = state.filteredCompanies,
                            selectedId = state.selectedCompanyId,
                            isSelecting = state.isSelecting,
                            onSelect = { onEvent(CompanyEvent.SelectCompany(it)) },
                        )
                        PullRefreshIndicator(
                            refreshing = state.isRefreshing,
                            state = pullRefreshState,
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingState() {
    FullScreenLoading(modifier = Modifier.testTag("company_loading"))
}

@Composable
private fun ErrorState(
    error: CompanyUiError,
    onRetry: () -> Unit,
) {
    val headline = when (error) {
        is CompanyUiError.Offline -> stringResource(R.string.company_offline_title)
        is CompanyUiError.Timeout -> stringResource(R.string.company_timeout_title)
        is CompanyUiError.Http -> stringResource(R.string.company_http_error_title)
        is CompanyUiError.Serialization -> stringResource(R.string.company_serialization_error_title)
        is CompanyUiError.Unknown -> stringResource(R.string.company_unknown_error_title)
    }
    Column(
        modifier = Modifier.fillMaxSize().testTag("company_error"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = headline, fontWeight = FontWeight.SemiBold)
        Text(
            text = error.message,
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(
            onClick = onRetry,
            modifier = Modifier.padding(top = 8.dp).testTag("company_retry"),
        ) {
            Text(text = stringResource(R.string.company_retry))
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize().testTag("company_empty"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = stringResource(R.string.company_empty))
    }
}

@Composable
private fun CompanyList(
    companies: List<ConnectorCompany>,
    selectedId: String?,
    isSelecting: Boolean,
    onSelect: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("company_list"),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(companies, key = { it.id }) { company ->
            val isSelected = selectedId == company.id
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("company_card_${company.id}"),
                onClick = { if (!isSelecting) onSelect(company.id) },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(text = company.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = company.id,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        company.baseCurrency?.let {
                            Text(
                                text = stringResource(R.string.company_currency, it),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (isSelected) {
                        Text(
                            text = stringResource(R.string.company_selected),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CompanyScreenPreview() {
    BudcomTheme {
        CompanyScreen(
            state = CompanyUiState(
                isLoading = false,
                companies = listOf(
                    ConnectorCompany("estimation", "ESTIMATION", null, null, "INR"),
                    ConnectorCompany("abc-trading", "ABC TRADING", null, null, "INR"),
                ),
                filteredCompanies = listOf(
                    ConnectorCompany("estimation", "ESTIMATION", null, null, "INR"),
                    ConnectorCompany("abc-trading", "ABC TRADING", null, null, "INR"),
                ),
                selectedCompanyId = "estimation",
            ),
            onEvent = {},
        )
    }
}
