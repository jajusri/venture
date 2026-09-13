package com.jajusri.venture.feature.masterdata.stockitem.data.remote

import com.jajusri.venture.core.network.ApiResult
import com.jajusri.venture.core.network.ErrorMapper
import com.jajusri.venture.core.network.NetworkConnectivityObserver
import com.jajusri.venture.core.network.RetryPolicy
import com.jajusri.venture.core.network.safeApiCall
import com.jajusri.venture.core.network.withRetry
import com.jajusri.venture.feature.masterdata.domain.MasterDataBrowserDefaults
import com.jajusri.venture.feature.masterdata.stockitem.domain.model.StockItemPage
import com.jajusri.venture.feature.masterdata.stockitem.domain.model.StockItemQuery
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
