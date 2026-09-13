package com.jajusri.venture.feature.party.sharing

import android.content.Context
import com.jajusri.venture.feature.voucher.sharing.InvoiceShareFileOperations
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Path-containment boundary for the Tally-enrichment XML export cache directory — same shape and
 * same security properties as
 * [com.jajusri.venture.feature.masterdata.ledger.sharing.LedgerStatementShareCacheBoundary],
 * deliberately kept as its own instance (own directory, own mutable cleanup state) rather than
 * reusing that or the Invoice one: mixing unrelated file domains into one boundary/cleanup
 * instance would make each domain's cache pressure and lifecycle affect the others'. Reuses
 * [InvoiceShareFileOperations] directly since that wrapper is already directory-agnostic.
 */
@Singleton
class PartyXmlExportCacheBoundary internal constructor(
    cacheRoot: File,
    private val fileOperations: InvoiceShareFileOperations = InvoiceShareFileOperations(),
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        fileOperations: InvoiceShareFileOperations,
    ) : this(context.cacheDir, fileOperations)

    private val rawDirectory = File(cacheRoot, PartyXmlExportCachePolicy.CACHE_DIRECTORY)
    private val canonicalCacheRoot = runCatching { fileOperations.canonicalPath(cacheRoot) }.getOrNull()
    private val normalizedCacheRoot = runCatching { fileOperations.normalizedAbsolutePath(cacheRoot) }.getOrNull()
    private val normalizedRawDirectory = runCatching { fileOperations.normalizedAbsolutePath(rawDirectory) }.getOrNull()
    private val expectedDirectoryPath = runCatching { fileOperations.canonicalPath(rawDirectory) }.getOrNull()
    private val boundaryIsSafe = canonicalCacheRoot != null &&
        normalizedCacheRoot != null &&
        normalizedRawDirectory != null &&
        expectedDirectoryPath != null &&
        File(normalizedRawDirectory).parent == normalizedCacheRoot &&
        File(expectedDirectoryPath).parent == canonicalCacheRoot &&
        File(normalizedRawDirectory).name == PartyXmlExportCachePolicy.CACHE_DIRECTORY &&
        File(expectedDirectoryPath).name == PartyXmlExportCachePolicy.CACHE_DIRECTORY

    fun accepts(directory: File): Boolean = runCatching {
        val hasTraversal = directory.path.split('/', '\\').any { it == ".." }
        !hasTraversal && boundaryIsSafe &&
            fileOperations.normalizedAbsolutePath(directory) == normalizedRawDirectory &&
            fileOperations.canonicalPath(directory) == expectedDirectoryPath
    }.getOrDefault(false)

    fun contains(file: File): Boolean = runCatching {
        val candidate = fileOperations.canonicalPath(file)
        val root = requireNotNull(expectedDirectoryPath)
        boundaryIsSafe && File(candidate).parent == root && File(candidate).name.isNotBlank()
    }.getOrDefault(false)
}

/**
 * Bounded age/count-based cache eviction for the Tally-enrichment XML export cache directory —
 * same policy shape as
 * [com.jajusri.venture.feature.masterdata.ledger.sharing.LedgerStatementShareCachePolicy]. Exported
 * XML files never contain more than the fields a review explicitly selected, so the same
 * age/count bounds used for PDFs are appropriate here too.
 */
@Singleton
class PartyXmlExportCachePolicy @Inject constructor(
    private val boundary: PartyXmlExportCacheBoundary,
    private val fileOperations: InvoiceShareFileOperations,
) {
    private val activeFiles = mutableSetOf<String>()
    private val protectedUntil = mutableMapOf<String, Long>()

    @Synchronized
    fun acquire(file: File) {
        activeFiles += file.safePath()
    }

    @Synchronized
    fun release(file: File) {
        activeFiles -= file.safePath()
    }

    @Synchronized
    fun cleanup(directory: File, nowMillis: Long) {
        if (!boundary.accepts(directory)) return
        val files = runCatching { fileOperations.listFiles(directory) }
            .getOrDefault(emptyList())
            .mapNotNull { candidate ->
                if (!boundary.contains(candidate)) return@mapNotNull null
                runCatching { candidate.takeIf(fileOperations::isFile) }.getOrNull()
            }

        protectedUntil.entries.removeAll { (path, until) -> until <= nowMillis && path !in activeFiles }

        val expired = files.filter { file -> isEligible(file, nowMillis) && ageOf(file, nowMillis) >= EXPIRY_MILLIS }
        expired.forEach(::deleteQuietly)

        val remaining = files.filter { file -> runCatching { fileOperations.exists(file) }.getOrDefault(false) }
        var excess = (remaining.size - MAX_CACHE_FILES).coerceAtLeast(0)
        if (excess == 0) return

        remaining
            .filter { file -> isEligible(file, nowMillis) && ageOf(file, nowMillis) >= MINIMUM_RETENTION_MILLIS }
            .sortedBy { file -> runCatching { fileOperations.lastModified(file) }.getOrDefault(nowMillis) }
            .forEach { file -> if (excess > 0 && deleteQuietly(file)) excess-- }
    }

    fun isManagedFile(file: File): Boolean = boundary.contains(file)

    fun acceptsDirectory(directory: File): Boolean = boundary.accepts(directory)

    @Synchronized
    internal fun discard(file: File): Boolean = deleteQuietly(file)

    private fun isEligible(file: File, nowMillis: Long): Boolean {
        val path = file.safePath()
        return path !in activeFiles && (protectedUntil[path] ?: 0L) <= nowMillis
    }

    private fun ageOf(file: File, nowMillis: Long): Long =
        (nowMillis - runCatching { fileOperations.lastModified(file) }.getOrDefault(nowMillis)).coerceAtLeast(0L)

    private fun deleteQuietly(file: File): Boolean =
        if (!boundary.contains(file)) false else runCatching { fileOperations.delete(file) }.getOrDefault(false)

    private fun File.safePath(): String = runCatching { canonicalPath }.getOrElse { absolutePath }

    companion object {
        internal const val CACHE_DIRECTORY = "party-xml-export"
        internal const val MAX_CACHE_FILES = 12
        internal const val MINIMUM_RETENTION_MILLIS = 60L * 60L * 1000L
        internal const val EXPIRY_MILLIS = 24L * 60L * 60L * 1000L
    }
}
