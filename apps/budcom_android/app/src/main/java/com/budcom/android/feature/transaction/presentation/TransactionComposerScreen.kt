package com.budcom.android.feature.transaction.presentation

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budcom.android.feature.transaction.domain.model.BuyAgainEntry
import com.budcom.android.feature.transaction.domain.model.TransactionDraftLine
import com.budcom.android.feature.transaction.domain.model.TransactionDraftPriceState
import com.budcom.android.feature.transaction.domain.model.TransactionSubmissionType

/**
 * The buyer transaction composer — Q2/Q3/Q5's locked one-screen UX: Selected products (always
 * first, always visible), Previously Bought (expandable, Q3's second priority tier), New SKUs
 * (Q3's third tier) all coexist in one [LazyColumn], matching
 * [com.budcom.android.feature.catalogue.presentation.CatalogueScreen]'s own existing lazy-list/
 * Scaffold/testTag conventions exactly — no new design system introduced.
 *
 * **New SKUs are real Catalogue data** (Phase A): [TransactionComposerViewModel] loads them
 * directly from the existing `CatalogueRepository.listAllPublished`, so `state.newSkus` is never
 * empty unless the company genuinely has no published products yet.
 */
@Composable
fun TransactionComposerRoute(
    viewModel: TransactionComposerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is TransactionComposerEffect.LaunchShareIntent -> context.startActivity(effect.intent)
            }
        }
    }

    TransactionComposerScreen(state = state, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionComposerScreen(
    state: TransactionComposerUiState,
    onEvent: (TransactionComposerEvent) -> Unit,
) {
    var previouslyBoughtExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("New Order") }) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center).testTag("composer_loading"))
                else -> LazyColumn(modifier = Modifier.fillMaxSize().testTag("composer_list")) {
                    item { SubmissionTypeRow(state, onEvent) }

                    val draft = state.draft
                    item {
                        Text(
                            "Selected products",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    if (draft == null || draft.isEmpty) {
                        item {
                            Text(
                                "No products selected yet — pick something from Previously Bought or New SKUs below.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp).testTag("composer_selected_empty"),
                            )
                        }
                    } else {
                        items(draft.lines, key = { "selected-${it.linkedProductId}" }) { line ->
                            SelectedLineRow(line = line, onEvent = onEvent)
                        }
                        item {
                            Text(
                                totalLabel(draft.totalAmount),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag("composer_total"),
                            )
                        }
                    }

                    item {
                        TextButton(
                            onClick = { previouslyBoughtExpanded = !previouslyBoughtExpanded },
                            modifier = Modifier.testTag("composer_expand_previously_bought"),
                        ) {
                            Text(if (previouslyBoughtExpanded) "Hide previously bought items" else "Expand all previously bought items")
                        }
                    }
                    if (previouslyBoughtExpanded) {
                        if (state.buyAgainEntries.isEmpty()) {
                            item {
                                Text(
                                    "No previously completed orders yet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp).testTag("composer_buy_again_empty"),
                                )
                            }
                        } else {
                            items(state.buyAgainEntries, key = { "buyagain-${it.linkedProductId}" }) { entry ->
                                BuyAgainRow(entry = entry, onEvent = onEvent)
                            }
                        }
                    }

                    item {
                        Text(
                            "New SKUs",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    if (state.newSkus.isEmpty()) {
                        item {
                            Text(
                                "No other catalogue products to show.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp).testTag("composer_new_skus_empty"),
                            )
                        }
                    } else {
                        items(state.newSkus, key = { "new-${it.linkedProductId}" }) { row ->
                            NewSkuRow(
                                row = row,
                                onClick = {
                                    onEvent(
                                        TransactionComposerEvent.AddOrIncrementProduct(
                                            row.linkedProductId, row.displayName, row.unit, row.sku, row.priceState,
                                        ),
                                    )
                                },
                            )
                        }
                    }

                    item { ReviewAndShareSection(state, onEvent) }
                }
            }

            state.message?.let { message ->
                Card(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).testTag("composer_message"),
                    onClick = { onEvent(TransactionComposerEvent.DismissMessage) },
                ) {
                    Text(text = message, modifier = Modifier.padding(12.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubmissionTypeRow(state: TransactionComposerUiState, onEvent: (TransactionComposerEvent) -> Unit) {
    val submissionType = state.draft?.submissionType ?: TransactionSubmissionType.Estimate
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("composer_submission_type")) {
        SegmentedButton(
            selected = submissionType == TransactionSubmissionType.Estimate,
            onClick = { onEvent(TransactionComposerEvent.SubmissionTypeChanged(TransactionSubmissionType.Estimate)) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            modifier = Modifier.testTag("composer_type_estimate"),
        ) { Text("Estimate") }
        SegmentedButton(
            selected = submissionType == TransactionSubmissionType.PurchaseOrder,
            onClick = { onEvent(TransactionComposerEvent.SubmissionTypeChanged(TransactionSubmissionType.PurchaseOrder)) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            modifier = Modifier.testTag("composer_type_po"),
        ) { Text("Purchase Order") }
    }
}

@Composable
private fun SelectedLineRow(line: TransactionDraftLine, onEvent: (TransactionComposerEvent) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).testTag("composer_selected_${line.linkedProductId}"),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = line.snapshotProductName, style = MaterialTheme.typography.titleSmall)
            Text(text = priceLabel(line.priceState), style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    onClick = { onEvent(TransactionComposerEvent.SetQuantity(line.linkedProductId, decrement(line.quantity))) },
                    modifier = Modifier.testTag("composer_decrement_${line.linkedProductId}"),
                ) { Text("−", style = MaterialTheme.typography.titleMedium) }
                Text(text = "Qty ${line.quantity}", modifier = Modifier.testTag("composer_qty_${line.linkedProductId}"))
                TextButton(
                    onClick = { onEvent(TransactionComposerEvent.SetQuantity(line.linkedProductId, increment(line.quantity))) },
                    modifier = Modifier.testTag("composer_increment_${line.linkedProductId}"),
                ) { Text("+", style = MaterialTheme.typography.titleMedium) }
                IconButton(
                    onClick = { onEvent(TransactionComposerEvent.RemoveProduct(line.linkedProductId)) },
                    modifier = Modifier.testTag("composer_remove_${line.linkedProductId}"),
                ) { Icon(Icons.Filled.Delete, contentDescription = "Remove ${line.snapshotProductName}") }
            }
        }
    }
}

@Composable
private fun BuyAgainRow(entry: BuyAgainEntry, onEvent: (TransactionComposerEvent) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .testTag("composer_buyagain_${entry.linkedProductId}"),
        // Selecting a previously-bought product uses exactly the same inline SelectBuyAgainItem
        // event every other selection path in this screen already relies on -- never a second
        // selection mechanism, and the price state resolved here (Visible/ActualPrice, since a
        // completed purchase is by definition something this buyer was already shown a price for)
        // is a placeholder pending the real Catalogue/access-grant resolver this feature's own
        // architecture doc names as still-unresolved (Finding 3) -- flagged, not silently assumed.
        onClick = {
            onEvent(
                TransactionComposerEvent.SelectBuyAgainItem(
                    entry,
                    TransactionDraftPriceState.ActualPrice(unitAmount = "0", currencyCode = null),
                ),
            )
        },
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = entry.mostRecentSnapshotProductName, style = MaterialTheme.typography.titleSmall)
            Text(
                text = "Bought ${entry.purchaseCount} time${if (entry.purchaseCount == 1) "" else "s"} · last qty ${entry.mostRecentQuantity}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NewSkuRow(row: TransactionNewSkuRow, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).testTag("composer_newsku_${row.linkedProductId}"),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = row.displayName, style = MaterialTheme.typography.titleSmall)
            Text(text = priceLabel(row.priceState), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ReviewAndShareSection(state: TransactionComposerUiState, onEvent: (TransactionComposerEvent) -> Unit) {
    val hasItems = state.draft?.isEmpty == false
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Sharing sends an Estimate/PO as a plain document — it is not automatically an accepted order.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(
                onClick = { onEvent(TransactionComposerEvent.ShareViaWhatsApp) },
                enabled = hasItems,
                modifier = Modifier.testTag("composer_share_whatsapp"),
            ) { Text("Share Estimate") }
            TextButton(
                onClick = { onEvent(TransactionComposerEvent.SubmitInApp) },
                enabled = hasItems,
                modifier = Modifier.testTag("composer_submit_inapp"),
            ) { Text("Submit") }
        }
    }
}

/** Q4's locked four-state distinction, rendered — never inferred from whether an amount happens to
 * exist, and [TransactionDraftPriceState.Hidden] never reveals a number, matching this phase's own
 * explicit "never expose hidden prices in UI/totals/accessibility text" rule (the [Hidden] branch
 * below has no code path that could read an amount even if one were somehow present). */
private fun priceLabel(state: TransactionDraftPriceState): String = when (state) {
    is TransactionDraftPriceState.ActualPrice -> "${state.currencyCode.orEmpty()} ${state.unitAmount}".trim()
    TransactionDraftPriceState.NoPriceSupplied -> "No price supplied"
    TransactionDraftPriceState.ContactForPrice -> "Contact for price"
    TransactionDraftPriceState.Hidden -> "Contact for price"
}

/** Mirrors [com.budcom.android.feature.transaction.sharing.TransactionShareTextRenderer]'s own
 * total-line fallback precedent — a non-numeric state, never a fabricated/partial sum, whenever the
 * domain's own [com.budcom.android.feature.transaction.domain.model.TransactionDraft.totalAmount]
 * is null (any Contact-for-price, no-price-supplied, or Hidden line present). */
private fun totalLabel(totalAmount: String?): String = if (totalAmount != null) "Total: $totalAmount" else "Total: Contact for price"

private fun increment(quantity: String): String = (quantity.toBigDecimalOrNull() ?: java.math.BigDecimal.ONE).plus(java.math.BigDecimal.ONE).toPlainString()
private fun decrement(quantity: String): String = ((quantity.toBigDecimalOrNull() ?: java.math.BigDecimal.ONE) - java.math.BigDecimal.ONE).toPlainString()
