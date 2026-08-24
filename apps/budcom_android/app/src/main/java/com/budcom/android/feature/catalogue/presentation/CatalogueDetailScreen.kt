package com.budcom.android.feature.catalogue.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.budcom.android.feature.catalogue.domain.model.CatalogueLifecycleAction
import com.budcom.android.feature.catalogue.domain.model.PriceDisplayMode

@Composable
fun CatalogueDetailRoute(
    onBack: () -> Unit,
    viewModel: CatalogueDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CatalogueDetailScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogueDetailScreen(
    state: CatalogueDetailUiState,
    onEvent: (CatalogueDetailEvent) -> Unit,
    onBack: () -> Unit,
) {
    LaunchedEffect(state.message) {
        // Message is transient UI feedback (Snackbar-equivalent kept minimal per this task's
        // "no excessive configuration screens" UX principle) -- auto-dismissed by ViewModel state,
        // no host wiring needed for this MVP surface.
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.product?.displayName ?: "Product") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.notFound || state.product == null -> Text(
                    text = "Product not found",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                else -> CatalogueDetailContent(state, onEvent)
            }
        }
    }
}

@Composable
private fun CatalogueDetailContent(state: CatalogueDetailUiState, onEvent: (CatalogueDetailEvent) -> Unit) {
    val product = state.product!!
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LifecycleChip(product.lifecycleState)

        if (!product.sourceAvailable) {
            Card(modifier = Modifier.fillMaxWidth().testTag("catalogue_detail_unavailable_banner")) {
                Text(
                    text = "This product's linked Tally stock item is no longer available. " +
                        "It has not been deleted or removed from Catalogue — you can re-link it or leave it as-is.",
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        if (product.tallyName != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("From Tally (read-only)", style = MaterialTheme.typography.labelLarge)
                    ReadOnlyRow("Name", product.tallyName)
                    ReadOnlyRow("Unit", product.unit)
                    ReadOnlyRow("HSN", product.hsnCode)
                    ReadOnlyRow("GST rate", product.gstRate)
                    ReadOnlyRow("Stock group", product.stockGroupKey)
                }
            }
        }

        val canEdit = state.canEdit
        OutlinedTextField(
            value = state.descriptionDraft,
            onValueChange = { onEvent(CatalogueDetailEvent.DescriptionChanged(it)) },
            label = { Text("Description") },
            enabled = canEdit,
            modifier = Modifier.fillMaxWidth().testTag("catalogue_detail_description"),
        )
        OutlinedTextField(
            value = state.specificationsDraft,
            onValueChange = { onEvent(CatalogueDetailEvent.SpecificationsChanged(it)) },
            label = { Text("Specifications") },
            enabled = canEdit,
            modifier = Modifier.fillMaxWidth().testTag("catalogue_detail_specifications"),
        )
        OutlinedTextField(
            value = state.categoryDraft,
            onValueChange = { onEvent(CatalogueDetailEvent.CategoryChanged(it)) },
            label = { Text("Category (customer-facing)") },
            enabled = canEdit,
            modifier = Modifier.fillMaxWidth().testTag("catalogue_detail_category"),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.priceDisplayMode == PriceDisplayMode.Open,
                onClick = { onEvent(CatalogueDetailEvent.PriceDisplayModeChanged(PriceDisplayMode.Open)) },
                label = { Text("Show price") },
                enabled = canEdit,
                modifier = Modifier.testTag("catalogue_detail_price_open"),
            )
            FilterChip(
                selected = state.priceDisplayMode == PriceDisplayMode.ContactForPrice,
                onClick = { onEvent(CatalogueDetailEvent.PriceDisplayModeChanged(PriceDisplayMode.ContactForPrice)) },
                label = { Text("Contact for price") },
                enabled = canEdit,
                modifier = Modifier.testTag("catalogue_detail_price_contact"),
            )
        }
        if (state.priceDisplayMode == PriceDisplayMode.Open) {
            OutlinedTextField(
                value = state.manualPriceDraft,
                onValueChange = { onEvent(CatalogueDetailEvent.ManualPriceChanged(it)) },
                label = { Text("Price") },
                enabled = canEdit,
                modifier = Modifier.fillMaxWidth().testTag("catalogue_detail_price_amount"),
            )
        }

        if (canEdit) {
            Button(
                onClick = { onEvent(CatalogueDetailEvent.SaveEnrichment) },
                enabled = state.isDirty && !state.isSaving,
                modifier = Modifier.fillMaxWidth().testTag("catalogue_detail_save"),
            ) { Text(if (state.isSaving) "Saving…" else "Save") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.availableActions.forEach { action ->
                OutlinedButton(
                    onClick = { onEvent(CatalogueDetailEvent.Transition(action)) },
                    modifier = Modifier.testTag("catalogue_detail_action_${action.name}"),
                ) { Text(action.label()) }
            }
        }

        state.message?.let {
            Text(text = it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("catalogue_detail_message"))
        }
    }
}

@Composable
private fun ReadOnlyRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "$label:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun CatalogueLifecycleAction.label(): String = when (this) {
    CatalogueLifecycleAction.SubmitForReview -> "Submit for review"
    CatalogueLifecycleAction.Publish -> "Publish"
    CatalogueLifecycleAction.Archive -> "Archive"
    CatalogueLifecycleAction.Unarchive -> "Restore"
    CatalogueLifecycleAction.ReopenForEdit -> "Edit"
}
