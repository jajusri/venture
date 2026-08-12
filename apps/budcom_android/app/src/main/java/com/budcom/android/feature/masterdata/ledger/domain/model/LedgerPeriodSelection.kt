package com.budcom.android.feature.masterdata.ledger.domain.model

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * The Ledger period a user has asked to view. [Last7Sales] is the app-wide default (see
 * [LedgerPeriodDefaults]) — every other case is an explicit user choice and, per the locked
 * period-selection contract, must be answered from local Room data only, never a network call.
 */
sealed interface LedgerPeriodSelection {
    /** Last 7 regular Sales vouchers for the ledger, plus every accounting movement from the
     * earliest of those 7 through today. See [LedgerPeriodDefaults.DEFAULT_LAST_SALES_COUNT]. */
    data object Last7Sales : LedgerPeriodSelection
    data object ThisMonth : LedgerPeriodSelection
    data object CurrentFinancialYear : LedgerPeriodSelection
    data object PreviousFinancialYear : LedgerPeriodSelection
    data object Last30Days : LedgerPeriodSelection
    data class Custom(val from: String, val to: String) : LedgerPeriodSelection
}

/**
 * Indian financial-year (1 April -> 31 March) and other period-boundary math shared by the
 * local-first Ledger statement. Mirrors [com.budcom.android.feature.voucher.domain.model.VoucherDateRangeDefaults]'s
 * fixed [BUSINESS_ZONE] choice for the same reason: India has no DST, and there is no
 * company/system timezone setting elsewhere to source this from — computing "today" from raw UTC
 * would lose the current business day for up to 5.5 hours every night.
 */
object LedgerPeriodDefaults {
    const val DEFAULT_LAST_SALES_COUNT = 7
    const val LAST_30_DAYS_LOOKBACK = 29L
    private val BUSINESS_ZONE: ZoneId = ZoneId.of("Asia/Kolkata")

    fun today(clock: Clock = Clock.system(BUSINESS_ZONE)): LocalDate = LocalDate.now(clock.withZone(BUSINESS_ZONE))

    /** 1 April -> [today], inclusive. A 1 Jan-31 Mar "today" belongs to the FY that started the
     * previous calendar year (e.g. 2026-02-01 is inside FY 2025-26, not FY 2026-27). */
    fun currentFinancialYear(clock: Clock = Clock.system(BUSINESS_ZONE)): LedgerStatementDateRange {
        val today = today(clock)
        val startYear = if (today.monthValue >= 4) today.year else today.year - 1
        return LedgerStatementDateRange(
            from = LocalDate.of(startYear, 4, 1).toString(),
            to = today.toString(),
        )
    }

    /** The full 1 April -> 31 March year immediately before [currentFinancialYear]. */
    fun previousFinancialYear(clock: Clock = Clock.system(BUSINESS_ZONE)): LedgerStatementDateRange {
        val current = currentFinancialYear(clock)
        val currentStartYear = LocalDate.parse(current.from).year
        return LedgerStatementDateRange(
            from = LocalDate.of(currentStartYear - 1, 4, 1).toString(),
            to = LocalDate.of(currentStartYear, 3, 31).toString(),
        )
    }

    fun thisMonth(clock: Clock = Clock.system(BUSINESS_ZONE)): LedgerStatementDateRange {
        val today = today(clock)
        return LedgerStatementDateRange(from = today.withDayOfMonth(1).toString(), to = today.toString())
    }

    fun last30Days(clock: Clock = Clock.system(BUSINESS_ZONE)): LedgerStatementDateRange {
        val today = today(clock)
        return LedgerStatementDateRange(from = today.minusDays(LAST_30_DAYS_LOOKBACK).toString(), to = today.toString())
    }
}
