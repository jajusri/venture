package com.jajusri.venture.feature.masterdata.stockitem.data.repository

import com.jajusri.venture.core.common.AppError
import com.jajusri.venture.core.common.AppResult
import com.jajusri.venture.core.connectorauth.domain.ConnectorTransportSelection
import com.jajusri.venture.core.connectorauth.domain.ConnectorTransportSelectionGate
import com.jajusri.venture.core.network.ApiResult
import com.jajusri.venture.core.network.ErrorMapper
import com.jajusri.venture.core.util.DispatcherProvider
import com.jajusri.venture.feature.company.data.repository.SelectedCompanyStore
import com.jajusri.venture.feature.masterdata.domain.MasterDataCacheDefaults
import com.jajusri.venture.feature.masterdata.stockitem.data.local.StockItemLocalDataSource
import com.jajusri.venture.feature.masterdata.stockitem.data.remote.AuthenticatedStockItemRemoteDataSource
import com.jajusri.venture.feature.masterdata.stockitem.data.remote.StockItemRemoteDataSource
import com.jajusri.venture.feature.masterdata.stockitem.domain.model.StockItem
import com.jajusri.venture.feature.masterdata.stockitem.domain.model.StockItemPage
import com.jajusri.venture.feature.masterdata.stockitem.domain.model.StockItemQuery
import com.jajusri.venture.feature.masterdata.stockitem.domain.repository.StockItemRepository
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
    private val transportGate: ConnectorTransportSelectionGate,
    private val authenticatedRemoteDataSource: AuthenticatedStockItemRemoteDataSource,
) : StockItemRepository {

    override suspend fun loadStockItems(query: StockItemQuery): AppResult<StockItemPage> =
        withContext(dispatchers.io) {
            // Captured once per call, before either transport is chosen or invoked, so a company
            // switch that lands while this request is in flight can never mislabel that request's
            // response (or any later page fetched while warming a full snapshot) under the newly
            // selected company. The Connector itself is never told which company this request is
            // for — that is established separately by the already-cut-over company/session
            // repository — this is purely the local label used when persisting the response.
            val companyId = selectedCompanyStore.getSelectedCompanyId()
            val selection = transportGate.resolve()
            val fetchPage: suspend (StockItemQuery) -> AppResult<StockItemPage> = when (selection) {
                ConnectorTransportSelection.LEGACY -> { q -> remoteDataSource.fetchStockItems(q).toAppResult(errorMapper) }
                ConnectorTransportSelection.AUTHENTICATED -> { q -> authenticatedRemoteDataSource.fetchStockItems(q) }
            }

            when (val result = fetchPage(query)) {
                is AppResult.Success -> {
                    persistSuccessfulPage(companyId, query, result.value, fetchPage)
                    AppResult.Success(result.value)
                }

                is AppResult.Failure -> {
                    // An authentication rejection must remain visible, never silently masked by a
                    // stale-cache fallback — every other authenticated failure, and every legacy
                    // failure, degrades to cached rows exactly as before.
                    if (selection == ConnectorTransportSelection.AUTHENTICATED && result.error.isAuthenticationRejection()) {
                        result
                    } else {
                        val cached = companyId?.takeIf { it.isNotBlank() }?.let { localDataSource.query(it, query) }
                        cached?.let { AppResult.Success(it) } ?: result
                    }
                }
            }
        }

    private suspend fun persistSuccessfulPage(
        companyId: String?,
        query: StockItemQuery,
        page: StockItemPage,
        fetchPage: suspend (StockItemQuery) -> AppResult<StockItemPage>,
    ) {
        val id = companyId ?: return
        if (query.text.isNullOrBlank() && query.page == 1) {
            warmFullSnapshot(id, page, fetchPage)
        } else {
            localDataSource.upsert(id, page.items, page.dataFreshnessAt)
        }
    }

    /**
     * Fetches remaining unfiltered pages — through whichever transport [selection] chose for the
     * first page, never the other one — and atomically replaces the company stock-item cache only
     * when every page succeeds. Partial failure upserts accumulated rows without clearing.
     */
    private suspend fun warmFullSnapshot(
        companyId: String,
        firstPage: StockItemPage,
        fetchPage: suspend (StockItemQuery) -> AppResult<StockItemPage>,
    ) {
        val collected = firstPage.items.toMutableList()
        var freshness = firstPage.dataFreshnessAt
        val pageSize = firstPage.pagination.pageSize.coerceIn(1, MasterDataCacheDefaults.SNAPSHOT_PAGE_SIZE)
        val totalPages = firstPage.pagination.totalPages.coerceAtMost(MasterDataCacheDefaults.MAX_SNAPSHOT_PAGES)

        for (pageNum in 2..totalPages) {
            when (val next = fetchPage(StockItemQuery(page = pageNum, pageSize = pageSize))) {
                is AppResult.Success -> {
                    collected.addAll(next.value.items)
                    freshness = next.value.dataFreshnessAt ?: freshness
                }

                is AppResult.Failure -> {
                    localDataSource.upsert(companyId, collected.distinctById(), freshness)
                    return
                }
            }
        }

        localDataSource.replaceAll(companyId, collected.distinctById(), freshness)
    }
}

private fun List<StockItem>.distinctById(): List<StockItem> = distinctBy { it.id }

private fun <T> ApiResult<T>.toAppResult(errorMapper: ErrorMapper): AppResult<T> = when (this) {
    is ApiResult.Success -> AppResult.Success(data)
    is ApiResult.Failure -> AppResult.Failure(errorMapper.toAppError(error))
}

/** True only for the two outcomes [com.jajusri.venture.core.connectorauth.domain.AuthenticatedRepositoryFailurePolicy]
 * produces from an authentication rejection (401/403) — see its `AUTHENTICATED_*_CODE` constants. */
private fun AppError.isAuthenticationRejection(): Boolean =
    this is AppError.Remote && (
        code == com.jajusri.venture.core.connectorauth.domain.AUTHENTICATED_SECURE_PAIRING_REQUIRED_CODE ||
            code == com.jajusri.venture.core.connectorauth.domain.AUTHENTICATED_ACCESS_DENIED_CODE
        )
