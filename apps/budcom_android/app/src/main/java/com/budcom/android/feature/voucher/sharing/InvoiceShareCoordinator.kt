package com.budcom.android.feature.voucher.sharing

import android.content.Intent
import android.net.Uri
import com.budcom.android.feature.voucher.domain.model.VoucherDataQuality
import com.budcom.android.feature.voucher.domain.model.VoucherDetails
import com.budcom.android.feature.voucher.domain.model.VoucherStatus

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

fun VoucherDetails.isShareableInvoice(): Boolean =
    summary.type.trim().equals("sales", ignoreCase = true) &&
        summary.status == VoucherStatus.Active &&
        summary.dataQuality == VoucherDataQuality.Complete &&
        !summary.number.isNullOrBlank() &&
        summary.date.isNotBlank()

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
