package com.budcom.android.feature.voucher.domain.usecase

import com.budcom.android.core.common.AppResult
import com.budcom.android.feature.voucher.domain.model.VoucherDateRange
import com.budcom.android.feature.voucher.domain.model.VoucherPage
import com.budcom.android.feature.voucher.domain.model.VoucherQuery
import com.budcom.android.feature.voucher.domain.model.VoucherWindowPlanner
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex

sealed interface VoucherReconciliationOutcome {
    /** Every planned window was refreshed successfully. */
    data class Completed(val windowsSynced: Int) : VoucherReconciliationOutcome
    /** Stopped at the first window whose refresh failed; every earlier window is fully synced. */
    data class PartiallyCompleted(
        val windowsSynced: Int,
        val failedWindow: VoucherDateRange,
    ) : VoucherReconciliationOutcome
    /** A reconciliation for this company was already in progress; this call did nothing. */
    data object AlreadyRunning : VoucherReconciliationOutcome
}

/**
 * Orchestrates the permanent voucher snapshot synchronization lifecycle (BUDCOM MVP-1 Sections
 * 1 & 2): a fast refresh of the current/recent window first, so new and recently-edited vouchers
 * become visible quickly, then progressively older windows reconciled afterward — all through
 * the same [RefreshVouchersUseCase] scope-bounded windowed refresh, so every window individually
 * gets the full stage → validate → atomic-replace treatment. Awaiting this use case to completion
 * (rather than only its first window) IS "Full Reconcile" (Section 2) — the same window walk run
 * to completion instead of stopping after the fast pass.
 *
 * One authoritative reconciliation run per company at a time (Section 9): a concurrent call for
 * a company already reconciling returns [VoucherReconciliationOutcome.AlreadyRunning] immediately
 * rather than racing a second competing walk — this is what makes repeated Sync-button presses
 * and app-driven background continuation safe to call without extra caller-side coordination.
 * Stopping at the first failed window (rather than continuing past it) means every window before
 * the failure is already fully, atomically committed and the previous state for the failed
 * window and everything after it is left untouched — never a partial/corrupted snapshot.
 */
@Singleton
class ReconcileVoucherWindowsUseCase @Inject constructor(
    private val refreshVouchers: RefreshVouchersUseCase,
) {
    private val locks = ConcurrentHashMap<String, Mutex>()

    suspend operator fun invoke(
        companyId: String,
        totalLookbackDays: Long = VoucherWindowPlanner.DEFAULT_TOTAL_LOOKBACK_DAYS,
        onWindowSynced: suspend (VoucherDateRange, AppResult<VoucherPage>) -> Unit = { _, _ -> },
    ): VoucherReconciliationOutcome {
        val lock = locks.getOrPut(companyId) { Mutex() }
        if (!lock.tryLock()) return VoucherReconciliationOutcome.AlreadyRunning
        try {
            val windows = VoucherWindowPlanner.plan(totalLookbackDays = totalLookbackDays)
            windows.forEachIndexed { index, window ->
                val result = refreshVouchers(VoucherQuery(companyId = companyId, dateRange = window))
                onWindowSynced(window, result)
                if (result is AppResult.Failure) {
                    return VoucherReconciliationOutcome.PartiallyCompleted(index, window)
                }
            }
            return VoucherReconciliationOutcome.Completed(windows.size)
        } finally {
            lock.unlock()
        }
    }
}
