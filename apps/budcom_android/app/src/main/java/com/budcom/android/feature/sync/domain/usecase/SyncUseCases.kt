package com.budcom.android.feature.sync.domain.usecase

import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.feature.company.domain.port.CompanySessionPort
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerQuery
import com.budcom.android.feature.masterdata.ledger.domain.usecase.RefreshLedgersUseCase
import com.budcom.android.feature.party.domain.usecase.ReconcilePartiesFromLedgersUseCase
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
import timber.log.Timber
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
 * For [SyncTarget.Vouchers] and [SyncTarget.Ledgers], a Connector-side extraction success is not
 * the whole user-facing operation: the freshly-extracted data still has to reach Android's own
 * offline store (Room) before the user should see "Completed" — the Connector's own database
 * being current is not the same thing. Before this fix, only Vouchers had this second step wired
 * up; a Ledgers "Sync Now" updated the Connector's own database but left Android's `cached_ledgers`
 * untouched, so anything reading Room afterward (the Ledger Browser, and critically
 * [com.budcom.android.feature.party.domain.usecase.ReconcilePartiesFromLedgersUseCase] — Connect's
 * Customer/Supplier population) silently kept operating on stale-or-absent data. Physically
 * reproduced on a real device (2026-08-22/23): Connect showed zero customers after a "successful"
 * Ledgers sync until a separate, undiscoverable Ledger Browser pull-to-refresh was also performed.
 * [refreshVouchers]/[refreshLedgers] are the exact same fetch-and-atomically-persist paths each
 * browser's own explicit Refresh already uses (Room pruning/full-snapshot warm, pagination-
 * completeness proof, and all) — reused here rather than duplicated, so the one Sync-tab action
 * ends with the fresh data already queryable from Room, with no second manual Refresh required.
 * No new Tally extraction and no second sync engine: this only ever calls the Connector's existing
 * `GET /ledgers`/vouchers read endpoints, exactly as each browser screen's own Refresh already
 * does. A failure at this second step is reported as this operation's failure, even though the
 * Connector-side extraction itself already succeeded: a half-finished chain (Connector fresh, Room
 * stale) must never be presented to the user as a completed sync.
 *
 * TD-041: Party reconciliation from freshly-refreshed ledgers also happens here, awaited in this
 * same suspend chain, rather than as a detached `viewModelScope.launch` fire-and-forget triggered
 * from [com.budcom.android.feature.sync.presentation.SyncViewModel] (the original design). That
 * detached launch was a real, live-reproduced defect: it started only AFTER this use-case already
 * returned "Completed" to the Sync screen, so navigating away within the ~4s a 900+ ledger
 * reconciliation takes silently cancelled it via `viewModelScope`'s cancellation-on-clear, leaving
 * Connect's Party data stale with zero error surfaced anywhere. The Ledger Room refresh just above
 * never showed this because it was always awaited inside the same call, before "Completed" is ever
 * reported. Moving reconciliation into this already-awaited chain makes it equally robust, and as a
 * side effect also fixes `RunAvailableSyncsUseCase`'s sequential Ledgers -> Stock items -> Vouchers
 * run: every call to this use-case for [SyncTarget.Ledgers] now reconciles, not only a lone
 * per-target "Sync now" tap.
 */
class StartTargetSyncUseCase @Inject constructor(
    private val repository: SyncRepository,
    private val companySession: CompanySessionPort,
    private val refreshVouchers: RefreshVouchersUseCase,
    private val refreshLedgers: RefreshLedgersUseCase,
    private val reconcilePartiesFromLedgers: ReconcilePartiesFromLedgersUseCase,
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
        return when (target) {
            SyncTarget.Vouchers -> completeVoucherWindowFetch(companyId, started)
            SyncTarget.Ledgers -> completeLedgerRoomRefresh(companyId, started)
            SyncTarget.StockItems -> started
        }
    }

    private suspend fun completeVoucherWindowFetch(
        companyId: String,
        started: AppResult<SyncOutcome>,
    ): AppResult<SyncOutcome> {
        val extraction = (started as? AppResult.Success)?.value as? SyncOutcome.Succeeded
            ?: return started
        val query = VoucherQuery(companyId, VoucherDateRangeDefaults.lastDaysInclusive())
        return when (val refreshed = refreshVouchers(query)) {
            is AppResult.Success -> AppResult.Success(extraction)
            is AppResult.Failure -> refreshed
        }
    }

    private suspend fun completeLedgerRoomRefresh(
        companyId: String,
        started: AppResult<SyncOutcome>,
    ): AppResult<SyncOutcome> {
        val extraction = (started as? AppResult.Success)?.value as? SyncOutcome.Succeeded
            ?: return started
        return when (val refreshed = refreshLedgers(LedgerQuery())) {
            is AppResult.Success -> {
                reconcilePartiesAfterLedgerRefresh(companyId)
                AppResult.Success(extraction)
            }
            is AppResult.Failure -> refreshed
        }
    }

    /**
     * Best-effort and failure-isolated by design, matching the original ViewModel-level comment
     * this replaces: a Party-reconciliation defect must never turn a genuinely completed Ledgers
     * sync into a reported failure. Only the *scope* changed (awaited here vs. detached before) —
     * this still can't fail the sync outcome.
     */
    private suspend fun reconcilePartiesAfterLedgerRefresh(companyId: String) {
        val startedAt = System.currentTimeMillis()
        Timber.tag("TD041").d("reconcile START company=$companyId at=$startedAt")
        runCatching { reconcilePartiesFromLedgers(companyId) }
            .onSuccess {
                val elapsed = System.currentTimeMillis() - startedAt
                Timber.tag("TD041").d("reconcile END company=$companyId count=${it.size} elapsedMs=$elapsed")
            }
            .onFailure { Timber.w(it, "Party reconciliation from ledgers failed; sync outcome is unaffected.") }
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
