package com.budcom.android.feature.masterdata.ledger.data.repository

import com.budcom.android.core.common.AppResult
import com.budcom.android.core.network.ApiResult
import com.budcom.android.core.network.ErrorMapper
import com.budcom.android.core.util.DispatcherProvider
import com.budcom.android.feature.company.data.repository.SelectedCompanyStore
import com.budcom.android.feature.masterdata.domain.MasterDataCacheDefaults
import com.budcom.android.feature.masterdata.ledger.data.local.LedgerLocalDataSource
import com.budcom.android.feature.masterdata.ledger.data.remote.LedgerRemoteDataSource
import com.budcom.android.feature.masterdata.ledger.domain.model.Ledger
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerPage
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerQuery
import com.budcom.android.feature.masterdata.ledger.domain.repository.LedgerRepository
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LedgerRepositoryImpl @Inject constructor(
    private val remoteDataSource: LedgerRemoteDataSource,
    private val localDataSource: LedgerLocalDataSource,
    private val selectedCompanyStore: SelectedCompanyStore,
    private val errorMapper: ErrorMapper,
    private val dispatchers: DispatcherProvider,
) : LedgerRepository {

    override suspend fun loadLedgers(query: LedgerQuery): AppResult<LedgerPage> =
        withContext(dispatchers.io) {
            when (val result = remoteDataSource.fetchLedgers(query)) {
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

    private suspend fun persistSuccessfulPage(query: LedgerQuery, page: LedgerPage) {
        val companyId = selectedCompanyStore.getSelectedCompanyId() ?: return
        if (query.text.isNullOrBlank() && query.page == 1) {
            warmFullSnapshot(companyId, page)
        } else {
            localDataSource.upsert(companyId, page.items, page.dataFreshnessAt)
        }
    }

    /**
     * Fetches remaining unfiltered pages and atomically replaces the company ledger cache
     * only when every page succeeds. Partial failure upserts accumulated rows without clearing.
     */
    private suspend fun warmFullSnapshot(companyId: String, firstPage: LedgerPage) {
        val collected = firstPage.items.toMutableList()
        var freshness = firstPage.dataFreshnessAt
        val pageSize = firstPage.pageSize.coerceIn(1, MasterDataCacheDefaults.SNAPSHOT_PAGE_SIZE)
        val totalPages = firstPage.totalPages.coerceAtMost(MasterDataCacheDefaults.MAX_SNAPSHOT_PAGES)

        for (pageNum in 2..totalPages) {
            when (
                val next = remoteDataSource.fetchLedgers(
                    LedgerQuery(page = pageNum, pageSize = pageSize),
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

private fun List<Ledger>.distinctById(): List<Ledger> = distinctBy { it.id }
