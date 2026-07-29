package com.budcom.android.feature.masterdata.stockitem.data.repository

import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.network.ApiResult
import com.budcom.android.core.network.ErrorMapper
import com.budcom.android.core.network.NetworkError
import com.budcom.android.core.util.DispatcherProvider
import com.budcom.android.feature.company.data.repository.SelectedCompanyStore
import com.budcom.android.feature.masterdata.domain.model.MasterDataPagination
import com.budcom.android.feature.masterdata.stockitem.data.local.StockItemLocalDataSource
import com.budcom.android.feature.masterdata.stockitem.data.remote.StockItemRemoteDataSource
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItem
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItemDataQuality
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItemPage
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItemQuery
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItemStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StockItemRepositoryImplTest {
    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = dispatcher
        override val io: CoroutineDispatcher = dispatcher
        override val default: CoroutineDispatcher = dispatcher
    }
    private val errorMapper = object : ErrorMapper {
        override fun toNetworkError(throwable: Throwable): NetworkError = NetworkError.Unknown()
        override fun toAppError(error: NetworkError): AppError = when (error) {
            is NetworkError.NoConnectivity -> AppError.Offline()
            is NetworkError.Timeout -> AppError.Timeout()
            is NetworkError.Http -> AppError.Remote(error.httpStatus, error.code, error.message)
            is NetworkError.Serialization -> AppError.Serialization(error.message)
            is NetworkError.Unknown -> AppError.Unexpected(IllegalStateException(error.message))
        }
    }

    @Test
    fun `maps success page and replaces cache`() = runTest(dispatcher) {
        val local = FakeStockLocal()
        val remote = FakeRemote(
            ApiResult.Success(
                StockItemPage(
                    items = listOf(sampleItem()),
                    pagination = MasterDataPagination(1, 50, 1, 1),
                    dataFreshnessAt = "t",
                ),
            ),
        )
        val repo = StockItemRepositoryImpl(
            remote,
            local,
            FakeSelectedCompanyStore("co-1"),
            errorMapper,
            dispatchers,
        )
        val result = repo.loadStockItems(StockItemQuery()) as AppResult.Success
        assertEquals(1, result.value.items.size)
        assertEquals(1, local.replaceCount)
    }

    @Test
    fun `offline without cache maps failure`() = runTest(dispatcher) {
        val repo = StockItemRepositoryImpl(
            FakeRemote(ApiResult.Failure(NetworkError.NoConnectivity)),
            FakeStockLocal(),
            FakeSelectedCompanyStore("co-1"),
            errorMapper,
            dispatchers,
        )
        val result = repo.loadStockItems(StockItemQuery()) as AppResult.Failure
        assertTrue(result.error is AppError.Offline)
    }

    @Test
    fun `offline with cache returns cached page`() = runTest(dispatcher) {
        val local = FakeStockLocal().apply {
            stored["co-1"] = mutableListOf(sampleItem())
        }
        val repo = StockItemRepositoryImpl(
            FakeRemote(ApiResult.Failure(NetworkError.NoConnectivity)),
            local,
            FakeSelectedCompanyStore("co-1"),
            errorMapper,
            dispatchers,
        )
        val result = repo.loadStockItems(StockItemQuery()) as AppResult.Success
        assertEquals("guid:widget", result.value.items.single().id)
    }

    private class FakeRemote(
        private val result: ApiResult<StockItemPage>,
    ) : StockItemRemoteDataSource {
        override suspend fun fetchStockItems(query: StockItemQuery): ApiResult<StockItemPage> = result
    }

    private class FakeStockLocal : StockItemLocalDataSource {
        val stored = mutableMapOf<String, MutableList<StockItem>>()
        var replaceCount = 0

        override suspend fun hasCache(companyId: String): Boolean = stored[companyId]?.isNotEmpty() == true

        override suspend fun upsert(companyId: String, items: List<StockItem>, dataFreshnessAt: String?) {
            val bucket = stored.getOrPut(companyId) { mutableListOf() }
            items.forEach { item ->
                bucket.removeAll { it.id == item.id }
                bucket.add(item)
            }
        }

        override suspend fun replaceAll(companyId: String, items: List<StockItem>, dataFreshnessAt: String?) {
            replaceCount++
            stored[companyId] = items.toMutableList()
        }

        override suspend fun query(companyId: String, query: StockItemQuery): StockItemPage? {
            val items = stored[companyId] ?: return null
            if (items.isEmpty()) return null
            return StockItemPage(
                items = items,
                pagination = MasterDataPagination(1, query.pageSize, items.size, 1),
                dataFreshnessAt = "cached",
            )
        }
    }

    private class FakeSelectedCompanyStore(
        initial: String?,
    ) : SelectedCompanyStore {
        private val state = MutableStateFlow(initial)
        override fun observeSelectedCompanyId(): Flow<String?> = state
        override suspend fun getSelectedCompanyId(): String? = state.value
        override suspend fun saveSelectedCompanyId(companyId: String) {
            state.value = companyId
        }
        override suspend fun clearSelectedCompanyId() {
            state.value = null
        }
    }

    private fun sampleItem() = StockItem(
        id = "guid:widget",
        name = "Widget",
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
}
