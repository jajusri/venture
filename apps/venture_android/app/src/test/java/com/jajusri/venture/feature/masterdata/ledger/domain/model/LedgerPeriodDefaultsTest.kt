package com.jajusri.venture.feature.masterdata.ledger.domain.model

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class LedgerPeriodDefaultsTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun clockAt(iso: String): Clock = Clock.fixed(Instant.parse(iso), zone)

    @Test
    fun `current financial year starts 1 April of the current FY start year`() {
        // 2026-08-12 is inside FY 2026-27 (started 2026-04-01).
        val range = LedgerPeriodDefaults.currentFinancialYear(clockAt("2026-08-12T04:00:00Z"))
        assertEquals("2026-04-01", range.from)
        assertEquals("2026-08-12", range.to)
    }

    @Test
    fun `31 March is still the previous financial year, not the new one`() {
        val range = LedgerPeriodDefaults.currentFinancialYear(clockAt("2026-03-31T04:00:00Z"))
        assertEquals("2025-04-01", range.from)
        assertEquals("2026-03-31", range.to)
    }

    @Test
    fun `1 April is already the new financial year`() {
        val range = LedgerPeriodDefaults.currentFinancialYear(clockAt("2026-04-01T04:00:00Z"))
        assertEquals("2026-04-01", range.from)
        assertEquals("2026-04-01", range.to)
    }

    @Test
    fun `January is inside the financial year that started the previous calendar year`() {
        val range = LedgerPeriodDefaults.currentFinancialYear(clockAt("2026-01-15T04:00:00Z"))
        assertEquals("2025-04-01", range.from)
        assertEquals("2026-01-15", range.to)
    }

    @Test
    fun `previous financial year is the full 1 April to 31 March year before current`() {
        val range = LedgerPeriodDefaults.previousFinancialYear(clockAt("2026-08-12T04:00:00Z"))
        assertEquals("2025-04-01", range.from)
        assertEquals("2026-03-31", range.to)
    }

    @Test
    fun `previous financial year around the 31 March boundary steps back a full year`() {
        // "Today" 2026-03-31 is inside FY 2025-26 (started 2025-04-01), so previous FY is 2024-25.
        val range = LedgerPeriodDefaults.previousFinancialYear(clockAt("2026-03-31T04:00:00Z"))
        assertEquals("2024-04-01", range.from)
        assertEquals("2025-03-31", range.to)
    }

    @Test
    fun `this month starts on the 1st and ends today`() {
        val range = LedgerPeriodDefaults.thisMonth(clockAt("2026-08-12T04:00:00Z"))
        assertEquals("2026-08-01", range.from)
        assertEquals("2026-08-12", range.to)
    }

    @Test
    fun `last 30 days spans exactly 30 calendar days inclusive of today`() {
        val range = LedgerPeriodDefaults.last30Days(clockAt("2026-08-12T04:00:00Z"))
        assertEquals("2026-07-14", range.from)
        assertEquals("2026-08-12", range.to)
    }

    @Test
    fun `business zone protects the current day near UTC midnight rollover`() {
        // 2026-08-11T19:00:00Z is 2026-08-12T00:30 IST — still "today" in Asia/Kolkata even
        // though raw UTC would still call it 2026-08-11.
        val today = LedgerPeriodDefaults.today(clockAt("2026-08-11T19:00:00Z"))
        assertEquals("2026-08-12", today.toString())
    }
}
