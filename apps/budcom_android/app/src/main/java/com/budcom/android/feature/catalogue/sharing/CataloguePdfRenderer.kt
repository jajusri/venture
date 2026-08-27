package com.budcom.android.feature.catalogue.sharing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.budcom.android.feature.catalogue.domain.model.CataloguePriceState
import com.budcom.android.feature.catalogue.domain.model.CatalogueProduct
import com.budcom.android.feature.catalogue.domain.model.CataloguePublishedSnapshot
import com.budcom.android.feature.catalogue.domain.model.resolveCataloguePriceState
import com.budcom.android.feature.catalogue.domain.repository.CatalogueRepository
import java.io.File

internal object CataloguePdfRenderer {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 36f
    private const val CARD_GAP = 12f
    private const val CARD_WIDTH = (PAGE_WIDTH - MARGIN * 2 - CARD_GAP) / 2f
    private const val IMAGE_SIZE = 92
    private const val CARD_HEIGHT = 172f

    suspend fun render(payload: CatalogueSharePayload, repository: CatalogueRepository, output: File) {
        val products = payload.snapshots.map { snapshot ->
            CataloguePdfProduct(
                snapshot = snapshot,
                product = runCatching { repository.findProduct(payload.companyId, snapshot.productId) }.getOrNull(),
                image = resolveImage(payload.companyId, snapshot, repository),
            )
        }
        renderProducts(payload, products, output)
    }

    internal fun renderProducts(payload: CatalogueSharePayload, products: List<CataloguePdfProduct>, output: File) {
        val document = PdfDocument()
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(35, 35, 35); textSize = 8f }
        val heading = Paint(body).apply { typeface = Typeface.DEFAULT_BOLD; textSize = 18f }
        val subheading = Paint(body).apply { color = Color.DKGRAY; textSize = 9f }
        val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.LTGRAY; strokeWidth = 0.7f }
        val totalPages = maxOf(1, (products.size + 5) / 6)
        try {
            (1..totalPages).forEach { pageNumber ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                val canvas = page.canvas
                if (pageNumber == 1) {
                    canvas.drawText(payload.businessName?.trim().takeUnless { it.isNullOrBlank() } ?: "BUDCOM Catalogue", MARGIN, 50f, heading)
                    canvas.drawText(scopeLabel(payload.scope), MARGIN, 68f, subheading)
                    canvas.drawLine(MARGIN, 82f, PAGE_WIDTH - MARGIN, 82f, rule)
                }
                val start = (pageNumber - 1) * 6
                products.drop(start).take(6).forEachIndexed { index, item ->
                    val column = index % 2
                    val row = index / 2
                    drawCard(canvas, item, MARGIN + column * (CARD_WIDTH + CARD_GAP), 96f + row * (CARD_HEIGHT + CARD_GAP), body, rule)
                }
                canvas.drawLine(MARGIN, 790f, PAGE_WIDTH - MARGIN, 790f, rule)
                canvas.drawText("Generated from the authorised published Catalogue", MARGIN, 808f, subheading)
                canvas.drawText("Page $pageNumber of $totalPages", PAGE_WIDTH - MARGIN - 62f, 808f, subheading)
                document.finishPage(page)
            }
            output.outputStream().use(document::writeTo)
        } finally {
            products.forEach { it.image?.recycle() }
            document.close()
        }
    }

    private suspend fun resolveImage(companyId: String, snapshot: CataloguePublishedSnapshot, repository: CatalogueRepository): Bitmap? {
        val assets = runCatching { repository.listAssets(companyId, snapshot.productId) }.getOrDefault(emptyList())
        val asset = assets.firstOrNull { it.assetId == snapshot.primaryAssetId } ?: assets.firstOrNull { it.isPrimary } ?: assets.firstOrNull()
        val file = asset?.let { runCatching { repository.resolveAssetFile(companyId, snapshot.productId, it.filePath) }.getOrNull() }
        return file?.let(::decodeThumbnail)
    }

    private fun decodeThumbnail(file: File): Bitmap? {
        if (!file.isFile) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= IMAGE_SIZE && bounds.outHeight / (sample * 2) >= IMAGE_SIZE) sample *= 2
        return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun drawCard(canvas: Canvas, item: CataloguePdfProduct, left: Float, top: Float, body: Paint, rule: Paint) {
        val right = left + CARD_WIDTH
        canvas.drawRect(left, top, right, top + CARD_HEIGHT, rule)
        val imageLeft = left + 8f
        val imageTop = top + 8f
        val imageRect = Rect(imageLeft.toInt(), imageTop.toInt(), (imageLeft + IMAGE_SIZE).toInt(), (imageTop + IMAGE_SIZE).toInt())
        val placeholder = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(238, 240, 242) }
        canvas.drawRect(imageRect, placeholder)
        item.image?.let { canvas.drawBitmap(it, null, imageRect, Paint(Paint.ANTI_ALIAS_FLAG)) }
        val textLeft = imageLeft + IMAGE_SIZE + 10f
        val textWidth = right - textLeft - 8f
        drawFitted(canvas, item.snapshot.displayName, textLeft, top + 22f, textWidth, body, bold = true)
        item.product?.sku?.trim()?.takeIf(String::isNotBlank)?.let { drawFitted(canvas, "SKU: $it", textLeft, top + 38f, textWidth, body) }
        var y = top + IMAGE_SIZE + 116f
        item.snapshot.customerFacingCategory?.trim()?.takeIf(String::isNotBlank)?.let { drawLine(canvas, "Category: $it", left + 8f, y, right - 8f, body); y += 12f }
        drawLine(canvas, cataloguePdfPriceLine(item.snapshot), left + 8f, y, right - 8f, body)
        item.snapshot.specifications?.trim()?.takeIf(String::isNotBlank)?.let { drawFitted(canvas, it, left + 8f, y + 13f, right - left - 16f, body) }
    }

    private fun drawLine(canvas: Canvas, text: String, left: Float, baseline: Float, right: Float, paint: Paint) =
        drawFitted(canvas, text, left, baseline, right - left, paint)

    private fun drawFitted(canvas: Canvas, text: String, left: Float, baseline: Float, width: Float, source: Paint, bold: Boolean = false) {
        val paint = Paint(source).apply { if (bold) typeface = Typeface.DEFAULT_BOLD }
        var value = text.replace(Regex("\\s+"), " ").trim()
        while (value.isNotEmpty() && paint.measureText(value) > width) value = value.dropLast(1).trimEnd()
        if (value != text.trim() && value.isNotEmpty()) value = value.dropLast(3).trimEnd() + "..."
        if (value.isNotEmpty()) canvas.drawText(value, left, baseline, paint)
    }

    private fun scopeLabel(scope: CatalogueShareScope): String = when (scope) {
        CatalogueShareScope.FullCatalogue -> "Full Catalogue"
        is CatalogueShareScope.Category -> "Category: ${scope.name}"
    }
}

internal data class CataloguePdfProduct(
    val snapshot: CataloguePublishedSnapshot,
    val product: CatalogueProduct?,
    val image: Bitmap?,
)

internal fun cataloguePdfPriceLine(snapshot: CataloguePublishedSnapshot): String = when (val state = resolveCataloguePriceState(snapshot.priceDisplayMode, snapshot.resolvedPriceAmount, snapshot.resolvedPriceCurrencyCode)) {
    is CataloguePriceState.ActualPrice -> "Price: ${state.amount} ${state.currencyCode.orEmpty()}".trim()
    CataloguePriceState.NoPriceSupplied -> "Price: No price supplied"
    CataloguePriceState.ContactForPrice -> "Price: Contact for price"
}