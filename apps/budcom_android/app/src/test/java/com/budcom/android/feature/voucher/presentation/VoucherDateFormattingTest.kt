package com.budcom.android.feature.voucher.presentation

import com.budcom.android.feature.voucher.domain.model.VoucherDataQuality
import com.budcom.android.feature.voucher.domain.model.VoucherDetails
import com.budcom.android.feature.voucher.domain.model.VoucherIdentity
import com.budcom.android.feature.voucher.domain.model.VoucherStatus
import com.budcom.android.feature.voucher.domain.model.VoucherSummary
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class VoucherDateFormattingTest {

    // 2. date formatter produces expected result for a normal valid date
    @Test
    fun `formats a normal ISO date into a compact, unambiguous display form`() {
        assertEquals("07 Aug 2026", formatVoucherDate("2026-08-07"))
        assertEquals("01 Jan 2026", formatVoucherDate("2026-01-01"))
        assertEquals("31 Dec 2026", formatVoucherDate("2026-12-31"))
    }

    // 3. date does not shift to previous/next day because of timezone conversion
    @Test
    fun `formatting never shifts the day, regardless of the JVM default time zone`() {
        val original = TimeZone.getDefault()
        try {
            for (zoneId in listOf("UTC", "Pacific/Kiritimati", "Etc/GMT+12", "Asia/Kolkata", "America/Los_Angeles")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zoneId))
                assertEquals("Failed for zone $zoneId", "07 Aug 2026", formatVoucherDate("2026-08-07"))
            }
        } finally {
            TimeZone.setDefault(original)
        }
    }

    // 4. unavailable date uses the established fallback
    @Test
    fun `a blank date uses the project's established unavailable-value placeholder`() {
        assertEquals("—", formatVoucherDate(""))
        assertEquals("—", formatVoucherDate("   "))
    }

    @Test
    fun `a malformed date is returned unchanged rather than fabricated or crashing`() {
        assertEquals("not-a-date", formatVoucherDate("not-a-date"))
        assertEquals("2026-13-40", formatVoucherDate("2026-13-40"))
    }

    // 1/7. voucher-list presentation exposes the authoritative, formatted voucher date
    @Test
    fun `list row mapping formats the authoritative voucher date`() {
        val row = sampleSummary(date = "2026-08-07").toRowUi()

        assertEquals("07 Aug 2026", row.dateLabel)
    }

    // 5/6. voucher number and party name remain present alongside the date
    @Test
    fun `list row mapping preserves voucher number and party name alongside the formatted date`() {
        val row = sampleSummary(number = "S-42", partyName = "Test Trading Co").toRowUi()

        assertEquals("S-42", row.primaryLabel)
        assertEquals("Test Trading Co", row.partyName)
    }

    // 8. long party name is handled safely (no truncation/crash at the mapping layer — the
    // Screen's own weighted Row + ellipsis handles display truncation; the mapper must not fail).
    @Test
    fun `a very long party name maps safely without truncation at the presentation-model layer`() {
        val longName = "A".repeat(500)
        val row = sampleSummary(partyName = longName).toRowUi()

        assertEquals(longName, row.partyName)
        assertEquals("07 Aug 2026", row.dateLabel)
    }

    // 9/10. voucher detail exposes the same, consistently-formatted date as the list
    @Test
    fun `detail mapping formats the same authoritative voucher date using identical semantics to the list`() {
        val summary = sampleSummary(date = "2026-08-07")
        val details = VoucherDetails(
            summary = summary,
            effectiveDate = null,
            narration = null,
            ledgerEntries = emptyList(),
            inventoryEntries = emptyList(),
        )

        val listDate = summary.toRowUi().dateLabel
        val detailDate = details.toContentUi().dateLabel

        assertEquals("07 Aug 2026", detailDate)
        assertEquals(listDate, detailDate)
    }

    private fun sampleSummary(
        date: String = "2026-08-07",
        number: String? = "S-1",
        partyName: String? = "Acme",
    ) = VoucherSummary(
        identity = VoucherIdentity("v-1"),
        date = date,
        type = "Sales",
        number = number,
        partyName = partyName,
        referenceNumber = null,
        amount = null,
        status = VoucherStatus.Active,
        dataQuality = VoucherDataQuality.Complete,
    )
}
