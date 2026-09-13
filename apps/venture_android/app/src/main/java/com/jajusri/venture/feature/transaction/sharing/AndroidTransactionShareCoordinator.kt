package com.jajusri.venture.feature.transaction.sharing

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.jajusri.venture.core.util.DispatcherProvider
import com.jajusri.venture.feature.transaction.domain.model.EstimatePo
import com.jajusri.venture.feature.transaction.domain.model.TermsAcknowledgment
import com.jajusri.venture.feature.transaction.domain.model.TransactionSubmissionType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deliberately takes no dependency on [com.jajusri.venture.feature.transaction.domain.port.TransactionSubmissionPort]
 * or any cross-company concept at all — this class only ever turns an already-resolved,
 * already-local [EstimatePo] into a share file and hands it to the OS chooser. There is no code
 * path here that could accidentally reach across a company boundary (the "no cross-company
 * transport accidentally invoked" property the WhatsApp path must hold is therefore a structural
 * fact about this class's dependency list, not something that needs a runtime check).
 */
@Singleton
class AndroidTransactionShareCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val cachePolicy: TransactionShareCachePolicy,
) : TransactionShareCoordinator {

    override suspend fun prepareShare(
        companyId: String,
        estimatePo: EstimatePo,
        priceVisibility: TransactionSharePriceVisibility,
        businessName: String?,
        buyerDisplayName: String?,
        terms: TermsAcknowledgment?,
    ): TransactionShareResult<PreparedTransactionShare> = withContext(dispatchers.io) {
        val content = when (val resolved = TransactionShareContent.resolve(estimatePo, priceVisibility, businessName, buyerDisplayName, terms)) {
            is TransactionShareResult.Failure -> return@withContext resolved
            is TransactionShareResult.Success -> resolved.value
        }

        runCatching {
            val directory = File(context.cacheDir, TransactionShareCachePolicy.CACHE_DIRECTORY)
            check(cachePolicy.acceptsDirectory(directory))
            check(directory.exists() || directory.mkdirs())
            check(cachePolicy.acceptsDirectory(directory))
            val suggestedName = suggestedFilename(estimatePo)
            val filename = "${UUID.randomUUID()}-$suggestedName"
            val file = File(directory, filename)
            cachePolicy.acquire(file)
            try {
                cachePolicy.cleanup(directory, System.currentTimeMillis())
                file.writeText(content)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.invoice-files", file)
                PreparedTransactionShare(uri.toString(), file.absolutePath, suggestedName)
            } catch (failure: Throwable) {
                cachePolicy.release(file)
                cachePolicy.discard(file)
                throw failure
            }
        }.fold(
            onSuccess = { TransactionShareResult.Success(it) },
            onFailure = { TransactionShareResult.Failure("The share file could not be generated. Please try again.") },
        )
    }

    override fun createShareIntent(prepared: PreparedTransactionShare): TransactionShareResult<Intent> {
        val contentUri = Uri.parse(prepared.contentUri)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = TEXT_MIME
            putExtra(Intent.EXTRA_STREAM, contentUri)
            clipData = ClipData.newUri(context.contentResolver, prepared.suggestedFilename, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (send.resolveActivity(context.packageManager) == null) {
            releaseShare(prepared)
            return TransactionShareResult.Failure("No app is available to share files.")
        }
        cachePolicy.protectShared(File(prepared.cacheFilePath), System.currentTimeMillis())
        cachePolicy.release(File(prepared.cacheFilePath))
        return TransactionShareResult.Success(Intent.createChooser(send, "Share"))
    }

    override fun releaseShare(prepared: PreparedTransactionShare) {
        cachePolicy.release(File(prepared.cacheFilePath))
    }

    private fun suggestedFilename(estimatePo: EstimatePo): String {
        val label = when (estimatePo.submissionType) {
            TransactionSubmissionType.Estimate -> "estimate"
            TransactionSubmissionType.PurchaseOrder -> "purchase-order"
        }
        return "venture-$label.txt"
    }

    private companion object {
        const val TEXT_MIME = "text/plain"
    }
}
