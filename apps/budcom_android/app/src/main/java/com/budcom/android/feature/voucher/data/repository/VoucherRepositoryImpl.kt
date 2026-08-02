package com.budcom.android.feature.voucher.data.repository

import com.budcom.android.core.common.AppResult
import com.budcom.android.core.network.ApiResult
import com.budcom.android.core.network.ErrorMapper
import com.budcom.android.core.util.DispatcherProvider
import com.budcom.android.feature.voucher.data.remote.VoucherRemoteDataSource
import com.budcom.android.feature.voucher.data.local.VoucherLocalDataSource
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
    private val localDataSource: VoucherLocalDataSource,
) : VoucherRepository {

    override suspend fun listVouchers(query: VoucherQuery): AppResult<VoucherPage> =
        withContext(dispatchers.io) {
            when (val result = remoteDataSource.fetchVouchers(query)) {
                is ApiResult.Success -> {
                    val syncedAt = System.currentTimeMillis()
                    localDataSource.storeList(query.companyId, result.data.items, syncedAt)
                    AppResult.Success(result.data.copy(lastSyncedAt = syncedAt))
                }
                is ApiResult.Failure -> fallback(result.error, localDataSource.list(query))
            }
        }

    override suspend fun getVoucherDetails(
        companyId: String,
        voucherId: String,
    ): AppResult<VoucherDetails> =
        withContext(dispatchers.io) {
            when (val result = remoteDataSource.fetchVoucherDetails(companyId, voucherId)) {
                is ApiResult.Success -> {
                    val syncedAt = System.currentTimeMillis()
                    localDataSource.storeDetails(companyId, result.data, syncedAt)
                    AppResult.Success(result.data.copy(lastSyncedAt = syncedAt))
                }
                is ApiResult.Failure -> fallback(result.error, localDataSource.details(companyId, voucherId))
            }
        }

    private fun <T> fallback(networkError: com.budcom.android.core.network.NetworkError, cached: T?): AppResult<T> {
        val mapped = errorMapper.toAppError(networkError)
        val clientFailure = networkError is com.budcom.android.core.network.NetworkError.Http && networkError.httpStatus in 400..499
        return when {
            clientFailure -> AppResult.Failure(mapped)
            cached != null -> AppResult.Success(cached)
            else -> AppResult.Failure(com.budcom.android.core.common.AppError.Message(NO_CACHE_MESSAGE))
        }
    }

    companion object { const val NO_CACHE_MESSAGE = "No offline data available. Connect to BUDCOM Desktop and synchronize once." }
}
