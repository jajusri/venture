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
}
