package com.budcom.android.feature.catalogue.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budcom.android.feature.catalogue.domain.model.CatalogueLifecycleState
import com.budcom.android.feature.catalogue.domain.model.CatalogueProductSource

@Composable
fun CatalogueRoute(
    onOpenProductDetail: (String) -> Unit,
    viewModel: CatalogueViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.shareIntent.collect { intent -> context.startActivity(intent) }
    }
    CatalogueScreen(state = state, onEvent = viewModel::onEvent, onOpenProductDetail = onOpenProductDetail)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogueScreen(
    state: CatalogueUiState,
    onEvent: (CatalogueEvent) -> Unit,
    onOpenProductDetail: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Catalogue") },
                actions = {
                    Switch(
                        checked = state.isPublic,
                        onCheckedChange = { onEvent(CatalogueEvent.SetPublic(it)) },
                        modifier = Modifier.testTag("catalogue_public_toggle"),
                    )
                    IconButton(
                        onClick = { onEvent(CatalogueEvent.ShareFullCatalogue) },
                        modifier = Modifier.testTag("catalogue_share_button"),
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = "Share catalogue")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onEvent(CatalogueEvent.OpenAddManualDialog) },
                modifier = Modifier.testTag("catalogue_add_fab"),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add product")
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isInitialLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.isEmpty -> CatalogueEmptyState(modifier = Modifier.align(Alignment.Center))
                else -> LazyColumn(modifier = Modifier.fillMaxSize().testTag("catalogue_product_list")) {
                    items(state.products, key = { it.productId }) { row ->
                        CatalogueProductRow(row = row, onClick = { onOpenProductDetail(row.productId) })
                    }
                }
            }
            state.shareMessage?.let { message ->
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .testTag("catalogue_share_message"),
                    onClick = { onEvent(CatalogueEvent.DismissShareMessage) },
                ) {
                    Text(text = message, modifier = Modifier.padding(12.dp))
                }
            }
        }
    }

    if (state.showAddManualDialog) {
        AlertDialog(
            onDismissRequest = { onEvent(CatalogueEvent.DismissAddManualDialog) },
            title = { Text("New product") },
            text = {
                OutlinedTextField(
                    value = state.addManualName,
                    onValueChange = { onEvent(CatalogueEvent.AddManualNameChanged(it)) },
                    label = { Text("Product name") },
                    modifier = Modifier.fillMaxWidth().testTag("catalogue_add_name_field"),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { onEvent(CatalogueEvent.ConfirmAddManual) },
                    modifier = Modifier.testTag("catalogue_add_confirm"),
                ) { Text("Add as Draft") }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(CatalogueEvent.DismissAddManualDialog) }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun CatalogueEmptyState(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "No products yet",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "Tap + to add your first product. You can also link products from your Tally stock items.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CatalogueProductRow(row: CatalogueProductRowUi, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .testTag("catalogue_product_${row.productId}"),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = row.displayName, style = MaterialTheme.typography.titleSmall)
                LifecycleChip(row.lifecycleState)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (row.source == CatalogueProductSource.Tally) "From Tally stock" else "Manually added",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!row.sourceAvailable) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("catalogue_unavailable_${row.productId}"),
                        )
                        Text(
                            text = "Source unavailable",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun LifecycleChip(state: CatalogueLifecycleState) {
    SuggestionChip(
        onClick = {},
        label = { Text(state.name) },
        colors = SuggestionChipDefaults.suggestionChipColors(),
        modifier = Modifier.testTag("catalogue_lifecycle_chip_${state.name}"),
    )
}
