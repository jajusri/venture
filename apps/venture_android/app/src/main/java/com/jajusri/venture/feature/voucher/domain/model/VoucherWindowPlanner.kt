package com.jajusri.venture.feature.voucher.domain.model

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Builds the ordered sequence of bounded windows for the permanent voucher snapshot
 * synchronization architecture (VENTURE MVP-1): the current/recent window first (fast refresh),
 * then progressively older windows (background historical reconciliation). Each window is
 * [VoucherDateRangeDefaults.DEFAULT_LOOKBACK_DAYS] days — the same size the Connector already
 * uses as its own default sync window — so client and server windowing granularity stay aligned.
 */
object VoucherWindowPlanner {
    const val DEFAULT_TOTAL_LOOKBACK_DAYS = 365L

    fun plan(
        totalLookbackDays: Long = DEFAULT_TOTAL_LOOKBACK_DAYS,
        windowSizeDays: Long = VoucherDateRangeDefaults.DEFAULT_LOOKBACK_DAYS,
        clock: Clock = Clock.systemUTC(),
    ): List<VoucherDateRange> {
        require(totalLookbackDays > 0) { "totalLookbackDays must be positive" }
        require(windowSizeDays > 0) { "windowSizeDays must be positive" }
        val today = LocalDate.now(clock.withZone(ZoneOffset.UTC))
        val windows = mutableListOf<VoucherDateRange>()
        var windowEnd = today
        var remaining = totalLookbackDays
        while (remaining > 0) {
            val span = minOf(windowSizeDays, remaining)
            val windowStart = windowEnd.minusDays(span - 1)
            windows += VoucherDateRange(from = windowStart.toString(), to = windowEnd.toString())
            windowEnd = windowStart.minusDays(1)
            remaining -= span
        }
        return windows
    }
}
