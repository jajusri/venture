package com.budcom.android.feature.transaction.domain.model

/**
 * "Edit last order" = CREATE A NEW TRANSACTION FROM THE OLD ONE, never mutate the old one (task's
 * own explicit rule for this phase). This object is a pure read-then-construct function: it takes
 * already-fetched data and returns a brand-new [TransactionDraft] — there is no repository/DAO
 * dependency here at all, so there is structurally no way for this code to write back to the
 * original [com.budcom.android.feature.transaction.domain.model.CommercialTransaction] or its line
 * items. Immutability of the original transaction is therefore a compile-time fact about this
 * object's dependency list, the same guarantee
 * [com.budcom.android.feature.transaction.sharing.AndroidTransactionShareCoordinator]'s own doc
 * comment already relies on for "no cross-company transport accidentally invoked."
 */
object ReorderOperations {

    /**
     * @param lineItems the completed order's own line items (immutable snapshots already —
     *   architecture §4 — so reusing their `snapshotProductName`/`snapshotUnit`/`snapshotSku` here
     *   is safe even if the underlying Catalogue product has since changed or been archived).
     * @param resolvePriceState resolves each line's **current** price/authorization state, never
     *   the historical price the original order actually used — a reorder must reflect today's
     *   price and today's access grant, not silently carry forward a stale one. Deliberately a
     *   caller-supplied function (mirrors [TransactionDraftPriceState]'s own "received
     *   pre-resolved" discipline) so this object stays free of any Catalogue/access-grant
     *   dependency.
     */
    fun fromCompletedTransaction(
        companyId: String,
        buyerPartyId: String?,
        submissionType: TransactionSubmissionType,
        lineItems: List<TransactionLineItem>,
        resolvePriceState: (TransactionLineItem) -> TransactionDraftPriceState,
    ): TransactionDraft {
        val lines = lineItems.mapNotNull { item ->
            val productId = item.linkedProductId ?: return@mapNotNull null
            TransactionDraftLine(
                linkedProductId = productId,
                snapshotProductName = item.snapshotProductName,
                snapshotUnit = item.snapshotUnit,
                snapshotSku = item.snapshotSku,
                quantity = item.quantity,
                priceState = resolvePriceState(item),
            )
        }
        return TransactionDraft(companyId, buyerPartyId, submissionType, lines)
    }
}
