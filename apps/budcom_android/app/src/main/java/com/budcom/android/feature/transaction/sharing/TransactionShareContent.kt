package com.budcom.android.feature.transaction.sharing

import com.budcom.android.feature.transaction.domain.model.EstimatePo
import com.budcom.android.feature.transaction.domain.model.TermsAcknowledgment

/**
 * The safety-relevant part of [TransactionShareCoordinator.prepareShare], factored out from
 * [AndroidTransactionShareCoordinator] so it carries no Android/Context dependency — mirrors
 * [com.budcom.android.feature.catalogue.sharing.CatalogueShareContent]'s own separation. An empty
 * line-item list is refused before any file is ever written, matching that precedent's
 * empty-content refusal.
 */
object TransactionShareContent {
    fun resolve(
        estimatePo: EstimatePo,
        priceVisibility: TransactionSharePriceVisibility,
        businessName: String?,
        buyerDisplayName: String?,
        terms: TermsAcknowledgment?,
    ): TransactionShareResult<String> {
        if (estimatePo.lineItems.isEmpty()) {
            return TransactionShareResult.Failure("No items to share yet.")
        }
        return TransactionShareResult.Success(
            TransactionShareTextRenderer.render(estimatePo, priceVisibility, businessName, buyerDisplayName, terms),
        )
    }
}
