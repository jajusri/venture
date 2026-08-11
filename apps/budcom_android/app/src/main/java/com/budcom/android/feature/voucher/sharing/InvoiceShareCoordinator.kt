package com.budcom.android.feature.voucher.sharing

import android.content.Intent
import android.net.Uri
import com.budcom.android.feature.voucher.domain.model.VoucherDataQuality
import com.budcom.android.feature.voucher.domain.model.VoucherDetails
import com.budcom.android.feature.voucher.domain.model.VoucherStatus
import com.budcom.android.feature.voucher.domain.model.VoucherSummary

interface InvoiceShareCoordinator {
    suspend fun preparePdf(details: VoucherDetails): InvoiceShareResult<PreparedInvoicePdf>
    fun createPdfShareIntent(pdf: PreparedInvoicePdf): InvoiceShareResult<Intent>
    fun createSummaryShareIntent(details: VoucherDetails, companyName: String?): InvoiceShareResult<Intent>
    suspend fun savePdf(pdf: PreparedInvoicePdf, destination: Uri): InvoiceShareResult<Unit>
    fun releasePdf(pdf: PreparedInvoicePdf)
}

data class PreparedInvoicePdf(
    val contentUri: String,
    val cacheFilePath: String,
    val suggestedFilename: String,
)

sealed interface InvoiceShareResult<out T> {
    data class Success<T>(val value: T) : InvoiceShareResult<T>
    data class Failure(val message: String) : InvoiceShareResult<Nothing>
}

fun VoucherDetails.isShareableInvoice(): Boolean = shareIneligibilityReason() == null

/**
 * Concise, honest reason sharing is unavailable for this voucher, or `null` when it is
 * eligible. Mirrors the conditions in [isShareableInvoice] so the UI can explain an
 * ineligible voucher instead of silently hiding the share action. A pure function of this
 * voucher's own [summary] — belongs to the individual voucher, never affected by any other
 * voucher (same party, other transaction type, or otherwise).
 */
fun VoucherDetails.shareIneligibilityReason(): String? = summary.summaryShareIneligibilityReason()

/**
 * The subset of [shareIneligibilityReason] decidable from [VoucherSummary] alone (no ledger/
 * inventory lines required) — used both by the full-details path above and by
 * [VoucherDetailsViewModel] when only the cached summary is available, so a voucher whose
 * details have not been individually downloaded yet still gets an honest explanation instead
 * of the share action silently vanishing.
 */
fun VoucherSummary.summaryShareIneligibilityReason(): String? {
    if (!type.trim().equals("sales", ignoreCase = true)) {
        return "Only sales vouchers can be shared as an invoice."
    }
    if (status != VoucherStatus.Active) {
        return "Cancelled vouchers cannot be shared as an invoice."
    }
    if (dataQuality != VoucherDataQuality.Complete) {
        return "This voucher's synced data is incomplete, so it cannot be shared as an invoice."
    }
    if (number.isNullOrBlank()) {
        return "This voucher is missing a voucher number, so it cannot be shared as an invoice."
    }
    if (date.isBlank()) {
        return "This voucher is missing a date, so it cannot be shared as an invoice."
    }
    return null
}

fun sanitizedInvoiceFilename(invoiceNumber: String): String {
    val safe = invoiceNumber
        .trim()
        .replace(Regex("[^A-Za-z0-9._-]+"), "-")
        .trim('.', '-', '_')
        .take(80)
        .ifBlank { "invoice" }
    return "BUDCOM-Invoice-$safe.pdf"
}

fun buildInvoiceSummary(details: VoucherDetails, companyName: String?): String = buildList {
    companyName?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
    details.summary.number?.trim()?.takeIf { it.isNotBlank() }?.let { add("Invoice: $it") }
    details.summary.date.trim().takeIf { it.isNotBlank() }?.let { add("Date: $it") }
    details.summary.partyName?.trim()?.takeIf { it.isNotBlank() }?.let { add("Party: $it") }
    details.summary.amount?.value?.trim()?.takeIf { it.isNotBlank() }?.let { add("Total: $it") }
    add("Shared from BUDCOM")
}.joinToString("\n")
