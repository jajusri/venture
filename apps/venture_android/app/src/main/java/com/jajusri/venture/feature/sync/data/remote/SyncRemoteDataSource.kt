package com.jajusri.venture.feature.sync.data.remote

import com.jajusri.venture.core.network.ApiResult
import com.jajusri.venture.core.network.ErrorMapper
import com.jajusri.venture.core.network.NetworkConnectivityObserver
import com.jajusri.venture.core.network.RetryPolicy
import com.jajusri.venture.core.network.safeApiCall
import com.jajusri.venture.core.network.withRetry
import com.jajusri.venture.feature.sync.domain.SyncDefaults
import com.jajusri.venture.feature.sync.domain.model.SyncMode
import com.jajusri.venture.feature.sync.domain.model.SyncOutcome
import com.jajusri.venture.feature.sync.domain.model.SyncProgress
import com.jajusri.venture.feature.sync.domain.model.SyncRunSummary
import com.jajusri.venture.feature.sync.domain.model.SyncStatisticsSummary
import com.jajusri.venture.feature.sync.domain.model.SyncTarget
import javax.inject.Inject
import javax.inject.Singleton

interface SyncRemoteDataSource {
    suspend fun startSync(target: SyncTarget, mode: SyncMode): ApiResult<SyncOutcome>
    suspend fun cancelSync(target: SyncTarget): ApiResult<SyncProgress>
    suspend fun fetchStatus(target: SyncTarget): ApiResult<SyncProgress>
    suspend fun fetchStatistics(target: SyncTarget): ApiResult<SyncStatisticsSummary>
    suspend fun fetchRecentRuns(target: SyncTarget, limit: Int = SyncDefaults.RUNS_PREVIEW_LIMIT): ApiResult<List<SyncRunSummary>>
}

@Singleton
class DefaultSyncRemoteDataSource @Inject constructor(
    private val api: SyncApi,
    private val errorMapper: ErrorMapper,
    private val connectivityObserver: NetworkConnectivityObserver,
    private val retryPolicy: RetryPolicy,
) : SyncRemoteDataSource {

    override suspend fun startSync(target: SyncTarget, mode: SyncMode): ApiResult<SyncOutcome> {
        // Mutating start must not auto-retry.
        return safeApiCall(errorMapper, connectivityObserver) {
            val body = SyncStartRequestDto(incremental = mode == SyncMode.Incremental)
            when (target) {
                SyncTarget.Ledgers -> api.startLedgerSync(body).toOutcome()
                SyncTarget.StockItems -> api.startStockItemSync(body).toOutcome()
                SyncTarget.Vouchers -> api.startVoucherSync(VoucherSyncStartRequestDto()).toOutcome()
            }
        }
    }

    override suspend fun cancelSync(target: SyncTarget): ApiResult<SyncProgress> =
        safeApiCall(errorMapper, connectivityObserver) {
            when (target) {
                SyncTarget.Ledgers -> api.cancelLedgerSync().progress.toDomain()
                SyncTarget.StockItems -> api.cancelStockItemSync().progress.toDomain()
                SyncTarget.Vouchers -> error("Voucher sync cancel is not publicly available.")
            }
        }

    override suspend fun fetchStatus(target: SyncTarget): ApiResult<SyncProgress> =
        withRetry(retryPolicy) {
            safeApiCall(errorMapper, connectivityObserver) {
                when (target) {
                    SyncTarget.Ledgers -> api.ledgerSyncStatus().let { it.progress.toDomain(it.schedulerState?.toDomain()) }
                    SyncTarget.StockItems -> api.stockItemSyncStatus().let { it.progress.toDomain(it.schedulerState?.toDomain()) }
                    SyncTarget.Vouchers -> error("Voucher sync status is not publicly available.")
                }
            }
        }

    override suspend fun fetchStatistics(target: SyncTarget): ApiResult<SyncStatisticsSummary> =
        withRetry(retryPolicy) {
            safeApiCall(errorMapper, connectivityObserver) {
                when (target) {
                    SyncTarget.Ledgers -> api.ledgerStatistics().statistics.toSummary()
                    SyncTarget.StockItems -> api.stockItemStatistics().statistics.toSummary()
                    SyncTarget.Vouchers -> error("Voucher sync statistics are not publicly available.")
                }
            }
        }

    override suspend fun fetchRecentRuns(
        target: SyncTarget,
        limit: Int,
    ): ApiResult<List<SyncRunSummary>> =
        withRetry(retryPolicy) {
            safeApiCall(errorMapper, connectivityObserver) {
                when (target) {
                    SyncTarget.Ledgers ->
                        api.ledgerSyncRuns(limit).runs.map { it.toDomain(SyncTarget.Ledgers) }
                    SyncTarget.StockItems ->
                        api.stockItemSyncRuns(limit).runs.map { it.toDomain(SyncTarget.StockItems) }
                    SyncTarget.Vouchers -> emptyList()
                }
            }
        }
}
