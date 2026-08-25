package com.budcom.android.feature.catalogue.sharing

import com.budcom.android.feature.catalogue.domain.model.Branch
import com.budcom.android.feature.catalogue.domain.model.CatalogueLifecycleAction
import com.budcom.android.feature.catalogue.domain.model.CatalogueLifecycleState
import com.budcom.android.feature.catalogue.domain.model.CatalogueOverrideAttribute
import com.budcom.android.feature.catalogue.domain.model.CatalogueOverrideRow
import com.budcom.android.feature.catalogue.domain.model.CatalogueOverrideScope
import com.budcom.android.feature.catalogue.domain.model.CatalogueProduct
import com.budcom.android.feature.catalogue.domain.model.CataloguePublishedSnapshot
import com.budcom.android.feature.catalogue.domain.model.CatalogueTimestamp
import com.budcom.android.feature.catalogue.domain.repository.CatalogueEnrichmentUpdate
import com.budcom.android.feature.catalogue.domain.repository.CatalogueLifecycleResult
import com.budcom.android.feature.catalogue.domain.repository.CatalogueReconciliationResult
import com.budcom.android.feature.catalogue.domain.repository.CatalogueRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogueShareContentTest {

    @Test
    fun `a Private catalogue is refused before any product content is ever read`() = runTest {
        val repository = FakeShareRepository(isPublic = false)
        repository.published += sampleSnapshot() // present, but must never be read

        val result = CatalogueShareContent.resolve(repository, "co-1", CatalogueShareScope.FullCatalogue, null)

        assertTrue(result is CatalogueShareResult.Failure)
        assertTrue("must never touch listAllPublished for a Private catalogue", !repository.listAllPublishedCalled)
    }

    @Test
    fun `an empty published set is refused with an honest message, never an empty file`() = runTest {
        val repository = FakeShareRepository(isPublic = true)
        val result = CatalogueShareContent.resolve(repository, "co-1", CatalogueShareScope.FullCatalogue, null)
        assertTrue(result is CatalogueShareResult.Failure)
    }

    @Test
    fun `a Public catalogue with Published products succeeds and includes them`() = runTest {
        val repository = FakeShareRepository(isPublic = true)
        repository.published += sampleSnapshot(displayName = "Widget")

        val result = CatalogueShareContent.resolve(repository, "co-1", CatalogueShareScope.FullCatalogue, "Acme")
        val text = (result as CatalogueShareResult.Success).value
        assertTrue(text.contains("Widget"))
        assertTrue(text.contains("Acme"))
    }

    @Test
    fun `category scope only pulls that category, never the full published set`() = runTest {
        val repository = FakeShareRepository(isPublic = true)
        repository.published += sampleSnapshot(productId = "p1", displayName = "Electronics Item", category = "Electronics")
        repository.published += sampleSnapshot(productId = "p2", displayName = "Grocery Item", category = "Grocery")

        val result = CatalogueShareContent.resolve(repository, "co-1", CatalogueShareScope.Category("Electronics"), null)
        val text = (result as CatalogueShareResult.Success).value
        assertTrue(text.contains("Electronics Item"))
        assertTrue(!text.contains("Grocery Item"))
    }
}

private fun sampleSnapshot(
    productId: String = "p1",
    displayName: String = "Item",
    category: String? = null,
) = CataloguePublishedSnapshot(
    companyId = "co-1",
    productId = productId,
    displayName = displayName,
    description = null,
    specifications = null,
    customerFacingCategory = category,
    priceDisplayMode = com.budcom.android.feature.catalogue.domain.model.PriceDisplayMode.ContactForPrice,
    resolvedPriceAmount = null,
    resolvedPriceCurrencyCode = null,
    primaryAssetId = null,
    publishedAt = com.budcom.android.feature.catalogue.domain.model.CatalogueTimestamp(
        1_000L, com.budcom.android.feature.catalogue.domain.model.CatalogueTimestampSource.DeviceLocalProvisional,
    ),
)

/** Minimal fake proving exactly what [CatalogueShareContentTest] exercises, plus an explicit
 * [listAllPublishedCalled] flag so the Private-refusal test can prove the read never happened at
 * all, not just that its result was discarded. */
private class FakeShareRepository(private val isPublic: Boolean) : CatalogueRepository {
    val published = mutableListOf<CataloguePublishedSnapshot>()
    var listAllPublishedCalled = false
        private set

    override suspend fun isPublic(companyId: String): Boolean = isPublic
    override suspend fun setPublic(companyId: String, isPublic: Boolean, timestamp: CatalogueTimestamp) = Unit

    override suspend fun upsertCustomFields(companyId: String, productId: String, values: Map<String, String?>, timestamp: CatalogueTimestamp) = Unit
    override suspend fun listCustomFields(companyId: String, productId: String): Map<String, String?> = emptyMap()
    override suspend fun listAllCustomFieldColumnNames(companyId: String): List<String> = emptyList()

    override suspend fun listAllPublished(companyId: String): List<CataloguePublishedSnapshot> {
        listAllPublishedCalled = true
        return published
    }

    override suspend fun listUnlinkedStockItems(companyId: String): List<com.budcom.android.feature.masterdata.stockitem.domain.model.StockItem> =
        error("not used in these tests")
    override suspend fun createDraftsFromStockItems(
        companyId: String,
        stockItemIds: List<String>,
        timestamp: CatalogueTimestamp,
        onProgress: suspend (linked: Int, total: Int) -> Unit,
    ): Int = error("not used in these tests")
    override suspend fun warmStockItemCache(companyId: String) = error("not used in these tests")
    override suspend fun addAsset(
        companyId: String,
        productId: String,
        sourceUri: android.net.Uri,
        timestamp: CatalogueTimestamp,
    ): com.budcom.android.feature.catalogue.storage.CatalogueAssetResult = error("not used in these tests")
    override suspend fun listAssets(companyId: String, productId: String): List<com.budcom.android.feature.catalogue.domain.model.CatalogueAsset> =
        error("not used in these tests")
    override suspend fun setPrimaryAsset(companyId: String, productId: String, assetId: String, timestamp: CatalogueTimestamp) =
        error("not used in these tests")
    override suspend fun deleteAsset(companyId: String, productId: String, assetId: String) = error("not used in these tests")
    override fun resolveAssetFile(companyId: String, productId: String, filePath: String): java.io.File? = error("not used in these tests")

    override suspend fun listPublishedForCategory(companyId: String, category: String): List<CataloguePublishedSnapshot> =
        published.filter { it.customerFacingCategory == category }

    override suspend fun createDraftFromStockItem(companyId: String, stockItemId: String, timestamp: CatalogueTimestamp): CatalogueProduct? =
        error("not used in these tests")
    override suspend fun createManualDraft(companyId: String, displayName: String, timestamp: CatalogueTimestamp): CatalogueProduct =
        error("not used in these tests")
    override suspend fun findProduct(companyId: String, productId: String): CatalogueProduct? = error("not used in these tests")
    override suspend fun listProducts(companyId: String): List<CatalogueProduct> = error("not used in these tests")
    override suspend fun listProductsByState(companyId: String, state: CatalogueLifecycleState): List<CatalogueProduct> =
        error("not used in these tests")
    override suspend fun updateEnrichment(
        companyId: String,
        productId: String,
        update: CatalogueEnrichmentUpdate,
        timestamp: CatalogueTimestamp,
    ): CatalogueProduct? = error("not used in these tests")
    override suspend fun transitionLifecycle(
        companyId: String,
        productId: String,
        action: CatalogueLifecycleAction,
        isOwner: Boolean,
        timestamp: CatalogueTimestamp,
    ): CatalogueLifecycleResult = error("not used in these tests")
    override suspend fun reconcileStockItemLinks(companyId: String, timestamp: CatalogueTimestamp): CatalogueReconciliationResult =
        error("not used in these tests")
    override suspend fun upsertBranch(companyId: String, branchId: String, name: String, isActive: Boolean, timestamp: CatalogueTimestamp): Branch =
        error("not used in these tests")
    override suspend fun listBranches(companyId: String): List<Branch> = error("not used in these tests")
    override suspend fun setOverride(
        companyId: String,
        scope: CatalogueOverrideScope,
        attribute: CatalogueOverrideAttribute,
        value: String,
        timestamp: CatalogueTimestamp,
    ) = error("not used in these tests")
    override suspend fun clearOverride(companyId: String, scope: CatalogueOverrideScope, attribute: CatalogueOverrideAttribute) =
        error("not used in these tests")
    override suspend fun resolveOverride(
        companyId: String,
        productId: String,
        branchId: String?,
        attribute: CatalogueOverrideAttribute,
    ): CatalogueOverrideRow? = error("not used in these tests")
}
