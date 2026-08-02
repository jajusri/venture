package com.budcom.android.feature.voucher.presentation

import com.budcom.android.core.common.AppError
import com.budcom.android.feature.masterdata.presentation.MasterDataUiError
import com.budcom.android.feature.masterdata.presentation.toMasterDataUiError
import com.budcom.android.feature.voucher.domain.model.VoucherDetails
import com.budcom.android.feature.voucher.domain.model.VoucherInventoryLine
import com.budcom.android.feature.voucher.domain.model.VoucherLedgerLine
import com.budcom.android.feature.voucher.domain.model.VoucherMoney
import com.budcom.android.feature.voucher.domain.model.VoucherCacheState

data class VoucherDetailsUiState(
    val voucherId: String = "",
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isOnline: Boolean = true,
    val details: VoucherDetailsContentUi? = null,
    val error: MasterDataUiError? = null,
    val canShareInvoice: Boolean = false,
    val showShareOptions: Boolean = false,
    val isShareBusy: Boolean = false,
    val shareMessage: String? = null,
    val shareError: String? = null,
    val cacheState: VoucherCacheState = VoucherCacheState.NoCache,
    val lastSyncedAt: Long? = null,
) {
    val isBusy: Boolean get() = isInitialLoading || isRefreshing
    val hasContent: Boolean get() = details != null
}

data class VoucherDetailsContentUi(
    val id: String,
    val documentTitle: String,
    val partyHeading: String,
    val typeLabel: String,
    val numberLabel: String,
    val dateLabel: String,
    val effectiveDateLabel: String?,
    val partyLabel: String?,
    val referenceLabel: String?,
    val amountLabel: String?,
    val statusLabel: String,
    val dataQualityLabel: String,
    val narration: String?,
    val ledgerLines: List<VoucherLedgerLineUi>,
    val inventoryLines: List<VoucherInventoryLineUi>,
)

data class VoucherLedgerLineUi(
    val lineNumber: Int,
    val ledgerName: String,
    val amountLabel: String,
    val deemedPositiveLabel: String?,
)

data class VoucherInventoryLineUi(
    val lineNumber: Int,
    val itemName: String,
    val quantityLabel: String?,
    val rateLabel: String? = null,
    val amountLabel: String?,
)

sealed interface VoucherDetailsEvent {
    data object Load : VoucherDetailsEvent
    data object Refresh : VoucherDetailsEvent
    data object Retry : VoucherDetailsEvent
    data object OpenShareOptions : VoucherDetailsEvent
    data object DismissShareOptions : VoucherDetailsEvent
    data object SharePdf : VoucherDetailsEvent
    data object ShareSummary : VoucherDetailsEvent
    data object SavePdf : VoucherDetailsEvent
    data class SaveDestinationSelected(val uri: android.net.Uri?) : VoucherDetailsEvent
    data class ShareActivityFinished(val cancelled: Boolean) : VoucherDetailsEvent
}

internal fun AppError.toVoucherDetailsUiError(): MasterDataUiError = toMasterDataUiError()

internal fun VoucherDetails.toContentUi(): VoucherDetailsContentUi {
    val summary = summary
    return VoucherDetailsContentUi(
        id = summary.identity.id,
        documentTitle = summary.type.toDocumentTitle(),
        partyHeading = summary.type.toPartyHeading(),
        typeLabel = summary.type,
        numberLabel = summary.number?.takeIf { it.isNotBlank() } ?: "—",
        dateLabel = summary.date,
        effectiveDateLabel = effectiveDate,
        partyLabel = summary.partyName,
        referenceLabel = summary.referenceNumber,
        amountLabel = summary.amount.formatAmount(),
        statusLabel = summary.status.name.lowercase().replaceFirstChar { it.titlecase() },
        dataQualityLabel = summary.dataQuality.name.lowercase().replaceFirstChar { it.titlecase() },
        narration = narration,
        ledgerLines = ledgerEntries.map { it.toLineUi() },
        inventoryLines = inventoryEntries.map { it.toLineUi() },
    )
}

private fun String.toDocumentTitle(): String = when (trim().lowercase()) {
    "sales" -> "Sales invoice"
    "purchase" -> "Purchase invoice"
    "payment" -> "Payment voucher"
    "receipt" -> "Receipt voucher"
    "contra" -> "Contra voucher"
    "credit note" -> "Credit note"
    "debit note" -> "Debit note"
    else -> "$this voucher"
}

private fun String.toPartyHeading(): String = when (trim().lowercase()) {
    "sales", "credit note" -> "Bill to"
    "purchase", "debit note" -> "Supplier"
    else -> "Account / party"
}

private fun VoucherLedgerLine.toLineUi(): VoucherLedgerLineUi = VoucherLedgerLineUi(
    lineNumber = lineNumber,
    ledgerName = ledgerName,
    amountLabel = amount.formatAmount() ?: "—",
    deemedPositiveLabel = isDeemedPositive?.let { if (it) "Deemed positive: yes" else "Deemed positive: no" },
)

private fun VoucherInventoryLine.toLineUi(): VoucherInventoryLineUi = VoucherInventoryLineUi(
    lineNumber = lineNumber,
    itemName = itemName,
    quantityLabel = quantity,
    rateLabel = rate?.takeIf { it.isNotBlank() },
    amountLabel = amount.formatAmount(),
)

private fun VoucherMoney?.formatAmount(): String? {
    val money = this ?: return null
    val side = money.side?.name?.lowercase()
    return if (side != null) "${money.value} ($side)" else money.value
}
