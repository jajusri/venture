package com.budcom.android.feature.masterdata.stockitem.presentation

import androidx.lifecycle.SavedStateHandle
import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.network.NetworkConnectivityObserver
import com.budcom.android.feature.masterdata.domain.model.MasterDataPagination
import com.budcom.android.feature.masterdata.presentation.MasterDataUiError
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItem
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItemDataQuality
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItemPage
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItemQuery
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItemStatus
import com.budcom.android.feature.masterdata.stockitem.domain.repository.StockItemRepository
import com.budcom.android.feature.masterdata.stockitem.domain.usecase.LoadStockItemsUseCase
import com.budcom.android.feature.masterdata.stockitem.domain.usecase.RefreshStockItemsUseCase
import com.budcom.android.navigation.Routes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StockItemBrowserViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeStockItemRepository
    private lateinit var connectivity: FakeConnectivity

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeStockItemRepository()
        connectivity = FakeConnectivity(true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createVm(query: String = "") = StockItemBrowserViewModel(
        savedStateHandle = SavedStateHandle(mapOf(Routes.QUERY_ARG to query)),
        loadStockItems = LoadStockItemsUseCase(repository),
        refreshStockItems = RefreshStockItemsUseCase(repository),
        connectivityObserver = connectivity,
    )

    @Test
    fun `loads stock items on start`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isInitialLoading)
        assertEquals(1, vm.uiState.value.stockItems.size)
        assertEquals("Widget", vm.uiState.value.stockItems[0].primaryLabel)
    }

    @Test
    fun `empty result`() = runTest(dispatcher) {
        repository.result = AppResult.Success(
            StockItemPage(emptyList(), MasterDataPagination(1, 50, 0, 0), null),
        )
        val vm = createVm()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.stockItems.isEmpty())
        assertEquals(null, vm.uiState.value.error)
    }

    @Test
    fun `offline error without content`() = runTest(dispatcher) {
        repository.result = AppResult.Failure(AppError.Offline())
        val vm = createVm()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.error is MasterDataUiError.Offline)
    }

    @Test
    fun `refresh retains content on failure`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.stockItems.size)
        repository.result = AppResult.Failure(AppError.Timeout())
        vm.onEvent(StockItemBrowserEvent.Refresh)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.stockItems.size)
        assertTrue(vm.uiState.value.error is MasterDataUiError.Timeout)
    }

    @Test
    fun `search passes query to repository`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        vm.onEvent(StockItemBrowserEvent.SearchChanged("widget"))
        advanceUntilIdle()
        assertEquals("widget", repository.lastQuery?.text)
    }

    @Test
    fun `load next page appends`() = runTest(dispatcher) {
        repository.result = AppResult.Success(
            StockItemPage(
                items = listOf(sampleItem("1", "A")),
                pagination = MasterDataPagination(1, 1, 2, 2),
                dataFreshnessAt = null,
            ),
        )
        val vm = createVm()
        advanceUntilIdle()
        repository.result = AppResult.Success(
            StockItemPage(
                items = listOf(sampleItem("2", "B")),
                pagination = MasterDataPagination(2, 1, 2, 2),
                dataFreshnessAt = null,
            ),
        )
        vm.onEvent(StockItemBrowserEvent.LoadNextPage)
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.stockItems.size)
        assertFalse(vm.uiState.value.canLoadMore)
    }
}

private class FakeStockItemRepository : StockItemRepository {
    var result: AppResult<StockItemPage> = AppResult.Success(
        StockItemPage(
            items = listOf(sampleItem("guid:widget", "Widget")),
            pagination = MasterDataPagination(1, 50, 1, 1),
            dataFreshnessAt = "t",
        ),
    )
    var lastQuery: StockItemQuery? = null

    override suspend fun loadStockItems(query: StockItemQuery): AppResult<StockItemPage> {
        lastQuery = query
        return result
    }
}

private class FakeConnectivity(online: Boolean) : NetworkConnectivityObserver {
    private val flow = MutableStateFlow(online)
    override val isOnline: Flow<Boolean> = flow
    override fun current(): Boolean = flow.value
}

private fun sampleItem(id: String, name: String) = StockItem(
    id = id,
    name = name,
    alias = null,
    parentGroup = "Primary",
    category = null,
    baseUnit = "Nos",
    partNumber = null,
    hsnCode = null,
    gstRate = null,
    status = StockItemStatus.Active,
    closingBalance = null,
    dataQuality = StockItemDataQuality.Complete,
    syncedAt = "t",
)
