package com.budcom.android.feature.voucher.data.repository

import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.connectorauth.domain.ConnectorTransportSelection
import com.budcom.android.core.connectorauth.domain.ConnectorTransportSelectionGate
import com.budcom.android.core.network.ApiResult
import com.budcom.android.core.network.ErrorMapper
import com.budcom.android.core.util.DispatcherProvider
import com.budcom.android.core.util.TimeProvider
import com.budcom.android.feature.voucher.data.remote.AuthenticatedVoucherDetailRemoteDataSource
import com.budcom.android.feature.voucher.data.remote.AuthenticatedVoucherListRemoteDataSource
import com.budcom.android.feature.voucher.data.remote.VoucherRemoteDataSource
import com.budcom.android.feature.voucher.data.local.VoucherLocalDataSource
import com.budcom.android.feature.voucher.domain.model.VoucherCacheState
import com.budcom.android.feature.voucher.domain.model.VoucherDetails
import com.budcom.android.feature.voucher.domain.model.VoucherPage
import com.budcom.android.feature.voucher.domain.model.VoucherQuery
import com.budcom.android.feature.voucher.domain.model.VoucherSummary
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
 *
 * [refreshVouchers] resolves [ConnectorTransportSelectionGate] once per call and, on
 * AUTHENTICATED, routes through [AuthenticatedVoucherListRemoteDataSource] instead of the legacy
 * [VoucherRemoteDataSource] — never both, never falling back to legacy once AUTHENTICATED is
 * selected. [query]'s `companyId`/`dateRange` are the sole scope used for both dispatch and
 * persistence for the entire call: neither is ever re-read from any mutable store, so a company
 * or scope change elsewhere in the app while a refresh is in flight can never mislabel that
 * refresh's write. [refreshVoucherDetails] resolves the same gate independently (Phase 3S-D2) and
 * routes through [AuthenticatedVoucherDetailRemoteDataSource] on AUTHENTICATED — a separate
 * adapter boundary from the list one, since [voucherId]/[companyId] are this method's own
 * parameters (not read from [query]), matching [AuthenticatedConnectorOperation.GetVoucherById]'s
 * now-corrected `voucherId`+`companyId` contract.
 */
@Singleton
class VoucherRepositoryImpl @Inject constructor(
    private val remoteDataSource: VoucherRemoteDataSource,
    private val errorMapper: ErrorMapper,
    private val dispatchers: DispatcherProvider,
    private val localDataSource: VoucherLocalDataSource,
    private val timeProvider: TimeProvider,
    private val transportGate: ConnectorTransportSelectionGate,
    private val authenticatedRemoteDataSource: AuthenticatedVoucherListRemoteDataSource,
    private val authenticatedDetailRemoteDataSource: AuthenticatedVoucherDetailRemoteDataSource,
) : VoucherRepository {

    override suspend fun listVouchers(query: VoucherQuery): AppResult<VoucherPage> =
        withContext(dispatchers.io) {
            localDataSource.list(query)?.let { AppResult.Success(it) }
                ?: AppResult.Failure(AppError.Message(NO_CACHE_MESSAGE))
        }

    override suspend fun refreshVouchers(query: VoucherQuery): AppResult<VoucherPage> =
        withContext(dispatchers.io) {
            when (transportGate.resolve()) {
                ConnectorTransportSelection.LEGACY -> when (val result = remoteDataSource.fetchVouchers(query)) {
                    is ApiResult.Success -> persistAndReturn(query, result.data)
                    is ApiResult.Failure -> AppResult.Failure(errorMapper.toAppError(result.error))
                }

                ConnectorTransportSelection.AUTHENTICATED -> when (val result = authenticatedRemoteDataSource.fetchVouchers(query)) {
                    is AppResult.Success -> persistAndReturn(query, result.value)
                    is AppResult.Failure -> result
                }
            }
        }

    private suspend fun persistAndReturn(query: VoucherQuery, page: VoucherPage): AppResult<VoucherPage> {
        val syncedAt = timeProvider.nowEpochMillis()
        localDataSource.storeList(query.companyId, page.items, syncedAt)
        val refreshed = localDataSource.list(query)
            ?: page.copy(cacheState = VoucherCacheState.Live, lastSyncedAt = syncedAt)
        return AppResult.Success(refreshed.copy(cacheState = VoucherCacheState.Live, lastSyncedAt = syncedAt))
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
            when (transportGate.resolve()) {
                ConnectorTransportSelection.LEGACY -> when (val result = remoteDataSource.fetchVoucherDetails(companyId, voucherId)) {
                    is ApiResult.Success -> persistDetailsAndReturn(companyId, result.data)
                    is ApiResult.Failure -> AppResult.Failure(errorMapper.toAppError(result.error))
                }

                ConnectorTransportSelection.AUTHENTICATED -> when (
                    val result = authenticatedDetailRemoteDataSource.fetchVoucherDetails(companyId, voucherId)
                ) {
                    is AppResult.Success -> persistDetailsAndReturn(companyId, result.value)
                    is AppResult.Failure -> result
                }
            }
        }

    private suspend fun persistDetailsAndReturn(companyId: String, details: VoucherDetails): AppResult<VoucherDetails> {
        val syncedAt = timeProvider.nowEpochMillis()
        localDataSource.storeDetails(companyId, details, syncedAt)
        return AppResult.Success(details.copy(cacheState = VoucherCacheState.Live, lastSyncedAt = syncedAt))
    }

    override suspend fun getCachedVoucherSummary(companyId: String, voucherId: String): VoucherSummary? =
        withContext(dispatchers.io) { localDataSource.summary(companyId, voucherId) }

    companion object {
        const val NO_CACHE_MESSAGE = "No offline data available. Connect to BUDCOM Desktop and synchronize once."
        const val NO_CACHE_DETAILS_MESSAGE = "No offline data available for this voucher. Connect to BUDCOM Desktop and synchronize once."
    }
}
