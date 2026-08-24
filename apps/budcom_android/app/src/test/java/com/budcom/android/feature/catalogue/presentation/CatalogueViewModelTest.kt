package com.budcom.android.feature.catalogue.presentation

import com.budcom.android.core.common.AppResult
import com.budcom.android.feature.businessprofile.domain.model.BusinessProfile
import com.budcom.android.feature.businessprofile.domain.model.BusinessProfileDraft
import com.budcom.android.feature.businessprofile.domain.repository.BusinessProfileRepository
import com.budcom.android.feature.businessprofile.storage.BusinessProfileLogoResult
import com.budcom.android.feature.catalogue.domain.model.Branch
import com.budcom.android.feature.catalogue.domain.model.CatalogueLifecycleAction
import com.budcom.android.feature.catalogue.domain.model.CatalogueLifecycleState
import com.budcom.android.feature.catalogue.domain.model.CatalogueOverrideAttribute
import com.budcom.android.feature.catalogue.domain.model.CatalogueOverrideRow
import com.budcom.android.feature.catalogue.domain.model.CatalogueOverrideScope
import com.budcom.android.feature.catalogue.domain.model.CatalogueProduct
import com.budcom.android.feature.catalogue.domain.model.CatalogueProductSource
import com.budcom.android.feature.catalogue.domain.model.CataloguePublishedSnapshot
import com.budcom.android.feature.catalogue.domain.model.CatalogueTimestamp
import com.budcom.android.feature.catalogue.domain.model.CatalogueTimestampSource
import com.budcom.android.feature.catalogue.domain.model.PriceDisplayMode
import com.budcom.android.feature.catalogue.domain.port.CatalogueClock
import com.budcom.android.feature.catalogue.domain.repository.CatalogueEnrichmentUpdate
import com.budcom.android.feature.catalogue.domain.repository.CatalogueLifecycleResult
import com.budcom.android.feature.catalogue.domain.repository.CatalogueReconciliationResult
import com.budcom.android.feature.catalogue.domain.repository.CatalogueRepository
import com.budcom.android.feature.company.domain.port.CompanySessionPort
import com.budcom.android.feature.company.domain.port.SelectedCompanyStatus
import com.budcom.android.feature.company.domain.port.SessionValidationStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogueViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        repository: CatalogueRepository,
        company: FakeCompanySessionPort,
        shareCoordinator: FakeCatalogueShareCoordinator = FakeCatalogueShareCoordinator(),
        businessProfileRepository: FakeBusinessProfileRepository = FakeBusinessProfileRepository(),
    ) = CatalogueViewModel(repository, company, FakeCatalogueClock(), shareCoordinator, businessProfileRepository)

    @Test
    fun `loads products when a company becomes selected`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        repository.products["co-1"] = mutableListOf(sampleProduct(productId = "p1", displayName = "Widget"))
        val company = FakeCompanySessionPort("co-1")

        val vm = viewModel(repository, company)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("Widget"), vm.uiState.value.products.map { it.displayName })
        assertTrue(!vm.uiState.value.isInitialLoading)
    }

    @Test
    fun `switching companies reloads the list from scratch`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        repository.products["co-A"] = mutableListOf(sampleProduct(productId = "a1", displayName = "A Item"))
        repository.products["co-B"] = mutableListOf(sampleProduct(productId = "b1", displayName = "B Item"))
        val company = FakeCompanySessionPort("co-A")

        val vm = viewModel(repository, company)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("A Item"), vm.uiState.value.products.map { it.displayName })

        company.set("co-B")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("B Item"), vm.uiState.value.products.map { it.displayName })
    }

    @Test
    fun `toggling Public persists through the repository and updates state`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        val company = FakeCompanySessionPort("co-1")
        val vm = viewModel(repository, company)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(!vm.uiState.value.isPublic)

        vm.onEvent(CatalogueEvent.SetPublic(true))
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.isPublic)
        assertTrue(repository.publicByCompany["co-1"] == true)
    }

    @Test
    fun `the FAB opens a choice between manual entry and linking from Tally stock`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        val vm = viewModel(repository, FakeCompanySessionPort("co-1"))
        dispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(CatalogueEvent.OpenAddChoiceDialog)

        assertTrue(vm.uiState.value.showAddChoiceDialog)
    }

    @Test
    fun `choosing manual entry closes the choice dialog and opens the manual-name dialog`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        val vm = viewModel(repository, FakeCompanySessionPort("co-1"))
        dispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(CatalogueEvent.OpenAddChoiceDialog)

        vm.onEvent(CatalogueEvent.ChooseManualEntry)

        assertTrue(!vm.uiState.value.showAddChoiceDialog)
        assertTrue(vm.uiState.value.showAddManualDialog)
    }

    @Test
    fun `choosing link-from-stock closes the choice dialog and emits a navigation effect`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        val vm = viewModel(repository, FakeCompanySessionPort("co-1"))
        dispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(CatalogueEvent.OpenAddChoiceDialog)

        val effects = mutableListOf<CatalogueEffect>()
        val job = launch { vm.effects.collect { effects.add(it) } }
        vm.onEvent(CatalogueEvent.ChooseLinkFromStock)
        dispatcher.scheduler.advanceUntilIdle()
        job.cancel()

        assertTrue(!vm.uiState.value.showAddChoiceDialog)
        assertEquals(listOf(CatalogueEffect.NavigateToStockItemPicker), effects)
    }

    @Test
    fun `a failed share surfaces the failure message rather than launching an intent`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        val company = FakeCompanySessionPort("co-1")
        val vm = viewModel(repository, company)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(CatalogueEvent.ShareFullCatalogue)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("not exercised in these tests", vm.uiState.value.shareMessage)
    }

    @Test
    fun `confirming the add-manual dialog creates a Draft and refreshes the list`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        val company = FakeCompanySessionPort("co-1")
        val vm = viewModel(repository, company)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(CatalogueEvent.OpenAddManualDialog)
        vm.onEvent(CatalogueEvent.AddManualNameChanged("Hand-made Basket"))
        vm.onEvent(CatalogueEvent.ConfirmAddManual)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repository.products["co-1"]?.size)
        assertEquals("Hand-made Basket", repository.products["co-1"]!!.single().displayName)
        assertTrue(!vm.uiState.value.showAddManualDialog)
        assertEquals(listOf("Hand-made Basket"), vm.uiState.value.products.map { it.displayName })
    }

    @Test
    fun `a blank manual name is never submitted`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        val company = FakeCompanySessionPort("co-1")
        val vm = viewModel(repository, company)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(CatalogueEvent.OpenAddManualDialog)
        vm.onEvent(CatalogueEvent.AddManualNameChanged("   "))
        vm.onEvent(CatalogueEvent.ConfirmAddManual)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(repository.products["co-1"].isNullOrEmpty())
    }
}

// ============================== Fakes ==============================

internal class FakeCatalogueClock(private val timestamp: CatalogueTimestamp = CatalogueTimestamp(1_000L, CatalogueTimestampSource.DeviceLocalProvisional)) :
    CatalogueClock {
    override suspend fun now(): CatalogueTimestamp = timestamp
}

internal class FakeCompanySessionPort(initial: String?) : CompanySessionPort {
    private val state = MutableStateFlow(initial)
    fun set(companyId: String?) { state.value = companyId }
    override fun observeSelectedCompanyId() = state
    override suspend fun readSelectedCompany(): AppResult<SelectedCompanyStatus> =
        AppResult.Success(SelectedCompanyStatus(state.value, state.value))
    override suspend fun validateSessionStatus(): AppResult<SessionValidationStatus> =
        error("not used in these tests")
}

internal fun sampleProduct(
    companyId: String = "co-1",
    productId: String = "p1",
    displayName: String = "Item",
    lifecycleState: CatalogueLifecycleState = CatalogueLifecycleState.Draft,
    sourceAvailable: Boolean = true,
) = CatalogueProduct(
    companyId = companyId,
    productId = productId,
    source = CatalogueProductSource.Manual,
    linkedStockItemId = null,
    sku = null,
    displayNameOverride = displayName,
    tallyName = null,
    unit = null,
    hsnCode = null,
    gstRate = null,
    stockGroupKey = null,
    description = null,
    specifications = null,
    customerFacingCategory = null,
    priceDisplayMode = PriceDisplayMode.ContactForPrice,
    manualPriceAmount = null,
    manualPriceCurrencyCode = null,
    lifecycleState = lifecycleState,
    sourceAvailable = sourceAvailable,
    createdAt = CatalogueTimestamp(1_000L, CatalogueTimestampSource.DeviceLocalProvisional),
    updatedAt = CatalogueTimestamp(1_000L, CatalogueTimestampSource.DeviceLocalProvisional),
    archivedAt = null,
)

internal class FakeCatalogueShareCoordinator : com.budcom.android.feature.catalogue.sharing.CatalogueShareCoordinator {
    override suspend fun prepareShare(
        companyId: String,
        scope: com.budcom.android.feature.catalogue.sharing.CatalogueShareScope,
        businessName: String?,
    ) = com.budcom.android.feature.catalogue.sharing.CatalogueShareResult.Failure("not exercised in these tests")
    override fun createShareIntent(prepared: com.budcom.android.feature.catalogue.sharing.PreparedCatalogueShare) =
        error("not used in these tests")
    override fun releaseShare(prepared: com.budcom.android.feature.catalogue.sharing.PreparedCatalogueShare) = Unit
}

internal class FakeBusinessProfileRepository : BusinessProfileRepository {
    override suspend fun getProfile(companyId: String): BusinessProfile? = null
    override suspend fun saveProfile(companyId: String, draft: BusinessProfileDraft): BusinessProfile = error("not used in these tests")
    override suspend fun updateLogo(companyId: String, sourceUri: android.net.Uri): BusinessProfileLogoResult? = error("not used in these tests")
    override suspend fun clearLogo(companyId: String) = Unit
    override suspend fun resolveLogoFile(logoAssetPath: String?): java.io.File? = null
}

/** In-memory fake covering exactly what the presentation-layer tests exercise. */
internal class FakeCatalogueRepository : CatalogueRepository {
    val products = mutableMapOf<String, MutableList<CatalogueProduct>>()
    val publicByCompany = mutableMapOf<String, Boolean>()
    var nextId = 0

val unlinkedStockItems = mutableMapOf<String, MutableList<com.budcom.android.feature.masterdata.stockitem.domain.model.StockItem>>()

    override suspend fun createDraftFromStockItem(companyId: String, stockItemId: String, timestamp: CatalogueTimestamp): CatalogueProduct? {
        val list = unlinkedStockItems[companyId] ?: return null
        val stockItem = list.firstOrNull { it.id == stockItemId } ?: return null
        list.remove(stockItem)
        val product = sampleProduct(companyId = companyId, productId = "linked-${nextId++}", displayName = stockItem.name)
            .copy(source = com.budcom.android.feature.catalogue.domain.model.CatalogueProductSource.Tally, linkedStockItemId = stockItemId, tallyName = stockItem.name)
        products.getOrPut(companyId) { mutableListOf() }.add(product)
        return product
    }

    override suspend fun listUnlinkedStockItems(companyId: String): List<com.budcom.android.feature.masterdata.stockitem.domain.model.StockItem> =
        unlinkedStockItems[companyId].orEmpty()

    val assetsByProduct = mutableMapOf<Pair<String, String>, MutableList<com.budcom.android.feature.catalogue.domain.model.CatalogueAsset>>()
    var nextAssetId = 0
    var addAssetFailure: com.budcom.android.feature.catalogue.storage.CatalogueAssetFailureReason? = null

    override suspend fun addAsset(
        companyId: String,
        productId: String,
        sourceUri: android.net.Uri,
        timestamp: CatalogueTimestamp,
    ): com.budcom.android.feature.catalogue.storage.CatalogueAssetResult {
        addAssetFailure?.let { return com.budcom.android.feature.catalogue.storage.CatalogueAssetResult.Failure(it) }
        val assetId = "asset-${nextAssetId++}"
        val list = assetsByProduct.getOrPut(companyId to productId) { mutableListOf() }
        list.add(
            com.budcom.android.feature.catalogue.domain.model.CatalogueAsset(
                companyId = companyId,
                productId = productId,
                assetId = assetId,
                isPrimary = list.isEmpty(),
                sortOrder = list.size,
                filePath = "$companyId/$productId/$assetId.jpg",
                createdAt = timestamp,
            ),
        )
        return com.budcom.android.feature.catalogue.storage.CatalogueAssetResult.Success(assetId, "$companyId/$productId/$assetId.jpg")
    }

    override suspend fun listAssets(companyId: String, productId: String): List<com.budcom.android.feature.catalogue.domain.model.CatalogueAsset> =
        assetsByProduct[companyId to productId].orEmpty()

    override suspend fun setPrimaryAsset(companyId: String, productId: String, assetId: String, timestamp: CatalogueTimestamp) {
        val list = assetsByProduct[companyId to productId] ?: return
        assetsByProduct[companyId to productId] = list.map { it.copy(isPrimary = it.assetId == assetId) }.toMutableList()
    }

    override suspend fun deleteAsset(companyId: String, productId: String, assetId: String) {
        val list = assetsByProduct[companyId to productId] ?: return
        val wasPrimary = list.firstOrNull { it.assetId == assetId }?.isPrimary == true
        list.removeAll { it.assetId == assetId }
        if (wasPrimary && list.isNotEmpty()) list[0] = list[0].copy(isPrimary = true)
    }

    override fun resolveAssetFile(companyId: String, productId: String, filePath: String): java.io.File? = null

    override suspend fun createManualDraft(companyId: String, displayName: String, timestamp: CatalogueTimestamp): CatalogueProduct {
        val product = sampleProduct(companyId = companyId, productId = "manual-${nextId++}", displayName = displayName)
        products.getOrPut(companyId) { mutableListOf() }.add(product)
        return product
    }

    override suspend fun findProduct(companyId: String, productId: String): CatalogueProduct? =
        products[companyId]?.firstOrNull { it.productId == productId }

    override suspend fun listProducts(companyId: String): List<CatalogueProduct> = products[companyId].orEmpty()

    override suspend fun listProductsByState(companyId: String, state: CatalogueLifecycleState): List<CatalogueProduct> =
        products[companyId].orEmpty().filter { it.lifecycleState == state }

    override suspend fun updateEnrichment(
        companyId: String,
        productId: String,
        update: CatalogueEnrichmentUpdate,
        timestamp: CatalogueTimestamp,
    ): CatalogueProduct? {
        val list = products[companyId] ?: return null
        val existing = list.firstOrNull { it.productId == productId } ?: return null
        val updated = existing.copy(
            description = update.description ?: existing.description,
            specifications = update.specifications ?: existing.specifications,
            customerFacingCategory = update.customerFacingCategory ?: existing.customerFacingCategory,
            priceDisplayMode = update.priceDisplayMode ?: existing.priceDisplayMode,
            manualPriceAmount = update.manualPriceAmount ?: existing.manualPriceAmount,
        )
        list[list.indexOf(existing)] = updated
        return updated
    }

    override suspend fun transitionLifecycle(
        companyId: String,
        productId: String,
        action: CatalogueLifecycleAction,
        isOwner: Boolean,
        timestamp: CatalogueTimestamp,
    ): CatalogueLifecycleResult {
        val list = products[companyId] ?: return CatalogueLifecycleResult.ProductNotFound
        val existing = list.firstOrNull { it.productId == productId } ?: return CatalogueLifecycleResult.ProductNotFound
        val next = com.budcom.android.feature.catalogue.domain.model.CatalogueLifecycleTransitions.transition(
            existing.lifecycleState, action, isOwner,
        ) ?: return CatalogueLifecycleResult.Rejected
        val updated = existing.copy(lifecycleState = next)
        list[list.indexOf(existing)] = updated
        return CatalogueLifecycleResult.Success(updated)
    }

    override suspend fun reconcileStockItemLinks(companyId: String, timestamp: CatalogueTimestamp): CatalogueReconciliationResult =
        CatalogueReconciliationResult(emptyList(), emptyList())

    override suspend fun upsertBranch(companyId: String, branchId: String, name: String, isActive: Boolean, timestamp: CatalogueTimestamp): Branch =
        error("not used in these tests")

    override suspend fun listBranches(companyId: String): List<Branch> = emptyList()

    override suspend fun setOverride(
        companyId: String,
        scope: CatalogueOverrideScope,
        attribute: CatalogueOverrideAttribute,
        value: String,
        timestamp: CatalogueTimestamp,
    ) = Unit

    override suspend fun clearOverride(companyId: String, scope: CatalogueOverrideScope, attribute: CatalogueOverrideAttribute) = Unit

    override suspend fun resolveOverride(
        companyId: String,
        productId: String,
        branchId: String?,
        attribute: CatalogueOverrideAttribute,
    ): CatalogueOverrideRow? = null

    override suspend fun listPublishedForCategory(companyId: String, category: String): List<CataloguePublishedSnapshot> = emptyList()
    override suspend fun listAllPublished(companyId: String): List<CataloguePublishedSnapshot> = emptyList()
    override suspend fun isPublic(companyId: String): Boolean = publicByCompany[companyId] ?: false
    override suspend fun setPublic(companyId: String, isPublic: Boolean, timestamp: CatalogueTimestamp) {
        publicByCompany[companyId] = isPublic
    }
}
