package com.budcom.android.feature.voucher.data.remote

import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.network.ApiResult
import com.budcom.android.core.network.ErrorMapper
import com.budcom.android.core.network.NetworkConnectivityObserver
import com.budcom.android.core.network.RetryPolicy
import com.budcom.android.core.network.safeApiCall
import com.budcom.android.core.network.withRetry
import com.budcom.android.feature.voucher.domain.model.VoucherDetails
import com.budcom.android.feature.voucher.domain.model.VoucherPage
import com.budcom.android.feature.voucher.domain.model.VoucherQuery
import javax.inject.Inject
import javax.inject.Singleton

interface VoucherRemoteDataSource {
    suspend fun fetchVouchers(query: VoucherQuery): ApiResult<VoucherPage>
    suspend fun fetchVoucherDetails(companyId: String, voucherId: String): ApiResult<VoucherDetails>
}

@Singleton
class DefaultVoucherRemoteDataSource @Inject constructor(
    private val api: VoucherApi,
    private val errorMapper: ErrorMapper,
    private val connectivityObserver: NetworkConnectivityObserver,
    private val retryPolicy: RetryPolicy,
) : VoucherRemoteDataSource {

    override suspend fun fetchVouchers(query: VoucherQuery): ApiResult<VoucherPage> =
        withRetry(retryPolicy) {
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
        withRetry(retryPolicy) {
            safeApiCall(errorMapper, connectivityObserver) {
                val envelope = api.getVoucher(id = voucherId, company = companyId)
                val voucher = envelope.data.voucher
                    ?: throw IllegalStateException("Voucher details payload was empty.")
                voucher.toDomain()
            }
        }
}
