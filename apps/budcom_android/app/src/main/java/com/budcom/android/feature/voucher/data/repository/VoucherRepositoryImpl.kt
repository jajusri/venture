package com.budcom.android.feature.voucher.data.repository

import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.network.ApiResult
import com.budcom.android.core.network.ErrorMapper
import com.budcom.android.core.util.DispatcherProvider
import com.budcom.android.core.util.TimeProvider
import com.budcom.android.feature.voucher.data.remote.VoucherRemoteDataSource
import com.budcom.android.feature.voucher.data.local.VoucherLocalDataSource
import com.budcom.android.feature.voucher.domain.model.VoucherCacheState
import com.budcom.android.feature.voucher.domain.model.VoucherDetails
import com.budcom.android.feature.voucher.domain.model.VoucherPage
import com.budcom.android.feature.voucher.domain.model.VoucherQuery
import com.budcom.android.feature.voucher.domain.repository.VoucherRepository
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room is the sole source of truth for [listVouchers]/[getVoucherDetails]: both read the
 * local cache immediately and never contact the Connector. [refreshVouchers]/
 * [refreshVoucherDetails] are the only operations that reach the network; they persist a
 * successful response and otherwise leave the existing cache untouched — a failed refresh
 * is reported as a failure and must never be interpreted as "no cached data".
 */
@Singleton
class VoucherRepositoryImpl @Inject constructor(
    private val remoteDataSource: VoucherRemoteDataSource,
    private val errorMapper: ErrorMapper,
    private val dispatchers: DispatcherProvider,
    private val localDataSource: VoucherLocalDataSource,
    private val timeProvider: TimeProvider,
) : VoucherRepository {

    override suspend fun listVouchers(query: VoucherQuery): AppResult<VoucherPage> =
        withContext(dispatchers.io) {
            localDataSource.list(query)?.let { AppResult.Success(it) }
                ?: AppResult.Failure(AppError.Message(NO_CACHE_MESSAGE))
        }

    override suspend fun refreshVouchers(query: VoucherQuery): AppResult<VoucherPage> =
        withContext(dispatchers.io) {
            when (val result = remoteDataSource.fetchVouchers(query)) {
                is ApiResult.Success -> {
                    val syncedAt = timeProvider.nowEpochMillis()
                    localDataSource.storeList(query.companyId, result.data.items, syncedAt)
                    val refreshed = localDataSource.list(query)
                        ?: result.data.copy(cacheState = VoucherCacheState.Live, lastSyncedAt = syncedAt)
                    AppResult.Success(refreshed.copy(cacheState = VoucherCacheState.Live, lastSyncedAt = syncedAt))
                }
                is ApiResult.Failure -> AppResult.Failure(errorMapper.toAppError(result.error))
            }
        }

    override suspend fun getVoucherDetails(
        companyId: String,
        voucherId: String,
    ): AppResult<VoucherDetails> =
        withContext(dispatchers.io) {
            localDataSource.details(companyId, voucherId)?.let { AppResult.Success(it) }
                ?: AppResult.Failure(AppError.Message(NO_CACHE_DETAILS_MESSAGE))
        }

    override suspend fun refreshVoucherDetails(
        companyId: String,
        voucherId: String,
    ): AppResult<VoucherDetails> =
        withContext(dispatchers.io) {
            when (val result = remoteDataSource.fetchVoucherDetails(companyId, voucherId)) {
                is ApiResult.Success -> {
                    val syncedAt = timeProvider.nowEpochMillis()
                    localDataSource.storeDetails(companyId, result.data, syncedAt)
                    AppResult.Success(result.data.copy(cacheState = VoucherCacheState.Live, lastSyncedAt = syncedAt))
                }
                is ApiResult.Failure -> AppResult.Failure(errorMapper.toAppError(result.error))
            }
        }

    companion object {
        const val NO_CACHE_MESSAGE = "No offline data available. Connect to BUDCOM Desktop and synchronize once."
        const val NO_CACHE_DETAILS_MESSAGE = "No offline data available for this voucher. Connect to BUDCOM Desktop and synchronize once."
    }
}
