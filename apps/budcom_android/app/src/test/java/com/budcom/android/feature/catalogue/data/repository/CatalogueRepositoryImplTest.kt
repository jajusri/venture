package com.budcom.android.feature.catalogue.data.repository

import com.budcom.android.core.util.DispatcherProvider
import com.budcom.android.feature.catalogue.data.local.BranchDao
import com.budcom.android.feature.catalogue.data.local.BranchEntity
import com.budcom.android.feature.catalogue.data.local.CATALOGUE_SOURCE_TYPE_TALLY_STOCK_ITEM
import com.budcom.android.feature.catalogue.data.local.CatalogueAssetDao
import com.budcom.android.feature.catalogue.data.local.CatalogueAssetEntity
import com.budcom.android.feature.catalogue.data.local.CatalogueCustomFieldDao
import com.budcom.android.feature.catalogue.data.local.CatalogueCustomFieldEntity
import com.budcom.android.feature.catalogue.data.local.CatalogueOverrideDao
import com.budcom.android.feature.catalogue.data.local.CatalogueOverrideEntity
import com.budcom.android.feature.catalogue.data.local.CatalogueProductDao
import com.budcom.android.feature.catalogue.data.local.CatalogueProductEntity
import com.budcom.android.feature.catalogue.data.local.CatalogueProductSourceLinkDao
import com.budcom.android.feature.catalogue.data.local.CatalogueProductSourceLinkEntity
import com.budcom.android.feature.catalogue.data.local.CataloguePublishedSnapshotDao
import com.budcom.android.feature.catalogue.data.local.CataloguePublishedSnapshotEntity
import com.budcom.android.feature.catalogue.data.local.CatalogueSettingsDao
import com.budcom.android.feature.catalogue.data.local.CatalogueSettingsEntity
import com.budcom.android.feature.catalogue.domain.model.CatalogueLifecycleAction
import com.budcom.android.feature.catalogue.domain.model.CatalogueLifecycleState
import com.budcom.android.feature.catalogue.domain.model.CatalogueOverrideAttribute
import com.budcom.android.feature.catalogue.domain.model.CatalogueOverrideScope
import com.budcom.android.feature.catalogue.domain.model.CatalogueProductSource
import com.budcom.android.feature.catalogue.domain.model.CatalogueTimestamp
import com.budcom.android.feature.catalogue.domain.model.CatalogueTimestampSource
import com.budcom.android.feature.catalogue.domain.repository.CatalogueEnrichmentUpdate
import com.budcom.android.feature.catalogue.domain.repository.CatalogueLifecycleResult
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItem
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItemDataQuality
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItemStatus
import com.budcom.android.feature.masterdata.stockitem.domain.port.StockItemLookupPort
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogueRepositoryImplTest {
    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = dispatcher
        override val io: CoroutineDispatcher = dispatcher
        override val default: CoroutineDispatcher = dispatcher
    }
    private val productDao = FakeCatalogueProductDao()
    private val sourceLinkDao = FakeCatalogueProductSourceLinkDao()
    private val branchDao = FakeBranchDao()
    private val overrideDao = FakeCatalogueOverrideDao()
    private val snapshotDao = FakeCataloguePublishedSnapshotDao()
    private val settingsDao = FakeCatalogueSettingsDao()
    private val assetDao = FakeCatalogueAssetDao()
    private val assetStore = FakeCatalogueAssetStore()
    private val customFieldDao = FakeCatalogueCustomFieldDao()
    private val stockItemLookup = FakeStockItemLookupPort()

    private fun repository() = CatalogueRepositoryImpl(
        productDao, sourceLinkDao, branchDao, overrideDao, snapshotDao, settingsDao, assetDao, assetStore, customFieldDao,
        stockItemLookup, dispatchers,
    )

    private fun ts(millis: Long = 1_000L) = CatalogueTimestamp(millis, CatalogueTimestampSource.DeviceLocalProvisional)

    private fun stockItem(id: String = "guid:widget", name: String = "Widget", parentGroup: String? = "Finished Goods") = StockItem(
        id = id, name = name, alias = null, parentGroup = parentGroup, category = null, baseUnit = "Nos",
        partNumber = "PN-1", hsnCode = "8471", gstRate = "18", status = StockItemStatus.Active, closingBalance = null,
        dataQuality = StockItemDataQuality.Complete, syncedAt = "t",
    )

    // ============================== Identity / creation ==============================

    @Test
    fun `creates a Draft product linked to a Stock Item with Tally fields resolved live`() = runTest(dispatcher) {
        stockItemLookup.stored.getOrPut("co-1") { mutableMapOf() }["guid:widget"] = stockItem()
        val product = repository().createDraftFromStockItem("co-1", "guid:widget", ts())!!

        assertEquals(CatalogueProductSource.Tally, product.source)
        assertEquals("Widget", product.tallyName)
        assertEquals("Finished Goods", product.stockGroupKey)
        assertEquals("Nos", product.unit)
        assertEquals(CatalogueLifecycleState.Draft, product.lifecycleState)
        assertEquals("Widget", product.displayName)
    }

    @Test
    fun `returns null when the target Stock Item does not exist locally`() = runTest(dispatcher) {
        val product = repository().createDraftFromStockItem("co-1", "guid:missing", ts())
        assertNull(product)
    }

    @Test
    fun `repeat draft creation for the same Stock Item is idempotent, no duplicate product`() = runTest(dispatcher) {
        stockItemLookup.stored.getOrPut("co-1") { mutableMapOf() }["guid:widget"] = stockItem()
        val repo = repository()
        val first = repo.createDraftFromStockItem("co-1", "guid:widget", ts())!!
        val second = repo.createDraftFromStockItem("co-1", "guid:widget", ts(2_000L))!!

        assertEquals(first.productId, second.productId)
        assertEquals(1, productDao.store.size)
        assertEquals(1, sourceLinkDao.store.size)
    }

    @Test
    fun `a Tally rename is reflected immediately without any Catalogue-side update`() = runTest(dispatcher) {
        stockItemLookup.stored.getOrPut("co-1") { mutableMapOf() }["guid:widget"] = stockItem(name = "Old Name")
        val repo = repository()
        val product = repo.createDraftFromStockItem("co-1", "guid:widget", ts())!!
        assertEquals("Old Name", product.displayName)

        // Simulate a routine Stock Item sync renaming the linked item -- Catalogue never touches
        // its own row for this.
        stockItemLookup.stored["co-1"]!!["guid:widget"] = stockItem(name = "New Name")

        val reread = repo.findProduct("co-1", product.productId)!!
        assertEquals(product.productId, reread.productId)
        assertEquals("New Name", reread.displayName)
    }

    @Test
    fun `Catalogue-owned enrichment survives a simulated Tally re-sync of the same Stock Item`() = runTest(dispatcher) {
        stockItemLookup.stored.getOrPut("co-1") { mutableMapOf() }["guid:widget"] = stockItem()
        val repo = repository()
        val product = repo.createDraftFromStockItem("co-1", "guid:widget", ts())!!
        repo.updateEnrichment("co-1", product.productId, CatalogueEnrichmentUpdate(description = "Hand-crafted"), ts())

        // Simulate a routine re-sync that re-writes the Stock Item row (e.g. refreshed HSN).
        stockItemLookup.stored["co-1"]!!["guid:widget"] = stockItem().copy(hsnCode = "9999")

        val reread = repo.findProduct("co-1", product.productId)!!
        assertEquals("Hand-crafted", reread.description)
        assertEquals("9999", reread.hsnCode)
    }

    @Test
    fun `a Manual product has no linked Stock Item and no Tally-owned fields`() = runTest(dispatcher) {
        val product = repository().createManualDraft("co-1", "Hand-made Basket", ts())
        assertEquals(CatalogueProductSource.Manual, product.source)
        assertNull(product.linkedStockItemId)
        assertNull(product.tallyName)
        assertEquals("Hand-made Basket", product.displayName)
    }

    @Test
    fun `updateEnrichment on a non-existent product returns null`() = runTest(dispatcher) {
        assertNull(repository().updateEnrichment("co-1", "missing", CatalogueEnrichmentUpdate(description = "x"), ts()))
    }

    // ============================== Lifecycle / publish ==============================

    @Test
    fun `Publish creates an atomic published snapshot and Draft submit does not`() = runTest(dispatcher) {
        val repo = repository()
        val product = repo.createManualDraft("co-1", "Basket", ts())
        repo.updateEnrichment("co-1", product.productId, CatalogueEnrichmentUpdate(description = "Woven basket"), ts())

        val reviewResult = repo.transitionLifecycle("co-1", product.productId, CatalogueLifecycleAction.SubmitForReview, isOwner = false, ts())
        assertTrue(reviewResult is CatalogueLifecycleResult.Success)
        assertNull("no snapshot before Publish", snapshotDao.findByProductId("co-1", product.productId))

        val publishResult = repo.transitionLifecycle("co-1", product.productId, CatalogueLifecycleAction.Publish, isOwner = true, ts())
        val published = (publishResult as CatalogueLifecycleResult.Success).product
        assertEquals(CatalogueLifecycleState.Published, published.lifecycleState)
        val snapshot = snapshotDao.findByProductId("co-1", product.productId)
        assertNotNull("Publish must create the snapshot", snapshot)
        assertEquals("Woven basket", snapshot!!.description)
    }

    @Test
    fun `Publish without owner access is rejected and leaves state unchanged`() = runTest(dispatcher) {
        val repo = repository()
        val product = repo.createManualDraft("co-1", "Basket", ts())
        val result = repo.transitionLifecycle("co-1", product.productId, CatalogueLifecycleAction.Publish, isOwner = false, ts())
        assertEquals(CatalogueLifecycleResult.Rejected, result)
        assertEquals(CatalogueLifecycleState.Draft, repo.findProduct("co-1", product.productId)!!.lifecycleState)
    }

    @Test
    fun `an invalid transition from Published is rejected and the Published record is untouched`() = runTest(dispatcher) {
        val repo = repository()
        val product = repo.createManualDraft("co-1", "Basket", ts())
        repo.transitionLifecycle("co-1", product.productId, CatalogueLifecycleAction.Publish, isOwner = true, ts())

        val result = repo.transitionLifecycle("co-1", product.productId, CatalogueLifecycleAction.SubmitForReview, isOwner = true, ts())
        assertEquals(CatalogueLifecycleResult.Rejected, result)
        assertEquals(CatalogueLifecycleState.Published, repo.findProduct("co-1", product.productId)!!.lifecycleState)
    }

    @Test
    fun `transitioning a non-existent product returns ProductNotFound`() = runTest(dispatcher) {
        val result = repository().transitionLifecycle("co-1", "missing", CatalogueLifecycleAction.Publish, isOwner = true, ts())
        assertEquals(CatalogueLifecycleResult.ProductNotFound, result)
    }

    @Test
    fun `Archive removes the product from the published snapshot pool`() = runTest(dispatcher) {
        val repo = repository()
        val product = repo.createManualDraft("co-1", "Basket", ts())
        repo.transitionLifecycle("co-1", product.productId, CatalogueLifecycleAction.Publish, isOwner = true, ts())
        assertNotNull(snapshotDao.findByProductId("co-1", product.productId))

        repo.transitionLifecycle("co-1", product.productId, CatalogueLifecycleAction.Archive, isOwner = true, ts())

        assertNull("Archive must remove the snapshot", snapshotDao.findByProductId("co-1", product.productId))
        assertEquals(CatalogueLifecycleState.Archived, repo.findProduct("co-1", product.productId)!!.lifecycleState)
    }

    @Test
    fun `only Published products appear in listAllPublished, never Draft or Review`() = runTest(dispatcher) {
        val repo = repository()
        val draft = repo.createManualDraft("co-1", "Draft Item", ts())
        val published = repo.createManualDraft("co-1", "Published Item", ts())
        repo.transitionLifecycle("co-1", published.productId, CatalogueLifecycleAction.Publish, isOwner = true, ts())

        val all = repo.listAllPublished("co-1")
        assertEquals(listOf("Published Item"), all.map { it.displayName })
        assertTrue(all.none { it.productId == draft.productId })
    }

    // ============================== Reconciliation ==============================

    @Test
    fun `reconciliation flags source unavailable when the linked Stock Item disappears, never deletes`() = runTest(dispatcher) {
        stockItemLookup.stored.getOrPut("co-1") { mutableMapOf() }["guid:widget"] = stockItem()
        val repo = repository()
        val product = repo.createDraftFromStockItem("co-1", "guid:widget", ts())!!
        assertTrue(product.sourceAvailable)

        stockItemLookup.stored["co-1"]!!.remove("guid:widget")
        val result = repo.reconcileStockItemLinks("co-1", ts())

        assertEquals(listOf(product.productId), result.nowUnavailable)
        assertTrue(result.reappeared.isEmpty())
        val reread = repo.findProduct("co-1", product.productId)!!
        assertFalse(reread.sourceAvailable)
        assertEquals(CatalogueLifecycleState.Draft, reread.lifecycleState) // never auto-archived/deleted
    }

    @Test
    fun `reconciliation auto-clears source unavailable on reappearance, no owner action needed`() = runTest(dispatcher) {
        stockItemLookup.stored.getOrPut("co-1") { mutableMapOf() }["guid:widget"] = stockItem()
        val repo = repository()
        val product = repo.createDraftFromStockItem("co-1", "guid:widget", ts())!!
        stockItemLookup.stored["co-1"]!!.remove("guid:widget")
        repo.reconcileStockItemLinks("co-1", ts())

        stockItemLookup.stored["co-1"]!!["guid:widget"] = stockItem()
        val result = repo.reconcileStockItemLinks("co-1", ts(2_000L))

        assertEquals(listOf(product.productId), result.reappeared)
        assertTrue(repo.findProduct("co-1", product.productId)!!.sourceAvailable)
    }

    @Test
    fun `an inactive Stock Item is treated as unavailable, not just a missing row`() = runTest(dispatcher) {
        stockItemLookup.stored.getOrPut("co-1") { mutableMapOf() }["guid:widget"] = stockItem()
        val repo = repository()
        val product = repo.createDraftFromStockItem("co-1", "guid:widget", ts())!!

        stockItemLookup.stored["co-1"]!!["guid:widget"] = stockItem().copy(status = StockItemStatus.Inactive)
        repo.reconcileStockItemLinks("co-1", ts())

        assertFalse(repo.findProduct("co-1", product.productId)!!.sourceAvailable)
    }

    // ============================== Overrides (wiring, not the pure algorithm) ==============================

    @Test
    fun `resolveOverride uses the linked Stock Item's parent group as the stock-group key`() = runTest(dispatcher) {
        stockItemLookup.stored.getOrPut("co-1") { mutableMapOf() }["guid:widget"] = stockItem(parentGroup = "Electronics")
        val repo = repository()
        val product = repo.createDraftFromStockItem("co-1", "guid:widget", ts())!!
        repo.setOverride("co-1", CatalogueOverrideScope.StockGroup("Electronics"), CatalogueOverrideAttribute.PriceSyncMode, "MANUAL", ts())
        repo.setOverride("co-1", CatalogueOverrideScope.CatalogueWide, CatalogueOverrideAttribute.PriceSyncMode, "AUTO", ts())

        val resolved = repo.resolveOverride("co-1", product.productId, branchId = null, CatalogueOverrideAttribute.PriceSyncMode)
        assertEquals("MANUAL", resolved?.value)
    }

    @Test
    fun `item-level override wins over stock-group and catalogue-wide through the repository`() = runTest(dispatcher) {
        stockItemLookup.stored.getOrPut("co-1") { mutableMapOf() }["guid:widget"] = stockItem(parentGroup = "Electronics")
        val repo = repository()
        val product = repo.createDraftFromStockItem("co-1", "guid:widget", ts())!!
        repo.setOverride("co-1", CatalogueOverrideScope.CatalogueWide, CatalogueOverrideAttribute.PriceSyncMode, "AUTO", ts())
        repo.setOverride("co-1", CatalogueOverrideScope.StockGroup("Electronics"), CatalogueOverrideAttribute.PriceSyncMode, "AUTO", ts())
        repo.setOverride("co-1", CatalogueOverrideScope.Item(product.productId), CatalogueOverrideAttribute.PriceSyncMode, "MANUAL", ts())

        val resolved = repo.resolveOverride("co-1", product.productId, branchId = "b1", CatalogueOverrideAttribute.PriceSyncMode)
        assertEquals("MANUAL", resolved?.value)
    }

    // ============================== Company isolation ==============================

    @Test
    fun `products, branches, overrides and published snapshots never cross company boundaries`() = runTest(dispatcher) {
        val repo = repository()
        val productA = repo.createManualDraft("co-A", "A Item", ts())
        val productB = repo.createManualDraft("co-B", "B Item", ts())
        repo.transitionLifecycle("co-A", productA.productId, CatalogueLifecycleAction.Publish, isOwner = true, ts())
        repo.transitionLifecycle("co-B", productB.productId, CatalogueLifecycleAction.Publish, isOwner = true, ts())
        repo.upsertBranch("co-A", "br-1", "Main Branch", isActive = true, ts())
        repo.setOverride("co-A", CatalogueOverrideScope.CatalogueWide, CatalogueOverrideAttribute.PriceSyncMode, "MANUAL", ts())
        repo.setPublic("co-A", true, ts())

        assertEquals(listOf("A Item"), repo.listProducts("co-A").map { it.displayName })
        assertEquals(listOf("B Item"), repo.listProducts("co-B").map { it.displayName })
        assertEquals(listOf("A Item"), repo.listAllPublished("co-A").map { it.displayName })
        assertEquals(listOf("B Item"), repo.listAllPublished("co-B").map { it.displayName })
        assertTrue("branch must not leak to co-B", repo.listBranches("co-B").isEmpty())
        assertNull(
            "override must not leak to co-B",
            repo.resolveOverride("co-B", productB.productId, null, CatalogueOverrideAttribute.PriceSyncMode),
        )
        assertTrue("public setting must not leak to co-B", !repo.isPublic("co-B"))
        assertTrue(repo.isPublic("co-A"))
    }

    @Test
    fun `reconciliation for one company never touches another company's products`() = runTest(dispatcher) {
        stockItemLookup.stored.getOrPut("co-A") { mutableMapOf() }["guid:a"] = stockItem(id = "guid:a")
        stockItemLookup.stored.getOrPut("co-B") { mutableMapOf() }["guid:b"] = stockItem(id = "guid:b")
        val repo = repository()
        val productA = repo.createDraftFromStockItem("co-A", "guid:a", ts())!!
        val productB = repo.createDraftFromStockItem("co-B", "guid:b", ts())!!
        stockItemLookup.stored["co-A"]!!.remove("guid:a")
        stockItemLookup.stored["co-B"]!!.remove("guid:b")

        repo.reconcileStockItemLinks("co-A", ts())

        assertFalse(repo.findProduct("co-A", productA.productId)!!.sourceAvailable)
        assertTrue("co-B must be untouched by a co-A reconciliation sweep", repo.findProduct("co-B", productB.productId)!!.sourceAvailable)
    }

    // ============================== Unlinked Stock Item listing ==============================

    @Test
    fun `listUnlinkedStockItems excludes Stock Items already linked to a product`() = runTest(dispatcher) {
        stockItemLookup.stored.getOrPut("co-1") { mutableMapOf() }.apply {
            put("guid:linked", stockItem(id = "guid:linked", name = "Linked"))
            put("guid:free", stockItem(id = "guid:free", name = "Free"))
        }
        val repo = repository()
        repo.createDraftFromStockItem("co-1", "guid:linked", ts())

        val unlinked = repo.listUnlinkedStockItems("co-1")

        assertEquals(listOf("guid:free"), unlinked.map { it.id })
    }

    @Test
    fun `listUnlinkedStockItems never leaks across companies`() = runTest(dispatcher) {
        stockItemLookup.stored.getOrPut("co-A") { mutableMapOf() }["guid:a"] = stockItem(id = "guid:a")
        stockItemLookup.stored.getOrPut("co-B") { mutableMapOf() }["guid:b"] = stockItem(id = "guid:b")
        val repo = repository()

        assertEquals(listOf("guid:a"), repo.listUnlinkedStockItems("co-A").map { it.id })
        assertEquals(listOf("guid:b"), repo.listUnlinkedStockItems("co-B").map { it.id })
    }

    // ============================== Assets ==============================

    @Test
    fun `the first asset added to a product becomes primary automatically`() = runTest(dispatcher) {
        val repo = repository()
        val product = repo.createManualDraft("co-1", "Basket", ts())

        val result = repo.addAsset("co-1", product.productId, android.net.TestUri.create(), ts())

        val assetId = (result as com.budcom.android.feature.catalogue.storage.CatalogueAssetResult.Success).assetId
        val assets = repo.listAssets("co-1", product.productId)
        assertEquals(1, assets.size)
        assertTrue(assets.single().isPrimary)
        assertEquals(assetId, assets.single().assetId)
    }

    @Test
    fun `a second asset does not become primary until explicitly set`() = runTest(dispatcher) {
        val repo = repository()
        val product = repo.createManualDraft("co-1", "Basket", ts())
        repo.addAsset("co-1", product.productId, android.net.TestUri.create(), ts())
        repo.addAsset("co-1", product.productId, android.net.TestUri.create(), ts())

        val assets = repo.listAssets("co-1", product.productId)
        assertEquals(1, assets.count { it.isPrimary })
    }

    @Test
    fun `setPrimaryAsset clears every other asset's primary flag first`() = runTest(dispatcher) {
        val repo = repository()
        val product = repo.createManualDraft("co-1", "Basket", ts())
        val first = (repo.addAsset("co-1", product.productId, android.net.TestUri.create(), ts())
            as com.budcom.android.feature.catalogue.storage.CatalogueAssetResult.Success)
        val second = (repo.addAsset("co-1", product.productId, android.net.TestUri.create(), ts())
            as com.budcom.android.feature.catalogue.storage.CatalogueAssetResult.Success)

        repo.setPrimaryAsset("co-1", product.productId, second.assetId, ts())

        val assets = repo.listAssets("co-1", product.productId).associateBy { it.assetId }
        assertFalse(assets.getValue(first.assetId).isPrimary)
        assertTrue(assets.getValue(second.assetId).isPrimary)
    }

    @Test
    fun `deleting the primary asset promotes the next remaining one`() = runTest(dispatcher) {
        val repo = repository()
        val product = repo.createManualDraft("co-1", "Basket", ts())
        val first = (repo.addAsset("co-1", product.productId, android.net.TestUri.create(), ts())
            as com.budcom.android.feature.catalogue.storage.CatalogueAssetResult.Success)
        repo.addAsset("co-1", product.productId, android.net.TestUri.create(), ts())

        repo.deleteAsset("co-1", product.productId, first.assetId)

        val remaining = repo.listAssets("co-1", product.productId)
        assertEquals(1, remaining.size)
        assertTrue("the only remaining asset must become primary", remaining.single().isPrimary)
    }

    @Test
    fun `deleting the only asset leaves the product with none, never an orphaned primary flag error`() = runTest(dispatcher) {
        val repo = repository()
        val product = repo.createManualDraft("co-1", "Basket", ts())
        val only = (repo.addAsset("co-1", product.productId, android.net.TestUri.create(), ts())
            as com.budcom.android.feature.catalogue.storage.CatalogueAssetResult.Success)

        repo.deleteAsset("co-1", product.productId, only.assetId)

        assertTrue(repo.listAssets("co-1", product.productId).isEmpty())
    }

    @Test
    fun `a rejected asset, such as an unsupported file type, is never recorded in Room`() = runTest(dispatcher) {
        assetStore.failureReason = com.budcom.android.feature.catalogue.storage.CatalogueAssetFailureReason.UnsupportedFileType
        val repo = repository()
        val product = repo.createManualDraft("co-1", "Basket", ts())

        val result = repo.addAsset("co-1", product.productId, android.net.TestUri.create(), ts())

        assertTrue(result is com.budcom.android.feature.catalogue.storage.CatalogueAssetResult.Failure)
        assertTrue(repo.listAssets("co-1", product.productId).isEmpty())
    }

    @Test
    fun `assets never leak across companies`() = runTest(dispatcher) {
        val repo = repository()
        val productA = repo.createManualDraft("co-A", "A Item", ts())
        val productB = repo.createManualDraft("co-B", "B Item", ts())
        repo.addAsset("co-A", productA.productId, android.net.TestUri.create(), ts())

        assertEquals(1, repo.listAssets("co-A", productA.productId).size)
        assertTrue("co-B's product must see no assets from co-A", repo.listAssets("co-B", productB.productId).isEmpty())
    }

    // ===== Adversarial: the exact same identifier/name reused across two companies (not merely
    // different data that happens not to leak -- these prove no accidental collision when the
    // companyId prefix is the *only* thing distinguishing two otherwise-identical keys). =====

    @Test
    fun `the identical Tally Stock Item GUID in two different companies resolves to two independent products`() = runTest(dispatcher) {
        stockItemLookup.stored.getOrPut("co-A") { mutableMapOf() }["guid:same"] = stockItem(id = "guid:same", name = "Sugar 1kg")
        stockItemLookup.stored.getOrPut("co-B") { mutableMapOf() }["guid:same"] = stockItem(id = "guid:same", name = "Sugar 1kg")
        val repo = repository()

        val productA = repo.createDraftFromStockItem("co-A", "guid:same", ts())!!
        val productB = repo.createDraftFromStockItem("co-B", "guid:same", ts())!!

        assertTrue("co-A and co-B must never resolve to the same Catalogue Product row", productA.productId != productB.productId)
        assertEquals(listOf("Sugar 1kg"), repo.listProducts("co-A").map { it.displayName })
        assertEquals(listOf("Sugar 1kg"), repo.listProducts("co-B").map { it.displayName })
        // Removing co-A's link must never affect co-B's independently-created link to the "same" guid.
        repo.reconcileStockItemLinks("co-A", ts())
        assertTrue(repo.findProduct("co-B", productB.productId)!!.sourceAvailable)
    }

    @Test
    fun `the identical SKU entered in two different companies never cross-resolves on Excel-style lookup`() = runTest(dispatcher) {
        val repo = repository()
        val productA = repo.createManualDraft("co-A", "Widget A", ts())
        val productB = repo.createManualDraft("co-B", "Widget B", ts())
        repo.updateEnrichment("co-A", productA.productId, CatalogueEnrichmentUpdate(sku = "SKU-100"), ts())
        repo.updateEnrichment("co-B", productB.productId, CatalogueEnrichmentUpdate(sku = "SKU-100"), ts())

        assertEquals("SKU-100", repo.findProduct("co-A", productA.productId)!!.sku)
        assertEquals("SKU-100", repo.findProduct("co-B", productB.productId)!!.sku)
        // A lookup scoped to co-A must never surface co-B's product, even though the SKU text matches.
        assertTrue(repo.listProducts("co-A").none { it.productId == productB.productId })
        assertTrue(repo.listProducts("co-B").none { it.productId == productA.productId })
    }

    @Test
    fun `the identical customer-facing category name in two companies never mixes published items on a category share`() = runTest(dispatcher) {
        val repo = repository()
        val productA = repo.createManualDraft("co-A", "Widget A", ts())
        val productB = repo.createManualDraft("co-B", "Widget B", ts())
        repo.updateEnrichment("co-A", productA.productId, CatalogueEnrichmentUpdate(customerFacingCategory = "Snacks"), ts())
        repo.updateEnrichment("co-B", productB.productId, CatalogueEnrichmentUpdate(customerFacingCategory = "Snacks"), ts())
        repo.transitionLifecycle("co-A", productA.productId, CatalogueLifecycleAction.Publish, isOwner = true, ts())
        repo.transitionLifecycle("co-B", productB.productId, CatalogueLifecycleAction.Publish, isOwner = true, ts())

        val categoryA = repo.listPublishedForCategory("co-A", "Snacks")
        val categoryB = repo.listPublishedForCategory("co-B", "Snacks")

        assertEquals(listOf("Widget A"), categoryA.map { it.displayName })
        assertEquals(listOf("Widget B"), categoryB.map { it.displayName })
    }

    @Test
    fun `the identical custom Excel column name and value in two companies are stored and read back independently`() = runTest(dispatcher) {
        val repo = repository()
        val productA = repo.createManualDraft("co-A", "Widget A", ts())
        val productB = repo.createManualDraft("co-B", "Widget B", ts())
        repo.upsertCustomFields("co-A", productA.productId, mapOf("Warranty" to "12 months"), ts())
        repo.upsertCustomFields("co-B", productB.productId, mapOf("Warranty" to "24 months"), ts())

        assertEquals("12 months", repo.listCustomFields("co-A", productA.productId)["Warranty"])
        assertEquals("24 months", repo.listCustomFields("co-B", productB.productId)["Warranty"])
        assertEquals(listOf("Warranty"), repo.listAllCustomFieldColumnNames("co-A"))
        assertEquals(listOf("Warranty"), repo.listAllCustomFieldColumnNames("co-B"))
    }
}

// ============================== Fakes ==============================

private class FakeCatalogueProductDao : CatalogueProductDao {
    val store = mutableMapOf<Pair<String, String>, CatalogueProductEntity>()
    override suspend fun upsert(entity: CatalogueProductEntity) {
        store[entity.companyId to entity.productId] = entity
    }
    override suspend fun findById(companyId: String, productId: String): CatalogueProductEntity? = store[companyId to productId]
    override suspend fun findAllForCompany(companyId: String): List<CatalogueProductEntity> =
        store.values.filter { it.companyId == companyId }
    override suspend fun findAllByLifecycleState(companyId: String, lifecycleState: String): List<CatalogueProductEntity> =
        store.values.filter { it.companyId == companyId && it.lifecycleState == lifecycleState }
    override suspend fun findAllLinkedToStockItems(companyId: String): List<CatalogueProductEntity> =
        store.values.filter { it.companyId == companyId && it.linkedStockItemId != null }
}

private class FakeCatalogueProductSourceLinkDao : CatalogueProductSourceLinkDao {
    val store = mutableMapOf<Triple<String, String, String>, CatalogueProductSourceLinkEntity>()
    override suspend fun upsert(entity: CatalogueProductSourceLinkEntity) {
        store[Triple(entity.companyId, entity.sourceType, entity.externalStockItemId)] = entity
    }
    override suspend fun findByExternalKey(companyId: String, sourceType: String, externalStockItemId: String) =
        store[Triple(companyId, sourceType, externalStockItemId)]
    override suspend fun findByProductId(companyId: String, productId: String) =
        store.values.firstOrNull { it.companyId == companyId && it.productId == productId }
    override suspend fun findAllForCompany(companyId: String) = store.values.filter { it.companyId == companyId }
}

private class FakeBranchDao : BranchDao {
    val store = mutableMapOf<Pair<String, String>, BranchEntity>()
    override suspend fun upsert(entity: BranchEntity) {
        store[entity.companyId to entity.branchId] = entity
    }
    override suspend fun findAllForCompany(companyId: String): List<BranchEntity> = store.values.filter { it.companyId == companyId }
    override suspend fun findById(companyId: String, branchId: String): BranchEntity? = store[companyId to branchId]
}

private class FakeCatalogueOverrideDao : CatalogueOverrideDao {
    val store = mutableMapOf<List<String>, CatalogueOverrideEntity>()
    override suspend fun upsert(entity: CatalogueOverrideEntity) {
        store[listOf(entity.companyId, entity.scopeType, entity.scopeKey, entity.attributeName)] = entity
    }
    override suspend fun findAllForCompanyAndAttribute(companyId: String, attributeName: String): List<CatalogueOverrideEntity> =
        store.values.filter { it.companyId == companyId && it.attributeName == attributeName }
    override suspend fun delete(companyId: String, scopeType: String, scopeKey: String, attributeName: String) {
        store.remove(listOf(companyId, scopeType, scopeKey, attributeName))
    }
}

private class FakeCataloguePublishedSnapshotDao : CataloguePublishedSnapshotDao {
    val store = mutableMapOf<Pair<String, String>, CataloguePublishedSnapshotEntity>()
    override suspend fun publish(entity: CataloguePublishedSnapshotEntity) {
        store[entity.companyId to entity.productId] = entity
    }
    override suspend fun findByProductId(companyId: String, productId: String) = store[companyId to productId]
    override suspend fun findAllPublishedForCompany(companyId: String) = store.values.filter { it.companyId == companyId }
    override suspend fun findAllPublishedForCategory(companyId: String, category: String) =
        store.values.filter { it.companyId == companyId && it.customerFacingCategory == category }
    override suspend fun deleteByProductId(companyId: String, productId: String) {
        store.remove(companyId to productId)
    }
}

private class FakeCatalogueSettingsDao : CatalogueSettingsDao {
    val store = mutableMapOf<String, CatalogueSettingsEntity>()
    override suspend fun upsert(entity: CatalogueSettingsEntity) {
        store[entity.companyId] = entity
    }
    override suspend fun findByCompany(companyId: String) = store[companyId]
}

private class FakeCatalogueAssetDao : CatalogueAssetDao {
    val store = mutableMapOf<Triple<String, String, String>, CatalogueAssetEntity>()
    override suspend fun upsert(entity: CatalogueAssetEntity) {
        store[Triple(entity.companyId, entity.productId, entity.assetId)] = entity
    }
    override suspend fun findAllForProduct(companyId: String, productId: String) =
        store.values.filter { it.companyId == companyId && it.productId == productId }.sortedBy { it.sortOrder }
    override suspend fun findById(companyId: String, productId: String, assetId: String) = store[Triple(companyId, productId, assetId)]
    override suspend fun clearPrimaryExcept(companyId: String, productId: String, keepAssetId: String) {
        store.values.filter { it.companyId == companyId && it.productId == productId && it.assetId != keepAssetId }
            .forEach { store[Triple(it.companyId, it.productId, it.assetId)] = it.copy(isPrimary = false) }
    }
    override suspend fun delete(companyId: String, productId: String, assetId: String) {
        store.remove(Triple(companyId, productId, assetId))
    }
}

private class FakeCatalogueCustomFieldDao : CatalogueCustomFieldDao {
    val store = mutableMapOf<Triple<String, String, String>, CatalogueCustomFieldEntity>()
    override suspend fun upsert(entity: CatalogueCustomFieldEntity) {
        store[Triple(entity.companyId, entity.productId, entity.columnName)] = entity
    }
    override suspend fun findAllForProduct(companyId: String, productId: String) =
        store.values.filter { it.companyId == companyId && it.productId == productId }
    override suspend fun findAllColumnNamesForCompany(companyId: String) =
        store.values.filter { it.companyId == companyId }.map { it.columnName }.distinct()
    override suspend fun findAllForCompany(companyId: String) =
        store.values.filter { it.companyId == companyId }
}

private class FakeStockItemLookupPort : StockItemLookupPort {
    val stored = mutableMapOf<String, MutableMap<String, StockItem>>()
    override suspend fun findById(companyId: String, stockItemId: String): StockItem? = stored[companyId]?.get(stockItemId)
    override suspend fun listAllForCompany(companyId: String): List<StockItem> = stored[companyId]?.values?.toList().orEmpty()
}

private class FakeCatalogueAssetStore : com.budcom.android.feature.catalogue.storage.CatalogueAssetStore {
    var nextAssetId = 0
    val saved = mutableListOf<Triple<String, String, android.net.Uri>>()
    val deleted = mutableListOf<Triple<String, String, String>>()
    var failureReason: com.budcom.android.feature.catalogue.storage.CatalogueAssetFailureReason? = null

    override suspend fun saveAsset(companyId: String, productId: String, sourceUri: android.net.Uri): com.budcom.android.feature.catalogue.storage.CatalogueAssetResult {
        saved += Triple(companyId, productId, sourceUri)
        failureReason?.let { return com.budcom.android.feature.catalogue.storage.CatalogueAssetResult.Failure(it) }
        val assetId = "asset-${nextAssetId++}"
        return com.budcom.android.feature.catalogue.storage.CatalogueAssetResult.Success(assetId, "$companyId/$productId/$assetId.jpg")
    }

    override fun resolveAssetFile(companyId: String, productId: String, filePath: String?): java.io.File? = null

    override suspend fun deleteAsset(companyId: String, productId: String, filePath: String) {
        deleted += Triple(companyId, productId, filePath)
    }
}
