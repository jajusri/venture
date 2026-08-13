package com.budcom.android.feature.masterdata.ledger.sharing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WhatsAppRecipientResolverTest {

    @Test
    fun `a valid explicit mobile wins over any alias`() {
        val result = resolveWhatsAppRecipient(explicitMobile = "9876543210", alias = "9123456789")
        assertEquals("+919876543210", result.normalizedNumber)
        assertEquals(RecipientResolutionSource.ExplicitMobile, result.source)
    }

    @Test
    fun `alias is ignored entirely when explicit mobile is valid, even if alias also looks valid`() {
        val result = resolveWhatsAppRecipient(explicitMobile = "9876543210", alias = "9123456789")
        assertEquals(RecipientResolutionSource.ExplicitMobile, result.source)
    }

    @Test
    fun `a valid 10-digit Indian alias is accepted as a fallback when explicit mobile is missing`() {
        val result = resolveWhatsAppRecipient(explicitMobile = null, alias = "9123456789")
        assertEquals("+919123456789", result.normalizedNumber)
        assertEquals(RecipientResolutionSource.AliasFallback, result.source)
    }

    @Test
    fun `an accepted alias normalizes to the +91XXXXXXXXXX form`() {
        val result = resolveWhatsAppRecipient(explicitMobile = null, alias = "9876543210")
        assertEquals("+919876543210", result.normalizedNumber)
    }

    @Test
    fun `an alias that fails Indian mobile leading-digit plausibility is rejected`() {
        val result = resolveWhatsAppRecipient(explicitMobile = null, alias = "1234567890")
        assertNull(result.normalizedNumber)
        assertEquals(RecipientResolutionSource.None, result.source)
    }

    @Test
    fun `a short numeric ledger code is rejected, not mistaken for a phone number`() {
        val result = resolveWhatsAppRecipient(explicitMobile = null, alias = "1501")
        assertNull(result.normalizedNumber)
        assertEquals(RecipientResolutionSource.None, result.source)
    }

    @Test
    fun `alphabetic or mixed alias text is rejected`() {
        listOf("98A7654321", "WL-9876543210").forEach { alias ->
            val result = resolveWhatsAppRecipient(explicitMobile = null, alias = alias)
            assertNull("expected rejection for '$alias'", result.normalizedNumber)
            assertEquals(RecipientResolutionSource.None, result.source)
        }
    }

    @Test
    fun `an invalid explicit mobile falls through to a valid alias fallback`() {
        val result = resolveWhatsAppRecipient(explicitMobile = "1234", alias = "9123456789")
        assertEquals("+919123456789", result.normalizedNumber)
        assertEquals(RecipientResolutionSource.AliasFallback, result.source)
    }

    @Test
    fun `neither an explicit mobile nor an alias yields a recipient results in None`() {
        val result = resolveWhatsAppRecipient(explicitMobile = null, alias = null)
        assertNull(result.normalizedNumber)
        assertEquals(RecipientResolutionSource.None, result.source)
    }

    @Test
    fun `wrong-length numeric strings are rejected on both sides of 10 digits`() {
        listOf("987654321", "98765432100").forEach { alias ->
            val result = resolveWhatsAppRecipient(explicitMobile = null, alias = alias)
            assertNull("expected rejection for '$alias'", result.normalizedNumber)
        }
    }

    @Test
    fun `internal whitespace is never silently stripped, only safe leading and trailing trim is applied`() {
        // "98765 43210" is 11 characters with a mid-string space; only leading/trailing
        // whitespace is a safe trim, so this must be rejected, not treated as "9876543210".
        val result = resolveWhatsAppRecipient(explicitMobile = null, alias = "98765 43210")
        assertNull(result.normalizedNumber)
        assertEquals(RecipientResolutionSource.None, result.source)
    }

    @Test
    fun `safe leading and trailing whitespace is trimmed before validation`() {
        val result = resolveWhatsAppRecipient(explicitMobile = null, alias = "  9876543210  ")
        assertEquals("+919876543210", result.normalizedNumber)
    }

    @Test
    fun `blank and empty strings are rejected, not treated as valid`() {
        listOf("", "   ").forEach { alias ->
            val result = resolveWhatsAppRecipient(explicitMobile = null, alias = alias)
            assertNull(result.normalizedNumber)
        }
    }
}
