package com.budcom.android.feature.catalogue.domain.excel

import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogueExcelColumnsTest {

    @Test
    fun `a genuinely new custom column name is valid`() {
        assertEquals(CustomColumnNameValidation.Valid, CatalogueExcelColumns.validateCustomColumnName("Warranty (months)"))
    }

    @Test
    fun `a blank custom column name is rejected as blank, not silently accepted`() {
        assertEquals(CustomColumnNameValidation.Blank, CatalogueExcelColumns.validateCustomColumnName("   "))
    }

    @Test
    fun `an exact reserved-name match triggers the rename prompt`() {
        assertEquals(CustomColumnNameValidation.Reserved, CatalogueExcelColumns.validateCustomColumnName("Price"))
    }

    @Test
    fun `a reserved-name match is case-insensitive and trims whitespace`() {
        assertEquals(CustomColumnNameValidation.Reserved, CatalogueExcelColumns.validateCustomColumnName("  price  "))
        assertEquals(CustomColumnNameValidation.Reserved, CatalogueExcelColumns.validateCustomColumnName("STOCK ITEM REFERENCE"))
    }

    @Test
    fun `every documented reserved name is itself detected as reserved`() {
        CatalogueExcelColumns.RESERVED_NAMES.forEach { name ->
            assertEquals("$name must be detected as reserved", CustomColumnNameValidation.Reserved, CatalogueExcelColumns.validateCustomColumnName(name))
        }
    }
}
