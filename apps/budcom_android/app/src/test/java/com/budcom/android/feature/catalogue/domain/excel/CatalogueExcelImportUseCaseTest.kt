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
}
