package com.jajusri.venture.feature.voucher.sharing

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InvoiceShareCachePolicy @Inject constructor(
    private val boundary: InvoiceShareCacheBoundary,
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
    fun protectShared(file: File, nowMillis: Long) {
        val path = file.safePath()
        protectedUntil[path] = maxOf(protectedUntil[path] ?: 0L, nowMillis + MINIMUM_RETENTION_MILLIS)
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

        val expired = files.filter { file ->
            isEligible(file, nowMillis) && ageOf(file, nowMillis) >= EXPIRY_MILLIS
        }
        expired.forEach(::deleteQuietly)

        val remaining = files.filter { file -> runCatching { fileOperations.exists(file) }.getOrDefault(false) }
        var excess = (remaining.size - MAX_CACHE_FILES).coerceAtLeast(0)
        if (excess == 0) return

        remaining
            .filter { file -> isEligible(file, nowMillis) && ageOf(file, nowMillis) >= MINIMUM_RETENTION_MILLIS }
            .sortedBy { file -> runCatching { fileOperations.lastModified(file) }.getOrDefault(nowMillis) }
            .forEach { file ->
                if (excess > 0 && deleteQuietly(file)) excess--
            }
    }

    @Synchronized
    internal fun isActive(file: File): Boolean = file.safePath() in activeFiles

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
        internal const val CACHE_DIRECTORY = "invoice-share"
        internal const val MAX_CACHE_FILES = 12
        internal const val MINIMUM_RETENTION_MILLIS = 60L * 60L * 1000L
        internal const val EXPIRY_MILLIS = 24L * 60L * 60L * 1000L
    }
}
