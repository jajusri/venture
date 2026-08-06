package com.budcom.android.feature.masterdata.ledger.data.repository

import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.connectorauth.domain.ConnectorTransportSelection
import com.budcom.android.core.connectorauth.domain.ConnectorTransportSelectionGate
import com.budcom.android.core.network.ApiResult
import com.budcom.android.core.network.ErrorMapper
import com.budcom.android.core.util.DispatcherProvider
import com.budcom.android.feature.company.data.repository.SelectedCompanyStore
import com.budcom.android.feature.masterdata.domain.MasterDataCacheDefaults
import com.budcom.android.feature.masterdata.ledger.data.local.LedgerLocalDataSource
import com.budcom.android.feature.masterdata.ledger.data.remote.AuthenticatedLedgerRemoteDataSource
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
    private val transportGate: ConnectorTransportSelectionGate,
    private val authenticatedRemoteDataSource: AuthenticatedLedgerRemoteDataSource,
) : LedgerRepository {

    override suspend fun loadLedgers(query: LedgerQuery): AppResult<LedgerPage> =
        withContext(dispatchers.io) {
            // Captured once per call, before either transport is chosen or invoked, so a company
            // switch that lands while this request is in flight can never mislabel that request's
            // response (or any later page fetched while warming a full snapshot) under the newly
            // selected company. The Connector itself is never told which company this request is
            // for — that is established separately by the already-cut-over company/session
            // repository — this is purely the local label used when persisting the response.
            val companyId = selectedCompanyStore.getSelectedCompanyId()
            val selection = transportGate.resolve()
            val fetchPage: suspend (LedgerQuery) -> AppResult<LedgerPage> = when (selection) {
                ConnectorTransportSelection.LEGACY -> { q -> remoteDataSource.fetchLedgers(q).toAppResult(errorMapper) }
                ConnectorTransportSelection.AUTHENTICATED -> { q -> authenticatedRemoteDataSource.fetchLedgers(q) }
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
        query: LedgerQuery,
        page: LedgerPage,
        fetchPage: suspend (LedgerQuery) -> AppResult<LedgerPage>,
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
     * first page, never the other one — and atomically replaces the company ledger cache only
     * when every page succeeds. Partial failure upserts accumulated rows without clearing.
     */
    private suspend fun warmFullSnapshot(
        companyId: String,
        firstPage: LedgerPage,
        fetchPage: suspend (LedgerQuery) -> AppResult<LedgerPage>,
    ) {
        val collected = firstPage.items.toMutableList()
        var freshness = firstPage.dataFreshnessAt
        val pageSize = firstPage.pageSize.coerceIn(1, MasterDataCacheDefaults.SNAPSHOT_PAGE_SIZE)
        val totalPages = firstPage.totalPages.coerceAtMost(MasterDataCacheDefaults.MAX_SNAPSHOT_PAGES)

        for (pageNum in 2..totalPages) {
            when (val next = fetchPage(LedgerQuery(page = pageNum, pageSize = pageSize))) {
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

private fun List<Ledger>.distinctById(): List<Ledger> = distinctBy { it.id }

private fun <T> ApiResult<T>.toAppResult(errorMapper: ErrorMapper): AppResult<T> = when (this) {
    is ApiResult.Success -> AppResult.Success(data)
    is ApiResult.Failure -> AppResult.Failure(errorMapper.toAppError(error))
}

/** True only for the two outcomes [com.budcom.android.core.connectorauth.domain.AuthenticatedRepositoryFailurePolicy]
 * produces from an authentication rejection (401/403) — see its `AUTHENTICATED_*_CODE` constants. */
private fun AppError.isAuthenticationRejection(): Boolean =
    this is AppError.Remote && (
        code == com.budcom.android.core.connectorauth.domain.AUTHENTICATED_SECURE_PAIRING_REQUIRED_CODE ||
            code == com.budcom.android.core.connectorauth.domain.AUTHENTICATED_ACCESS_DENIED_CODE
        )
