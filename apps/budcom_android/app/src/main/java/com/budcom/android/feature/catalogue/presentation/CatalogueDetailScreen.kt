package com.budcom.android.feature.catalogue.presentation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budcom.android.feature.catalogue.domain.model.CatalogueLifecycleAction
import com.budcom.android.feature.catalogue.domain.model.PriceDisplayMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Composable
fun CatalogueDetailRoute(
    onBack: () -> Unit,
    viewModel: CatalogueDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (success && uri != null) viewModel.onEvent(CatalogueDetailEvent.PhotoSelected(uri))
    }
    val pickPhotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.onEvent(CatalogueDetailEvent.PhotoSelected(uri))
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                CatalogueDetailEffect.RequestGalleryPick ->
                    pickPhotoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                CatalogueDetailEffect.RequestCameraCapture -> {
                    val uri = createTempCameraUri(context)
                    pendingCameraUri = uri
                    takePictureLauncher.launch(uri)
                }
            }
        }
    }
    CatalogueDetailScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack)
}

/** A fresh, app-private temp file per capture, exposed via the same `${applicationId}.invoice-files`
 * FileProvider authority every other share/export flow in this app already uses, with its own
 * additive `catalogue-camera` cache-path entry — mirrors the existing Ledger/Voucher/Party share
 * pattern rather than inventing a new file-exposure mechanism for the one new case (handing a
 * destination `Uri` to the system camera app). Never cleaned up explicitly here: these are tiny
 * (single photo) app-cache files the OS is free to reclaim under storage pressure like any other
 * cache content, unlike the share caches' own deliberate retention policy. */
private fun createTempCameraUri(context: Context): Uri {
    val directory = File(context.cacheDir, "catalogue-camera").apply { mkdirs() }
    val file = File(directory, "${UUID.randomUUID()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.invoice-files", file)
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
        PhotosSection(state = state, canEdit = canEdit, onEvent = onEvent)

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

/**
 * Not required to publish (locked, Brainstorm Outcome §4) — this section never blocks anything,
 * it is purely additive. Tapping a thumbnail sets it primary; the small close button removes it.
 * A gentle nudge line (not a blocking requirement) appears only while the product has no photos
 * yet, matching the locked "gentle photo nudge" UX principle (Brainstorm Outcome §8).
 */
@Composable
private fun PhotosSection(state: CatalogueDetailUiState, canEdit: Boolean, onEvent: (CatalogueDetailEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Photos", style = MaterialTheme.typography.labelLarge)
        if (state.assets.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.assets, key = { it.assetId }) { asset ->
                    AssetThumbnail(
                        asset = asset,
                        enabled = canEdit,
                        onSetPrimary = { onEvent(CatalogueDetailEvent.SetPrimaryAsset(asset.assetId)) },
                        onDelete = { onEvent(CatalogueDetailEvent.DeleteAsset(asset.assetId)) },
                    )
                }
            }
        } else {
            Text(
                text = "Add a photo to make this pop — not required to publish.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (canEdit) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onEvent(CatalogueDetailEvent.TakePhoto) },
                    modifier = Modifier.testTag("catalogue_detail_take_photo"),
                ) { Text("Take photo") }
                OutlinedButton(
                    onClick = { onEvent(CatalogueDetailEvent.PickPhotoFromGallery) },
                    modifier = Modifier.testTag("catalogue_detail_pick_photo"),
                ) { Text("Choose photo") }
            }
        }
    }
}

@Composable
private fun AssetThumbnail(asset: CatalogueAssetUi, enabled: Boolean, onSetPrimary: () -> Unit, onDelete: () -> Unit) {
    var bitmap by remember(asset.file) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(asset.file) {
        // Decoded off the main thread -- same discipline as
        // BusinessProfileScreen's own logo display (BitmapFactory.decodeFile is blocking I/O).
        bitmap = asset.file?.let { file -> withContext(Dispatchers.IO) { BitmapFactory.decodeFile(file.absolutePath) } }
    }
    Box(modifier = Modifier.size(84.dp).testTag("catalogue_asset_${asset.assetId}")) {
        val current = bitmap
        val shape = RoundedCornerShape(8.dp)
        val border = if (asset.isPrimary) 3.dp to MaterialTheme.colorScheme.primary else 1.dp to MaterialTheme.colorScheme.outline
        if (current != null) {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .border(width = border.first, color = border.second, shape = shape)
                    .let { if (enabled) it.clickable(onClick = onSetPrimary) else it },
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().clip(shape).background(MaterialTheme.colorScheme.surfaceVariant))
        }
        if (asset.isPrimary) {
            Icon(
                Icons.Filled.Star,
                contentDescription = "Primary photo",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.BottomStart).size(18.dp).testTag("catalogue_asset_primary_${asset.assetId}"),
            )
        }
        if (enabled) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.align(Alignment.TopEnd).size(22.dp).testTag("catalogue_asset_delete_${asset.assetId}"),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Remove photo", tint = Color.White)
            }
        }
    }
}

private fun CatalogueLifecycleAction.label(): String = when (this) {
    CatalogueLifecycleAction.SubmitForReview -> "Submit for review"
    CatalogueLifecycleAction.Publish -> "Publish"
    CatalogueLifecycleAction.Archive -> "Archive"
    CatalogueLifecycleAction.Unarchive -> "Restore"
    CatalogueLifecycleAction.ReopenForEdit -> "Edit"
}
