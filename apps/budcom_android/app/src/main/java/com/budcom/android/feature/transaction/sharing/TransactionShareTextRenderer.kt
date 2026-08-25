package com.budcom.android.feature.transaction.sharing

import com.budcom.android.feature.transaction.domain.model.EstimatePo
import com.budcom.android.feature.transaction.domain.model.PaymentTiming
import com.budcom.android.feature.transaction.domain.model.TermsAcknowledgment
import com.budcom.android.feature.transaction.domain.model.TransactionLineItem
import com.budcom.android.feature.transaction.domain.model.TransactionSubmissionType
import java.time.Instant

/**
 * Renders an [EstimatePo] as a plain-text share file — same deliberate-simplification reasoning as
 * [com.budcom.android.feature.catalogue.sharing.CatalogueShareTextRenderer]'s own doc comment (no
 * existing PDF layout precedent for this content shape, plain text is trivially, testably correct,
 * and the [TransactionShareCoordinator] contract this renders through does not change if a PDF
 * renderer replaces this later).
 *
 * Pure function, no Android/repository dependency — every price decision is driven entirely by
 * [priceVisibility] and each line's own already-resolved
 * [TransactionLineItem.isContactForPrice]/amount fields, nothing is re-derived from Catalogue here.
 */
object TransactionShareTextRenderer {

    fun render(
        estimatePo: EstimatePo,
        priceVisibility: TransactionSharePriceVisibility,
        businessName: String?,
        buyerDisplayName: String?,
        terms: TermsAcknowledgment?,
    ): String = buildString {
        appendLine(businessName?.trim()?.takeIf(String::isNotBlank) ?: "BUDCOM")
        appendLine(documentTypeLabel(estimatePo.submissionType))
        appendLine("Ref: ${estimatePo.estimatePoId}")
        appendLine("Date: ${Instant.ofEpochMilli(estimatePo.submittedAt.epochMillis)}")
        buyerDisplayName?.trim()?.takeIf(String::isNotBlank)?.let { appendLine("To: $it") }
        appendLine("=".repeat(40))
        appendLine()

        estimatePo.lineItems.forEach { line ->
            appendLine(line.snapshotProductName)
            val unitSuffix = line.snapshotUnit?.trim()?.takeIf(String::isNotBlank)?.let { " $it" } ?: ""
            appendLine("Qty: ${line.quantity}$unitSuffix")
            appendLine(priceLine(line, priceVisibility))
            appendLine("-".repeat(40))
        }
        appendLine()

        appendLine(totalLine(estimatePo, priceVisibility))

        terms?.let { appendTerms(it) }

        if (estimatePo.submissionType == TransactionSubmissionType.PurchaseOrder) {
            appendLine()
            appendLine("This is a Purchase Order.")
        } else {
            appendLine()
            appendLine("This is a non-binding Estimate.")
        }
    }

    private fun documentTypeLabel(submissionType: TransactionSubmissionType): String = when (submissionType) {
        TransactionSubmissionType.Estimate -> "Estimate"
        TransactionSubmissionType.PurchaseOrder -> "Purchase Order"
    }

    /**
     * The adversarial safety property this whole file exists to prove (task's own "IMPORTANT PRICE
     * RULE"): when [priceVisibility] is [TransactionSharePriceVisibility.Hidden], this function
     * never reads [line]'s amount fields at all — it cannot leak a number it never looks at. Mirrors
     * [com.budcom.android.feature.catalogue.sharing.CatalogueShareTextRenderer]'s own three-way
     * "actual price / no price supplied yet / Contact for price" distinction for the *visible* case,
     * adding the fourth, hard "not authorized right now" case on top.
     */
    private fun priceLine(line: TransactionLineItem, priceVisibility: TransactionSharePriceVisibility): String {
        if (priceVisibility == TransactionSharePriceVisibility.Hidden) return "Price: Contact for price"
        if (line.isContactForPrice) return "Price: Contact for price"
        val amount = line.lineTotalAmount?.trim()?.takeIf(String::isNotBlank)
            ?: return "Price: Not supplied yet"
        return "Price: $amount ${line.unitPriceCurrencyCode.orEmpty()}".trim()
    }

    private fun totalLine(estimatePo: EstimatePo, priceVisibility: TransactionSharePriceVisibility): String {
        if (priceVisibility == TransactionSharePriceVisibility.Hidden) return "Total: Contact for price"
        val anyContactForPrice = estimatePo.lineItems.any { it.isContactForPrice }
        val anyMissingAmount = estimatePo.lineItems.any { !it.isContactForPrice && it.lineTotalAmount.isNullOrBlank() }
        if (anyContactForPrice || anyMissingAmount) return "Total: Contact for price"
        return "Total: ${estimatePo.totalAmount} ${estimatePo.currencyCode.orEmpty()}".trim()
    }

    /** Structured, bounded fields only (Q11) — never a signature/attestation, never document-
     * generation of anything resembling a contract, and always explicitly labeled non-legal,
     * matching architecture §7's "not a legal contract, enforced at the data/UI level" rule. */
    private fun StringBuilder.appendTerms(terms: TermsAcknowledgment) {
        appendLine()
        appendLine("Terms acknowledged (not a legal contract):")
        appendLine("Payment: ${paymentTimingLabel(terms)}")
        terms.note?.trim()?.takeIf(String::isNotBlank)?.let { appendLine("Note: $it") }
    }

    private fun paymentTimingLabel(terms: TermsAcknowledgment): String = when (terms.paymentTiming) {
        PaymentTiming.Advance -> "Advance"
        PaymentTiming.OnDelivery -> "On delivery"
        PaymentTiming.CreditDays -> "Credit ${terms.creditDays ?: "?"} days"
        PaymentTiming.Partial -> "Partial (${terms.partialAdvancePercent ?: "?"}% advance)"
    }
}
