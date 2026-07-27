package com.budcom.android.feature.voucher.data.repository

import com.budcom.android.core.common.AppResult
import com.budcom.android.core.network.ApiResult
import com.budcom.android.core.network.ErrorMapper
import com.budcom.android.core.util.DispatcherProvider
import com.budcom.android.feature.voucher.data.remote.VoucherRemoteDataSource
import com.budcom.android.feature.voucher.domain.model.VoucherDetails
import com.budcom.android.feature.voucher.domain.model.VoucherPage
import com.budcom.android.feature.voucher.domain.model.VoucherQuery
import com.budcom.android.feature.voucher.domain.repository.VoucherRepository
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoucherRepositoryImpl @Inject constructor(
    private val remoteDataSource: VoucherRemoteDataSource,
    private val errorMapper: ErrorMapper,
    private val dispatchers: DispatcherProvider,
) : VoucherRepository {

    override suspend fun listVouchers(query: VoucherQuery): AppResult<VoucherPage> =
        withContext(dispatchers.io) {
            when (val result = remoteDataSource.fetchVouchers(query)) {
                is ApiResult.Success -> AppResult.Success(result.data)
                is ApiResult.Failure -> AppResult.Failure(errorMapper.toAppError(result.error))
            }
        }

    override suspend fun getVoucherDetails(
        companyId: String,
        voucherId: String,
    ): AppResult<VoucherDetails> =
        withContext(dispatchers.io) {
            when (val result = remoteDataSource.fetchVoucherDetails(companyId, voucherId)) {
                is ApiResult.Success -> AppResult.Success(result.data)
                is ApiResult.Failure -> AppResult.Failure(errorMapper.toAppError(result.error))
            }
        }
}
