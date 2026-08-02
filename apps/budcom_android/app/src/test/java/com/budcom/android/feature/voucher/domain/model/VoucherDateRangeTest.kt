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
    fun `default lookback is inclusive UTC calendar window`() {
        val clock = Clock.fixed(Instant.parse("2026-07-29T12:00:00Z"), ZoneOffset.UTC)
        val range = VoucherDateRangeDefaults.lastDaysInclusive(days = 30, clock = clock)
        assertEquals("2026-06-29", range.from)
        assertEquals("2026-07-29", range.to)
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
