package com.budcom.android.feature.voucher.sharing

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

open class InvoiceShareFileOperations @Inject constructor() {
    open fun canonicalPath(file: File): String = file.canonicalPath
    open fun normalizedAbsolutePath(file: File): String = file.absoluteFile.toPath().normalize().toString()
    open fun listFiles(directory: File): List<File> = directory.listFiles()?.toList().orEmpty()
    open fun isFile(file: File): Boolean = file.isFile
    open fun exists(file: File): Boolean = file.exists()
    open fun lastModified(file: File): Long = file.lastModified()
    open fun delete(file: File): Boolean = file.delete()
}

@Singleton
class InvoiceShareCacheBoundary internal constructor(
    cacheRoot: File,
    private val fileOperations: InvoiceShareFileOperations = InvoiceShareFileOperations(),
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        fileOperations: InvoiceShareFileOperations,
    ) : this(context.cacheDir, fileOperations)

    private val rawDirectory = File(cacheRoot, InvoiceShareCachePolicy.CACHE_DIRECTORY)
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
        File(normalizedRawDirectory).name == InvoiceShareCachePolicy.CACHE_DIRECTORY &&
        File(expectedDirectoryPath).name == InvoiceShareCachePolicy.CACHE_DIRECTORY

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
