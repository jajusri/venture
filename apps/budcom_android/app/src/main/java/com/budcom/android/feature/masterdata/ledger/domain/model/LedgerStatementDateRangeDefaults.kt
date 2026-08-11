package com.budcom.android.feature.masterdata.ledger.domain.model

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * Client-side default statement period. Not a Connector-defined default.
 *
 * Mirrors [com.budcom.android.feature.voucher.domain.model.VoucherDateRangeDefaults] exactly —
 * same [BUSINESS_ZONE] fix for the same reason (India has no DST; computing "today" from raw
 * UTC loses the current business day for up to 5.5 hours every night). Kept as its own small
 * object rather than reusing the Voucher one directly, since [LedgerStatementDateRange] is a
 * distinct type from `VoucherDateRange` and this call site should not depend on the Voucher
 * feature package.
 */
object LedgerStatementDateRangeDefaults {
    const val DEFAULT_LOOKBACK_DAYS = 29L
    private val BUSINESS_ZONE: ZoneId = ZoneId.of("Asia/Kolkata")

    fun lastDaysInclusive(
        days: Long = DEFAULT_LOOKBACK_DAYS,
        clock: Clock = Clock.system(BUSINESS_ZONE),
    ): LedgerStatementDateRange {
        val today = LocalDate.now(clock.withZone(BUSINESS_ZONE))
        val from = today.minusDays(days.coerceAtLeast(0))
        return LedgerStatementDateRange(from = from.toString(), to = today.toString())
    }
}
