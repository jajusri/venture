package com.jajusri.venture.feature.voucher.domain.model

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoucherWindowPlannerTest {
    private val fixedClock = Clock.fixed(Instant.parse("2026-07-27T00:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `first window is the most recent, matching the Connector's own default lookback size`() {
        val windows = VoucherWindowPlanner.plan(clock = fixedClock)
        assertEquals(VoucherDateRange("2026-06-28", "2026-07-27"), windows.first())
    }

    @Test
    fun `windows are contiguous with no gap and no overlap, oldest last`() {
        val windows = VoucherWindowPlanner.plan(totalLookbackDays = 90, windowSizeDays = 30, clock = fixedClock)
        assertEquals(3, windows.size)
        for (index in 0 until windows.size - 1) {
            val currentStart = java.time.LocalDate.parse(windows[index].from)
            val nextEnd = java.time.LocalDate.parse(windows[index + 1].to)
            assertEquals(currentStart.minusDays(1), nextEnd)
        }
    }

    @Test
    fun `total span across all windows covers exactly totalLookbackDays`() {
        val windows = VoucherWindowPlanner.plan(totalLookbackDays = 100, windowSizeDays = 30, clock = fixedClock)
        val oldestStart = java.time.LocalDate.parse(windows.last().from)
        val newestEnd = java.time.LocalDate.parse(windows.first().to)
        val totalDays = java.time.temporal.ChronoUnit.DAYS.between(oldestStart, newestEnd) + 1
        assertEquals(100L, totalDays)
    }

    @Test
    fun `a final partial window is shorter than windowSizeDays rather than overshooting`() {
        val windows = VoucherWindowPlanner.plan(totalLookbackDays = 40, windowSizeDays = 30, clock = fixedClock)
        assertEquals(2, windows.size)
        val lastSpanDays = java.time.temporal.ChronoUnit.DAYS.between(
            java.time.LocalDate.parse(windows.last().from),
            java.time.LocalDate.parse(windows.last().to),
        ) + 1
        assertEquals(10L, lastSpanDays)
    }

    @Test
    fun `every window respects the VoucherDateRange invariant of from not exceeding to`() {
        val windows = VoucherWindowPlanner.plan(clock = fixedClock)
        assertTrue(windows.all { it.from <= it.to })
    }
}
