package com.budcom.android.feature.voucher.domain.model

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * Client-side defaults for required Connector date parameters.
 * Not a Connector-defined default period.
 *
 * [BUSINESS_ZONE] is the business/accounting day, not the device's own UTC
 * calendar day: India has no DST, so a fixed IANA zone is correct here, and no
 * company/system-level timezone setting exists elsewhere in the app to source
 * this from. Computing "today" from raw UTC instead loses the current business
 * day for up to 5.5 hours every night (00:00-05:29 IST is still "yesterday" in
 * UTC) — this mirrors the Connector's own default-window computation, which
 * has the same [BUSINESS_ZONE] fix for the same reason.
 */
object VoucherDateRangeDefaults {
    const val DEFAULT_LOOKBACK_DAYS = 30L
    private val BUSINESS_ZONE: ZoneId = ZoneId.of("Asia/Kolkata")

    fun lastDaysInclusive(
        days: Long = DEFAULT_LOOKBACK_DAYS,
        clock: Clock = Clock.system(BUSINESS_ZONE),
    ): VoucherDateRange {
        val today = LocalDate.now(clock.withZone(BUSINESS_ZONE))
        val from = today.minusDays(days.coerceAtLeast(0))
        return VoucherDateRange(from = from.toString(), to = today.toString())
    }
}
