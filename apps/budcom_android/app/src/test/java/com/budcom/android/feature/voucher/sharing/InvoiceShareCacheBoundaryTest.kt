package com.budcom.android.feature.voucher.sharing

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class InvoiceShareCacheBoundaryTest {
    private lateinit var root: File
    private lateinit var expected: File
    private lateinit var boundary: InvoiceShareCacheBoundary

    @Before
    fun setUp() {
        root = Files.createTempDirectory("invoice-boundary").toFile()
        expected = File(root, InvoiceShareCachePolicy.CACHE_DIRECTORY).apply { mkdirs() }
        boundary = InvoiceShareCacheBoundary(root)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `only exact canonical invoice cache directory is accepted`() {
        assertTrue(boundary.accepts(expected))
        assertFalse(boundary.accepts(File(root, "sibling")))
        assertFalse(boundary.accepts(File(File(root, "sibling"), InvoiceShareCachePolicy.CACHE_DIRECTORY)))
        assertFalse(boundary.accepts(File(root.parentFile, InvoiceShareCachePolicy.CACHE_DIRECTORY)))
        assertFalse(boundary.accepts(File(root, "child/../${InvoiceShareCachePolicy.CACHE_DIRECTORY}")))
    }

    @Test
    fun `candidate must resolve to a direct child inside approved directory`() {
        val inside = File(expected, "invoice.pdf").apply { writeText("inside") }
        val nested = File(expected, "nested/invoice.pdf")
        val outside = File(root, "outside.pdf").apply { writeText("outside") }
        assertTrue(boundary.contains(inside))
        assertFalse(boundary.contains(nested))
        assertFalse(boundary.contains(outside))
    }

    @Test
    fun `simulated candidate link escape is rejected without filesystem link support`() {
        val outside = File(root, "outside.pdf").apply { writeText("outside") }
        val link = File(expected, "linked.pdf")
        val operations = object : InvoiceShareFileOperations() {
            override fun canonicalPath(file: File): String =
                if (file.absolutePath == link.absolutePath) outside.canonicalPath else super.canonicalPath(file)
        }
        assertFalse(InvoiceShareCacheBoundary(root, operations).contains(link))
    }

    @Test
    fun `approved directory resolving outside canonical cache root is rejected deterministically`() {
        val externalRoot = Files.createTempDirectory("external-invoice-boundary").toFile()
        val externalDirectory = File(externalRoot, InvoiceShareCachePolicy.CACHE_DIRECTORY).apply { mkdirs() }
        val operations = object : InvoiceShareFileOperations() {
            override fun canonicalPath(file: File): String =
                if (file.absolutePath == expected.absolutePath) externalDirectory.canonicalPath else super.canonicalPath(file)
        }
        try {
            assertFalse(InvoiceShareCacheBoundary(root, operations).accepts(expected))
        } finally {
            externalRoot.deleteRecursively()
        }
    }
}
