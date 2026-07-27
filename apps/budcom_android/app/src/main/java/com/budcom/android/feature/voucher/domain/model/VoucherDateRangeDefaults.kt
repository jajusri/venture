package com.budcom.android.feature.voucher.domain.model

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Client-side defaults for required Connector date parameters.
 * Not a Connector-defined default period.
 */
object VoucherDateRangeDefaults {
    const val DEFAULT_LOOKBACK_DAYS = 30L

    fun lastDaysInclusive(
        days: Long = DEFAULT_LOOKBACK_DAYS,
        clock: Clock = Clock.systemUTC(),
    ): VoucherDateRange {
        val today = LocalDate.now(clock.withZone(ZoneOffset.UTC))
        val from = today.minusDays(days.coerceAtLeast(0))
        return VoucherDateRange(from = from.toString(), to = today.toString())
    }
}
