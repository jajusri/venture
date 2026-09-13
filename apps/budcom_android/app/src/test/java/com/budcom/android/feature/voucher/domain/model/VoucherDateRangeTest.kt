package com.budcom.android.feature.voucher.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class VoucherDateRangeTest {
    @Test
    fun `rejects inverted and malformed ranges`() {
        assertThrows(IllegalArgumentException::class.java) {
            VoucherDateRange(from = "2026-07-28", to = "2026-07-01")
        }
        assertThrows(IllegalArgumentException::class.java) {
            VoucherDateRange(from = "07-01-2026", to = "2026-07-28")
        }
    }

    @Test
    fun `rejects a manual range wider than one year (TD-022)`() {
        assertThrows(IllegalArgumentException::class.java) {
            VoucherDateRange(from = "2020-01-01", to = "2026-07-28")
        }
    }

    @Test
    fun `accepts a manual range exactly at the one-year bound`() {
        // 2025-07-29..2026-07-29 spans exactly 365 days (no leap day in range) -- must not throw.
        VoucherDateRange(from = "2025-07-29", to = "2026-07-29")
    }

    @Test
    fun `default lookback is inclusive IST business-day window`() {
        // Noon UTC is mid-afternoon IST too, so this alone can't distinguish
        // UTC-vs-IST — kept as a baseline sanity check; the boundary case below
        // is the one that actually exercises the fix.
        val clock = Clock.fixed(Instant.parse("2026-07-29T12:00:00Z"), ZoneOffset.UTC)
        val range = VoucherDateRangeDefaults.lastDaysInclusive(days = 30, clock = clock)
        assertEquals("2026-06-29", range.from)
        assertEquals("2026-07-29", range.to)
    }

    @Test
    fun `default lookback includes today in IST while UTC is still on the previous day`() {
        // 2026-08-11T04:09 IST == 2026-08-10T22:39 UTC — the exact gap that
        // dropped the acceptance-test voucher: a UTC-based "today" would report
        // 2026-08-10, silently excluding a voucher dated 2026-08-11.
        val clock = Clock.fixed(Instant.parse("2026-08-10T22:39:18.671Z"), ZoneOffset.UTC)
        val range = VoucherDateRangeDefaults.lastDaysInclusive(days = 30, clock = clock)
        assertEquals("2026-08-11", range.to)
        assertEquals("2026-07-12", range.from)
    }

    @Test
    fun `default lookback midnight boundary follows IST, not UTC`() {
        // 2026-08-11T00:00 IST == 2026-08-10T18:30 UTC.
        val atIstMidnight = Clock.fixed(Instant.parse("2026-08-10T18:30:00Z"), ZoneOffset.UTC)
        assertEquals("2026-08-11", VoucherDateRangeDefaults.lastDaysInclusive(clock = atIstMidnight).to)
        val oneMinuteBefore = Clock.fixed(Instant.parse("2026-08-10T18:29:00Z"), ZoneOffset.UTC)
        assertEquals("2026-08-10", VoucherDateRangeDefaults.lastDaysInclusive(clock = oneMinuteBefore).to)
    }

    @Test
    fun `default lookback crosses month, financial-year, and leap-day boundaries correctly`() {
        val monthBoundary = Clock.fixed(Instant.parse("2026-08-01T04:00:00Z"), ZoneOffset.UTC) // 09:30 IST
        assertEquals(
            "2026-07-02",
            VoucherDateRangeDefaults.lastDaysInclusive(days = 30, clock = monthBoundary).from,
        )

        val financialYearBoundary = Clock.fixed(Instant.parse("2026-04-01T04:00:00Z"), ZoneOffset.UTC)
        assertEquals("2026-04-01", VoucherDateRangeDefaults.lastDaysInclusive(clock = financialYearBoundary).to)

        val leapDay = Clock.fixed(Instant.parse("2028-02-29T04:00:00Z"), ZoneOffset.UTC) // 09:30 IST
        assertEquals("2028-02-29", VoucherDateRangeDefaults.lastDaysInclusive(clock = leapDay).to)
        assertEquals("2028-01-30", VoucherDateRangeDefaults.lastDaysInclusive(days = 30, clock = leapDay).from)
    }

    @Test
    fun `default lookback never includes a future day`() {
        val clock = Clock.fixed(Instant.parse("2026-08-10T22:39:18.671Z"), ZoneOffset.UTC)
        val range = VoucherDateRangeDefaults.lastDaysInclusive(clock = clock)
        // "to" must be today's IST date, never a day beyond it.
        assertTrue(range.to <= "2026-08-11")
        assertTrue(range.from <= range.to)
    }

    @Test
    fun `page canLoadMore follows connector pagination`() {
        val page = VoucherPage(
            companyId = "c1",
            items = emptyList(),
            page = 1,
            pageSize = 50,
            totalItems = 120,
            totalPages = 3,
        )
        assertTrue(page.canLoadMore)
        assertFalse(page.copy(page = 3).canLoadMore)
    }
}
