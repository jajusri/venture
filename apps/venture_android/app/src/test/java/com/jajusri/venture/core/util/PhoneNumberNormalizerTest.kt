package com.jajusri.venture.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneNumberNormalizerTest {

    @Test
    fun `exactly 10 digits with valid leading digit is accepted`() {
        assertEquals("+919876543210", PhoneNumberNormalizer.normalizeIndianMobile("9876543210"))
    }

    @Test
    fun `9 digits is rejected`() {
        assertNull(PhoneNumberNormalizer.normalizeIndianMobile("987654321"))
    }

    @Test
    fun `11 digits is rejected`() {
        assertNull(PhoneNumberNormalizer.normalizeIndianMobile("98765432101"))
    }

    @Test
    fun `alphabetic alias is rejected`() {
        assertNull(PhoneNumberNormalizer.normalizeIndianMobile("98A7654321"))
    }

    @Test
    fun `formatted alias with punctuation is rejected, not silently reformatted`() {
        assertNull(PhoneNumberNormalizer.normalizeIndianMobile("98765-43210"))
        assertNull(PhoneNumberNormalizer.normalizeIndianMobile("+91 98765 43210"))
    }

    @Test
    fun `leading digit outside 6-9 is rejected`() {
        assertNull(PhoneNumberNormalizer.normalizeIndianMobile("1234567890"))
        assertNull(PhoneNumberNormalizer.normalizeIndianMobile("5876543210"))
    }

    @Test
    fun `safe leading and trailing whitespace is trimmed before validation`() {
        assertEquals("+919876543210", PhoneNumberNormalizer.normalizeIndianMobile("  9876543210  "))
    }

    @Test
    fun `null and blank input is rejected`() {
        assertNull(PhoneNumberNormalizer.normalizeIndianMobile(null))
        assertNull(PhoneNumberNormalizer.normalizeIndianMobile(""))
        assertNull(PhoneNumberNormalizer.normalizeIndianMobile("   "))
    }

    @Test
    fun `search normalization strips formatting and country code`() {
        assertEquals("9876543210", PhoneNumberNormalizer.normalizeForSearch("9876543210"))
        assertEquals("9876543210", PhoneNumberNormalizer.normalizeForSearch("+91 98765 43210"))
        assertEquals("9876543210", PhoneNumberNormalizer.normalizeForSearch("098765-43210"))
    }

    @Test
    fun `search normalization rejects fewer than 10 digits`() {
        assertNull(PhoneNumberNormalizer.normalizeForSearch("987654321"))
        assertNull(PhoneNumberNormalizer.normalizeForSearch(null))
    }
}
