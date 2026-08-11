package com.budcom.android.feature.masterdata.ledger.data.remote

import com.budcom.android.core.network.ApiResult
import com.budcom.android.core.network.ErrorMapper
import com.budcom.android.core.network.NetworkConnectivityObserver
import com.budcom.android.core.network.RetryPolicy
import com.budcom.android.core.network.safeApiCall
import com.budcom.android.core.network.withRetry
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerPage
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerQuery
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatement
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatementDateRange
import javax.inject.Inject
import javax.inject.Singleton

interface LedgerRemoteDataSource {
    suspend fun fetchLedgers(query: LedgerQuery): ApiResult<LedgerPage>
    suspend fun fetchLedgerStatement(ledgerId: String, range: LedgerStatementDateRange): ApiResult<LedgerStatement>
}

@Singleton
class DefaultLedgerRemoteDataSource @Inject constructor(
    private val api: LedgerApi,
    private val errorMapper: ErrorMapper,
    private val connectivityObserver: NetworkConnectivityObserver,
) : LedgerRemoteDataSource {

    override suspend fun fetchLedgers(query: LedgerQuery): ApiResult<LedgerPage> =
        // Master-data GETs fail over to Room; avoid multi-attempt delays when Connector is down.
        withRetry(RetryPolicy.None) {
            safeApiCall(errorMapper, connectivityObserver) {
                api.getLedgers(
                    query = query.normalizedText(),
                    page = query.page.coerceAtLeast(1),
                    pageSize = query.pageSize.coerceIn(1, 100),
                    sortBy = query.toApiSortBy(),
                    sortDirection = query.toApiSortDirection(),
                ).toDomain()
            }
        }

    override suspend fun fetchLedgerStatement(
        ledgerId: String,
        range: LedgerStatementDateRange,
    ): ApiResult<LedgerStatement> =
        withRetry(RetryPolicy.None) {
            safeApiCall(errorMapper, connectivityObserver) {
                api.getLedgerStatement(ledgerId = ledgerId, from = range.from, to = range.to).toDomain()
            }
        }
}
