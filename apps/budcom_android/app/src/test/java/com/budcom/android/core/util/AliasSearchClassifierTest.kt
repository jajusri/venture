package com.budcom.android.core.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AliasSearchClassifierTest {

    @Test
    fun `1 digit is a short numeric alias`() {
        assertTrue(AliasSearchClassifier.isShortNumericAlias("1"))
    }

    @Test
    fun `5 digits is a short numeric alias`() {
        assertTrue(AliasSearchClassifier.isShortNumericAlias("12345"))
    }

    @Test
    fun `6 digits is not a short numeric alias -- too long for a shortcut, too short for a mobile`() {
        assertFalse(AliasSearchClassifier.isShortNumericAlias("123456"))
    }

    @Test
    fun `9 digits is not a short numeric alias`() {
        assertFalse(AliasSearchClassifier.isShortNumericAlias("123456789"))
    }

    @Test
    fun `10 digits is not a short numeric alias -- that is the mobile-candidate rule's territory`() {
        assertFalse(AliasSearchClassifier.isShortNumericAlias("9876543210"))
    }

    @Test
    fun `11 digits is not a short numeric alias`() {
        assertFalse(AliasSearchClassifier.isShortNumericAlias("98765432101"))
    }

    @Test
    fun `non-numeric alias is never a short numeric alias`() {
        assertFalse(AliasSearchClassifier.isShortNumericAlias("A1"))
        assertFalse(AliasSearchClassifier.isShortNumericAlias("Cash"))
    }

    @Test
    fun `formatted alias with internal punctuation is not a short numeric alias`() {
        assertFalse(AliasSearchClassifier.isShortNumericAlias("1-2"))
    }

    @Test
    fun `outer whitespace is trimmed before classification`() {
        assertTrue(AliasSearchClassifier.isShortNumericAlias("  25  "))
    }

    @Test
    fun `empty string is not a short numeric alias`() {
        assertFalse(AliasSearchClassifier.isShortNumericAlias(""))
    }

    @Test
    fun `null is not a short numeric alias`() {
        assertFalse(AliasSearchClassifier.isShortNumericAlias(null))
    }

    @Test
    fun `10-digit and 1-5-digit classifications never overlap for the same value`() {
        val mobileCandidate = "9876543210"
        assertTrue(PhoneNumberNormalizer.normalizeIndianMobile(mobileCandidate) != null)
        assertFalse(AliasSearchClassifier.isShortNumericAlias(mobileCandidate))

        val shortcut = "12345"
        assertTrue(AliasSearchClassifier.isShortNumericAlias(shortcut))
        assertTrue(PhoneNumberNormalizer.normalizeIndianMobile(shortcut) == null)
    }
}
