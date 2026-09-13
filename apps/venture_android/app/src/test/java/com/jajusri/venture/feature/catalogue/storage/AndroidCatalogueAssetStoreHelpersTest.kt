package com.jajusri.venture.feature.catalogue.storage

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
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

    // ============================== resolveContainedAssetFile (isolation audit) ==============================

    private fun tempBaseDir() = kotlin.io.path.createTempDirectory("catalogue-asset-test").toFile()

    @Test
    fun `resolves a file that genuinely lives under this company and product's own directory`() {
        val base = tempBaseDir()
        val dir = File(base, "co-1/prod-1").apply { mkdirs() }
        val file = File(dir, "asset-1.jpg").apply { writeText("x") }

        val resolved = resolveContainedAssetFile(base, "co-1", "prod-1", "co-1/prod-1/asset-1.jpg")

        assertEquals(file.canonicalFile, resolved?.canonicalFile)
    }

    @Test
    fun `refuses a path pointing at a DIFFERENT company's directory even though it is still under the shared root`() {
        val base = tempBaseDir()
        File(base, "co-B/prod-B").apply { mkdirs() }
        File(base, "co-B/prod-B/secret.jpg").writeText("someone else's photo")

        // Caller believes it is asking for co-A's asset, but the stored filePath string actually
        // points at co-B's file -- this is exactly the class of bug architecture §13 names ("a
        // hypothetical future DAO method that omits its WHERE companyId clause").
        val resolved = resolveContainedAssetFile(base, "co-A", "prod-A", "co-B/prod-B/secret.jpg")

        assertEquals("must never resolve a file outside the requesting company+product's own directory", null, resolved)
    }

    @Test
    fun `refuses a path pointing at a different PRODUCT within the same company`() {
        val base = tempBaseDir()
        File(base, "co-1/prod-B").apply { mkdirs() }
        File(base, "co-1/prod-B/other.jpg").writeText("a different product's photo")

        val resolved = resolveContainedAssetFile(base, "co-1", "prod-A", "co-1/prod-B/other.jpg")

        assertEquals(null, resolved)
    }

    @Test
    fun `refuses a traversal attempt escaping the shared root entirely`() {
        val base = tempBaseDir()
        val outside = File(base.parentFile, "outside-${base.name}.jpg").apply { writeText("x") }
        try {
            val resolved = resolveContainedAssetFile(base, "co-1", "prod-1", "../${outside.name}")
            assertEquals(null, resolved)
        } finally {
            outside.delete()
        }
    }

    @Test
    fun `refuses a non-existent file rather than resolving a path that merely looks right`() {
        val base = tempBaseDir()
        File(base, "co-1/prod-1").mkdirs()

        val resolved = resolveContainedAssetFile(base, "co-1", "prod-1", "co-1/prod-1/never-written.jpg")

        assertEquals(null, resolved)
    }

    @Test
    fun `blank or null filePath is refused without touching the filesystem`() {
        val base = tempBaseDir()
        assertEquals(null, resolveContainedAssetFile(base, "co-1", "prod-1", null))
        assertEquals(null, resolveContainedAssetFile(base, "co-1", "prod-1", ""))
        assertEquals(null, resolveContainedAssetFile(base, "co-1", "prod-1", "   "))
    }
}
