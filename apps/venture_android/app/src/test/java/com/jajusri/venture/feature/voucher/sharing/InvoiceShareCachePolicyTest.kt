package com.jajusri.venture.feature.voucher.sharing

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

class InvoiceShareCachePolicyTest {
    private lateinit var root: File
    private lateinit var invoiceDirectory: File
    private lateinit var policy: InvoiceShareCachePolicy
    private lateinit var fileOperations: InvoiceShareFileOperations
    private val now = 2_000_000_000_000L

    @Before
    fun setUp() {
        root = Files.createTempDirectory("invoice-share-policy").toFile()
        invoiceDirectory = File(root, InvoiceShareCachePolicy.CACHE_DIRECTORY).apply { mkdirs() }
        fileOperations = InvoiceShareFileOperations()
        policy = InvoiceShareCachePolicy(InvoiceShareCacheBoundary(root, fileOperations), fileOperations)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `thirteenth new file is retained`() {
        val files = (1..13).map { file("new-$it.pdf", age = 1_000L) }
        policy.cleanup(invoiceDirectory, now)
        assertTrue(files.all(File::exists))
    }

    @Test
    fun `active generated and pending save files are skipped`() {
        val generated = file("generated.pdf", age = InvoiceShareCachePolicy.EXPIRY_MILLIS * 2)
        val pendingSave = file("pending-save.pdf", age = InvoiceShareCachePolicy.EXPIRY_MILLIS * 2)
        policy.acquire(generated)
        policy.acquire(pendingSave)
        policy.cleanup(invoiceDirectory, now)
        assertTrue(generated.exists())
        assertTrue(pendingSave.exists())
    }

    @Test
    fun `recently shared file remains through retention window`() {
        val shared = file("shared.pdf", age = InvoiceShareCachePolicy.EXPIRY_MILLIS * 2)
        policy.protectShared(shared, now)
        policy.cleanup(invoiceDirectory, now + InvoiceShareCachePolicy.MINIMUM_RETENTION_MILLIS - 1)
        assertTrue(shared.exists())
    }

    @Test
    fun `concurrent cleanup cannot delete another operation active file`() {
        val active = file("active.pdf", age = InvoiceShareCachePolicy.EXPIRY_MILLIS * 2)
        val acquired = CountDownLatch(1)
        val cleaned = CountDownLatch(1)
        val owner = thread {
            policy.acquire(active)
            acquired.countDown()
            cleaned.await()
            assertTrue(active.exists())
            policy.release(active)
        }
        val cleaner = thread {
            acquired.await()
            policy.cleanup(invoiceDirectory, now)
            cleaned.countDown()
        }
        owner.join()
        cleaner.join()
    }

    @Test
    fun `old inactive files are deleted and count enforcement uses only eligible files`() {
        val recent = (1..13).map { file("recent-$it.pdf", age = 1_000L) }
        val old = (1..4).map { file("old-$it.pdf", age = InvoiceShareCachePolicy.MINIMUM_RETENTION_MILLIS + it) }
        val expired = file("expired.pdf", age = InvoiceShareCachePolicy.EXPIRY_MILLIS + 1)
        policy.cleanup(invoiceDirectory, now)
        assertFalse(expired.exists())
        assertTrue(recent.all(File::exists))
        assertTrue(old.count(File::exists) < old.size)
    }

    @Test
    fun `cleanup stays inside invoice directory and leaves user saved files untouched`() {
        val cached = file("expired.pdf", age = InvoiceShareCachePolicy.EXPIRY_MILLIS + 1)
        val userSaved = File(root, "VENTURE-Invoice-user.pdf").apply {
            writeText("saved")
            setLastModified(now - InvoiceShareCachePolicy.EXPIRY_MILLIS - 1)
        }
        policy.cleanup(invoiceDirectory, now)
        policy.cleanup(root, now)
        assertFalse(cached.exists())
        assertTrue(userSaved.exists())
    }

    @Test
    fun `actual listing failure is contained and preparation can continue`() {
        val failingOperations = object : InvoiceShareFileOperations() {
            override fun listFiles(directory: File): List<File> = throw SecurityException("denied")
        }
        val failingPolicy = InvoiceShareCachePolicy(
            InvoiceShareCacheBoundary(root, failingOperations),
            failingOperations,
        )
        val preparing = File(invoiceDirectory, "preparing.pdf")
        failingPolicy.acquire(preparing)
        failingPolicy.cleanup(invoiceDirectory, now)
        preparing.writeText("prepared")
        assertTrue(preparing.exists())
        assertTrue(failingPolicy.isActive(preparing))
        failingPolicy.protectShared(preparing, now)
        failingPolicy.release(preparing)
        assertFalse(failingPolicy.isActive(preparing))
    }

    @Test
    fun `leases release after success failure and cancellation paths`() {
        listOf("success.pdf", "failure.pdf", "cancel.pdf").forEach { name ->
            val file = file(name, age = InvoiceShareCachePolicy.EXPIRY_MILLIS * 2)
            policy.acquire(file)
            assertTrue(policy.isActive(file))
            policy.release(file)
            assertFalse(policy.isActive(file))
            policy.cleanup(invoiceDirectory, now)
            assertFalse(file.exists())
        }
    }

    @Test
    fun `outside candidate is rejected before metadata and inside candidate is processed`() {
        val outside = File(root, "outside.pdf").apply { writeText("outside") }
        val linked = File(invoiceDirectory, "linked.pdf")
        val inside = file("inside.pdf", age = InvoiceShareCachePolicy.EXPIRY_MILLIS + 1)
        val metadataCalls = mutableListOf<String>()
        val operations = object : InvoiceShareFileOperations() {
            override fun listFiles(directory: File): List<File> = listOf(linked, inside)
            override fun canonicalPath(file: File): String =
                if (file.absolutePath == linked.absolutePath) outside.canonicalPath else super.canonicalPath(file)
            override fun isFile(file: File): Boolean {
                metadataCalls += "isFile:${file.name}"
                return super.isFile(file)
            }
            override fun lastModified(file: File): Long {
                metadataCalls += "lastModified:${file.name}"
                return super.lastModified(file)
            }
            override fun exists(file: File): Boolean {
                metadataCalls += "exists:${file.name}"
                return super.exists(file)
            }
            override fun delete(file: File): Boolean {
                metadataCalls += "delete:${file.name}"
                return super.delete(file)
            }
        }
        InvoiceShareCachePolicy(InvoiceShareCacheBoundary(root, operations), operations)
            .cleanup(invoiceDirectory, now)
        assertTrue(metadataCalls.none { it.endsWith(":linked.pdf") })
        assertTrue(metadataCalls.any { it == "isFile:inside.pdf" })
        assertTrue(metadataCalls.any { it == "delete:inside.pdf" })
        assertTrue(outside.exists())
        assertFalse(inside.exists())
    }

    @Test
    fun `candidate canonicalization failure is contained while other eligible files continue`() {
        val broken = File(invoiceDirectory, "broken.pdf")
        val inside = file("eligible.pdf", age = InvoiceShareCachePolicy.EXPIRY_MILLIS + 1)
        val operations = object : InvoiceShareFileOperations() {
            override fun listFiles(directory: File): List<File> = listOf(broken, inside)
            override fun canonicalPath(file: File): String {
                if (file.absolutePath == broken.absolutePath) throw SecurityException("unresolvable")
                return super.canonicalPath(file)
            }
        }
        InvoiceShareCachePolicy(InvoiceShareCacheBoundary(root, operations), operations)
            .cleanup(invoiceDirectory, now)
        assertFalse(inside.exists())
    }

    @Test
    fun `discard revalidates containment and cannot delete an outside file`() {
        val inside = file("discard.pdf", age = 0L)
        val outside = File(root, "outside-discard.pdf").apply { writeText("outside") }
        assertFalse(policy.discard(outside))
        assertTrue(outside.exists())
        assertTrue(policy.discard(inside))
        assertFalse(inside.exists())
    }

    private fun file(name: String, age: Long): File = File(invoiceDirectory, name).apply {
        writeText(name)
        setLastModified(now - age)
    }
}
