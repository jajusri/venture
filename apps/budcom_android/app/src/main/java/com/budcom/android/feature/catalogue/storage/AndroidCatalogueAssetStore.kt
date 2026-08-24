package com.budcom.android.feature.catalogue.storage

import android.content.Context
import android.net.Uri
import com.budcom.android.core.util.DispatcherProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_ASSET_BYTES = 5L * 1024 * 1024
private const val ASSET_DIRECTORY = "catalogue_assets"
private val ALLOWED_MIME_TO_EXTENSION = mapOf(
    "image/jpeg" to "jpg",
    "image/png" to "png",
    "image/webp" to "webp",
)

private enum class CopyOutcome { Success, TooLarge, UnreadableSource, StorageError }

/**
 * App-private internal-storage implementation of [CatalogueAssetStore], mirroring
 * [com.budcom.android.feature.businessprofile.storage.AndroidBusinessProfileLogoStore]'s exact
 * discipline (allowlist, streaming size cap, sanitized path segments, path-containment check on
 * read). Every file lives under `context.filesDir/catalogue_assets/<companyId>/<productId>/
 * <assetId>.<ext>` (architecture §10's filename convention, one level deeper than the Business
 * Profile logo store for multi-image/multi-product support) — never exposed through the app's
 * `FileProvider` beyond the existing share-generation flow's own already-proven pattern.
 */
@Singleton
class AndroidCatalogueAssetStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) : CatalogueAssetStore {

    override suspend fun saveAsset(companyId: String, productId: String, sourceUri: Uri): CatalogueAssetResult =
        withContext(dispatchers.io) {
            val extension = ALLOWED_MIME_TO_EXTENSION[context.contentResolver.getType(sourceUri)]
                ?: return@withContext CatalogueAssetResult.Failure(CatalogueAssetFailureReason.UnsupportedFileType)

            val directory = productDirectory(companyId, productId)
            val assetId = UUID.randomUUID().toString()
            val target = File(directory, "$assetId.$extension")

            val outcome = runCatching {
                check(directory.exists() || directory.mkdirs())
                val input = context.contentResolver.openInputStream(sourceUri) ?: return@runCatching CopyOutcome.UnreadableSource
                input.use { source ->
                    target.outputStream().use { out ->
                        if (copyBounded(source, out, MAX_ASSET_BYTES)) CopyOutcome.Success else CopyOutcome.TooLarge
                    }
                }
            }.getOrElse { CopyOutcome.StorageError }

            when (outcome) {
                CopyOutcome.Success -> CatalogueAssetResult.Success(
                    assetId = assetId,
                    filePath = relativePath(companyId, productId, "$assetId.$extension"),
                )
                CopyOutcome.TooLarge -> {
                    target.delete()
                    CatalogueAssetResult.Failure(CatalogueAssetFailureReason.FileTooLarge)
                }
                CopyOutcome.UnreadableSource -> {
                    target.delete()
                    CatalogueAssetResult.Failure(CatalogueAssetFailureReason.UnreadableSource)
                }
                CopyOutcome.StorageError -> {
                    target.delete()
                    CatalogueAssetResult.Failure(CatalogueAssetFailureReason.StorageError)
                }
            }
        }

    override fun resolveAssetFile(companyId: String, productId: String, filePath: String?): File? =
        resolveContainedAssetFile(File(context.filesDir, ASSET_DIRECTORY), companyId, productId, filePath)

    override suspend fun deleteAsset(companyId: String, productId: String, filePath: String): Unit = withContext(dispatchers.io) {
        resolveAssetFile(companyId, productId, filePath)?.delete()
        Unit
    }

    private fun productDirectory(companyId: String, productId: String): File =
        File(context.filesDir, "$ASSET_DIRECTORY/${sanitizedSegment(companyId)}/${sanitizedSegment(productId)}")

    private fun relativePath(companyId: String, productId: String, fileName: String): String =
        "${sanitizedSegment(companyId)}/${sanitizedSegment(productId)}/$fileName"
}

/** Same allowlist-regex discipline as
 * [com.budcom.android.feature.businessprofile.storage.sanitizedCompanyFileStem] — never trusts a
 * raw id string directly as part of a filesystem path. */
internal fun sanitizedSegment(value: String): String =
    value.trim().replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('.', '-', '_').take(80).ifBlank { "x" }

/**
 * TD (this audit pass): [companyId]/[productId] were previously accepted but never actually used
 * to validate [filePath] — the old check only confirmed the candidate was *somewhere* under the
 * shared `catalogue_assets/` root, not that it was inside *this specific* company+product's own
 * subdirectory (architecture §13: "the full (companyId, productId, assetId) tuple must be
 * validated on every read"). Every current call site's `filePath` already originates from a
 * `companyId`/`productId`-scoped DAO query, so this was not observed to be exploitable through any
 * existing UI flow — but the containment check itself did not structurally enforce it, exactly the
 * residual-risk class architecture §13 names ("a hypothetical future DAO method that omits its
 * WHERE companyId clause would not be caught... named here so implementation-phase code review
 * knows to check for it explicitly"). Extracted as a pure, `Context`-free function so the tuple
 * check itself has direct JVM test coverage (the `saveAsset`/`Context`-coupled path still needs an
 * instrumented test, a pre-existing gap this store already disclosed it shares with
 * `BusinessProfileLogoStore`).
 */
internal fun resolveContainedAssetFile(baseDir: File, companyId: String, productId: String, filePath: String?): File? {
    if (filePath.isNullOrBlank()) return null
    val expectedDirectory = File(baseDir, "${sanitizedSegment(companyId)}/${sanitizedSegment(productId)}").canonicalFile
    val candidate = File(baseDir, filePath)
    return if (candidate.isFile && candidate.canonicalFile.parentFile == expectedDirectory) candidate else null
}

internal fun copyBounded(input: InputStream, output: OutputStream, maxBytes: Long): Boolean {
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
