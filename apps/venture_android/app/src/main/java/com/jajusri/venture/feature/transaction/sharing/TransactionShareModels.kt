package com.jajusri.venture.feature.transaction.sharing

import android.content.Intent

sealed interface TransactionShareResult<out T> {
    data class Success<T>(val value: T) : TransactionShareResult<T>
    data class Failure(val message: String) : TransactionShareResult<Nothing>
}

data class PreparedTransactionShare(
    val contentUri: String,
    val cacheFilePath: String,
    val suggestedFilename: String,
)

/**
 * Whether the recipient this share is being prepared for is currently authorized to see this
 * Estimate/PO's prices at all (docs/architecture/VENTURE-TRANSACTION-MODE-ARCHITECTURE.md §10, task's
 * own "IMPORTANT PRICE RULE"). Deliberately **not** resolved inside this `sharing` package — this
 * boundary receives an already-resolved decision so it carries no dependency on Catalogue's live
 * override-resolution chain or [com.jajusri.venture.feature.transaction.domain.model.CatalogueAccessGrant];
 * a future caller (the not-yet-built submission/share ViewModel) resolves this from
 * `CatalogueRepository`/`TransactionRepository.findActiveCatalogueAccessGrant` at share-preparation
 * time and passes the result in here. Keeping the resolution *outside* this pure/testable layer is
 * what makes the adversarial "never leak a hidden price" guarantee (below) checkable by a plain unit
 * test with no repository/Android dependency at all.
 */
enum class TransactionSharePriceVisibility {
    /** The recipient may see actual numeric prices for lines where a price was actually supplied. */
    Visible,
    /** The recipient is NOT authorized to see prices right now — every line renders as "Contact for
     * price" regardless of what its own snapshot amount/[com.jajusri.venture.feature.transaction.domain.model.TransactionLineItem.isContactForPrice]
     * flag says. This is the state that protects against accidentally leaking a hidden price: the
     * renderer never even looks at a line's stored amount when this is selected. */
    Hidden,
}

/**
 * Reuses the existing, proven Android `Intent.ACTION_SEND` share-sheet pattern — the exact same
 * mechanism [com.jajusri.venture.feature.catalogue.sharing.CatalogueShareCoordinator] already uses
 * for Catalogue content, itself reusing the original Ledger-statement/Voucher-PDF precedent
 * (PDL-020 §8). No second WhatsApp integration, no direct WhatsApp-package intent — the OS chooser
 * (which already lists WhatsApp/WhatsApp Business when installed) is the entire "WhatsApp
 * integration" this codebase has ever built, and this coordinator is deliberately just another
 * producer into that same chooser, not a new one.
 */
interface TransactionShareCoordinator {
    suspend fun prepareShare(
        companyId: String,
        estimatePo: com.jajusri.venture.feature.transaction.domain.model.EstimatePo,
        priceVisibility: TransactionSharePriceVisibility,
        businessName: String?,
        buyerDisplayName: String?,
        terms: com.jajusri.venture.feature.transaction.domain.model.TermsAcknowledgment? = null,
    ): TransactionShareResult<PreparedTransactionShare>

    fun createShareIntent(prepared: PreparedTransactionShare): TransactionShareResult<Intent>
    fun releaseShare(prepared: PreparedTransactionShare)
}
