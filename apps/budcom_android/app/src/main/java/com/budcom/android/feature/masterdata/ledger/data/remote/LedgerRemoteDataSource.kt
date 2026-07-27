package com.budcom.android.feature.masterdata.ledger.data.remote

import com.budcom.android.core.network.ApiResult
import com.budcom.android.core.network.ErrorMapper
import com.budcom.android.core.network.NetworkConnectivityObserver
import com.budcom.android.core.network.RetryPolicy
import com.budcom.android.core.network.safeApiCall
import com.budcom.android.core.network.withRetry
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerPage
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerQuery
import javax.inject.Inject
import javax.inject.Singleton

interface LedgerRemoteDataSource {
    suspend fun fetchLedgers(query: LedgerQuery): ApiResult<LedgerPage>
}

@Singleton
class DefaultLedgerRemoteDataSource @Inject constructor(
    private val api: LedgerApi,
    private val errorMapper: ErrorMapper,
    private val connectivityObserver: NetworkConnectivityObserver,
    private val retryPolicy: RetryPolicy,
) : LedgerRemoteDataSource {

    override suspend fun fetchLedgers(query: LedgerQuery): ApiResult<LedgerPage> =
        withRetry(retryPolicy) {
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
}
