package com.budcom.android.feature.catalogue.sharing

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.budcom.android.feature.catalogue.domain.model.CataloguePriceState
import com.budcom.android.feature.catalogue.domain.model.CataloguePublishedSnapshot
import com.budcom.android.feature.catalogue.domain.model.CatalogueTimestamp
import com.budcom.android.feature.catalogue.domain.model.CatalogueTimestampSource
import com.budcom.android.feature.catalogue.domain.model.PriceDisplayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CataloguePdfRendererTest {
    @Test
    fun rendersNonEmptyMultiPagePdfWithPlaceholderProducts() {
        val snapshots = (1..7).map { snapshot("p$it", "Product $it") }
        val payload = CatalogueSharePayload("co-1", "Acme", CatalogueShareScope.FullCatalogue, snapshots)
        val output = File.createTempFile("catalogue-renderer-", ".pdf")

        CataloguePdfRenderer.renderProducts(
            payload,
            snapshots.map { CataloguePdfProduct(it, null, null) },
            output,
        )

        assertTrue(output.isFile)
        assertTrue(output.length() > 0)
        ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer -> assertEquals(2, renderer.pageCount) }
        }
        output.delete()
    }

    @Test
    fun priceStatesRemainExplicit() {
        assertEquals("Price: 250 INR", cataloguePdfPriceLine(snapshot("p1", "Open", PriceDisplayMode.Open, "250")))
        assertEquals("Price: No price supplied", cataloguePdfPriceLine(snapshot("p2", "Missing", PriceDisplayMode.Open, null)))
        assertEquals("Price: Contact for price", cataloguePdfPriceLine(snapshot("p3", "Private", PriceDisplayMode.ContactForPrice, "999")))
    }

    private fun snapshot(
        productId: String,
        name: String,
        mode: PriceDisplayMode = PriceDisplayMode.ContactForPrice,
        amount: String? = null,
    ) = CataloguePublishedSnapshot(
        companyId = "co-1",
        productId = productId,
        displayName = name,
        description = "Description",
        specifications = "Specification",
        customerFacingCategory = "Range",
        priceDisplayMode = mode,
        resolvedPriceAmount = amount,
        resolvedPriceCurrencyCode = if (amount == null) null else "INR",
        primaryAssetId = null,
        publishedAt = CatalogueTimestamp(1_000L, CatalogueTimestampSource.DeviceLocalProvisional),
    )
}
