package com.budcom.android.feature.catalogue.domain.excel

import com.budcom.android.feature.catalogue.domain.model.CatalogueTimestamp
import com.budcom.android.feature.catalogue.domain.model.CatalogueTimestampSource
import com.budcom.android.feature.catalogue.presentation.FakeCatalogueRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogueExcelExportUseCaseTest {

    private fun ts() = CatalogueTimestamp(1L, CatalogueTimestampSource.DeviceLocalProvisional)

    @Test
    fun `export includes every native column plus every distinct custom column used in the company`() = runTest {
        val repository = FakeCatalogueRepository()
        val product = repository.createManualDraft("co-1", "Widget", ts())
        repository.updateEnrichment(
            "co-1", product.productId,
            com.budcom.android.feature.catalogue.domain.repository.CatalogueEnrichmentUpdate(description = "A fine widget"),
            ts(),
        )
        repository.upsertCustomFields("co-1", product.productId, mapOf("Warranty (months)" to "12"), ts())
        val useCase = CatalogueExcelExportUseCase(repository)

        val result = useCase.export("co-1")

        assertTrue(result.headers.containsAll(CatalogueExcelColumns.NATIVE_EXPORT_ORDER))
        assertTrue(result.headers.contains("Warranty (months)"))
        val row = result.rows.single()
        assertEquals("Widget", row[CatalogueExcelColumns.PRODUCT_NAME])
        assertEquals("A fine widget", row[CatalogueExcelColumns.DESCRIPTION])
        assertEquals("12", row["Warranty (months)"])
    }

    @Test
    fun `Publication State is exported as read-only informational lifecycle state`() = runTest {
        val repository = FakeCatalogueRepository()
        repository.createManualDraft("co-1", "Widget", ts())
        val useCase = CatalogueExcelExportUseCase(repository)

        val row = useCase.export("co-1").rows.single()

        assertEquals("Draft", row[CatalogueExcelColumns.PUBLICATION_STATE])
    }

    @Test
    fun `a product missing a custom column that another product in the same company has exports a blank cell, not a dropped column`() = runTest {
        val repository = FakeCatalogueRepository()
        val withWarranty = repository.createManualDraft("co-1", "Widget", ts())
        repository.createManualDraft("co-1", "Gadget", ts())
        repository.upsertCustomFields("co-1", withWarranty.productId, mapOf("Warranty" to "12 months"), ts())
        val useCase = CatalogueExcelExportUseCase(repository)

        val result = useCase.export("co-1")

        assertTrue(result.headers.contains("Warranty"))
        val gadgetRow = result.rows.single { it[CatalogueExcelColumns.PRODUCT_NAME] == "Gadget" }
        assertEquals(null, gadgetRow["Warranty"])
    }

    @Test
    fun `export for one company never includes another company's products or custom columns`() = runTest {
        val repository = FakeCatalogueRepository()
        repository.createManualDraft("co-A", "Widget A", ts()).let { repository.upsertCustomFields("co-A", it.productId, mapOf("A-only" to "x"), ts()) }
        repository.createManualDraft("co-B", "Widget B", ts())
        val useCase = CatalogueExcelExportUseCase(repository)

        val resultB = useCase.export("co-B")

        assertTrue(resultB.rows.none { it[CatalogueExcelColumns.PRODUCT_NAME] == "Widget A" })
        assertFalse(resultB.headers.contains("A-only"))
    }

    @Test
    fun `export then parse then preview then re-commit is idempotent -- no new products, no changed values`() = runTest {
        // Uses a Tally-linked product, not a Manual one: a Manual product has no code path that
        // ever populates its required "Unit" column (architecture §6's field-ownership table
        // treats Unit as Tally-authoritative unconditionally) -- exporting one and re-importing it
        // unchanged would always skip on "Missing required column: Unit", a real gap this test
        // run discovered and reported rather than silently worked around (see the MVP-1.4
        // implementation report's defect list). The Tally-linked path is the one with genuinely
        // complete data today, so it is what this round-trip test exercises.
        val repository = FakeCatalogueRepository()
        repository.unlinkedStockItems.getOrPut("co-1") { mutableListOf() }.add(
            com.budcom.android.feature.masterdata.stockitem.domain.model.StockItem(
                id = "guid:widget", name = "Widget", alias = null, parentGroup = "Finished Goods",
                category = null, baseUnit = "Nos", partNumber = null, hsnCode = "8471", gstRate = "18",
                status = com.budcom.android.feature.masterdata.stockitem.domain.model.StockItemStatus.Active,
                closingBalance = null,
                dataQuality = com.budcom.android.feature.masterdata.stockitem.domain.model.StockItemDataQuality.Complete,
                syncedAt = "t",
            ),
        )
        val product = repository.createDraftFromStockItem("co-1", "guid:widget", ts())!!
        repository.updateEnrichment(
            "co-1", product.productId,
            com.budcom.android.feature.catalogue.domain.repository.CatalogueEnrichmentUpdate(
                description = "A, fine \"widget\"",
                customerFacingCategory = "Tools",
            ),
            ts(),
        )
        repository.upsertCustomFields("co-1", product.productId, mapOf("Warranty" to "12 months"), ts())
        val exportUseCase = CatalogueExcelExportUseCase(repository)
        val csv = exportUseCase.export("co-1").toCsv()

        val parsed = CatalogueCsvFormat.parse(csv)
        assertTrue("a round-tripped file must never report a duplicate header", parsed.duplicateHeaderWarnings.isEmpty())

        val preview = CatalogueExcelValidator.preview(parsed.rows) { identifier ->
            // Re-import identity resolution keys on Stock Item Reference (the linked Tally Stock
            // Item's own stable id) -- exactly what a real caller's lookup against
            // catalogue_product_source_link would do.
            if (identifier == "guid:widget") product.productId else null
        }
        assertEquals(1, preview.updateCount)
        assertEquals(0, preview.createCount)

        val importUseCase = CatalogueExcelImportUseCase(repository, com.budcom.android.feature.catalogue.presentation.FakeCatalogueClock())
        importUseCase.commit("co-1", parsed.rows, preview)

        assertEquals(1, repository.products["co-1"]!!.size)
        val reloaded = repository.products["co-1"]!!.single()
        assertEquals("A, fine \"widget\"", reloaded.description)
        assertEquals("Tools", reloaded.customerFacingCategory)
        assertEquals("12 months", repository.listCustomFields("co-1", product.productId)["Warranty"])
    }
}
