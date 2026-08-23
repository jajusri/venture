package com.budcom.android.feature.masterdata.ledger.data.remote

import com.budcom.android.core.network.ApiResult
import com.budcom.android.core.network.ErrorMapper
import com.budcom.android.core.network.NetworkConnectivityObserver
import com.budcom.android.core.network.RetryPolicy
import com.budcom.android.core.network.safeApiCall
import com.budcom.android.core.network.withRetry
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerContactDetails
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerContactDetailsBulkResult
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerPage
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerQuery
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatement
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatementDateRange
import javax.inject.Inject
import javax.inject.Singleton

interface LedgerRemoteDataSource {
    suspend fun fetchLedgers(query: LedgerQuery): ApiResult<LedgerPage>
    suspend fun fetchLedgerStatement(ledgerId: String, range: LedgerStatementDateRange): ApiResult<LedgerStatement>

    /** Bounded, single-ledger, on-demand read of the fuller `LedgerDetails` shape (mailing/
     * contact/gst) — used only by MVP-1.1-D's explicit re-sync action, never by any bulk/automatic
     * sync path. See [LedgerApi.getLedgerDetail]'s doc comment for exactly what this reads. */
    suspend fun fetchLedgerContactDetails(ledgerId: String): ApiResult<LedgerContactDetails>

    /**
     * Manually-triggered, bulk, all-ledgers-in-one-call read of Tally contact-compatible fields
     * (Connect address/email/GSTIN auto-population). Never invoked by any scheduled/automatic
     * sync path — see [LedgerApi.postLedgerContactDetailsSync]'s doc comment.
     */
    suspend fun fetchLedgerContactDetailsBulk(): ApiResult<LedgerContactDetailsBulkResult>
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

    override suspend fun fetchLedgerContactDetails(ledgerId: String): ApiResult<LedgerContactDetails> =
        withRetry(RetryPolicy.None) {
            safeApiCall(errorMapper, connectivityObserver) {
                api.getLedgerDetail(ledgerId).ledger.toContactDetails()
            }
        }

    override suspend fun fetchLedgerContactDetailsBulk(): ApiResult<LedgerContactDetailsBulkResult> =
        withRetry(RetryPolicy.None) {
            safeApiCall(errorMapper, connectivityObserver) {
                api.postLedgerContactDetailsSync().toDomain()
            }
        }
}
