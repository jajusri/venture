package com.budcom.android.feature.voucher.data.remote

import com.budcom.android.core.network.ApiException
import com.budcom.android.core.network.ApiResult
import com.budcom.android.core.network.ErrorMapper
import com.budcom.android.core.network.NetworkConnectivityObserver
import com.budcom.android.core.network.NetworkError
import com.budcom.android.core.network.safeApiCall
import com.budcom.android.feature.voucher.domain.model.VoucherDetails
import com.budcom.android.feature.voucher.domain.model.VoucherPage
import com.budcom.android.feature.voucher.domain.model.VoucherQuery
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

interface VoucherRemoteDataSource {
    suspend fun fetchVouchers(query: VoucherQuery): ApiResult<VoucherPage>
    suspend fun fetchVoucherDetails(companyId: String, voucherId: String): ApiResult<VoucherDetails>
}

/**
 * Bounded attempt policy for manual voucher refresh: one short attempt, one quick retry,
 * total user-visible failure within [VoucherRefreshTimeoutPolicy.attemptTimeoutMillis] * 2
 * plus [VoucherRefreshTimeoutPolicy.retryDelayMillis] (~6s with the defaults below), regardless
 * of the OkHttp client's longer connect/call timeouts. See RetryPolicy for the
 * general-purpose policy other Connector calls still use.
 */
data class VoucherRefreshTimeoutPolicy(
    val attemptTimeoutMillis: Long = 3_000L,
    val retryDelayMillis: Long = 300L,
    val maxAttempts: Int = 2,
) {
    init {
        require(attemptTimeoutMillis > 0L) { "attemptTimeoutMillis must be > 0." }
        require(retryDelayMillis >= 0L) { "retryDelayMillis must be >= 0." }
        require(maxAttempts >= 1) { "maxAttempts must be at least 1." }
    }

    companion object {
        val Default: VoucherRefreshTimeoutPolicy = VoucherRefreshTimeoutPolicy()
    }
}

@Singleton
class DefaultVoucherRemoteDataSource @Inject constructor(
    private val api: VoucherApi,
    private val errorMapper: ErrorMapper,
    private val connectivityObserver: NetworkConnectivityObserver,
    private val timeoutPolicy: VoucherRefreshTimeoutPolicy = VoucherRefreshTimeoutPolicy.Default,
) : VoucherRemoteDataSource {

    override suspend fun fetchVouchers(query: VoucherQuery): ApiResult<VoucherPage> =
        withBoundedAttempts {
            safeApiCall(errorMapper, connectivityObserver) {
                api.listVouchers(
                    company = query.companyId,
                    from = query.dateRange.from,
                    to = query.dateRange.to,
                    page = query.page.coerceAtLeast(1),
                    pageSize = query.pageSize.coerceIn(1, 100),
                    sort = query.sort.toApiSortParam(),
                    query = query.normalizedSearch(),
                    voucherType = query.normalizedOptional(query.voucherType),
                    voucherNumber = query.normalizedOptional(query.voucherNumber),
                    partyName = query.normalizedOptional(query.partyName),
                ).toDomain()
            }
        }

    override suspend fun fetchVoucherDetails(
        companyId: String,
        voucherId: String,
    ): ApiResult<VoucherDetails> =
        withBoundedAttempts {
            safeApiCall(errorMapper, connectivityObserver) {
                val envelope = api.getVoucher(id = voucherId, company = companyId)
                val voucher = envelope.data.voucher
                    ?: throw ApiException(
                        NetworkError.Http(
                            httpStatus = 404,
                            code = "NOT_FOUND",
                            message = "Voucher was not found.",
                        ),
                    )
                voucher.toDomain()
            }
        }

    /**
     * Runs [block] with a hard per-attempt deadline, retrying at most once. A stalled
     * connection (Connector unreachable, packets black-holed) is reported as a timeout
     * instead of riding out OkHttp's much longer connect/call timeouts.
     */
    private suspend fun <T> withBoundedAttempts(block: suspend () -> ApiResult<T>): ApiResult<T> {
        var attempt = 0
        while (true) {
            val result = withTimeoutOrNull(timeoutPolicy.attemptTimeoutMillis) { block() }
                ?: ApiResult.Failure(NetworkError.Timeout())
            attempt += 1
            if (result is ApiResult.Success || attempt >= timeoutPolicy.maxAttempts) return result
            delay(timeoutPolicy.retryDelayMillis)
        }
    }
}
