package com.budcom.android.feature.masterdata.ledger.presentation

import com.budcom.android.core.common.AppError
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatement
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatementAmount
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatementTransaction
import com.budcom.android.feature.masterdata.presentation.MasterDataUiError
import com.budcom.android.feature.masterdata.presentation.toMasterDataUiError

data class LedgerStatementUiState(
    val ledgerId: String = "",
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isOnline: Boolean = true,
    val fromDate: String = "",
    val toDate: String = "",
    val content: LedgerStatementContentUi? = null,
    val error: MasterDataUiError? = null,
    /** Set only when a manual refresh fails while cached content remains visible. */
    val refreshError: String? = null,
    val showShareOptions: Boolean = false,
    val isShareBusy: Boolean = false,
    val shareMessage: String? = null,
    val shareError: String? = null,
) {
    val isBusy: Boolean get() = isInitialLoading || isRefreshing
    val hasContent: Boolean get() = content != null
}

data class LedgerStatementContentUi(
    val ledgerName: String,
    val parentGroup: String?,
    val periodLabel: String,
    val openingLabel: String,
    val closingLabel: String,
    val rows: List<LedgerStatementRowUi>,
    val coverageMessage: String?,
    val lastSyncedAt: Long?,
)

data class LedgerStatementRowUi(
    val voucherId: String,
    val dateLabel: String,
    val voucherTypeLabel: String,
    val voucherNumberLabel: String,
    val particularsLabel: String?,
    val debitLabel: String?,
    val creditLabel: String?,
    val runningBalanceLabel: String?,
)

sealed interface LedgerStatementEvent {
    data object Load : LedgerStatementEvent
    data object Refresh : LedgerStatementEvent
    data object Retry : LedgerStatementEvent
    data class PeriodChanged(val from: String, val to: String) : LedgerStatementEvent
    /** Ignored for a blank id — a transaction row always carries the real Voucher identity, so a
     * blank id here means the row itself is malformed, never a legitimate deep link to reject. */
    data class TransactionTapped(val voucherId: String) : LedgerStatementEvent
    data object OpenShareOptions : LedgerStatementEvent
    data object DismissShareOptions : LedgerStatementEvent
    data object SharePdf : LedgerStatementEvent
    data object SavePdf : LedgerStatementEvent
    data class SaveDestinationSelected(val uri: android.net.Uri?) : LedgerStatementEvent
    data class ShareActivityFinished(val cancelled: Boolean) : LedgerStatementEvent
}

sealed interface LedgerStatementEffect {
    data class OpenVoucherDetails(val voucherId: String) : LedgerStatementEffect
}

sealed interface LedgerStatementShareEffect {
    val operationId: Long
    data class LaunchShare(override val operationId: Long, val intent: android.content.Intent) : LedgerStatementShareEffect
    data class CreatePdfDocument(override val operationId: Long, val suggestedFilename: String) : LedgerStatementShareEffect
}

internal fun AppError.toLedgerStatementUiError(): MasterDataUiError = toMasterDataUiError()

internal fun LedgerStatement.toContentUi(lastSyncedAt: Long?): LedgerStatementContentUi = LedgerStatementContentUi(
    ledgerName = ledgerName,
    parentGroup = parentGroup,
    periodLabel = "${period.from} to ${period.to}",
    openingLabel = openingBalance.toLabel() ?: "Not available",
    closingLabel = closingBalance.toLabel() ?: "Not available",
    rows = transactions.map { it.toRowUi() },
    coverageMessage = coverage.message,
    lastSyncedAt = lastSyncedAt,
)

private fun LedgerStatementTransaction.toRowUi(): LedgerStatementRowUi = LedgerStatementRowUi(
    voucherId = voucherId,
    dateLabel = date,
    voucherTypeLabel = voucherType,
    voucherNumberLabel = voucherNumber?.takeIf { it.isNotBlank() } ?: "—",
    particularsLabel = listOfNotNull(
        referenceNumber?.takeIf { it.isNotBlank() }?.let { "Ref: $it" },
        narration?.takeIf { it.isNotBlank() },
    ).joinToString(" · ").takeIf { it.isNotBlank() },
    debitLabel = debit,
    creditLabel = credit,
    runningBalanceLabel = runningBalance.toLabel(),
)

internal fun LedgerStatementAmount?.toLabel(): String? {
    val value = this ?: return null
    return "${value.amount} ${value.side.name}"
}
