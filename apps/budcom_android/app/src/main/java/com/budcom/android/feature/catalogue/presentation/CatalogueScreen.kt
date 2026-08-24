package com.budcom.android.feature.catalogue.presentation

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.budcom.android.feature.catalogue.domain.model.CatalogueLifecycleState
import com.budcom.android.feature.catalogue.domain.model.CatalogueProductSource

@Composable
fun CatalogueRoute(
    onOpenProductDetail: (String) -> Unit,
    onOpenStockItemPicker: () -> Unit,
    viewModel: CatalogueViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.shareIntent.collect { intent -> context.startActivity(intent) }
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                CatalogueEffect.NavigateToStockItemPicker -> onOpenStockItemPicker()
            }
        }
    }
    // Found live on a real device (2026-08-24): returning from the detail screen after a
    // Publish/Archive/enrichment edit left this list showing stale lifecycle-state chips.
    // NavHost disposes and recreates this whole composable (a fresh LaunchedEffect scope) every
    // time this destination becomes current again -- a "skip the first resume" guard here was
    // tried first and does NOT work, precisely because every return looks like a first resume to
    // a freshly recomposed effect scope. The ViewModel instance itself persists (hiltViewModel is
    // entry-scoped), so this harmless extra refresh on true first-entry just races the ViewModel's
    // own initial company-subscription load, not a correctness issue. Mirrors DashboardScreen's
    // own RESUMED-lifecycle reconciliation pattern.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.onEvent(CatalogueEvent.Refresh)
        }
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
                    Box {
                        IconButton(
                            onClick = { onEvent(CatalogueEvent.OpenShareMenu) },
                            modifier = Modifier.testTag("catalogue_share_button"),
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = "Share catalogue")
                        }
                        DropdownMenu(
                            expanded = state.showShareMenu,
                            onDismissRequest = { onEvent(CatalogueEvent.DismissShareMenu) },
                            modifier = Modifier.testTag("catalogue_share_menu"),
                        ) {
                            DropdownMenuItem(
                                text = { Text("Share full catalogue") },
                                onClick = { onEvent(CatalogueEvent.ShareFullCatalogue) },
                                modifier = Modifier.testTag("catalogue_share_menu_full"),
                            )
                            DropdownMenuItem(
                                text = { Text("Share a category") },
                                onClick = { onEvent(CatalogueEvent.OpenCategoryShareDialog) },
                                modifier = Modifier.testTag("catalogue_share_menu_category"),
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onEvent(CatalogueEvent.OpenAddChoiceDialog) },
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

    if (state.showAddChoiceDialog) {
        AlertDialog(
            onDismissRequest = { onEvent(CatalogueEvent.DismissAddChoiceDialog) },
            title = { Text("Add product") },
            text = { Text("Enter details yourself, or link a product you already have in Tally.") },
            confirmButton = {
                TextButton(
                    onClick = { onEvent(CatalogueEvent.ChooseLinkFromStock) },
                    modifier = Modifier.testTag("catalogue_choose_link_stock"),
                ) { Text("Link from Tally stock") }
            },
            dismissButton = {
                TextButton(
                    onClick = { onEvent(CatalogueEvent.ChooseManualEntry) },
                    modifier = Modifier.testTag("catalogue_choose_manual"),
                ) { Text("Enter manually") }
            },
        )
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

    if (state.showCategoryShareDialog) {
        AlertDialog(
            onDismissRequest = { onEvent(CatalogueEvent.DismissCategoryShareDialog) },
            title = { Text("Share a category") },
            text = {
                if (state.availableCategories.isEmpty()) {
                    Text("No categories among your published products yet.")
                } else {
                    Column(modifier = Modifier.testTag("catalogue_category_share_list")) {
                        state.availableCategories.forEach { category ->
                            Text(
                                text = category,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onEvent(CatalogueEvent.ShareCategory(category)) }
                                    .padding(vertical = 12.dp)
                                    .testTag("catalogue_category_share_$category"),
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { onEvent(CatalogueEvent.DismissCategoryShareDialog) }) { Text("Cancel") }
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
