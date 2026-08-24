package com.budcom.android.feature.catalogue.storage

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Focused unit coverage for [AndroidCatalogueAssetStore]'s pure, Android-Context-free helpers.
 * The store's Context/ContentResolver-coupled methods themselves need an instrumented test (this
 * project has no Robolectric); these two helpers carry the actual adversarial-safety logic
 * (path-traversal sanitization, streaming size-cap enforcement) and are exercised here without one.
 */
class AndroidCatalogueAssetStoreHelpersTest {

    /** The real safety property: [sanitizedSegment]'s output is always used as exactly one path
     * segment (joined with an explicit "/" by its caller) — it must never itself contain a "/" or
     * "\", or an adversarial companyId/productId could escape into a sibling/parent directory. */
    @Test
    fun `sanitizedSegment output never contains a path separator, for any adversarial input`() {
        listOf(
            "../../etc/passwd",
            "co\\..\\1",
            "../../../x",
            "a/b\\c/../../d",
            "....//....//etc",
        ).forEach { adversarial ->
            val sanitized = sanitizedSegment(adversarial)
            assertFalse("must not contain '/': $sanitized", sanitized.contains("/"))
            assertFalse("must not contain '\\\\': $sanitized", sanitized.contains("\\"))
        }
    }

    @Test
    fun `sanitizedSegment trims leading and trailing separator-like characters`() {
        assertEquals("company-1", sanitizedSegment(".-_company-1_-."))
    }

    @Test
    fun `sanitizedSegment never returns blank, falling back to a safe placeholder`() {
        assertEquals("x", sanitizedSegment("///...///"))
        assertEquals("x", sanitizedSegment(""))
        assertEquals("x", sanitizedSegment("   "))
    }

    @Test
    fun `sanitizedSegment truncates to a bounded length`() {
        val huge = "a".repeat(500)
        assertEquals(80, sanitizedSegment(huge).length)
    }

    @Test
    fun `sanitizedSegment is deterministic for the same input`() {
        assertEquals(sanitizedSegment("Acme Corp Pvt Ltd"), sanitizedSegment("Acme Corp Pvt Ltd"))
    }

    @Test
    fun `copyBounded succeeds for a source at exactly the byte cap`() {
        val data = ByteArray(10) { it.toByte() }
        val output = ByteArrayOutputStream()
        assertTrue(copyBounded(ByteArrayInputStream(data), output, maxBytes = 10))
        assertEquals(10, output.size())
    }

    @Test
    fun `copyBounded rejects a source exceeding the byte cap`() {
        val data = ByteArray(11) { it.toByte() }
        val output = ByteArrayOutputStream()
        assertFalse(copyBounded(ByteArrayInputStream(data), output, maxBytes = 10))
    }

    @Test
    fun `copyBounded handles an empty source`() {
        val output = ByteArrayOutputStream()
        assertTrue(copyBounded(ByteArrayInputStream(ByteArray(0)), output, maxBytes = 10))
        assertEquals(0, output.size())
    }
}
