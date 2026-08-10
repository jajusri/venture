package com.budcom.android.feature.voucher.domain.usecase

import com.budcom.android.core.common.AppResult
import com.budcom.android.feature.company.domain.repository.CompanyRepository
import com.budcom.android.feature.voucher.domain.model.VoucherDateRange
import com.budcom.android.feature.voucher.domain.model.VoucherPage
import com.budcom.android.feature.voucher.domain.model.VoucherQuery
import com.budcom.android.feature.voucher.domain.model.VoucherWindowPlanner
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex

sealed interface VoucherReconciliationOutcome {
    /**
     * Every planned window was refreshed successfully. [scopeIsAuthoritative] MUST be checked
     * before this is ever reported as "full history reconciled": true means the walk covered the
     * company's real Tally `BOOKSFROM` history boundary; false means `booksFrom` was unavailable
     * and the walk only covered the [VoucherWindowPlanner.DEFAULT_TOTAL_LOOKBACK_DAYS] fallback
     * window, a limited scope whose actual historical coverage relative to the company's real
     * history is unknown — never represent that case as complete company history.
     */
    data class Completed(val windowsSynced: Int, val scopeIsAuthoritative: Boolean) : VoucherReconciliationOutcome
    /** Stopped at the first window whose refresh failed; every earlier window is fully synced. */
    data class PartiallyCompleted(
        val windowsSynced: Int,
        val failedWindow: VoucherDateRange,
        val scopeIsAuthoritative: Boolean,
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
 * Full Reconcile's overall coverage is NOT an arbitrary fixed lookback: it derives from the
 * company's own authoritative Tally `BOOKSFROM` date (already surfaced end-to-end as
 * [com.budcom.android.feature.company.domain.model.ConnectorCompany.booksFrom], cached locally
 * via [CompanyRepository.loadCompanies]) — the true "beginning of this company's voucher
 * history," not an invented boundary. `booksFrom` is not always populated by every Tally
 * install/version; when it's absent for a given company, this falls back to
 * [VoucherWindowPlanner.DEFAULT_TOTAL_LOOKBACK_DAYS] as an explicit, documented degraded
 * behavior, not a silent substitute for a real accounting-period boundary.
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
    private val companyRepository: CompanyRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val locks = ConcurrentHashMap<String, Mutex>()

    suspend operator fun invoke(
        companyId: String,
        onWindowSynced: suspend (VoucherDateRange, AppResult<VoucherPage>) -> Unit = { _, _ -> },
    ): VoucherReconciliationOutcome {
        val lock = locks.getOrPut(companyId) { Mutex() }
        if (!lock.tryLock()) return VoucherReconciliationOutcome.AlreadyRunning
        try {
            val scope = resolveAuthoritativeLookbackDays(companyId)
            val windows = VoucherWindowPlanner.plan(totalLookbackDays = scope.totalLookbackDays, clock = clock)
            windows.forEachIndexed { index, window ->
                val result = refreshVouchers(VoucherQuery(companyId = companyId, dateRange = window))
                onWindowSynced(window, result)
                if (result is AppResult.Failure) {
                    return VoucherReconciliationOutcome.PartiallyCompleted(index, window, scope.isAuthoritative)
                }
            }
            return VoucherReconciliationOutcome.Completed(windows.size, scope.isAuthoritative)
        } finally {
            lock.unlock()
        }
    }

    private data class LookbackScope(val totalLookbackDays: Long, val isAuthoritative: Boolean)

    /**
     * Reads the LOCALLY CACHED company list (never triggers a network discovery call — this is a
     * scope lookup, not itself a sync) and resolves this company's `booksFrom` into a lookback
     * day count from "today." Falls back to [VoucherWindowPlanner.DEFAULT_TOTAL_LOOKBACK_DAYS]
     * (marked [LookbackScope.isAuthoritative] = false) when the company isn't in the cache,
     * `booksFrom` is null, or it doesn't parse as a plain ISO date — never throws, never blocks
     * reconciliation on a missing/malformed field, but never silently claims that fallback window
     * represents this company's actual complete history either.
     */
    private suspend fun resolveAuthoritativeLookbackDays(companyId: String): LookbackScope {
        val companies = (companyRepository.loadCompanies() as? AppResult.Success)?.value?.items
        val booksFrom = companies?.firstOrNull { it.id == companyId }?.booksFrom
        val parsedBooksFrom = booksFrom?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val today = LocalDate.now(clock.withZone(ZoneOffset.UTC))
        val authoritativeDays = parsedBooksFrom
            ?.let { ChronoUnit.DAYS.between(it, today) + 1 }
            ?.takeIf { it > 0 }
        return if (authoritativeDays != null) {
            LookbackScope(authoritativeDays, isAuthoritative = true)
        } else {
            LookbackScope(VoucherWindowPlanner.DEFAULT_TOTAL_LOOKBACK_DAYS, isAuthoritative = false)
        }
    }
}
