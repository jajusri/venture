package com.jajusri.venture.feature.catalogue.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogueOverrideResolverTest {

    private fun row(scope: CatalogueOverrideScope, value: String) = CatalogueOverrideRow(
        companyId = "co-1",
        scope = scope,
        attribute = CatalogueOverrideAttribute.PriceSyncMode,
        value = value,
        updatedAt = CatalogueTimestamp(1L, CatalogueTimestampSource.DeviceLocalProvisional),
    )

    @Test
    fun `no rows resolves to null`() {
        assertNull(CatalogueOverrideResolver.resolve(emptyList(), "p1", "b1", "sg1"))
    }

    @Test
    fun `catalogue-wide only resolves when nothing more specific exists`() {
        val rows = listOf(row(CatalogueOverrideScope.CatalogueWide, "AUTO"))
        assertEquals("AUTO", CatalogueOverrideResolver.resolve(rows, "p1", "b1", "sg1")?.value)
    }

    @Test
    fun `stock-group beats catalogue-wide`() {
        val rows = listOf(
            row(CatalogueOverrideScope.CatalogueWide, "AUTO"),
            row(CatalogueOverrideScope.StockGroup("sg1"), "MANUAL"),
        )
        assertEquals("MANUAL", CatalogueOverrideResolver.resolve(rows, "p1", "b1", "sg1")?.value)
    }

    @Test
    fun `branch beats stock-group and catalogue-wide`() {
        val rows = listOf(
            row(CatalogueOverrideScope.CatalogueWide, "AUTO"),
            row(CatalogueOverrideScope.StockGroup("sg1"), "MANUAL"),
            row(CatalogueOverrideScope.Branch("b1"), "AUTO"),
        )
        assertEquals("AUTO", CatalogueOverrideResolver.resolve(rows, "p1", "b1", "sg1")?.value)
    }

    @Test
    fun `item beats every other level`() {
        val rows = listOf(
            row(CatalogueOverrideScope.CatalogueWide, "AUTO"),
            row(CatalogueOverrideScope.StockGroup("sg1"), "AUTO"),
            row(CatalogueOverrideScope.Branch("b1"), "AUTO"),
            row(CatalogueOverrideScope.Item("p1"), "MANUAL"),
        )
        assertEquals("MANUAL", CatalogueOverrideResolver.resolve(rows, "p1", "b1", "sg1")?.value)
    }

    @Test
    fun `item override for a different product does not match`() {
        val rows = listOf(row(CatalogueOverrideScope.Item("other-product"), "MANUAL"))
        assertNull(CatalogueOverrideResolver.resolve(rows, "p1", "b1", "sg1"))
    }

    @Test
    fun `branch level is skipped entirely when no branch context is selected`() {
        val rows = listOf(
            row(CatalogueOverrideScope.Branch("b1"), "MANUAL"),
            row(CatalogueOverrideScope.CatalogueWide, "AUTO"),
        )
        assertEquals("AUTO", CatalogueOverrideResolver.resolve(rows, "p1", branchId = null, stockGroupKey = "sg1")?.value)
    }

    @Test
    fun `stock-group level is skipped entirely for a manual product with no stock group key`() {
        val rows = listOf(
            row(CatalogueOverrideScope.StockGroup("sg1"), "MANUAL"),
            row(CatalogueOverrideScope.CatalogueWide, "AUTO"),
        )
        assertEquals("AUTO", CatalogueOverrideResolver.resolve(rows, "p1", branchId = "b1", stockGroupKey = null)?.value)
    }

    @Test
    fun `a different branch's override never matches`() {
        val rows = listOf(row(CatalogueOverrideScope.Branch("other-branch"), "MANUAL"))
        assertNull(CatalogueOverrideResolver.resolve(rows, "p1", "b1", "sg1"))
    }

    @Test
    fun `a different stock group's override never matches`() {
        val rows = listOf(row(CatalogueOverrideScope.StockGroup("other-group"), "MANUAL"))
        assertNull(CatalogueOverrideResolver.resolve(rows, "p1", "b1", "sg1"))
    }
}
