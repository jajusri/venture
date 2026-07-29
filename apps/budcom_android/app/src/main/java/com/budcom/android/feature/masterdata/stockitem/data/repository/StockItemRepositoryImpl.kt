package com.budcom.android.feature.masterdata.stockitem.data.repository

import com.budcom.android.core.common.AppResult
import com.budcom.android.core.network.ApiResult
import com.budcom.android.core.network.ErrorMapper
import com.budcom.android.core.util.DispatcherProvider
import com.budcom.android.feature.company.data.repository.SelectedCompanyStore
import com.budcom.android.feature.masterdata.domain.MasterDataCacheDefaults
import com.budcom.android.feature.masterdata.stockitem.data.local.StockItemLocalDataSource
import com.budcom.android.feature.masterdata.stockitem.data.remote.StockItemRemoteDataSource
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItem
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItemPage
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItemQuery
import com.budcom.android.feature.masterdata.stockitem.domain.repository.StockItemRepository
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StockItemRepositoryImpl @Inject constructor(
    private val remoteDataSource: StockItemRemoteDataSource,
    private val localDataSource: StockItemLocalDataSource,
    private val selectedCompanyStore: SelectedCompanyStore,
    private val errorMapper: ErrorMapper,
    private val dispatchers: DispatcherProvider,
) : StockItemRepository {

    override suspend fun loadStockItems(query: StockItemQuery): AppResult<StockItemPage> =
        withContext(dispatchers.io) {
            when (val result = remoteDataSource.fetchStockItems(query)) {
                is ApiResult.Success -> {
                    persistSuccessfulPage(query, result.data)
                    AppResult.Success(result.data)
                }

                is ApiResult.Failure -> {
                    val companyId = selectedCompanyStore.getSelectedCompanyId()
                    if (!companyId.isNullOrBlank()) {
                        val cached = localDataSource.query(companyId, query)
                        if (cached != null) {
                            return@withContext AppResult.Success(cached)
                        }
                    }
                    AppResult.Failure(errorMapper.toAppError(result.error))
                }
            }
        }

    private suspend fun persistSuccessfulPage(query: StockItemQuery, page: StockItemPage) {
        val companyId = selectedCompanyStore.getSelectedCompanyId() ?: return
        if (query.text.isNullOrBlank() && query.page == 1) {
            warmFullSnapshot(companyId, page)
        } else {
            localDataSource.upsert(companyId, page.items, page.dataFreshnessAt)
        }
    }

    private suspend fun warmFullSnapshot(companyId: String, firstPage: StockItemPage) {
        val collected = firstPage.items.toMutableList()
        var freshness = firstPage.dataFreshnessAt
        val pageSize = firstPage.pagination.pageSize.coerceIn(1, MasterDataCacheDefaults.SNAPSHOT_PAGE_SIZE)
        val totalPages = firstPage.pagination.totalPages.coerceAtMost(MasterDataCacheDefaults.MAX_SNAPSHOT_PAGES)

        for (pageNum in 2..totalPages) {
            when (
                val next = remoteDataSource.fetchStockItems(
                    StockItemQuery(page = pageNum, pageSize = pageSize),
                )
            ) {
                is ApiResult.Success -> {
                    collected.addAll(next.data.items)
                    freshness = next.data.dataFreshnessAt ?: freshness
                }

                is ApiResult.Failure -> {
                    localDataSource.upsert(companyId, collected.distinctById(), freshness)
                    return
                }
            }
        }

        localDataSource.replaceAll(companyId, collected.distinctById(), freshness)
    }
}

private fun List<StockItem>.distinctById(): List<StockItem> = distinctBy { it.id }
