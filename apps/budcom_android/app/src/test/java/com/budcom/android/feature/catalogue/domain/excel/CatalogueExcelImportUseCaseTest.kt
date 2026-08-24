package com.budcom.android.feature.catalogue.domain.excel

import com.budcom.android.feature.catalogue.domain.model.PriceDisplayMode
import com.budcom.android.feature.catalogue.presentation.FakeCatalogueClock
import com.budcom.android.feature.catalogue.presentation.FakeCatalogueRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogueExcelImportUseCaseTest {

    private fun row(rowNumber: Int, vararg cells: Pair<String, String?>) = CatalogueExcelRow(rowNumber, cells.toMap())

    @Test
    fun `a Create outcome lands as a new Manual Draft with its enrichment applied`() = runTest {
        val repository = FakeCatalogueRepository()
        val useCase = CatalogueExcelImportUseCase(repository, FakeCatalogueClock())
        val rows = listOf(
            row(
                1,
                CatalogueExcelColumns.PRODUCT_NAME to "Hand-made Basket",
                CatalogueExcelColumns.UNIT to "Nos",
                CatalogueExcelColumns.DESCRIPTION to "Woven basket",
                CatalogueExcelColumns.PRICE_DISPLAY_MODE to "Open",
                CatalogueExcelColumns.PRICE to "499",
            ),
        )
        val preview = CatalogueExcelValidator.preview(rows, resolveExistingProductId = { null })

        val result = useCase.commit("co-1", rows, preview)

        assertEquals(1, result.created)
        assertEquals(0, result.updated)
        val product = repository.products["co-1"]!!.single()
        assertEquals("Hand-made Basket", product.displayName)
        assertEquals("Woven basket", product.description)
        assertEquals(PriceDisplayMode.Open, product.priceDisplayMode)
        assertEquals("499", product.manualPriceAmount)
        assertEquals(com.budcom.android.feature.catalogue.domain.model.CatalogueLifecycleState.Draft, product.lifecycleState)
    }

    @Test
    fun `a Skipped outcome is never committed`() = runTest {
        val repository = FakeCatalogueRepository()
        val useCase = CatalogueExcelImportUseCase(repository, FakeCatalogueClock())
        val rows = listOf(row(1, CatalogueExcelColumns.UNIT to "Nos"))
        val preview = CatalogueExcelValidator.preview(rows, resolveExistingProductId = { null })

        val result = useCase.commit("co-1", rows, preview)

        assertEquals(0, result.created)
        assertEquals(1, result.skipped)
        assertEquals(0, repository.products["co-1"]?.size ?: 0)
    }

    @Test
    fun `an Update outcome applies enrichment to the existing product without changing its identity`() = runTest {
        val repository = FakeCatalogueRepository()
        val existing = repository.createManualDraft("co-1", "Widget", com.budcom.android.feature.catalogue.domain.model.CatalogueTimestamp(1L, com.budcom.android.feature.catalogue.domain.model.CatalogueTimestampSource.DeviceLocalProvisional))
        val useCase = CatalogueExcelImportUseCase(repository, FakeCatalogueClock())
        val rows = listOf(
            row(
                1,
                CatalogueExcelColumns.PRODUCT_NAME to "Widget",
                CatalogueExcelColumns.UNIT to "Nos",
                CatalogueExcelColumns.SKU to "SKU-1",
                CatalogueExcelColumns.DESCRIPTION to "Updated description",
            ),
        )
        val preview = CatalogueExcelValidator.preview(rows, resolveExistingProductId = { existing.productId })

        val result = useCase.commit("co-1", rows, preview)

        assertEquals(1, result.updated)
        assertEquals(1, repository.products["co-1"]!!.size)
        assertEquals("Updated description", repository.products["co-1"]!!.single().description)
        assertEquals(existing.productId, repository.products["co-1"]!!.single().productId)
    }

    @Test
    fun `a custom column's value is persisted per product, round-trippable on export`() = runTest {
        val repository = FakeCatalogueRepository()
        val useCase = CatalogueExcelImportUseCase(repository, FakeCatalogueClock())
        val rows = listOf(
            row(
                1,
                CatalogueExcelColumns.PRODUCT_NAME to "Basket",
                CatalogueExcelColumns.UNIT to "Nos",
                "Warranty (months)" to "12",
            ),
        )
        val preview = CatalogueExcelValidator.preview(rows, resolveExistingProductId = { null })

        useCase.commit("co-1", rows, preview)

        val product = repository.products["co-1"]!!.single()
        val customFields = repository.listCustomFields("co-1", product.productId)
        assertEquals("12", customFields["Warranty (months)"])
    }

    @Test
    fun `a blank custom column on re-import clears a previously-set value`() = runTest {
        val repository = FakeCatalogueRepository()
        val ts = com.budcom.android.feature.catalogue.domain.model.CatalogueTimestamp(1L, com.budcom.android.feature.catalogue.domain.model.CatalogueTimestampSource.DeviceLocalProvisional)
        val existing = repository.createManualDraft("co-1", "Widget", ts)
        // A row with neither Stock Item Reference nor SKU can never resolve to an Update per the
        // locked contract (architecture §9: "Stable-identifier matching: Stock Item reference, or a
        // Catalogue-issued Product ID" -- Product Name alone is never a stable re-import identity),
        // so this test gives the product a real SKU first, exactly as an owner would before ever
        // relying on Excel re-import for a Manual product.
        repository.updateEnrichment("co-1", existing.productId, com.budcom.android.feature.catalogue.domain.repository.CatalogueEnrichmentUpdate(sku = "SKU-1"), ts)
        repository.upsertCustomFields("co-1", existing.productId, mapOf("Warranty (months)" to "12"), ts)
        val useCase = CatalogueExcelImportUseCase(repository, FakeCatalogueClock())
        val rows = listOf(
            row(
                1,
                CatalogueExcelColumns.PRODUCT_NAME to "Widget",
                CatalogueExcelColumns.UNIT to "Nos",
                CatalogueExcelColumns.SKU to "SKU-1",
                "Warranty (months)" to null,
            ),
        )
        val preview = CatalogueExcelValidator.preview(rows, resolveExistingProductId = { identifier -> if (identifier == "SKU-1") existing.productId else null })

        useCase.commit("co-1", rows, preview)

        assertEquals(null, repository.listCustomFields("co-1", existing.productId)["Warranty (months)"])
    }

    @Test
    fun `two companies importing the identically-named custom column never see each other's values`() = runTest {
        val repository = FakeCatalogueRepository()
        val useCase = CatalogueExcelImportUseCase(repository, FakeCatalogueClock())
        val rowsA = listOf(row(1, CatalogueExcelColumns.PRODUCT_NAME to "Widget A", CatalogueExcelColumns.UNIT to "Nos", "Warranty" to "12 months"))
        val rowsB = listOf(row(1, CatalogueExcelColumns.PRODUCT_NAME to "Widget B", CatalogueExcelColumns.UNIT to "Nos", "Warranty" to "24 months"))

        useCase.commit("co-A", rowsA, CatalogueExcelValidator.preview(rowsA, resolveExistingProductId = { null }))
        useCase.commit("co-B", rowsB, CatalogueExcelValidator.preview(rowsB, resolveExistingProductId = { null }))

        val productA = repository.products["co-A"]!!.single()
        val productB = repository.products["co-B"]!!.single()
        assertEquals("12 months", repository.listCustomFields("co-A", productA.productId)["Warranty"])
        assertEquals("24 months", repository.listCustomFields("co-B", productB.productId)["Warranty"])
        // co-A's export column-name listing must never include co-B's rows or vice versa.
        assertEquals(listOf("Warranty"), repository.listAllCustomFieldColumnNames("co-A"))
        assertEquals(listOf("Warranty"), repository.listAllCustomFieldColumnNames("co-B"))
    }
}
