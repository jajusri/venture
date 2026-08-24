package com.budcom.android.feature.catalogue.domain.excel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogueExcelValidatorTest {

    private fun row(rowNumber: Int, vararg cells: Pair<String, String?>) = CatalogueExcelRow(rowNumber, cells.toMap())

    @Test
    fun `a valid new row with no matching existing product becomes a Create`() {
        val preview = CatalogueExcelValidator.preview(
            listOf(row(1, CatalogueExcelColumns.PRODUCT_NAME to "Widget", CatalogueExcelColumns.UNIT to "Nos")),
            resolveExistingProductId = { null },
        )
        assertEquals(1, preview.createCount)
        assertEquals(0, preview.updateCount)
        assertEquals(0, preview.skipCount)
        assertEquals("Widget", (preview.outcomes.single() as CatalogueExcelRowOutcome.Create).productName)
    }

    @Test
    fun `a row whose Stock Item Reference already resolves to a product becomes an Update, never a duplicate Create`() {
        val preview = CatalogueExcelValidator.preview(
            listOf(
                row(
                    1,
                    CatalogueExcelColumns.PRODUCT_NAME to "Widget",
                    CatalogueExcelColumns.UNIT to "Nos",
                    CatalogueExcelColumns.STOCK_ITEM_REFERENCE to "guid:widget",
                ),
            ),
            resolveExistingProductId = { id -> if (id == "guid:widget") "prod-1" else null },
        )
        val outcome = preview.outcomes.single() as CatalogueExcelRowOutcome.Update
        assertEquals("prod-1", outcome.productId)
        assertEquals(1, preview.updateCount)
        assertEquals(0, preview.createCount)
    }

    @Test
    fun `a row missing Product Name is skipped with a specific reason, not silently dropped`() {
        val preview = CatalogueExcelValidator.preview(
            listOf(row(1, CatalogueExcelColumns.UNIT to "Nos")),
            resolveExistingProductId = { null },
        )
        val outcome = preview.outcomes.single() as CatalogueExcelRowOutcome.Skipped
        assertTrue(outcome.reason.contains(CatalogueExcelColumns.PRODUCT_NAME))
    }

    @Test
    fun `a row missing Unit is skipped with a specific reason`() {
        val preview = CatalogueExcelValidator.preview(
            listOf(row(1, CatalogueExcelColumns.PRODUCT_NAME to "Widget")),
            resolveExistingProductId = { null },
        )
        val outcome = preview.outcomes.single() as CatalogueExcelRowOutcome.Skipped
        assertTrue(outcome.reason.contains(CatalogueExcelColumns.UNIT))
    }

    @Test
    fun `an unparseable price under Open display mode is skipped, not silently coerced`() {
        val preview = CatalogueExcelValidator.preview(
            listOf(
                row(
                    1,
                    CatalogueExcelColumns.PRODUCT_NAME to "Widget",
                    CatalogueExcelColumns.UNIT to "Nos",
                    CatalogueExcelColumns.PRICE_DISPLAY_MODE to "Open",
                    CatalogueExcelColumns.PRICE to "not-a-number",
                ),
            ),
            resolveExistingProductId = { null },
        )
        val outcome = preview.outcomes.single() as CatalogueExcelRowOutcome.Skipped
        assertTrue(outcome.reason.contains("Price"))
    }

    @Test
    fun `a malformed price under Contact-for-price mode is not even checked, since it is never displayed`() {
        val preview = CatalogueExcelValidator.preview(
            listOf(
                row(
                    1,
                    CatalogueExcelColumns.PRODUCT_NAME to "Widget",
                    CatalogueExcelColumns.UNIT to "Nos",
                    CatalogueExcelColumns.PRICE_DISPLAY_MODE to "Contact for price",
                    CatalogueExcelColumns.PRICE to "garbage",
                ),
            ),
            resolveExistingProductId = { null },
        )
        assertEquals(1, preview.createCount)
    }

    @Test
    fun `duplicate rows for the same identity within one file resolve last-row-wins, both flagged`() {
        val preview = CatalogueExcelValidator.preview(
            listOf(
                row(1, CatalogueExcelColumns.PRODUCT_NAME to "Widget", CatalogueExcelColumns.UNIT to "Nos", CatalogueExcelColumns.SKU to "SKU-1"),
                row(2, CatalogueExcelColumns.PRODUCT_NAME to "Widget v2", CatalogueExcelColumns.UNIT to "Nos", CatalogueExcelColumns.SKU to "SKU-1"),
            ),
            resolveExistingProductId = { null },
        )
        val first = preview.outcomes[0] as CatalogueExcelRowOutcome.Skipped
        assertTrue(first.reason.contains("Duplicate"))
        assertTrue(preview.outcomes[1] is CatalogueExcelRowOutcome.Create)
    }

    @Test
    fun `rows with different identities never collide, even with identical product names`() {
        val preview = CatalogueExcelValidator.preview(
            listOf(
                row(1, CatalogueExcelColumns.PRODUCT_NAME to "Widget", CatalogueExcelColumns.UNIT to "Nos", CatalogueExcelColumns.SKU to "SKU-1"),
                row(2, CatalogueExcelColumns.PRODUCT_NAME to "Widget", CatalogueExcelColumns.UNIT to "Nos", CatalogueExcelColumns.SKU to "SKU-2"),
            ),
            resolveExistingProductId = { null },
        )
        assertEquals(2, preview.createCount)
    }

    @Test
    fun `a malformed row never rejects the whole file -- other valid rows still succeed`() {
        val preview = CatalogueExcelValidator.preview(
            listOf(
                row(1, CatalogueExcelColumns.PRODUCT_NAME to "Widget", CatalogueExcelColumns.UNIT to "Nos"),
                row(2, CatalogueExcelColumns.UNIT to "Nos"), // missing name
                row(3, CatalogueExcelColumns.PRODUCT_NAME to "Gadget", CatalogueExcelColumns.UNIT to "Pcs"),
            ),
            resolveExistingProductId = { null },
        )
        assertEquals(2, preview.createCount)
        assertEquals(1, preview.skipCount)
    }

    @Test
    fun `custom columns are collected opaquely and never treated as reserved`() {
        val preview = CatalogueExcelValidator.preview(
            listOf(
                row(
                    1,
                    CatalogueExcelColumns.PRODUCT_NAME to "Widget",
                    CatalogueExcelColumns.UNIT to "Nos",
                    "Warranty (months)" to "12",
                ),
            ),
            resolveExistingProductId = { null },
        )
        assertEquals(setOf("Warranty (months)"), preview.customColumnNames)
    }

    @Test
    fun `reserved column headers never appear in customColumnNames, case-insensitively`() {
        val preview = CatalogueExcelValidator.preview(
            listOf(row(1, "product name" to "Widget", "UNIT" to "Nos")),
            resolveExistingProductId = { null },
        )
        assertTrue(preview.customColumnNames.isEmpty())
    }

    @Test
    fun `an empty file (zero rows) previews to zero outcomes without crashing`() {
        val preview = CatalogueExcelValidator.preview(emptyList(), resolveExistingProductId = { null })
        assertEquals(0, preview.outcomes.size)
        assertEquals(0, preview.createCount)
        assertTrue(preview.customColumnNames.isEmpty())
    }

    @Test
    fun `Unicode product names and descriptions are preserved exactly, not mangled`() {
        val preview = CatalogueExcelValidator.preview(
            listOf(row(1, CatalogueExcelColumns.PRODUCT_NAME to "मसाला चाय", CatalogueExcelColumns.UNIT to "पैकेट")),
            resolveExistingProductId = { null },
        )
        val outcome = preview.outcomes.single() as CatalogueExcelRowOutcome.Create
        assertEquals("मसाला चाय", outcome.productName)
    }

    @Test
    fun `a very long product name is accepted, not truncated or rejected`() {
        val longName = "A".repeat(5_000)
        val preview = CatalogueExcelValidator.preview(
            listOf(row(1, CatalogueExcelColumns.PRODUCT_NAME to longName, CatalogueExcelColumns.UNIT to "Nos")),
            resolveExistingProductId = { null },
        )
        val outcome = preview.outcomes.single() as CatalogueExcelRowOutcome.Create
        assertEquals(5_000, outcome.productName.length)
    }

    @Test
    fun `a price containing currency symbols and thousands separators is rejected as unparseable, not silently coerced`() {
        val preview = CatalogueExcelValidator.preview(
            listOf(
                row(
                    1,
                    CatalogueExcelColumns.PRODUCT_NAME to "Widget",
                    CatalogueExcelColumns.UNIT to "Nos",
                    CatalogueExcelColumns.PRICE_DISPLAY_MODE to "Open",
                    CatalogueExcelColumns.PRICE to "₹1,299.00",
                ),
            ),
            resolveExistingProductId = { null },
        )
        val outcome = preview.outcomes.single() as CatalogueExcelRowOutcome.Skipped
        assertTrue(outcome.reason.contains("not a valid number"))
    }

    @Test
    fun `whitespace-only cell values are treated as absent, not as a present empty string`() {
        val preview = CatalogueExcelValidator.preview(
            listOf(row(1, CatalogueExcelColumns.PRODUCT_NAME to "Widget", CatalogueExcelColumns.UNIT to "   ")),
            resolveExistingProductId = { null },
        )
        val outcome = preview.outcomes.single() as CatalogueExcelRowOutcome.Skipped
        assertTrue(outcome.reason.contains("Unit"))
    }

    @Test
    fun `validateCustomColumnName rejects a reserved name case-insensitively and accepts a genuinely new one`() {
        assertEquals(CustomColumnNameValidation.Reserved, CatalogueExcelColumns.validateCustomColumnName("product name"))
        assertEquals(CustomColumnNameValidation.Reserved, CatalogueExcelColumns.validateCustomColumnName("PRICE"))
        assertEquals(CustomColumnNameValidation.Blank, CatalogueExcelColumns.validateCustomColumnName("   "))
        assertEquals(CustomColumnNameValidation.Valid, CatalogueExcelColumns.validateCustomColumnName("Warranty (months)"))
    }
}
