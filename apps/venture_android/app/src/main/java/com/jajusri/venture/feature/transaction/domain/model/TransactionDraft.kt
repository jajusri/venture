package com.jajusri.venture.feature.transaction.domain.model

import java.math.BigDecimal

/**
 * A buyer's in-progress, editable selection of Catalogue products + quantities before submission
 * (Catalogue's own locked Q1-5 "persistent basket" UX; Q6's deliberate, separate Estimate/PO
 * submission action). Deliberately **not** a Room entity — this is ephemeral composition state
 * that only becomes durable the moment [com.jajusri.venture.feature.transaction.domain.repository.TransactionRepository.createEstimatePo]
 * is actually called (via [TransactionDraftOperations.toSubmission]); Q5 itself never specifies a
 * persistence requirement for this state ("one continuous, persistent, expandable/collapsible
 * state" describes UI behavior, not a database table), so adding one here would be speculative
 * infrastructure this task's own governing brief explicitly warns against inventing.
 */
data class TransactionDraft(
    val companyId: String,
    val buyerPartyId: String?,
    val submissionType: TransactionSubmissionType,
    val lines: List<TransactionDraftLine>,
) {
    val isEmpty: Boolean get() = lines.isEmpty()

    /**
     * `null` whenever ANY line's price isn't currently an actual, showable amount — never a
     * partial/best-effort sum, and never derived from "does an amount happen to exist" (task's
     * own Phase 10 rule: visibility must never be inferred from amount presence). Mirrors
     * [com.jajusri.venture.feature.transaction.sharing.TransactionShareTextRenderer]'s own
     * "any Contact-for-price or missing line collapses the whole total" discipline.
     */
    val totalAmount: String?
        get() {
            if (lines.isEmpty()) return null
            val lineTotals = lines.map { line ->
                val state = line.priceState as? TransactionDraftPriceState.ActualPrice ?: return null
                val unit = state.unitAmount.toBigDecimalOrNullSafe() ?: return null
                val qty = line.quantity.toBigDecimalOrNullSafe() ?: return null
                unit * qty
            }
            return lineTotals.fold(BigDecimal.ZERO, BigDecimal::plus).toPlainString()
        }
}

data class TransactionDraftLine(
    val linkedProductId: String,
    val snapshotProductName: String,
    val snapshotUnit: String?,
    val snapshotSku: String?,
    val quantity: String,
    val priceState: TransactionDraftPriceState,
)

/**
 * Q4/Q18's locked four-state price distinction, applied at basket-composition time. The caller (a
 * future ViewModel) resolves this per line from Catalogue's price-display mode + override chain +
 * [CatalogueAccessGrant] before adding/updating a draft line — this domain layer only stores and
 * preserves whatever was resolved, exactly matching how
 * [com.jajusri.venture.feature.transaction.sharing.TransactionSharePriceVisibility] is deliberately
 * received pre-resolved by the sharing layer rather than computed there.
 */
sealed interface TransactionDraftPriceState {
    data class ActualPrice(val unitAmount: String, val currencyCode: String?) : TransactionDraftPriceState
    data object NoPriceSupplied : TransactionDraftPriceState
    data object ContactForPrice : TransactionDraftPriceState
    /** Not currently authorized for this buyer (no active [CatalogueAccessGrant] and the catalogue/
     * override chain resolves to Contact-for-price) — distinct from [ContactForPrice] itself
     * (the seller's own deliberate per-item choice) so a future submission-time gate
     * ([TransactionDraftOperations.toSubmission]) can refuse a commit against an unauthorized
     * price without conflating it with an ordinary Contact-for-price item. */
    data object Hidden : TransactionDraftPriceState
}

internal fun String.toBigDecimalOrNullSafe(): BigDecimal? = runCatching { BigDecimal(this) }.getOrNull()
