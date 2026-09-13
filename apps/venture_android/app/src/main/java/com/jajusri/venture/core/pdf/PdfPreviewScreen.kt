package com.jajusri.venture.core.pdf

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** One extra action alongside the always-present Save/Share (e.g. a WhatsApp shortcut). */
data class PdfPreviewAction(
    val label: String,
    val testTag: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * TD-028: shared in-app PDF preview for both Voucher and Ledger Statement PDFs — the gateway
 * between "generate" and "save/share" the required MVP-1 workflow calls for. Renders the exact
 * same generated file Save/Share operate on (same [filePath]), never a separately regenerated
 * document. Offline, no new dependency: [PdfPageRenderer] wraps the platform's own
 * `android.graphics.pdf.PdfRenderer`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfPreviewScreen(
    filePath: String,
    title: String,
    renderer: PdfPageRenderer,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
    isActionBusy: Boolean = false,
    extraActions: List<PdfPreviewAction> = emptyList(),
) {
    BackHandler(onBack = onBack)

    var document by remember(filePath) { mutableStateOf<PdfPreviewDocument?>(null) }
    var loadFailed by remember(filePath) { mutableStateOf(false) }

    DisposableEffect(filePath) {
        onDispose { document?.close() }
    }
    LaunchedEffect(filePath) {
        document = renderer.open(filePath)
        loadFailed = document == null
    }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("pdf_preview_screen"),
        topBar = {
            TopAppBar(
                title = { Text(text = title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("pdf_preview_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            BottomAppBar {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onSave,
                        enabled = !isActionBusy,
                        modifier = Modifier.testTag("pdf_preview_save"),
                    ) { Text("Save") }
                    Button(
                        onClick = onShare,
                        enabled = !isActionBusy,
                        modifier = Modifier.testTag("pdf_preview_share"),
                    ) { Text("Share") }
                    extraActions.forEach { action ->
                        Button(
                            onClick = action.onClick,
                            enabled = !isActionBusy && action.enabled,
                            modifier = Modifier.testTag(action.testTag),
                        ) { Text(action.label) }
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
            when {
                loadFailed -> Text(
                    text = "This PDF could not be opened for preview.",
                    modifier = Modifier.padding(16.dp).testTag("pdf_preview_error"),
                )
                document == null -> CircularProgressIndicator(modifier = Modifier.testTag("pdf_preview_loading"))
                else -> PdfPreviewPages(document = document!!, modifier = Modifier.testTag("pdf_preview_pages"))
            }
        }
    }
}

@Composable
private fun PdfPreviewPages(document: PdfPreviewDocument, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val targetWidthPx = with(density) { 360.dp.toPx().toInt() }.coerceAtLeast(1)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(document.pageCount) { index ->
            PdfPreviewPage(pageIndex = index, document = document, targetWidthPx = targetWidthPx)
        }
    }
}

@Composable
private fun PdfPreviewPage(pageIndex: Int, document: PdfPreviewDocument, targetWidthPx: Int) {
    var bitmap by remember(pageIndex) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(pageIndex, targetWidthPx) {
        bitmap = document.renderPage(pageIndex, targetWidthPx)
    }

    var scale by remember(pageIndex) { mutableFloatStateOf(1f) }
    var offset by remember(pageIndex) { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier.fillMaxWidth().testTag("pdf_preview_page_$pageIndex"),
        contentAlignment = Alignment.Center,
    ) {
        val current = bitmap
        if (current != null) {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = "PDF page ${pageIndex + 1}",
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                    )
                    .pointerInput(pageIndex) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 4f)
                            offset = if (scale <= 1f) Offset.Zero else offset + pan
                        }
                    },
            )
        } else {
            CircularProgressIndicator(modifier = Modifier.padding(24.dp))
        }
    }
}
