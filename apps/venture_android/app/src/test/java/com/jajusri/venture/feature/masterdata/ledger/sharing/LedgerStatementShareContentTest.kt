package com.jajusri.venture.feature.masterdata.ledger.sharing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerStatementShareContentTest {

    @Test
    fun `sanitizes a ledger name into a safe filesystem-friendly filename`() {
        val filename = sanitizedLedgerStatementFilename("Acme / Traders & Co.", "2026-07-01", "2026-07-31")
        assertEquals("VENTURE-Ledger-Acme-Traders-Co-2026-07-01-to-2026-07-31.pdf", filename)
    }

    @Test
    fun `falls back to a generic name for a ledger name with no safe characters`() {
        val filename = sanitizedLedgerStatementFilename("###", "2026-07-01", "2026-07-31")
        assertTrue(filename.startsWith("VENTURE-Ledger-ledger-"))
    }

    @Test
    fun `truncates an excessively long ledger name rather than producing an unbounded filename`() {
        val longName = "A".repeat(200)
        val filename = sanitizedLedgerStatementFilename(longName, "2026-07-01", "2026-07-31")
        assertTrue(filename.length < 120)
    }
}
