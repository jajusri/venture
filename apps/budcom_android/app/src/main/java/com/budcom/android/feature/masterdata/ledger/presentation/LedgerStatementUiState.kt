package com.budcom.android.feature.masterdata.ledger.presentation

import com.budcom.android.core.common.AppError
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerPeriodSelection
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatement
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatementAmount
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatementMode
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatementTransaction
import com.budcom.android.feature.masterdata.ledger.sharing.LedgerSharingPreferences
import com.budcom.android.feature.masterdata.ledger.sharing.LedgerShareDestination
import com.budcom.android.feature.masterdata.ledger.sharing.PreparedLedgerStatementPdf
import com.budcom.android.feature.masterdata.ledger.sharing.RecipientResolutionSource
import com.budcom.android.feature.masterdata.ledger.sharing.resolveWhatsAppRecipient
import com.budcom.android.feature.masterdata.presentation.MasterDataUiError
import com.budcom.android.feature.masterdata.presentation.toMasterDataUiError

data class LedgerStatementUiState(
    val ledgerId: String = "",
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isOnline: Boolean = true,
    val periodSelection: LedgerPeriodSelection = LedgerPeriodSelection.Last7Sales,
    /** Last RESOLVED concrete range (output of [periodSelection], not an independent input) —
     * populated after a successful local read; used to pre-fill the Custom-period dialog and to
     * scope [LedgerStatementEvent.Refresh]'s bounded Voucher-sync window. */
    val fromDate: String = "",
    val toDate: String = "",
    val content: LedgerStatementContentUi? = null,
    val error: MasterDataUiError? = null,
    /** Set only when a manual refresh fails while cached content remains visible. */
    val refreshError: String? = null,
    /** Remembered Ledger Sharing defaults (Settings -> Ledger Sharing). The normal Share Ledger
     * tap uses these immediately; see [LedgerStatementEvent.ShareLedgerFast]. */
    val sharingPreferences: LedgerSharingPreferences = LedgerSharingPreferences(),
    /** True while the advanced/change-options sheet (long-press on Share Ledger) is open. */
    val showShareOptions: Boolean = false,
    /** One-time overrides live only while the advanced sheet is open — `null` means "use the
     * current on-screen period" (for period) or "use the remembered default" (mode/destination).
     * Confirming a share from the advanced sheet never writes these back as the new persisted
     * default — only Settings -> Ledger Sharing changes the default. */
    val advancedPeriod: LedgerPeriodSelection? = null,
    val advancedStatementMode: LedgerStatementMode? = null,
    val advancedDestination: LedgerShareDestination? = null,
    val isShareBusy: Boolean = false,
    val shareMessage: String? = null,
    val shareError: String? = null,
    /** TD-028: set once the statement PDF has been prepared for preview; the same generated file
     * Save/Share act on. Null when preview is not open. */
    val previewPdf: PreparedLedgerStatementPdf? = null,
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
    val whatsAppToPartyAvailable: Boolean,
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
    data class PeriodSelected(val period: LedgerPeriodSelection) : LedgerStatementEvent
    /** Ignored for a blank id — a transaction row always carries the real Voucher identity, so a
     * blank id here means the row itself is malformed, never a legitimate deep link to reject. */
    data class TransactionTapped(val voucherId: String) : LedgerStatementEvent
    /** Normal Share Ledger tap — uses [LedgerStatementUiState.sharingPreferences] and the
     * currently displayed period immediately, with no options screen. */
    data object ShareLedgerFast : LedgerStatementEvent
    /** Long-press on Share Ledger — opens the advanced/change-options sheet for a one-time
     * override; see [LedgerStatementUiState.advancedPeriod] and siblings. */
    data object OpenShareOptions : LedgerStatementEvent
    data object DismissShareOptions : LedgerStatementEvent
    data class AdvancedPeriodChanged(val period: LedgerPeriodSelection) : LedgerStatementEvent
    data class AdvancedStatementModeChanged(val mode: LedgerStatementMode) : LedgerStatementEvent
    /** Confirms the advanced sheet for exactly one share to [destination] — never persisted as
     * the new default. Also the sole path for Save PDF and Preview PDF, which are destinations
     * rather than separate top-level actions. */
    data class AdvancedShare(val destination: LedgerShareDestination) : LedgerStatementEvent
    data object DismissPreview : LedgerStatementEvent
    data object SaveFromPreview : LedgerStatementEvent
    data object ShareFromPreview : LedgerStatementEvent
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
    // No explicit mobile/phone field exists locally today (reserved for MVP-1.1 Connect), so this
    // only ever resolves via the Alias fallback or not at all — never a Connector/network call.
    whatsAppToPartyAvailable = resolveWhatsAppRecipient(explicitMobile = null, alias = ledgerAlias).source != RecipientResolutionSource.None,
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
