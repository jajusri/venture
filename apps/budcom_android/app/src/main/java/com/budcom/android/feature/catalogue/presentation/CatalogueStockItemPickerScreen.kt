package com.budcom.android.feature.catalogue.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItem

@Composable
fun CatalogueStockItemPickerRoute(
    onLinked: (String) -> Unit,
    onLinkedAll: () -> Unit,
    onBack: () -> Unit,
    viewModel: CatalogueStockItemPickerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.linked.collect { productId -> onLinked(productId) }
    }
    LaunchedEffect(viewModel) {
        viewModel.linkedAll.collect { onLinkedAll() }
    }
    CatalogueStockItemPickerScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogueStockItemPickerScreen(
    state: CatalogueStockItemPickerUiState,
    onEvent: (CatalogueStockItemPickerEvent) -> Unit,
    onBack: () -> Unit,
) {
    if (state.showLinkAllConfirmation) {
        AlertDialog(
            onDismissRequest = { onEvent(CatalogueStockItemPickerEvent.DismissLinkAllConfirmation) },
            title = { Text("Link all stock items?") },
            text = { Text("This creates a Draft product for every one of the ${state.allItems.size} Tally stock items not already in your Catalogue.") },
            confirmButton = {
                TextButton(onClick = { onEvent(CatalogueStockItemPickerEvent.ConfirmLinkAll) }) { Text("Link all") }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(CatalogueStockItemPickerEvent.DismissLinkAllConfirmation) }) { Text("Cancel") }
            },
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Link from Tally stock") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    if (state.allItems.isNotEmpty()) {
                        TextButton(
                            onClick = { onEvent(CatalogueStockItemPickerEvent.OpenLinkAllConfirmation) },
                            enabled = !state.isLinking,
                            modifier = Modifier.testTag("catalogue_picker_link_all"),
                        ) { Text("Link all") }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { onEvent(CatalogueStockItemPickerEvent.SearchChanged(it)) },
                label = { Text("Search stock items") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("catalogue_picker_search"),
            )
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    state.isEmpty -> Text(
                        text = "Every synced Tally stock item is already in your Catalogue.",
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.filteredItems.isEmpty() -> Text(
                        text = "No stock items match \"${state.searchQuery}\".",
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> LazyColumn(modifier = Modifier.fillMaxSize().testTag("catalogue_picker_list")) {
                        items(state.filteredItems, key = { it.id }) { item ->
                            StockItemPickerRow(
                                item = item,
                                enabled = !state.isLinking,
                                onClick = { onEvent(CatalogueStockItemPickerEvent.Pick(item.id)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StockItemPickerRow(item: StockItem, enabled: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .testTag("catalogue_picker_item_${item.id}"),
        onClick = onClick,
        enabled = enabled,
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = item.name, style = MaterialTheme.typography.titleSmall)
            val subtitle = listOfNotNull(item.parentGroup, item.baseUnit).joinToString(" · ")
            if (subtitle.isNotBlank()) {
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
