package com.jajusri.venture.feature.businessprofile.storage

import android.content.Context
import android.net.Uri
import com.jajusri.venture.core.util.DispatcherProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_LOGO_BYTES = 5L * 1024 * 1024
private const val LOGO_DIRECTORY = "business_profile_logos"
private val ALLOWED_MIME_TO_EXTENSION = mapOf(
    "image/jpeg" to "jpg",
    "image/png" to "png",
    "image/webp" to "webp",
)

private enum class CopyOutcome { Success, TooLarge, UnreadableSource, StorageError }

/**
 * App-private internal-storage implementation of [BusinessProfileLogoStore] (PDL-019 §4 — MVP-1.3's
 * chosen mechanism, not the existing Private USB Storage subsystem). Every file lives under
 * `context.filesDir/business_profile_logos/`, never exposed through the app's exported
 * `FileProvider` (that authority is scoped to a different, share-oriented cache directory) — this
 * store is display-only within the app, never shared externally, so no content:// URI is ever
 * minted for it.
 */
@Singleton
class AndroidBusinessProfileLogoStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) : BusinessProfileLogoStore {

    override suspend fun saveLogo(companyId: String, sourceUri: Uri): BusinessProfileLogoResult =
        withContext(dispatchers.io) {
            val extension = ALLOWED_MIME_TO_EXTENSION[context.contentResolver.getType(sourceUri)]
                ?: return@withContext BusinessProfileLogoResult.Failure(BusinessProfileLogoFailureReason.UnsupportedFileType)

            val directory = File(context.filesDir, LOGO_DIRECTORY)
            val stem = sanitizedCompanyFileStem(companyId)
            val target = File(directory, "$stem.$extension")

            val outcome = runCatching {
                check(directory.exists() || directory.mkdirs())
                val input = context.contentResolver.openInputStream(sourceUri) ?: return@runCatching CopyOutcome.UnreadableSource
                input.use { source ->
                    target.outputStream().use { out ->
                        if (copyBounded(source, out, MAX_LOGO_BYTES)) CopyOutcome.Success else CopyOutcome.TooLarge
                    }
                }
            }.getOrElse { CopyOutcome.StorageError }

            when (outcome) {
                CopyOutcome.Success -> {
                    clearOtherExtensions(directory, stem, keep = target)
                    BusinessProfileLogoResult.Success(target.absolutePath)
                }
                CopyOutcome.TooLarge -> {
                    target.delete()
                    BusinessProfileLogoResult.Failure(BusinessProfileLogoFailureReason.FileTooLarge)
                }
                CopyOutcome.UnreadableSource -> {
                    target.delete()
                    BusinessProfileLogoResult.Failure(BusinessProfileLogoFailureReason.UnreadableSource)
                }
                CopyOutcome.StorageError -> {
                    target.delete()
                    BusinessProfileLogoResult.Failure(BusinessProfileLogoFailureReason.StorageError)
                }
            }
        }

    override fun resolveLogoFile(logoAssetPath: String?): File? {
        if (logoAssetPath.isNullOrBlank()) return null
        val file = File(logoAssetPath)
        val directory = File(context.filesDir, LOGO_DIRECTORY)
        // Defensive containment check: only ever resolve a path actually inside this store's own
        // managed directory, even if a persisted path string were ever corrupted or tampered with.
        return if (file.isFile && file.canonicalFile.parentFile == directory.canonicalFile) file else null
    }

    override suspend fun deleteLogo(companyId: String): Unit = withContext(dispatchers.io) {
        val directory = File(context.filesDir, LOGO_DIRECTORY)
        val stem = sanitizedCompanyFileStem(companyId)
        directory.listFiles { file -> file.name.startsWith("$stem.") }?.forEach { it.delete() }
        Unit
    }

    private fun clearOtherExtensions(directory: File, stem: String, keep: File) {
        directory.listFiles { file -> file.name.startsWith("$stem.") && file != keep }?.forEach { it.delete() }
    }
}

/** Same allowlist-regex discipline as
 * [com.jajusri.venture.feature.party.sharing.sanitizedPartyXmlExportFilename] — never trusts a raw
 * `companyId` string directly as part of a filesystem path. */
private fun sanitizedCompanyFileStem(companyId: String): String =
    companyId.trim().replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('.', '-', '_').take(80).ifBlank { "company" }

/** Streams at most [maxBytes] from [input] to [output]. Returns `false` (never throws) the moment
 * the source would exceed the cap, so a malicious/oversized source is rejected without ever
 * buffering the whole file in memory. */
private fun copyBounded(input: InputStream, output: OutputStream, maxBytes: Long): Boolean {
    val buffer = ByteArray(8 * 1024)
    var total = 0L
    while (true) {
        val read = input.read(buffer)
        if (read == -1) return true
        total += read
        if (total > maxBytes) return false
        output.write(buffer, 0, read)
    }
}
