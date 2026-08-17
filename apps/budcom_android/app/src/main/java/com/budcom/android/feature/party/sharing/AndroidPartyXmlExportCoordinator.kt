package com.budcom.android.feature.party.sharing

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.budcom.android.core.util.DispatcherProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidPartyXmlExportCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val cachePolicy: PartyXmlExportCachePolicy,
) : PartyXmlExportCoordinator {

    override suspend fun prepareXml(
        xmlContent: String,
        suggestedFilename: String,
    ): PartyXmlExportResult<PreparedPartyXmlExport> = withContext(dispatchers.io) {
        runCatching {
            val directory = File(context.cacheDir, PartyXmlExportCachePolicy.CACHE_DIRECTORY)
            check(cachePolicy.acceptsDirectory(directory))
            check(directory.exists() || directory.mkdirs())
            check(cachePolicy.acceptsDirectory(directory))
            val filename = "${UUID.randomUUID()}-$suggestedFilename"
            val file = File(directory, filename)
            cachePolicy.acquire(file)
            try {
                cachePolicy.cleanup(directory, System.currentTimeMillis())
                file.writeText(xmlContent, Charsets.UTF_8)
                PreparedPartyXmlExport(file.absolutePath, suggestedFilename)
            } catch (failure: Throwable) {
                cachePolicy.release(file)
                cachePolicy.discard(file)
                throw failure
            }
        }.fold(
            onSuccess = { PartyXmlExportResult.Success(it) },
            onFailure = { PartyXmlExportResult.Failure("The Tally export XML could not be generated. Please try again.") },
        )
    }

    override suspend fun saveXml(
        export: PreparedPartyXmlExport,
        destination: Uri,
    ): PartyXmlExportResult<Unit> = withContext(dispatchers.io) {
        val source = File(export.cacheFilePath)
        try {
            runCatching {
                require(cachePolicy.isManagedFile(source) && source.isFile)
                context.contentResolver.openOutputStream(destination, "w").use { output ->
                    requireNotNull(output)
                    source.inputStream().use { input -> input.copyTo(output) }
                }
            }.fold(
                onSuccess = { PartyXmlExportResult.Success(Unit) },
                onFailure = { PartyXmlExportResult.Failure("The Tally export XML could not be saved. Please choose another location.") },
            )
        } finally {
            cachePolicy.release(source)
        }
    }

    override fun releaseXml(export: PreparedPartyXmlExport) {
        cachePolicy.release(File(export.cacheFilePath))
    }

    /** Not part of the [PartyXmlExportCoordinator] interface (no share-intent path exists for
     * enrichment XML) — kept only so callers can resolve a content:// URI if a future need for
     * one arises without duplicating the FileProvider authority string. */
    internal fun contentUriFor(export: PreparedPartyXmlExport): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.invoice-files", File(export.cacheFilePath))
}
