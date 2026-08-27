package com.budcom.android.feature.catalogue.presentation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.budcom.android.feature.catalogue.domain.excel.CatalogueExcelRowOutcome
import com.budcom.android.feature.catalogue.domain.model.CatalogueLifecycleState
import com.budcom.android.feature.catalogue.domain.model.CatalogueProductSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CatalogueRoute(
    onOpenProductDetail: (String) -> Unit,
    onOpenStockItemPicker: () -> Unit,
    onOpenTransactionComposer: () -> Unit,
    viewModel: CatalogueViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    // Holds a just-generated export's CSV text between "user picked a destination" and "we can
    // actually write to it" -- CreateDocument's result callback has no way to carry our own
    // payload through, so it has to live here instead (same reason CatalogueDetailRoute holds
    // `pendingCameraUri` across its own two-step camera-capture flow).
    var pendingExportCsv by remember { mutableStateOf<String?>(null) }

    val pdfSaveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        viewModel.onEvent(CatalogueEvent.PdfSaveDestinationSelected(uri))
    }

    val importFilePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
            }
            if (text != null) viewModel.onEvent(CatalogueEvent.ExcelFileTextLoaded(text))
        }
    }
    val exportDestinationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val csv = pendingExportCsv
        pendingExportCsv = null
        if (uri == null || csv == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(csv) } }
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.shareIntent.collect { intent -> context.startActivity(intent) }
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                CatalogueEffect.NavigateToStockItemPicker -> onOpenStockItemPicker()
                CatalogueEffect.RequestExcelImportPick -> importFilePickerLauncher.launch("text/*")
                is CatalogueEffect.ExportCsvReady -> {
                    pendingExportCsv = effect.csvText
                    exportDestinationLauncher.launch(effect.suggestedFileName)
                }
                is CatalogueEffect.RequestPdfSave -> pdfSaveLauncher.launch(effect.suggestedFileName)
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
    CatalogueScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onOpenProductDetail = onOpenProductDetail,
        onOpenTransactionComposer = onOpenTransactionComposer,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogueScreen(
    state: CatalogueUiState,
    onEvent: (CatalogueEvent) -> Unit,
    onOpenProductDetail: (String) -> Unit,
    onOpenTransactionComposer: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Catalogue") },
                actions = {
                    IconButton(
                        onClick = onOpenTransactionComposer,
                        modifier = Modifier.testTag("catalogue_new_order_button"),
                    ) {
                        Icon(Icons.Filled.ShoppingCart, contentDescription = "New order")
                    }
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
                                text = { Text("Save PDF") },
                                onClick = { onEvent(CatalogueEvent.SaveFullCatalogue) },
                                modifier = Modifier.testTag("catalogue_share_menu_save_pdf"),
                            )
                            DropdownMenuItem(
                                text = { Text("Share a category") },
                                onClick = { onEvent(CatalogueEvent.OpenCategoryShareDialog) },
                                modifier = Modifier.testTag("catalogue_share_menu_category"),
                            )
                        }
                    }
                    Box {
                        IconButton(
                            onClick = { onEvent(CatalogueEvent.OpenMoreMenu) },
                            modifier = Modifier.testTag("catalogue_more_button"),
                        ) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = state.showMoreMenu,
                            onDismissRequest = { onEvent(CatalogueEvent.DismissMoreMenu) },
                            modifier = Modifier.testTag("catalogue_more_menu"),
                        ) {
                            DropdownMenuItem(
                                text = { Text("Import from Excel (CSV)") },
                                onClick = { onEvent(CatalogueEvent.ImportFromExcel) },
                                modifier = Modifier.testTag("catalogue_more_menu_import"),
                            )
                            DropdownMenuItem(
                                text = { Text("Export to Excel (CSV)") },
                                onClick = { onEvent(CatalogueEvent.ExportToExcel) },
                                modifier = Modifier.testTag("catalogue_more_menu_export"),
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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            BranchSelectorRow(state = state, onEvent = onEvent)
            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
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

    if (state.showAddBranchDialog) {
        AlertDialog(
            onDismissRequest = { onEvent(CatalogueEvent.DismissAddBranchDialog) },
            title = { Text("New branch") },
            text = {
                OutlinedTextField(
                    value = state.addBranchName,
                    onValueChange = { onEvent(CatalogueEvent.AddBranchNameChanged(it)) },
                    label = { Text("Branch name") },
                    modifier = Modifier.fillMaxWidth().testTag("catalogue_add_branch_name_field"),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { onEvent(CatalogueEvent.ConfirmAddBranch) },
                    modifier = Modifier.testTag("catalogue_add_branch_confirm"),
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(CatalogueEvent.DismissAddBranchDialog) }) { Text("Cancel") }
            },
        )
    }

    val importPreview = state.importPreview
    if (importPreview != null) {
        AlertDialog(
            onDismissRequest = { onEvent(CatalogueEvent.DismissImportPreview) },
            title = { Text("Import preview") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()).testTag("catalogue_import_preview"),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "${importPreview.createCount} new, ${importPreview.updateCount} updated, " +
                            "${importPreview.skipCount} skipped out of ${importPreview.outcomes.size} rows.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Native fields (Product Name, Unit, SKU, HSN, GST Rate, Price, etc.) are matched by " +
                            "their reserved names and applied as shown; every other column is kept as a custom " +
                            "field, unchanged, never interpreted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.importDuplicateHeaderWarnings.isNotEmpty()) {
                        Text(
                            "Duplicate column header(s), only the first used: ${state.importDuplicateHeaderWarnings.joinToString()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    importPreview.outcomes.filterIsInstance<CatalogueExcelRowOutcome.Skipped>().forEach { skipped ->
                        Text(
                            "Row ${skipped.rowNumber} skipped: ${skipped.reason}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("catalogue_import_skip_${skipped.rowNumber}"),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onEvent(CatalogueEvent.ConfirmImport) },
                    enabled = !state.isImporting && (importPreview.createCount > 0 || importPreview.updateCount > 0),
                    modifier = Modifier.testTag("catalogue_import_confirm"),
                ) { Text(if (state.isImporting) "Importing…" else "Import") }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(CatalogueEvent.DismissImportPreview) }) { Text("Cancel") }
            },
        )
    }
}

/**
 * A company-level branch selector (architecture §17), scoping context only -- selecting a branch
 * never filters [CatalogueUiState.products]: "One shared catalogue across branches" is locked
 * (Brainstorm Outcome §4). Deliberately minimal, matching this task's own scope discipline: a
 * dropdown to pick an existing branch or the catalogue-wide default, plus a single "Add branch"
 * action (name only, mirroring the manual-product-creation dialog's own minimal-fields precedent) --
 * no branch editing, deactivation, or a dedicated management screen, none of which are part of the
 * locked MVP-1.4 scope.
 */
@Composable
private fun BranchSelectorRow(state: CatalogueUiState, onEvent: (CatalogueEvent) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            TextButton(
                onClick = { onEvent(CatalogueEvent.OpenBranchMenu) },
                modifier = Modifier.testTag("catalogue_branch_selector"),
            ) { Text(state.selectedBranchName) }
            DropdownMenu(
                expanded = state.showBranchMenu,
                onDismissRequest = { onEvent(CatalogueEvent.DismissBranchMenu) },
                modifier = Modifier.testTag("catalogue_branch_menu"),
            ) {
                DropdownMenuItem(
                    text = { Text("All branches") },
                    onClick = { onEvent(CatalogueEvent.SelectBranch(null)) },
                    modifier = Modifier.testTag("catalogue_branch_menu_all"),
                )
                state.branches.forEach { branch ->
                    DropdownMenuItem(
                        text = { Text(branch.name) },
                        onClick = { onEvent(CatalogueEvent.SelectBranch(branch.branchId)) },
                        modifier = Modifier.testTag("catalogue_branch_menu_${branch.branchId}"),
                    )
                }
                DropdownMenuItem(
                    text = { Text("+ Add branch") },
                    onClick = { onEvent(CatalogueEvent.OpenAddBranchDialog) },
                    modifier = Modifier.testTag("catalogue_branch_menu_add"),
                )
            }
        }
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
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            CatalogueProductThumbnail(row = row)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
}

/**
 * Photo-display fix: the product's already-stored, already-resolved primary asset file
 * ([CatalogueProductRowUi.primaryAssetFile], resolved by [CatalogueViewModel] via the existing
 * [com.budcom.android.feature.catalogue.domain.repository.CatalogueRepository.listAssets]/
 * `resolveAssetFile`) simply had nowhere to render in this list row before -- this reuses the exact
 * decode-on-a-background-thread pattern [com.budcom.android.feature.catalogue.presentation.CatalogueDetailScreen]'s
 * own `AssetThumbnail` already established (itself mirroring `BusinessProfileScreen`'s logo
 * display), not a new image-loading mechanism. A missing/invalid file (deleted from disk, stale
 * reference) decodes to `null` and falls back to the existing no-image placeholder, never crashing.
 */
@Composable
private fun CatalogueProductThumbnail(row: CatalogueProductRowUi) {
    var bitmap by remember(row.primaryAssetFile) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(row.primaryAssetFile) {
        bitmap = row.primaryAssetFile?.let { file ->
            withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull() }
        }
    }
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .testTag("catalogue_product_thumbnail_${row.productId}"),
        contentAlignment = Alignment.Center,
    ) {
        val current = bitmap
        if (current != null) {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
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
