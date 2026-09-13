package com.jajusri.venture.feature.catalogue.presentation

import com.jajusri.venture.core.common.AppResult
import com.jajusri.venture.feature.businessprofile.domain.model.BusinessProfile
import com.jajusri.venture.feature.businessprofile.domain.model.BusinessProfileDraft
import com.jajusri.venture.feature.businessprofile.domain.repository.BusinessProfileRepository
import com.jajusri.venture.feature.businessprofile.storage.BusinessProfileLogoResult
import com.jajusri.venture.feature.catalogue.domain.model.Branch
import com.jajusri.venture.feature.catalogue.domain.model.CatalogueLifecycleAction
import com.jajusri.venture.feature.catalogue.domain.model.CatalogueLifecycleState
import com.jajusri.venture.feature.catalogue.domain.model.CatalogueOverrideAttribute
import com.jajusri.venture.feature.catalogue.domain.model.CatalogueOverrideRow
import com.jajusri.venture.feature.catalogue.domain.model.CatalogueOverrideScope
import com.jajusri.venture.feature.catalogue.domain.model.CatalogueProduct
import com.jajusri.venture.feature.catalogue.domain.model.CatalogueProductSource
import com.jajusri.venture.feature.catalogue.domain.model.CataloguePublishedSnapshot
import com.jajusri.venture.feature.catalogue.domain.model.CatalogueTimestamp
import com.jajusri.venture.feature.catalogue.domain.model.CatalogueTimestampSource
import com.jajusri.venture.feature.catalogue.domain.model.PriceDisplayMode
import com.jajusri.venture.feature.catalogue.domain.port.CatalogueClock
import com.jajusri.venture.feature.catalogue.domain.repository.CatalogueEnrichmentUpdate
import com.jajusri.venture.feature.catalogue.domain.repository.CatalogueLifecycleResult
import com.jajusri.venture.feature.catalogue.domain.repository.CatalogueReconciliationResult
import com.jajusri.venture.feature.catalogue.domain.repository.CatalogueRepository
import com.jajusri.venture.feature.company.domain.port.CompanySessionPort
import com.jajusri.venture.feature.company.domain.port.SelectedCompanyStatus
import com.jajusri.venture.feature.company.domain.port.SessionValidationStatus
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
        branchSelectionStore: FakeCatalogueBranchSelectionStore = FakeCatalogueBranchSelectionStore(),
    ) = CatalogueViewModel(
        repository, company, FakeCatalogueClock(), shareCoordinator, businessProfileRepository, branchSelectionStore,
        com.jajusri.venture.feature.catalogue.domain.excel.CatalogueExcelImportUseCase(repository, FakeCatalogueClock()),
        com.jajusri.venture.feature.catalogue.domain.excel.CatalogueExcelExportUseCase(repository),
    )

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

    // ============================== Resume freshness (Catalogue perf package) ==============================

    @Test
    fun `ResumeCheck does nothing when the change signal is unchanged`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        repository.products["co-1"] = mutableListOf(sampleProduct(productId = "p1", displayName = "Widget"))
        val vm = viewModel(repository, FakeCompanySessionPort("co-1"))
        dispatcher.scheduler.advanceUntilIdle()
        val reconcileCountAfterInitialLoad = repository.reconcileCallCount

        vm.onEvent(CatalogueEvent.ResumeCheck)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            "an unchanged signal must skip reconciliation entirely, not merely skip the UI update",
            reconcileCountAfterInitialLoad,
            repository.reconcileCallCount,
        )
        assertEquals(listOf("Widget"), vm.uiState.value.products.map { it.displayName })
    }

    @Test
    fun `ResumeCheck reconciles and reloads when the change signal differs`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        repository.products["co-1"] = mutableListOf(sampleProduct(productId = "p1", displayName = "Widget"))
        val vm = viewModel(repository, FakeCompanySessionPort("co-1"))
        dispatcher.scheduler.advanceUntilIdle()
        val reconcileCountAfterInitialLoad = repository.reconcileCallCount
        repository.changeSignal = repository.changeSignal.copy(localRevision = repository.changeSignal.localRevision + 1)
        repository.products["co-1"]!!.add(sampleProduct(productId = "p2", displayName = "Gadget"))

        vm.onEvent(CatalogueEvent.ResumeCheck)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(reconcileCountAfterInitialLoad + 1, repository.reconcileCallCount)
        assertEquals(setOf("Widget", "Gadget"), vm.uiState.value.products.map { it.displayName }.toSet())
    }

    // ============================== Pagination (Catalogue perf package) ==============================

    @Test
    fun `only the first page loads initially, with canLoadMore set once more products exist`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        repository.products["co-1"] = (1..(CATALOGUE_PAGE_SIZE + 20)).map { i ->
            sampleProduct(productId = "p$i", displayName = "Item $i")
        }.toMutableList()
        val vm = viewModel(repository, FakeCompanySessionPort("co-1"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(CATALOGUE_PAGE_SIZE, vm.uiState.value.products.size)
        assertTrue("a company with more products than one page must report canLoadMore", vm.uiState.value.canLoadMore)
    }

    @Test
    fun `a company with fewer products than one page reports no more pages`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        repository.products["co-1"] = mutableListOf(sampleProduct(productId = "p1", displayName = "Widget"))
        val vm = viewModel(repository, FakeCompanySessionPort("co-1"))
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(!vm.uiState.value.canLoadMore)
    }

    @Test
    fun `LoadMoreProducts appends the next page and stops once exhausted`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        repository.products["co-1"] = (1..(CATALOGUE_PAGE_SIZE + 20)).map { i ->
            sampleProduct(productId = "p$i", displayName = "Item $i")
        }.toMutableList()
        val vm = viewModel(repository, FakeCompanySessionPort("co-1"))
        dispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(CatalogueEvent.LoadMoreProducts)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(CATALOGUE_PAGE_SIZE + 20, vm.uiState.value.products.size)
        assertTrue("every product must have been paged in exactly once, no duplicates", vm.uiState.value.products.map { it.productId }.distinct().size == CATALOGUE_PAGE_SIZE + 20)
        assertTrue("no more pages left beyond what exists", !vm.uiState.value.canLoadMore)
    }

    // ============================== product-card photo display ==============================

    private fun ts(millis: Long = 1_000L) = CatalogueTimestamp(millis, CatalogueTimestampSource.DeviceLocalProvisional)

    private fun asset(
        companyId: String = "co-1",
        productId: String = "p1",
        assetId: String = "a1",
        filePath: String = "$companyId/$productId/$assetId.jpg",
        isPrimary: Boolean = true,
    ) = com.jajusri.venture.feature.catalogue.domain.model.CatalogueAsset(
        companyId = companyId, productId = productId, assetId = assetId, isPrimary = isPrimary, sortOrder = 0,
        filePath = filePath, createdAt = ts(),
    )

    @Test
    fun `a product with an added photo displays it on the product card`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        repository.products["co-1"] = mutableListOf(sampleProduct(productId = "p1", displayName = "Widget"))
        repository.assetsByProduct["co-1" to "p1"] = mutableListOf(asset())
        val company = FakeCompanySessionPort("co-1")

        val vm = viewModel(repository, company)
        dispatcher.scheduler.advanceUntilIdle()

        val row = vm.uiState.value.products.single()
        assertTrue("a product with a stored photo must resolve a non-null primaryAssetFile", row.primaryAssetFile != null)
        assertEquals(java.io.File("co-1/p1/a1.jpg").path, row.primaryAssetFile!!.path)
    }

    @Test
    fun `a product with no photo continues to show the existing no-image state`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        repository.products["co-1"] = mutableListOf(sampleProduct(productId = "p1", displayName = "Widget"))
        // No entry added to assetsByProduct at all -- matches a product that has never had a photo.
        val company = FakeCompanySessionPort("co-1")

        val vm = viewModel(repository, company)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(null, vm.uiState.value.products.single().primaryAssetFile)
    }

    @Test
    fun `when multiple assets exist the primary one is used, not merely the first added`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        repository.products["co-1"] = mutableListOf(sampleProduct(productId = "p1", displayName = "Widget"))
        repository.assetsByProduct["co-1" to "p1"] = mutableListOf(
            asset(assetId = "a1", filePath = "co-1/p1/a1.jpg", isPrimary = false),
            asset(assetId = "a2", filePath = "co-1/p1/a2.jpg", isPrimary = true),
        )
        val company = FakeCompanySessionPort("co-1")

        val vm = viewModel(repository, company)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(java.io.File("co-1/p1/a2.jpg").path, vm.uiState.value.products.single().primaryAssetFile!!.path)
    }

    @Test
    fun `a stored asset reference that no longer resolves to a real file never crashes the list`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        repository.products["co-1"] = mutableListOf(sampleProduct(productId = "p1", displayName = "Widget"))
        // An asset row exists, but its filePath deliberately does not match what resolveAssetFile
        // would recognize (simulates a stale DB reference to a file that was deleted from disk).
        repository.assetsByProduct["co-1" to "p1"] = mutableListOf(asset(filePath = "co-1/p1/never-resolves.jpg"))
        repository.brokenAssetFilePaths += "co-1/p1/never-resolves.jpg"
        val company = FakeCompanySessionPort("co-1")

        val vm = viewModel(repository, company)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Widget", vm.uiState.value.products.single().displayName)
        assertEquals(null, vm.uiState.value.products.single().primaryAssetFile)
    }

    @Test
    fun `a product's photo never resolves to another company's asset with the same product id`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        repository.products["co-A"] = mutableListOf(sampleProduct(companyId = "co-A", productId = "p1", displayName = "A Item"))
        repository.products["co-B"] = mutableListOf(sampleProduct(companyId = "co-B", productId = "p1", displayName = "B Item"))
        // Only company B's identically-numbered product has a photo.
        repository.assetsByProduct["co-B" to "p1"] = mutableListOf(asset(companyId = "co-B", productId = "p1"))
        val company = FakeCompanySessionPort("co-A")

        val vm = viewModel(repository, company)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(null, vm.uiState.value.products.single().primaryAssetFile)
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
    fun `opening the category share dialog lists only distinct categories among Published products`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        val company = FakeCompanySessionPort("co-1")
        val vm = viewModel(repository, company)
        dispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(CatalogueEvent.OpenAddManualDialog)
        vm.onEvent(CatalogueEvent.AddManualNameChanged("Snack A"))
        vm.onEvent(CatalogueEvent.ConfirmAddManual)
        vm.onEvent(CatalogueEvent.OpenAddManualDialog)
        vm.onEvent(CatalogueEvent.AddManualNameChanged("Snack B"))
        vm.onEvent(CatalogueEvent.ConfirmAddManual)
        vm.onEvent(CatalogueEvent.OpenAddManualDialog)
        vm.onEvent(CatalogueEvent.AddManualNameChanged("Draft Only"))
        vm.onEvent(CatalogueEvent.ConfirmAddManual)
        dispatcher.scheduler.advanceUntilIdle()
        val (snackA, snackB, draftOnly) = repository.products["co-1"]!!
        repository.updateEnrichment("co-1", snackA.productId, CatalogueEnrichmentUpdate(customerFacingCategory = "Snacks"), CatalogueTimestamp(1L, CatalogueTimestampSource.DeviceLocalProvisional))
        repository.updateEnrichment("co-1", snackB.productId, CatalogueEnrichmentUpdate(customerFacingCategory = "Snacks"), CatalogueTimestamp(1L, CatalogueTimestampSource.DeviceLocalProvisional))
        repository.updateEnrichment("co-1", draftOnly.productId, CatalogueEnrichmentUpdate(customerFacingCategory = "Unpublished Category"), CatalogueTimestamp(1L, CatalogueTimestampSource.DeviceLocalProvisional))
        repository.transitionLifecycle("co-1", snackA.productId, CatalogueLifecycleAction.Publish, isOwner = true, CatalogueTimestamp(1L, CatalogueTimestampSource.DeviceLocalProvisional))
        repository.transitionLifecycle("co-1", snackB.productId, CatalogueLifecycleAction.Publish, isOwner = true, CatalogueTimestamp(1L, CatalogueTimestampSource.DeviceLocalProvisional))
        // draftOnly deliberately never published.

        vm.onEvent(CatalogueEvent.OpenCategoryShareDialog)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.showCategoryShareDialog)
        assertEquals(listOf("Snacks"), vm.uiState.value.availableCategories)
    }

    @Test
    fun `sharing a category closes the dialog and attempts the share`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        val company = FakeCompanySessionPort("co-1")
        val vm = viewModel(repository, company)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(CatalogueEvent.ShareCategory("Snacks"))
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(!vm.uiState.value.showCategoryShareDialog)
        assertEquals("not exercised in these tests", vm.uiState.value.shareMessage)
    }

    // ============================== Branch selector ==============================

    @Test
    fun `a fresh company with no branches shows only the catalogue-wide default`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        val vm = viewModel(repository, FakeCompanySessionPort("co-1"))
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.branches.isEmpty())
        assertEquals(null, vm.uiState.value.selectedBranchId)
        assertEquals("All branches", vm.uiState.value.selectedBranchName)
    }

    @Test
    fun `adding a branch persists it, selects it, and it appears in the branch list`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        val vm = viewModel(repository, FakeCompanySessionPort("co-1"))
        dispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(CatalogueEvent.OpenAddBranchDialog)
        vm.onEvent(CatalogueEvent.AddBranchNameChanged("Main Branch"))
        vm.onEvent(CatalogueEvent.ConfirmAddBranch)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("Main Branch"), vm.uiState.value.branches.map { it.name })
        assertEquals("Main Branch", vm.uiState.value.selectedBranchName)
        assertTrue(!vm.uiState.value.showAddBranchDialog)
        assertEquals(1, repository.branches["co-1"]!!.size)
    }

    @Test
    fun `a blank branch name is never submitted`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        val vm = viewModel(repository, FakeCompanySessionPort("co-1"))
        dispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(CatalogueEvent.OpenAddBranchDialog)
        vm.onEvent(CatalogueEvent.AddBranchNameChanged("   "))
        vm.onEvent(CatalogueEvent.ConfirmAddBranch)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(repository.branches["co-1"].isNullOrEmpty())
    }

    @Test
    fun `selecting a branch updates state and persists through the branch selection store`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        repository.branches["co-1"] = mutableListOf(Branch("co-1", "br-1", "Main Branch", true, ts(), ts()))
        val branchStore = FakeCatalogueBranchSelectionStore()
        val vm = viewModel(repository, FakeCompanySessionPort("co-1"), branchSelectionStore = branchStore)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(CatalogueEvent.SelectBranch("br-1"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("br-1", vm.uiState.value.selectedBranchId)
        assertEquals("br-1", branchStore.currentValue("co-1"))
    }

    @Test
    fun `switching back to All branches clears the persisted selection`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        repository.branches["co-1"] = mutableListOf(Branch("co-1", "br-1", "Main Branch", true, ts(), ts()))
        val vm = viewModel(repository, FakeCompanySessionPort("co-1"))
        dispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(CatalogueEvent.SelectBranch("br-1"))
        dispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(CatalogueEvent.SelectBranch(null))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(null, vm.uiState.value.selectedBranchId)
        assertEquals("All branches", vm.uiState.value.selectedBranchName)
    }

    @Test
    fun `a selected branch survives a simulated relaunch -- a fresh ViewModel re-reads the persisted selection`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        repository.branches["co-1"] = mutableListOf(Branch("co-1", "br-1", "Main Branch", true, ts(), ts()))
        val branchStore = FakeCatalogueBranchSelectionStore()
        val firstSession = viewModel(repository, FakeCompanySessionPort("co-1"), branchSelectionStore = branchStore)
        dispatcher.scheduler.advanceUntilIdle()
        firstSession.onEvent(CatalogueEvent.SelectBranch("br-1"))
        dispatcher.scheduler.advanceUntilIdle()

        // A fresh ViewModel instance, same underlying store -- simulates the app being relaunched.
        val secondSession = viewModel(repository, FakeCompanySessionPort("co-1"), branchSelectionStore = branchStore)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("br-1", secondSession.uiState.value.selectedBranchId)
        assertEquals("Main Branch", secondSession.uiState.value.selectedBranchName)
    }

    @Test
    fun `a stored selection for a branch that no longer exists falls back to All branches rather than dangling`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        val branchStore = FakeCatalogueBranchSelectionStore()
        branchStore.setSelectedBranchId("co-1", "br-deleted")
        val vm = viewModel(repository, FakeCompanySessionPort("co-1"), branchSelectionStore = branchStore)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(null, vm.uiState.value.selectedBranchId)
        assertEquals("All branches", vm.uiState.value.selectedBranchName)
    }

    @Test
    fun `a single branch still renders correctly as the one selectable option`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        repository.branches["co-1"] = mutableListOf(Branch("co-1", "br-1", "Only Branch", true, ts(), ts()))
        val vm = viewModel(repository, FakeCompanySessionPort("co-1"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, vm.uiState.value.branches.size)
        assertEquals("Only Branch", vm.uiState.value.branches.single().name)
    }

    @Test
    fun `branch selection never mixes across companies`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        repository.branches["co-A"] = mutableListOf(Branch("co-A", "br-A", "Branch A", true, ts(), ts()))
        repository.branches["co-B"] = mutableListOf(Branch("co-B", "br-B", "Branch B", true, ts(), ts()))
        val company = FakeCompanySessionPort("co-A")
        val branchStore = FakeCatalogueBranchSelectionStore()
        val vm = viewModel(repository, company, branchSelectionStore = branchStore)
        dispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(CatalogueEvent.SelectBranch("br-A"))
        dispatcher.scheduler.advanceUntilIdle()

        company.set("co-B")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("Branch B"), vm.uiState.value.branches.map { it.name })
        assertEquals(null, vm.uiState.value.selectedBranchId)

        company.set("co-A")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("br-A", vm.uiState.value.selectedBranchId)
    }

    private fun ts() = CatalogueTimestamp(1_000L, CatalogueTimestampSource.DeviceLocalProvisional)

    // ============================== Excel import/export UI ==============================

    @Test
    fun `ImportFromExcel requests a file pick`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        val vm = viewModel(repository, FakeCompanySessionPort("co-1"))
        dispatcher.scheduler.advanceUntilIdle()

        val effects = mutableListOf<CatalogueEffect>()
        val job = launch(Dispatchers.Unconfined) { vm.effects.collect { effects.add(it) } }
        vm.onEvent(CatalogueEvent.ImportFromExcel)
        dispatcher.scheduler.advanceUntilIdle()
        job.cancel()

        assertEquals(listOf(CatalogueEffect.RequestExcelImportPick), effects)
        assertTrue(!vm.uiState.value.showMoreMenu)
    }

    @Test
    fun `a valid CSV builds an import preview without committing anything`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        val vm = viewModel(repository, FakeCompanySessionPort("co-1"))
        dispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(CatalogueEvent.ExcelFileTextLoaded("Product Name,Unit\r\nWidget,Nos\r\nGadget,Box\r\n"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, vm.uiState.value.importPreview?.createCount)
        assertTrue("nothing should be committed before ConfirmImport", repository.products["co-1"].isNullOrEmpty())
    }

    @Test
    fun `a malformed row is skipped with its reason shown in the preview, valid rows still preview fine`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        val vm = viewModel(repository, FakeCompanySessionPort("co-1"))
        dispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(CatalogueEvent.ExcelFileTextLoaded("Product Name,Unit\r\nWidget,Nos\r\n,Box\r\n"))
        dispatcher.scheduler.advanceUntilIdle()

        val preview = vm.uiState.value.importPreview!!
        assertEquals(1, preview.createCount)
        assertEquals(1, preview.skipCount)
        val skipped = preview.outcomes.filterIsInstance<com.jajusri.venture.feature.catalogue.domain.excel.CatalogueExcelRowOutcome.Skipped>().single()
        assertTrue(skipped.reason.contains("Product Name"))
    }

    @Test
    fun `an empty file previews to nothing, without crashing`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        val vm = viewModel(repository, FakeCompanySessionPort("co-1"))
        dispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(CatalogueEvent.ExcelFileTextLoaded(""))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, vm.uiState.value.importPreview?.outcomes?.size)
    }

    @Test
    fun `a duplicate column header is surfaced as a warning in the preview`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        val vm = viewModel(repository, FakeCompanySessionPort("co-1"))
        dispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(CatalogueEvent.ExcelFileTextLoaded("Product Name,Unit,Unit\r\nWidget,Nos,Box\r\n"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("Unit"), vm.uiState.value.importDuplicateHeaderWarnings)
    }

    @Test
    fun `a custom column is preserved as a distinct field, never reinterpreted as a native one`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        val vm = viewModel(repository, FakeCompanySessionPort("co-1"))
        dispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(CatalogueEvent.ExcelFileTextLoaded("Product Name,Unit,Warranty\r\nWidget,Nos,12 months\r\n"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(setOf("Warranty"), vm.uiState.value.importPreview?.customColumnNames)
    }

    @Test
    fun `confirming the import commits the previewed rows, clears the dialog, and refreshes the list`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        val vm = viewModel(repository, FakeCompanySessionPort("co-1"))
        dispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(CatalogueEvent.ExcelFileTextLoaded("Product Name,Unit\r\nWidget,Nos\r\n"))
        dispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(CatalogueEvent.ConfirmImport)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repository.products["co-1"]!!.size)
        assertEquals("Widget", repository.products["co-1"]!!.single().displayName)
        assertEquals(null, vm.uiState.value.importPreview)
        assertTrue(vm.uiState.value.shareMessage!!.contains("1 created"))
        assertEquals(listOf("Widget"), vm.uiState.value.products.map { it.displayName })
    }

    @Test
    fun `dismissing the import preview commits nothing`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        val vm = viewModel(repository, FakeCompanySessionPort("co-1"))
        dispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(CatalogueEvent.ExcelFileTextLoaded("Product Name,Unit\r\nWidget,Nos\r\n"))
        dispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(CatalogueEvent.DismissImportPreview)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(null, vm.uiState.value.importPreview)
        assertTrue(repository.products["co-1"].isNullOrEmpty())
    }

    @Test
    fun `import preview and commit are always scoped to the currently-selected company`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        val company = FakeCompanySessionPort("co-A")
        val vm = viewModel(repository, company)
        dispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(CatalogueEvent.ExcelFileTextLoaded("Product Name,Unit\r\nWidget,Nos\r\n"))
        dispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(CatalogueEvent.ConfirmImport)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repository.products["co-A"]!!.size)
        assertTrue("importing into co-A must never create a product under co-B", repository.products["co-B"].isNullOrEmpty())
    }

    @Test
    fun `ExportToExcel emits the generated CSV content ready for a destination pick`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        val vm = viewModel(repository, FakeCompanySessionPort("co-1"))
        dispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(CatalogueEvent.OpenAddManualDialog)
        vm.onEvent(CatalogueEvent.AddManualNameChanged("Widget"))
        vm.onEvent(CatalogueEvent.ConfirmAddManual)
        dispatcher.scheduler.advanceUntilIdle()

        val effects = mutableListOf<CatalogueEffect>()
        val job = launch(Dispatchers.Unconfined) { vm.effects.collect { effects.add(it) } }
        vm.onEvent(CatalogueEvent.ExportToExcel)
        dispatcher.scheduler.advanceUntilIdle()
        job.cancel()

        val ready = effects.filterIsInstance<CatalogueEffect.ExportCsvReady>().single()
        assertTrue(ready.csvText.contains("Widget"))
        assertEquals("catalogue-export.csv", ready.suggestedFileName)
        assertTrue(!vm.uiState.value.showMoreMenu)
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

internal class FakeCatalogueBranchSelectionStore : com.jajusri.venture.feature.catalogue.domain.port.CatalogueBranchSelectionStore {
    private val state = mutableMapOf<String, MutableStateFlow<String?>>()
    private fun flowFor(companyId: String) = state.getOrPut(companyId) { MutableStateFlow(null) }
    override fun observeSelectedBranchId(companyId: String): kotlinx.coroutines.flow.Flow<String?> = flowFor(companyId)
    override suspend fun setSelectedBranchId(companyId: String, branchId: String?) {
        flowFor(companyId).value = branchId
    }
    fun currentValue(companyId: String): String? = flowFor(companyId).value
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

private fun CatalogueProduct.toPublishedSnapshot() = CataloguePublishedSnapshot(
    companyId = companyId,
    productId = productId,
    displayName = displayName,
    description = description,
    specifications = specifications,
    customerFacingCategory = customerFacingCategory,
    priceDisplayMode = priceDisplayMode,
    resolvedPriceAmount = manualPriceAmount,
    resolvedPriceCurrencyCode = manualPriceCurrencyCode,
    primaryAssetId = null,
    publishedAt = updatedAt,
)

internal class FakeCatalogueShareCoordinator : com.jajusri.venture.feature.catalogue.sharing.CatalogueShareCoordinator {
    override suspend fun prepareShare(
        companyId: String,
        scope: com.jajusri.venture.feature.catalogue.sharing.CatalogueShareScope,
        businessName: String?,
    ) = com.jajusri.venture.feature.catalogue.sharing.CatalogueShareResult.Failure("not exercised in these tests")
    override fun createShareIntent(prepared: com.jajusri.venture.feature.catalogue.sharing.PreparedCatalogueShare) =
        error("not used in these tests")
    override fun releaseShare(prepared: com.jajusri.venture.feature.catalogue.sharing.PreparedCatalogueShare) = Unit
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

val unlinkedStockItems = mutableMapOf<String, MutableList<com.jajusri.venture.feature.masterdata.stockitem.domain.model.StockItem>>()

    override suspend fun createDraftFromStockItem(companyId: String, stockItemId: String, timestamp: CatalogueTimestamp): CatalogueProduct? {
        val list = unlinkedStockItems[companyId] ?: return null
        val stockItem = list.firstOrNull { it.id == stockItemId } ?: return null
        list.remove(stockItem)
        val product = sampleProduct(companyId = companyId, productId = "linked-${nextId++}", displayName = stockItem.name)
            .copy(
                source = com.jajusri.venture.feature.catalogue.domain.model.CatalogueProductSource.Tally,
                linkedStockItemId = stockItemId,
                tallyName = stockItem.name,
                unit = stockItem.baseUnit,
                hsnCode = stockItem.hsnCode,
                gstRate = stockItem.gstRate,
                stockGroupKey = stockItem.parentGroup,
            )
        products.getOrPut(companyId) { mutableListOf() }.add(product)
        return product
    }

    override suspend fun listUnlinkedStockItems(companyId: String): List<com.jajusri.venture.feature.masterdata.stockitem.domain.model.StockItem> =
        unlinkedStockItems[companyId].orEmpty()

    val warmStockItemCacheCalls = mutableListOf<String>()
    /** Lets a test simulate TD-050's "cold Room" scenario: mutate [unlinkedStockItems] here as if
     * [warmStockItemCache] were the real Stock Items snapshot pull populating it for the first time. */
    var onWarmStockItemCache: ((String) -> Unit)? = null
    override suspend fun warmStockItemCache(companyId: String) {
        warmStockItemCacheCalls += companyId
        onWarmStockItemCache?.invoke(companyId)
    }

    /** Small default so a handful of test items already exercise multiple "chunks" -- production
     * uses CATALOGUE_LINK_ALL_CHUNK_SIZE (200), this only needs to prove the ViewModel reacts
     * correctly to more than one onProgress call. */
    var linkAllChunkSize = 2
    /** If set, throws once cumulative [linked] reaches this count -- always *after* that chunk's
     * onProgress call, mirroring the real repository's "onProgress only fires for what already
     * committed" contract. */
    var failLinkAllAfterLinked: Int? = null

    override suspend fun createDraftsFromStockItems(
        companyId: String,
        stockItemIds: List<String>,
        timestamp: CatalogueTimestamp,
        onProgress: suspend (linked: Int, total: Int) -> Unit,
    ): Int {
        val total = stockItemIds.size
        var linked = 0
        onProgress(0, total)
        // A real suspension point between chunks (StandardTestDispatcher never runs a collector's
        // resumption until something here actually yields) -- without it every onProgress call
        // fires back-to-back in one synchronous burst and StateFlow's conflation means a collector
        // only ever observes the final value, never the intermediate ones a test needs to assert.
        kotlinx.coroutines.yield()
        for (chunk in stockItemIds.chunked(linkAllChunkSize)) {
            for (id in chunk) {
                if (createDraftFromStockItem(companyId, id, timestamp) != null) linked++
            }
            onProgress(linked, total)
            kotlinx.coroutines.yield()
            val failAt = failLinkAllAfterLinked
            if (failAt != null && linked >= failAt) error("simulated link-all failure")
        }
        return linked
    }

    val assetsByProduct = mutableMapOf<Pair<String, String>, MutableList<com.jajusri.venture.feature.catalogue.domain.model.CatalogueAsset>>()
    var nextAssetId = 0
    var addAssetFailure: com.jajusri.venture.feature.catalogue.storage.CatalogueAssetFailureReason? = null

    override suspend fun addAsset(
        companyId: String,
        productId: String,
        sourceUri: android.net.Uri,
        timestamp: CatalogueTimestamp,
    ): com.jajusri.venture.feature.catalogue.storage.CatalogueAssetResult {
        addAssetFailure?.let { return com.jajusri.venture.feature.catalogue.storage.CatalogueAssetResult.Failure(it) }
        val assetId = "asset-${nextAssetId++}"
        val list = assetsByProduct.getOrPut(companyId to productId) { mutableListOf() }
        list.add(
            com.jajusri.venture.feature.catalogue.domain.model.CatalogueAsset(
                companyId = companyId,
                productId = productId,
                assetId = assetId,
                isPrimary = list.isEmpty(),
                sortOrder = list.size,
                filePath = "$companyId/$productId/$assetId.jpg",
                createdAt = timestamp,
            ),
        )
        return com.jajusri.venture.feature.catalogue.storage.CatalogueAssetResult.Success(assetId, "$companyId/$productId/$assetId.jpg")
    }

    override suspend fun listAssets(companyId: String, productId: String): List<com.jajusri.venture.feature.catalogue.domain.model.CatalogueAsset> =
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

    /** Simulates a stored asset row whose file has since gone missing from disk -- the real
     * [com.jajusri.venture.feature.catalogue.storage.CatalogueAssetStore.resolveAssetFile] would
     * return `null` in exactly this situation too (file-existence check), never throw. */
    val brokenAssetFilePaths = mutableSetOf<String>()

    /** Mirrors the real [com.jajusri.venture.feature.catalogue.data.repository.CatalogueRepositoryImpl.resolveAssetFile]'s
     * company/product-scoped resolution: only returns a file for an asset that actually exists in
     * [assetsByProduct] under this exact `(companyId, productId)` pair -- never for another
     * company's/product's asset, and `null` (not a made-up file) whenever no such asset exists or
     * its path is listed in [brokenAssetFilePaths]. */
    override fun resolveAssetFile(companyId: String, productId: String, filePath: String): java.io.File? {
        if (filePath in brokenAssetFilePaths) return null
        val exists = assetsByProduct[companyId to productId]?.any { it.filePath == filePath } == true
        return if (exists) java.io.File(filePath) else null
    }

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
            displayNameOverride = when {
                update.clearDisplayNameOverride -> null
                update.displayNameOverride != null -> update.displayNameOverride
                else -> existing.displayNameOverride
            },
            sku = update.sku ?: existing.sku,
            // TD-047: mirrors CatalogueRepositoryImpl exactly -- only a Manual product's Unit is
            // ever writable via this path; a Tally-linked product's Unit stays Tally-authoritative.
            unit = if (existing.source == CatalogueProductSource.Manual) update.unit ?: existing.unit else existing.unit,
            description = update.description ?: existing.description,
            specifications = update.specifications ?: existing.specifications,
            customerFacingCategory = update.customerFacingCategory ?: existing.customerFacingCategory,
            priceDisplayMode = update.priceDisplayMode ?: existing.priceDisplayMode,
            manualPriceAmount = update.manualPriceAmount ?: existing.manualPriceAmount,
            manualPriceCurrencyCode = update.manualPriceCurrencyCode ?: existing.manualPriceCurrencyCode,
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
        val next = com.jajusri.venture.feature.catalogue.domain.model.CatalogueLifecycleTransitions.transition(
            existing.lifecycleState, action, isOwner,
        ) ?: return CatalogueLifecycleResult.Rejected
        val updated = existing.copy(lifecycleState = next)
        list[list.indexOf(existing)] = updated
        return CatalogueLifecycleResult.Success(updated)
    }

    var reconcileCallCount = 0
        private set
    override suspend fun reconcileStockItemLinks(companyId: String, timestamp: CatalogueTimestamp): CatalogueReconciliationResult {
        reconcileCallCount++
        return CatalogueReconciliationResult(emptyList(), emptyList())
    }

    /** Controllable stand-in for the real cheap freshness signal (default interface behavior is
     * "always different," i.e. every check reloads) -- tests that need to prove the resume-skip
     * fast path set this explicitly instead. */
    var changeSignal = com.jajusri.venture.feature.catalogue.domain.repository.CatalogueChangeSignal(localRevision = 0, stockItemFingerprint = "")
    var currentChangeSignalCallCount = 0
        private set
    override suspend fun currentChangeSignal(companyId: String) = changeSignal.also { currentChangeSignalCallCount++ }

    val branches = mutableMapOf<String, MutableList<Branch>>()

    override suspend fun upsertBranch(companyId: String, branchId: String, name: String, isActive: Boolean, timestamp: CatalogueTimestamp): Branch {
        val branch = Branch(companyId, branchId, name, isActive, timestamp, timestamp)
        val list = branches.getOrPut(companyId) { mutableListOf() }
        val existingIndex = list.indexOfFirst { it.branchId == branchId }
        if (existingIndex >= 0) list[existingIndex] = branch else list.add(branch)
        return branch
    }

    override suspend fun listBranches(companyId: String): List<Branch> = branches[companyId].orEmpty()

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

    override suspend fun listPublishedForCategory(companyId: String, category: String): List<CataloguePublishedSnapshot> =
        listAllPublished(companyId).filter { it.customerFacingCategory == category }

    override suspend fun listAllPublished(companyId: String): List<CataloguePublishedSnapshot> =
        products[companyId].orEmpty()
            .filter { it.lifecycleState == CatalogueLifecycleState.Published }
            .map { it.toPublishedSnapshot() }
    override suspend fun isPublic(companyId: String): Boolean = publicByCompany[companyId] ?: false
    override suspend fun setPublic(companyId: String, isPublic: Boolean, timestamp: CatalogueTimestamp) {
        publicByCompany[companyId] = isPublic
    }

    val customFields = mutableMapOf<Triple<String, String, String>, String?>()
    override suspend fun upsertCustomFields(companyId: String, productId: String, values: Map<String, String?>, timestamp: CatalogueTimestamp) {
        values.forEach { (column, value) -> customFields[Triple(companyId, productId, column)] = value }
    }
    override suspend fun listCustomFields(companyId: String, productId: String): Map<String, String?> =
        customFields.filterKeys { it.first == companyId && it.second == productId }.mapKeys { it.key.third }
    override suspend fun listAllCustomFieldColumnNames(companyId: String): List<String> =
        customFields.keys.filter { it.first == companyId }.map { it.third }.distinct()
}
