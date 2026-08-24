package com.budcom.android.feature.catalogue.sharing

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.budcom.android.core.util.DispatcherProvider
import com.budcom.android.feature.catalogue.domain.repository.CatalogueRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidCatalogueShareCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val repository: CatalogueRepository,
    private val cachePolicy: CatalogueShareCachePolicy,
) : CatalogueShareCoordinator {

    override suspend fun prepareShare(
        companyId: String,
        scope: CatalogueShareScope,
        businessName: String?,
    ): CatalogueShareResult<PreparedCatalogueShare> = withContext(dispatchers.io) {
        val content = when (val resolved = CatalogueShareContent.resolve(repository, companyId, scope, businessName)) {
            is CatalogueShareResult.Failure -> return@withContext resolved
            is CatalogueShareResult.Success -> resolved.value
        }

        runCatching {
            val directory = File(context.cacheDir, CatalogueShareCachePolicy.CACHE_DIRECTORY)
            check(cachePolicy.acceptsDirectory(directory))
            check(directory.exists() || directory.mkdirs())
            check(cachePolicy.acceptsDirectory(directory))
            val suggestedName = suggestedFilename(scope)
            val filename = "${UUID.randomUUID()}-$suggestedName"
            val file = File(directory, filename)
            cachePolicy.acquire(file)
            try {
                cachePolicy.cleanup(directory, System.currentTimeMillis())
                file.writeText(content)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.invoice-files", file)
                PreparedCatalogueShare(uri.toString(), file.absolutePath, suggestedName)
            } catch (failure: Throwable) {
                cachePolicy.release(file)
                cachePolicy.discard(file)
                throw failure
            }
        }.fold(
            onSuccess = { CatalogueShareResult.Success(it) },
            onFailure = { CatalogueShareResult.Failure("The catalogue share file could not be generated. Please try again.") },
        )
    }

    override fun createShareIntent(prepared: PreparedCatalogueShare): CatalogueShareResult<Intent> {
        val contentUri = Uri.parse(prepared.contentUri)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = TEXT_MIME
            putExtra(Intent.EXTRA_STREAM, contentUri)
            clipData = ClipData.newUri(context.contentResolver, prepared.suggestedFilename, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (send.resolveActivity(context.packageManager) == null) {
            releaseShare(prepared)
            return CatalogueShareResult.Failure("No app is available to share files.")
        }
        cachePolicy.protectShared(File(prepared.cacheFilePath), System.currentTimeMillis())
        cachePolicy.release(File(prepared.cacheFilePath))
        return CatalogueShareResult.Success(Intent.createChooser(send, "Share catalogue"))
    }

    override fun releaseShare(prepared: PreparedCatalogueShare) {
        cachePolicy.release(File(prepared.cacheFilePath))
    }

    private fun suggestedFilename(scope: CatalogueShareScope): String {
        val label = when (scope) {
            CatalogueShareScope.FullCatalogue -> "full-catalogue"
            is CatalogueShareScope.Category -> scope.name.trim().replace(Regex("[^A-Za-z0-9._-]+"), "-").ifBlank { "category" }
        }
        return "budcom-catalogue-$label.txt"
    }

    private companion object {
        const val TEXT_MIME = "text/plain"
    }
}
