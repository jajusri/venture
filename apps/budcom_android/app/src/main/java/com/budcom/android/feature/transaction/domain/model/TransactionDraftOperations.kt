package com.budcom.android.feature.transaction.domain.model

import com.budcom.android.feature.transaction.domain.repository.NewLineItem
import java.math.BigDecimal

/**
 * Pure editing/composition operations on a [TransactionDraft] — mirrors
 * [com.budcom.android.feature.catalogue.domain.model.CatalogueLifecycleTransitions]'s own
 * discipline (plain functions, no DAO/repository/coroutine dependency, an explicit rejected-result
 * type rather than throwing). This is the concrete mechanism behind the locked buyer-flow UX
 * (Q2/Q3/Q5): adding an already-selected product increments its quantity in place rather than
 * creating a duplicate row, "-/+" is always available on every product, and nothing here requires
 * leaving/reopening a separate selection screen — every operation returns a new, immediately
 * re-renderable [TransactionDraft].
 */
object TransactionDraftOperations {

    fun empty(companyId: String, buyerPartyId: String?, submissionType: TransactionSubmissionType): TransactionDraft =
        TransactionDraft(companyId, buyerPartyId, submissionType, emptyList())

    /**
     * Adds a new line, or increments an existing line's quantity by [quantityToAdd] if the same
     * product is already selected (Q2: "selected products automatically populate at the top... "
     * — the concrete guarantee that tapping "+" on an already-selected product never duplicates
     * it). [priceState] always overwrites the existing line's stored price state too, so
     * re-adding a product after its authorization/price changed reflects the current state, never
     * a stale one.
     */
    fun addOrIncrementLine(
        draft: TransactionDraft,
        linkedProductId: String,
        snapshotProductName: String,
        snapshotUnit: String?,
        snapshotSku: String?,
        priceState: TransactionDraftPriceState,
        quantityToAdd: String = "1",
    ): TransactionDraft {
        val existingIndex = draft.lines.indexOfFirst { it.linkedProductId == linkedProductId }
        if (existingIndex == -1) {
            val newLine = TransactionDraftLine(linkedProductId, snapshotProductName, snapshotUnit, snapshotSku, quantityToAdd, priceState)
            return draft.copy(lines = draft.lines + newLine)
        }
        val existing = draft.lines[existingIndex]
        val newQuantity = ((existing.quantity.toBigDecimalOrNullSafe() ?: BigDecimal.ZERO) + (quantityToAdd.toBigDecimalOrNullSafe() ?: BigDecimal.ZERO))
            .toPlainString()
        val updatedLines = draft.lines.toMutableList().also {
            it[existingIndex] = existing.copy(quantity = newQuantity, priceState = priceState)
        }
        return draft.copy(lines = updatedLines)
    }

    /** Sets a line's quantity directly. A quantity of zero or below removes the line entirely —
     * matching ordinary basket UX (an explicit "0" is the same as removing the item), never a
     * silently-invalid stored state. Preserves every other line's order and content unchanged. */
    fun setQuantity(draft: TransactionDraft, linkedProductId: String, quantity: String): TransactionDraft {
        val qty = quantity.toBigDecimalOrNullSafe() ?: return draft
        if (qty <= BigDecimal.ZERO) return removeLine(draft, linkedProductId)
        val index = draft.lines.indexOfFirst { it.linkedProductId == linkedProductId }
        if (index == -1) return draft
        val updated = draft.lines.toMutableList().also { it[index] = it[index].copy(quantity = quantity) }
        return draft.copy(lines = updated)
    }

    fun removeLine(draft: TransactionDraft, linkedProductId: String): TransactionDraft =
        draft.copy(lines = draft.lines.filterNot { it.linkedProductId == linkedProductId })

    sealed interface DraftToSubmissionResult {
        data class Success(val lineItems: List<NewLineItem>) : DraftToSubmissionResult
        data object EmptyDraft : DraftToSubmissionResult
        /** At least one line's [TransactionDraftPriceState] was [TransactionDraftPriceState.Hidden]
         * at submission time — refused rather than silently downgraded to Contact-for-price.
         * Submission (unlike a read-only WhatsApp re-share) is a commercial action; a buyer must
         * never be able to commit against a price they were never actually authorized to see. */
        data class UnauthorizedPriceLines(val linkedProductIds: List<String>) : DraftToSubmissionResult
    }

    /**
     * Converts a completed draft into the exact shape
     * [com.budcom.android.feature.transaction.domain.repository.TransactionRepository.createEstimatePo]
     * already accepts — this composition layer's entire purpose is to feed that existing seam, not
     * to open a new submission path. [DraftToSubmissionResult.Success.lineItems] can be passed to
     * `createEstimatePo` unchanged, and the returned `EstimatePo` can be passed to
     * [com.budcom.android.feature.transaction.sharing.TransactionShareCoordinator.prepareShare]
     * unchanged in turn — no additional glue code is needed between any of these three calls, by
     * construction.
     */
    fun toSubmission(draft: TransactionDraft): DraftToSubmissionResult {
        if (draft.lines.isEmpty()) return DraftToSubmissionResult.EmptyDraft
        val hidden = draft.lines.filter { it.priceState == TransactionDraftPriceState.Hidden }
        if (hidden.isNotEmpty()) return DraftToSubmissionResult.UnauthorizedPriceLines(hidden.map { it.linkedProductId })

        val lineItems = draft.lines.map { line ->
            val (amount, currency, isContactForPrice) = when (val state = line.priceState) {
                is TransactionDraftPriceState.ActualPrice -> Triple(state.unitAmount, state.currencyCode, false)
                TransactionDraftPriceState.NoPriceSupplied -> Triple(null, null, false)
                TransactionDraftPriceState.ContactForPrice -> Triple(null, null, true)
                TransactionDraftPriceState.Hidden -> error("unreachable — refused above")
            }
            val lineTotal = amount?.let { unitAmount ->
                val qty = line.quantity.toBigDecimalOrNullSafe()
                val unit = unitAmount.toBigDecimalOrNullSafe()
                if (qty != null && unit != null) (unit * qty).toPlainString() else null
            }
            NewLineItem(
                linkedProductId = line.linkedProductId,
                snapshotProductName = line.snapshotProductName,
                snapshotUnit = line.snapshotUnit,
                snapshotSku = line.snapshotSku,
                quantity = line.quantity,
                unitPriceAmount = amount,
                unitPriceCurrencyCode = currency,
                lineTotalAmount = lineTotal,
                isContactForPrice = isContactForPrice,
            )
        }
        return DraftToSubmissionResult.Success(lineItems)
    }
}
