package com.budcom.android.feature.masterdata.stockitem.data.remote

import com.budcom.android.core.network.ApiResult
import com.budcom.android.core.network.ErrorMapper
import com.budcom.android.core.network.NetworkConnectivityObserver
import com.budcom.android.core.network.RetryPolicy
import com.budcom.android.core.network.safeApiCall
import com.budcom.android.core.network.withRetry
import com.budcom.android.feature.masterdata.domain.MasterDataBrowserDefaults
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItemPage
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItemQuery
import javax.inject.Inject
import javax.inject.Singleton

interface StockItemRemoteDataSource {
    suspend fun fetchStockItems(query: StockItemQuery): ApiResult<StockItemPage>
}

@Singleton
class DefaultStockItemRemoteDataSource @Inject constructor(
    private val api: StockItemApi,
    private val errorMapper: ErrorMapper,
    private val connectivityObserver: NetworkConnectivityObserver,
) : StockItemRemoteDataSource {

    override suspend fun fetchStockItems(query: StockItemQuery): ApiResult<StockItemPage> =
        // Master-data GETs fail over to Room; avoid multi-attempt delays when Connector is down.
        withRetry(RetryPolicy.None) {
            safeApiCall(errorMapper, connectivityObserver) {
                api.getStockItems(
                    query = query.normalizedText(),
                    page = query.page.coerceAtLeast(1),
                    pageSize = query.pageSize.coerceIn(1, MasterDataBrowserDefaults.MAX_PAGE_SIZE),
                    sortBy = query.toApiSortBy(),
                    sortDirection = query.toApiSortDirection(),
                ).toDomain()
            }
        }
}
