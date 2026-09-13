package com.jajusri.venture.feature.catalogue.sharing

import com.jajusri.venture.feature.catalogue.domain.model.CataloguePublishedSnapshot
import com.jajusri.venture.feature.catalogue.domain.model.CatalogueTimestamp
import com.jajusri.venture.feature.catalogue.domain.model.CatalogueTimestampSource
import com.jajusri.venture.feature.catalogue.domain.model.PriceDisplayMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogueShareTextRendererTest {

    private fun snapshot(
        productId: String = "p1",
        displayName: String = "Widget",
        category: String? = null,
        priceDisplayMode: PriceDisplayMode = PriceDisplayMode.ContactForPrice,
        price: String? = null,
        description: String? = null,
    ) = CataloguePublishedSnapshot(
        companyId = "co-1",
        productId = productId,
        displayName = displayName,
        description = description,
        specifications = null,
        customerFacingCategory = category,
        priceDisplayMode = priceDisplayMode,
        resolvedPriceAmount = price,
        resolvedPriceCurrencyCode = if (price != null) "INR" else null,
        primaryAssetId = null,
        publishedAt = CatalogueTimestamp(1_000L, CatalogueTimestampSource.DeviceLocalProvisional),
    )

    @Test
    fun `uses the business name as the header when provided`() {
        val text = CatalogueShareTextRenderer.render("Acme Traders", CatalogueShareScope.FullCatalogue, listOf(snapshot()))
        assertTrue(text.lines().first() == "Acme Traders")
    }

    @Test
    fun `falls back to a generic header when no business name is available`() {
        val text = CatalogueShareTextRenderer.render(null, CatalogueShareScope.FullCatalogue, listOf(snapshot()))
        assertTrue(text.lines().first() == "VENTURE Catalogue")
    }

    @Test
    fun `blank business name also falls back to the generic header`() {
        val text = CatalogueShareTextRenderer.render("   ", CatalogueShareScope.FullCatalogue, listOf(snapshot()))
        assertTrue(text.lines().first() == "VENTURE Catalogue")
    }

    @Test
    fun `labels the scope for full catalogue and for a category`() {
        val full = CatalogueShareTextRenderer.render(null, CatalogueShareScope.FullCatalogue, listOf(snapshot()))
        assertTrue(full.contains("Full Catalogue"))

        val category = CatalogueShareTextRenderer.render(null, CatalogueShareScope.Category("Electronics"), listOf(snapshot()))
        assertTrue(category.contains("Category: Electronics"))
    }

    @Test
    fun `Contact-for-price mode never shows an amount, even if one is set`() {
        val text = CatalogueShareTextRenderer.render(
            null, CatalogueShareScope.FullCatalogue,
            listOf(snapshot(priceDisplayMode = PriceDisplayMode.ContactForPrice, price = "999")),
        )
        assertTrue(text.contains("Price: Contact for price"))
        assertFalse(text.contains("999"))
    }

    @Test
    fun `Open mode with a resolved price shows the amount and currency`() {
        val text = CatalogueShareTextRenderer.render(
            null, CatalogueShareScope.FullCatalogue,
            listOf(snapshot(priceDisplayMode = PriceDisplayMode.Open, price = "499")),
        )
        assertTrue(text.contains("Price: 499 INR"))
    }

    @Test
    fun `Open mode with no resolved price shows an explicit not-supplied state, never blank and never Contact-for-price`() {
        // Mid-MVP-1.4 lock: "No Price Supplied" must never be confused with the seller's
        // deliberate "Contact for Price" choice -- they are different states with different
        // meanings, even though both currently render with no numeric amount.
        val text = CatalogueShareTextRenderer.render(
            null, CatalogueShareScope.FullCatalogue,
            listOf(snapshot(priceDisplayMode = PriceDisplayMode.Open, price = null)),
        )
        assertTrue(text.contains("Price: Not supplied yet"))
        assertFalse(text.contains("Contact for price"))
    }

    @Test
    fun `every product in the list appears in the rendered output`() {
        val text = CatalogueShareTextRenderer.render(
            null, CatalogueShareScope.FullCatalogue,
            listOf(snapshot(productId = "p1", displayName = "Widget A"), snapshot(productId = "p2", displayName = "Widget B")),
        )
        assertTrue(text.contains("Widget A"))
        assertTrue(text.contains("Widget B"))
    }

    @Test
    fun `description is included when present and omitted when absent`() {
        val withDescription = CatalogueShareTextRenderer.render(
            null, CatalogueShareScope.FullCatalogue, listOf(snapshot(description = "A fine widget")),
        )
        assertTrue(withDescription.contains("A fine widget"))

        val withoutDescription = CatalogueShareTextRenderer.render(null, CatalogueShareScope.FullCatalogue, listOf(snapshot()))
        assertFalse(withoutDescription.contains("null"))
    }

    @Test
    fun `category line only appears when a customer-facing category is set`() {
        val withCategory = CatalogueShareTextRenderer.render(null, CatalogueShareScope.FullCatalogue, listOf(snapshot(category = "Electronics")))
        assertTrue(withCategory.contains("Category: Electronics"))

        val withoutCategory = CatalogueShareTextRenderer.render(null, CatalogueShareScope.FullCatalogue, listOf(snapshot(category = null)))
        assertFalse(withoutCategory.lines().drop(3).any { it.startsWith("Category:") })
    }
}
