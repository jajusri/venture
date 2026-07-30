package com.budcom.android.feature.sync.data.repository

import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.network.ApiResult
import com.budcom.android.core.network.ErrorMapper
import com.budcom.android.core.network.NetworkError
import com.budcom.android.core.util.TimeProvider
import com.budcom.android.feature.sync.data.remote.SyncRemoteDataSource
import com.budcom.android.feature.sync.domain.model.SyncMode
import com.budcom.android.feature.sync.domain.model.SyncOutcome
import com.budcom.android.feature.sync.domain.model.SyncProgress
import com.budcom.android.feature.sync.domain.model.SyncRunStatus
import com.budcom.android.feature.sync.domain.model.SyncRunSummary
import com.budcom.android.feature.sync.domain.model.SyncStatisticsSummary
import com.budcom.android.feature.sync.domain.model.SyncStatusSummary
import com.budcom.android.feature.sync.domain.model.SyncTarget
import com.budcom.android.feature.sync.domain.model.SyncTargetSnapshot
import com.budcom.android.feature.sync.domain.model.isActive
import com.budcom.android.feature.sync.domain.port.ObserveSyncStatusPort
import com.budcom.android.feature.sync.domain.repository.SyncRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val remote: SyncRemoteDataSource,
    private val errorMapper: ErrorMapper,
    private val timeProvider: TimeProvider,
) : SyncRepository, ObserveSyncStatusPort {

    private val _summary = MutableStateFlow(emptySummary())
    override val summary: StateFlow<SyncStatusSummary> = _summary.asStateFlow()

    private var localActiveTarget: SyncTarget? = null

    override fun bindCompany(companyId: String?) {
        _summary.update {
            it.copy(
                companyId = companyId,
                lastUpdatedEpochMillis = timeProvider.nowEpochMillis(),
            )
        }
    }

    override suspend fun startSync(target: SyncTarget, mode: SyncMode): AppResult<SyncOutcome> {
        if (localActiveTarget != null) {
            return AppResult.Failure(
                AppError.Message("A sync is already in progress in this app session."),
            )
        }
        localActiveTarget = target
        publishActive(target, SyncRunStatus.Running)
        val result = when (val api = remote.startSync(target, mode)) {
            is ApiResult.Success -> {
                val outcome = api.data
                localActiveTarget = null
                recordOutcome(outcome)
                AppResult.Success(outcome)
            }
            is ApiResult.Failure -> {
                val mapped = mapStartFailure(target, api.error)
                localActiveTarget = null
                recordOutcome(mapped)
                when (mapped) {
                    is SyncOutcome.Conflict -> AppResult.Success(mapped)
                    is SyncOutcome.Failed -> AppResult.Failure(mapped.error)
                    else -> AppResult.Failure(errorMapper.toAppError(api.error))
                }
            }
        }
        return result
    }

    override suspend fun cancelSync(target: SyncTarget): AppResult<SyncProgress> {
        if (target == SyncTarget.Vouchers) {
            return AppResult.Failure(
                AppError.Message("Public voucher sync cancel is not available."),
            )
        }
        return when (val api = remote.cancelSync(target)) {
            is ApiResult.Success -> {
                updateTargetProgress(target, api.data)
                AppResult.Success(api.data)
            }
            is ApiResult.Failure -> AppResult.Failure(errorMapper.toAppError(api.error))
        }
    }

    override suspend fun getStatus(target: SyncTarget): AppResult<SyncProgress> {
        if (target == SyncTarget.Vouchers) {
            return AppResult.Failure(AppError.Message("Voucher sync status is unavailable."))
        }
        return when (val api = remote.fetchStatus(target)) {
            is ApiResult.Success -> {
                updateTargetProgress(target, api.data)
                AppResult.Success(api.data)
            }
            is ApiResult.Failure -> AppResult.Failure(errorMapper.toAppError(api.error))
        }
    }

    override suspend fun getStatistics(target: SyncTarget): AppResult<SyncStatisticsSummary> {
        if (target == SyncTarget.Vouchers) {
            return AppResult.Failure(AppError.Message("Voucher sync statistics are unavailable."))
        }
        return when (val api = remote.fetchStatistics(target)) {
            is ApiResult.Success -> {
                updateStatistics(target, api.data)
                AppResult.Success(api.data)
            }
            is ApiResult.Failure -> AppResult.Failure(errorMapper.toAppError(api.error))
        }
    }

    override suspend fun listRecentRuns(
        target: SyncTarget,
        limit: Int,
    ): AppResult<List<SyncRunSummary>> {
        if (target == SyncTarget.Vouchers) {
            return AppResult.Success(emptyList())
        }
        return when (val api = remote.fetchRecentRuns(target, limit)) {
            is ApiResult.Success -> {
                updateRuns(target, api.data)
                AppResult.Success(api.data)
            }
            is ApiResult.Failure -> AppResult.Failure(errorMapper.toAppError(api.error))
        }
    }

    fun clearActiveIfCompanyChanged() {
        localActiveTarget = null
    }

    private fun mapStartFailure(target: SyncTarget, error: NetworkError): SyncOutcome {
        if (error is NetworkError.Http &&
            (error.httpStatus == 409 || error.code == "SYNC_CONFLICT")
        ) {
            return SyncOutcome.Conflict(
                target = target,
                message = error.message,
                existingSyncRunId = error.details["syncRunId"],
            )
        }
        return SyncOutcome.Failed(
            target = target,
            error = errorMapper.toAppError(error),
            progress = null,
        )
    }

    private fun recordOutcome(outcome: SyncOutcome) {
        _summary.update { current ->
            val targets = current.targets.map { snap ->
                if (snap.target != outcome.target) return@map snap
                when (outcome) {
                    is SyncOutcome.Succeeded -> snap.copy(
                        liveProgress = outcome.progress,
                        lastOutcome = outcome,
                        lastSuccessfulAt = outcome.progress.completedAt
                            ?: outcome.statistics?.lastSyncedAt
                            ?: snap.lastSuccessfulAt,
                        statistics = outcome.statistics ?: snap.statistics,
                    )
                    is SyncOutcome.PartiallySucceeded -> snap.copy(
                        liveProgress = outcome.progress,
                        lastOutcome = outcome,
                        statistics = outcome.statistics ?: snap.statistics,
                    )
                    is SyncOutcome.Cancelled -> snap.copy(
                        liveProgress = outcome.progress,
                        lastOutcome = outcome,
                    )
                    is SyncOutcome.Failed -> snap.copy(
                        liveProgress = outcome.progress,
                        lastOutcome = outcome,
                    )
                    is SyncOutcome.Conflict -> snap.copy(lastOutcome = outcome)
                }
            }
            rebuild(current.companyId, targets)
        }
    }

    private fun publishActive(target: SyncTarget, status: SyncRunStatus) {
        _summary.update { current ->
            val targets = current.targets.map {
                if (it.target == target) {
                    it.copy(
                        liveProgress = it.liveProgress?.copy(status = status)
                            ?: SyncProgress(
                                syncRunId = null,
                                status = status,
                                counts = com.budcom.android.feature.sync.domain.model.SyncCounts(
                                    0, 0, 0, 0, 0, null,
                                ),
                                startedAt = null,
                                completedAt = null,
                                durationMs = null,
                                lastError = null,
                                cancelRequested = false,
                            ),
                    )
                } else {
                    it
                }
            }
            rebuild(current.companyId, targets)
        }
    }

    private fun updateTargetProgress(target: SyncTarget, progress: SyncProgress) {
        _summary.update { current ->
            val targets = current.targets.map {
                if (it.target == target) it.copy(liveProgress = progress) else it
            }
            rebuild(current.companyId, targets)
        }
    }

    private fun updateStatistics(target: SyncTarget, statistics: SyncStatisticsSummary) {
        _summary.update { current ->
            val targets = current.targets.map {
                if (it.target == target) {
                    it.copy(
                        statistics = statistics,
                        lastSuccessfulAt = statistics.lastSyncedAt ?: it.lastSuccessfulAt,
                    )
                } else {
                    it
                }
            }
            rebuild(current.companyId, targets)
        }
    }

    private fun updateRuns(target: SyncTarget, runs: List<SyncRunSummary>) {
        _summary.update { current ->
            val targets = current.targets.map {
                if (it.target == target) it.copy(recentRuns = runs) else it
            }
            rebuild(current.companyId, targets)
        }
    }

    private fun rebuild(companyId: String?, targets: List<SyncTargetSnapshot>): SyncStatusSummary {
        val active = targets.firstOrNull { it.liveProgress?.status?.isActive() == true }
        val latestSuccess = targets.mapNotNull { it.lastSuccessfulAt }.maxOrNull()
        val latestFail = targets.mapNotNull { snap ->
            when (val o = snap.lastOutcome) {
                is SyncOutcome.Failed -> o.error.let { err ->
                    when (err) {
                        is AppError.Remote -> err.message
                        is AppError.Message -> err.message
                        is AppError.Timeout -> "Timed out"
                        is AppError.Offline -> "Offline"
                        else -> "Sync failed"
                    }
                }
                else -> null
            }
        }.lastOrNull()
        return SyncStatusSummary(
            companyId = companyId,
            isAnySyncActive = active != null || localActiveTarget != null,
            activeTarget = active?.target ?: localActiveTarget,
            activeStatus = active?.liveProgress?.status,
            latestSuccessfulAt = latestSuccess,
            latestFailedMessage = latestFail,
            targets = targets,
            lastUpdatedEpochMillis = timeProvider.nowEpochMillis(),
        )
    }

    private fun emptySummary(): SyncStatusSummary = SyncStatusSummary(
        companyId = null,
        isAnySyncActive = false,
        activeTarget = null,
        activeStatus = null,
        latestSuccessfulAt = null,
        latestFailedMessage = null,
        targets = listOf(
            SyncTargetSnapshot(SyncTarget.Ledgers, available = true),
            SyncTargetSnapshot(SyncTarget.StockItems, available = true),
            SyncTargetSnapshot(
                target = SyncTarget.Vouchers,
                available = true,
            ),
        ),
        lastUpdatedEpochMillis = 0L,
    )
}
