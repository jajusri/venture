package com.budcom.android.feature.catalogue.presentation

import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItem
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItemDataQuality
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItemStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class CatalogueStockItemPickerViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun stockItem(id: String, name: String) = StockItem(
        id = id, name = name, alias = null, parentGroup = "Finished Goods", category = null, baseUnit = "Nos",
        partNumber = null, hsnCode = null, gstRate = null, status = StockItemStatus.Active, closingBalance = null,
        dataQuality = StockItemDataQuality.Complete, syncedAt = "t",
    )

    private fun viewModel(repository: FakeCatalogueRepository, companyId: String? = "co-1") =
        CatalogueStockItemPickerViewModel(repository, FakeCompanySessionPort(companyId), FakeCatalogueClock())

    @Test
    fun `loads every unlinked Stock Item for the current company`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        repository.unlinkedStockItems["co-1"] = mutableListOf(stockItem("guid:a", "Widget A"), stockItem("guid:b", "Widget B"))

        val vm = viewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("Widget A", "Widget B"), vm.uiState.value.allItems.map { it.name })
        assertTrue(!vm.uiState.value.isLoading)
    }

    @Test
    fun `search filters the list case-insensitively without touching the underlying data`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        repository.unlinkedStockItems["co-1"] = mutableListOf(stockItem("guid:a", "Widget A"), stockItem("guid:b", "Gadget B"))
        val vm = viewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(CatalogueStockItemPickerEvent.SearchChanged("widget"))

        assertEquals(listOf("Widget A"), vm.uiState.value.filteredItems.map { it.name })
        assertEquals(2, vm.uiState.value.allItems.size)
    }

    @Test
    fun `picking a Stock Item creates a Draft and emits its productId`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        repository.unlinkedStockItems["co-1"] = mutableListOf(stockItem("guid:a", "Widget A"))
        val vm = viewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        val linked = mutableListOf<String>()
        val job = launch { vm.linked.collect { linked.add(it) } }
        vm.onEvent(CatalogueStockItemPickerEvent.Pick("guid:a"))
        dispatcher.scheduler.advanceUntilIdle()
        job.cancel()

        assertEquals(1, linked.size)
        assertEquals(1, repository.products["co-1"]?.size)
        assertEquals("Widget A", repository.products["co-1"]!!.single().displayName)
    }

    @Test
    fun `an already-linked Stock Item never appears again after being picked`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        repository.unlinkedStockItems["co-1"] = mutableListOf(stockItem("guid:a", "Widget A"), stockItem("guid:b", "Widget B"))
        val vm = viewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(CatalogueStockItemPickerEvent.Pick("guid:a"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("guid:b"), repository.unlinkedStockItems["co-1"]!!.map { it.id })
    }

    @Test
    fun `no company selected yields an empty, non-loading list rather than crashing`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        val vm = viewModel(repository, companyId = null)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(!vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.allItems.isEmpty())
    }

    @Test
    fun `OpenLinkAllConfirmation shows a confirmation before anything is linked`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        repository.unlinkedStockItems["co-1"] = mutableListOf(stockItem("guid:a", "Widget A"))
        val vm = viewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(CatalogueStockItemPickerEvent.OpenLinkAllConfirmation)

        assertTrue(vm.uiState.value.showLinkAllConfirmation)
        assertEquals(1, repository.unlinkedStockItems["co-1"]!!.size)
    }

    @Test
    fun `DismissLinkAllConfirmation closes the dialog without linking anything`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        repository.unlinkedStockItems["co-1"] = mutableListOf(stockItem("guid:a", "Widget A"))
        val vm = viewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(CatalogueStockItemPickerEvent.OpenLinkAllConfirmation)

        vm.onEvent(CatalogueStockItemPickerEvent.DismissLinkAllConfirmation)

        assertTrue(!vm.uiState.value.showLinkAllConfirmation)
        assertEquals(1, repository.unlinkedStockItems["co-1"]!!.size)
    }

    @Test
    fun `ConfirmLinkAll creates a Draft for every unlinked stock item and reports the count`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        repository.unlinkedStockItems["co-1"] = mutableListOf(
            stockItem("guid:a", "Widget A"), stockItem("guid:b", "Widget B"), stockItem("guid:c", "Widget C"),
        )
        val vm = viewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        val linkedAllCounts = mutableListOf<Int>()
        val job = launch { vm.linkedAll.collect { linkedAllCounts.add(it) } }
        vm.onEvent(CatalogueStockItemPickerEvent.OpenLinkAllConfirmation)
        vm.onEvent(CatalogueStockItemPickerEvent.ConfirmLinkAll)
        dispatcher.scheduler.advanceUntilIdle()
        job.cancel()

        assertEquals(listOf(3), linkedAllCounts)
        assertEquals(3, repository.products["co-1"]?.size)
        assertEquals(setOf("Widget A", "Widget B", "Widget C"), repository.products["co-1"]!!.map { it.displayName }.toSet())
        assertTrue(vm.uiState.value.allItems.isEmpty())
        assertTrue(!vm.uiState.value.isLinking)
        assertTrue(!vm.uiState.value.showLinkAllConfirmation)
    }

    @Test
    fun `ConfirmLinkAll with nothing to link does not emit or crash`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        val vm = viewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        val linkedAllCounts = mutableListOf<Int>()
        val job = launch { vm.linkedAll.collect { linkedAllCounts.add(it) } }
        vm.onEvent(CatalogueStockItemPickerEvent.ConfirmLinkAll)
        dispatcher.scheduler.advanceUntilIdle()
        job.cancel()

        assertTrue(linkedAllCounts.isEmpty())
    }

    // ============================== TD-050: Room warm-up before linking ==============================

    @Test
    fun `the Stock Item cache is warmed for the current company before the picker lists unlinked items`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        repository.unlinkedStockItems["co-1"] = mutableListOf(stockItem("guid:a", "Widget A"))
        val vm = viewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("co-1"), repository.warmStockItemCacheCalls)
        assertEquals(listOf("Widget A"), vm.uiState.value.allItems.map { it.name })
    }

    @Test
    fun `cold Room for a fresh company is warmed before the picker lists unlinked items`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        // Room starts cold for this company -- nothing cached locally yet, exactly TD-050's
        // "user never opened the Stock Items browser" scenario. onWarmStockItemCache simulates
        // what the real StockItemRepositoryImpl.loadStockItems()/warmFullSnapshot() pull does.
        repository.onWarmStockItemCache = { companyId ->
            repository.unlinkedStockItems[companyId] = mutableListOf(stockItem("guid:a", "Widget A"))
        }
        val vm = viewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("co-1"), repository.warmStockItemCacheCalls)
        assertEquals(
            "the picker must see the just-warmed items, not whatever was cached (nothing) before the warm-up",
            listOf("Widget A"),
            vm.uiState.value.allItems.map { it.name },
        )
        assertTrue(!vm.uiState.value.isLoading)
    }

    @Test
    fun `no company selected never triggers a warm-up call`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        viewModel(repository, companyId = null)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(repository.warmStockItemCacheCalls.isEmpty())
    }

    // ============================== TD-051: Link-all progress reporting ==============================

    @Test
    fun `ConfirmLinkAll reports progress from 0 through every intermediate chunk up to the final count`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        repository.linkAllChunkSize = 2
        repository.unlinkedStockItems["co-1"] = mutableListOf(
            stockItem("guid:a", "A"), stockItem("guid:b", "B"), stockItem("guid:c", "C"),
            stockItem("guid:d", "D"), stockItem("guid:e", "E"),
        )
        val vm = viewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        val progressSnapshots = mutableListOf<LinkAllProgress?>()
        val job = launch { vm.uiState.collect { progressSnapshots += it.linkAllProgress } }
        vm.onEvent(CatalogueStockItemPickerEvent.OpenLinkAllConfirmation)
        vm.onEvent(CatalogueStockItemPickerEvent.ConfirmLinkAll)
        dispatcher.scheduler.advanceUntilIdle()
        job.cancel()

        // chunkSize=2 over 5 items -> chunks of [2, 2, 1]: progress goes 0 -> 2 -> 4 -> 5, out of 5.
        val nonNullProgress = progressSnapshots.filterNotNull()
        assertEquals(listOf(0, 2, 4, 5), nonNullProgress.map { it.linked })
        assertTrue("every progress snapshot must report the same total", nonNullProgress.all { it.total == 5 })
        // The final emitted uiState clears progress back to null once the run completes.
        assertEquals(null, vm.uiState.value.linkAllProgress)
        assertTrue(!vm.uiState.value.isLinking)
    }

    @Test
    fun `a link-all failure mid-run still stops isLinking, clears progress, and reports only the truly-linked count`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        repository.linkAllChunkSize = 2
        repository.failLinkAllAfterLinked = 2 // fails right after the first chunk of 2 commits
        repository.unlinkedStockItems["co-1"] = mutableListOf(
            stockItem("guid:a", "A"), stockItem("guid:b", "B"),
            stockItem("guid:c", "C"), stockItem("guid:d", "D"),
        )
        val vm = viewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        val linkedAllCounts = mutableListOf<Int>()
        val job = launch { vm.linkedAll.collect { linkedAllCounts.add(it) } }
        vm.onEvent(CatalogueStockItemPickerEvent.OpenLinkAllConfirmation)
        vm.onEvent(CatalogueStockItemPickerEvent.ConfirmLinkAll)
        dispatcher.scheduler.advanceUntilIdle()
        job.cancel()

        assertEquals(
            "the emitted count must equal what the repository actually reported as linked, never an overcount",
            listOf(2),
            linkedAllCounts,
        )
        assertEquals(2, repository.products["co-1"]?.size)
        assertTrue("must not hang in the linking state after a failure", !vm.uiState.value.isLinking)
        assertEquals("progress must be cleared, not left showing a stale in-flight value", null, vm.uiState.value.linkAllProgress)
        // The 2 items the first chunk actually linked must no longer show up as unlinked.
        assertEquals(2, vm.uiState.value.allItems.size)
    }
}
