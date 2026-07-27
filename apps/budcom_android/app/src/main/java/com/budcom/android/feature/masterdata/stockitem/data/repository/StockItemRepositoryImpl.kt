package com.budcom.android.feature.masterdata.stockitem.data.repository

import com.budcom.android.core.common.AppResult
import com.budcom.android.core.network.ApiResult
import com.budcom.android.core.network.ErrorMapper
import com.budcom.android.core.util.DispatcherProvider
import com.budcom.android.feature.masterdata.stockitem.data.remote.StockItemRemoteDataSource
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItemPage
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItemQuery
import com.budcom.android.feature.masterdata.stockitem.domain.repository.StockItemRepository
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StockItemRepositoryImpl @Inject constructor(
    private val remoteDataSource: StockItemRemoteDataSource,
    private val errorMapper: ErrorMapper,
    private val dispatchers: DispatcherProvider,
) : StockItemRepository {

    override suspend fun loadStockItems(query: StockItemQuery): AppResult<StockItemPage> =
        withContext(dispatchers.io) {
            when (val result = remoteDataSource.fetchStockItems(query)) {
                is ApiResult.Success -> AppResult.Success(result.data)
                is ApiResult.Failure -> AppResult.Failure(errorMapper.toAppError(result.error))
            }
        }
}
