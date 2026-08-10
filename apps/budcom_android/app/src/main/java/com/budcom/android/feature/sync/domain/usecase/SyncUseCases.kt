package com.budcom.android.feature.sync.domain.usecase

import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.feature.company.domain.port.CompanySessionPort
import com.budcom.android.feature.sync.domain.SyncDefaults
import com.budcom.android.feature.sync.domain.model.SyncMode
import com.budcom.android.feature.sync.domain.model.SyncOutcome
import com.budcom.android.feature.sync.domain.model.SyncProgress
import com.budcom.android.feature.sync.domain.model.SyncTarget
import com.budcom.android.feature.sync.domain.model.isActive
import com.budcom.android.feature.sync.domain.repository.SyncRepository
import com.budcom.android.feature.voucher.domain.model.VoucherDateRangeDefaults
import com.budcom.android.feature.voucher.domain.model.VoucherQuery
import com.budcom.android.feature.voucher.domain.usecase.RefreshVouchersUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

class RefreshSyncOverviewUseCase @Inject constructor(
    private val repository: SyncRepository,
    private val companySession: CompanySessionPort,
) {
    suspend operator fun invoke() {
        val companyId = companySession.observeSelectedCompanyId().first()
        repository.bindCompany(companyId)
        SyncTarget.entries.filter { it != SyncTarget.Vouchers }.forEach { target ->
            repository.getStatus(target)
            repository.getStatistics(target)
            repository.listRecentRuns(target, SyncDefaults.RUNS_PREVIEW_LIMIT)
        }
    }
}

/**
 * For [SyncTarget.Vouchers], a Connector-side extraction success is not the whole user-facing
 * operation: the freshly-promoted authoritative window still has to reach Android's own offline
 * store before the user should see "Completed". [refreshVouchers] is the same complete-window
 * fetch-and-atomically-persist path the Vouchers screen's own Refresh already uses (Room pruning,
 * pagination-completeness proof, and all) — reused here rather than duplicated, so the one
 * Sync-tab action ends with the new Voucher already queryable from Room, with no second manual
 * Refresh required. A failure at this second step is reported as this operation's failure, even
 * though the Connector-side extraction itself already succeeded: a half-finished chain must never
 * be presented to the user as a completed sync.
 */
class StartTargetSyncUseCase @Inject constructor(
    private val repository: SyncRepository,
    private val companySession: CompanySessionPort,
    private val refreshVouchers: RefreshVouchersUseCase,
) {
    suspend operator fun invoke(
        target: SyncTarget,
        mode: SyncMode = SyncMode.Full,
    ): AppResult<SyncOutcome> {
        val companyId = companySession.observeSelectedCompanyId().first()
        if (companyId.isNullOrBlank()) {
            return AppResult.Failure(
                AppError.Message("Select a company before starting sync."),
            )
        }
        repository.bindCompany(companyId)
        val started = repository.startSync(target, mode)
        if (target != SyncTarget.Vouchers) return started
        val extraction = (started as? AppResult.Success)?.value as? SyncOutcome.Succeeded
            ?: return started
        return completeVoucherWindowFetch(companyId, extraction)
    }

    private suspend fun completeVoucherWindowFetch(
        companyId: String,
        extraction: SyncOutcome.Succeeded,
    ): AppResult<SyncOutcome> {
        val query = VoucherQuery(companyId, VoucherDateRangeDefaults.lastDaysInclusive())
        return when (val refreshed = refreshVouchers(query)) {
            is AppResult.Success -> AppResult.Success(extraction)
            is AppResult.Failure -> refreshed
        }
    }
}

class CancelTargetSyncUseCase @Inject constructor(
    private val repository: SyncRepository,
) {
    suspend operator fun invoke(target: SyncTarget): AppResult<SyncProgress> =
        repository.cancelSync(target)
}

/**
 * Sequential, non-atomic orchestration of available public sync targets.
 */
class RunAvailableSyncsUseCase @Inject constructor(
    private val startTargetSync: StartTargetSyncUseCase,
) {
    data class AggregateResult(
        val outcomes: List<SyncOutcome>,
    ) {
        val hasFailure: Boolean
            get() = outcomes.any { it is SyncOutcome.Failed || it is SyncOutcome.Conflict }
        val hasSuccess: Boolean
            get() = outcomes.any {
                it is SyncOutcome.Succeeded ||
                    it is SyncOutcome.PartiallySucceeded ||
                    it is SyncOutcome.Cancelled
            }
    }

    suspend operator fun invoke(): AppResult<AggregateResult> {
        val outcomes = mutableListOf<SyncOutcome>()
        for (target in listOf(SyncTarget.Ledgers, SyncTarget.StockItems, SyncTarget.Vouchers)) {
            when (val result = startTargetSync(target)) {
                is AppResult.Success -> outcomes.add(result.value)
                is AppResult.Failure -> {
                    outcomes.add(SyncOutcome.Failed(target, result.error, progress = null))
                }
            }
        }
        return AppResult.Success(AggregateResult(outcomes))
    }
}

/**
 * Observes Connector live status while a blocking start is in flight, or after process recreation.
 */
class ObserveSyncProgressUseCase @Inject constructor(
    private val repository: SyncRepository,
) {
    fun start(
        scope: CoroutineScope,
        target: SyncTarget,
        onProgress: (SyncProgress) -> Unit,
        onPollFailure: (AppError) -> Unit,
        shouldContinue: () -> Boolean = { true },
    ): Job = scope.launch {
        var failures = 0
        while (isActive && shouldContinue()) {
            when (val status = repository.getStatus(target)) {
                is AppResult.Success -> {
                    failures = 0
                    onProgress(status.value)
                    if (!status.value.status.isActive()) break
                }
                is AppResult.Failure -> {
                    failures += 1
                    if (failures >= SyncDefaults.MAX_CONSECUTIVE_POLL_FAILURES) {
                        onPollFailure(status.error)
                        break
                    }
                }
            }
            delay(SyncDefaults.STATUS_POLL_INTERVAL_MS)
        }
    }
}
