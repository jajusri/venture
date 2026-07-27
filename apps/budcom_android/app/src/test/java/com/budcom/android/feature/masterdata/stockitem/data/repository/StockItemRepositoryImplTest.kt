package com.budcom.android.feature.masterdata.stockitem.data.repository

import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.network.ApiResult
import com.budcom.android.core.network.ErrorMapper
import com.budcom.android.core.network.NetworkError
import com.budcom.android.core.util.DispatcherProvider
import com.budcom.android.feature.masterdata.domain.model.MasterDataPagination
import com.budcom.android.feature.masterdata.stockitem.data.remote.StockItemRemoteDataSource
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItem
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItemDataQuality
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItemPage
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItemQuery
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItemStatus
import kotlinx.coroutines.CoroutineDispatcher
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
    fun `maps success page`() = runTest(dispatcher) {
        val remote = FakeRemote(
            ApiResult.Success(
                StockItemPage(
                    items = listOf(sampleItem()),
                    pagination = MasterDataPagination(1, 50, 1, 1),
                    dataFreshnessAt = "t",
                ),
            ),
        )
        val repo = StockItemRepositoryImpl(remote, errorMapper, dispatchers)
        val result = repo.loadStockItems(StockItemQuery()) as AppResult.Success
        assertEquals(1, result.value.items.size)
        assertEquals("guid:widget", result.value.items[0].id)
    }

    @Test
    fun `maps offline failure`() = runTest(dispatcher) {
        val remote = FakeRemote(ApiResult.Failure(NetworkError.NoConnectivity))
        val repo = StockItemRepositoryImpl(remote, errorMapper, dispatchers)
        val result = repo.loadStockItems(StockItemQuery()) as AppResult.Failure
        assertTrue(result.error is AppError.Offline)
    }

    private class FakeRemote(
        private val result: ApiResult<StockItemPage>,
    ) : StockItemRemoteDataSource {
        override suspend fun fetchStockItems(query: StockItemQuery): ApiResult<StockItemPage> = result
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
