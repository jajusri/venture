package com.jajusri.venture.feature.voucher.presentation

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val VOUCHER_DATE_DISPLAY_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")

/**
 * Formats an authoritative Connector voucher date (`YYYY-MM-DD`, date-only — see
 * [com.jajusri.venture.feature.voucher.domain.model.VoucherSummary.date]) for display, e.g.
 * "07 Aug 2026". Uses [LocalDate] exclusively — a date-only type with no time-zone component — so
 * formatting can never shift the accounting date by a day. Falls back to the original value
 * unchanged if it does not parse as ISO-8601 (never fabricates a different date), and to the
 * project's established "—" placeholder only when the value is blank.
 */
internal fun formatVoucherDate(isoDate: String): String {
    if (isoDate.isBlank()) return "—"
    return try {
        LocalDate.parse(isoDate).format(VOUCHER_DATE_DISPLAY_FORMATTER)
    } catch (e: DateTimeParseException) {
        isoDate
    }
}
