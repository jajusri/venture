package com.budcom.android.feature.transaction.presentation

import android.content.Intent
import com.budcom.android.feature.transaction.domain.model.BuyAgainEntry
import com.budcom.android.feature.transaction.domain.model.TransactionDraft
import com.budcom.android.feature.transaction.domain.model.TransactionDraftPriceState
import com.budcom.android.feature.transaction.domain.model.TransactionSubmissionType

/**
 * The buyer transaction composer's UI state — Q2/Q3/Q5's locked "everything happens in the same
 * screen" shape: [draft] (selected products, always rendered first) and [buyAgainEntries]
 * (previously-bought, rendered second, each immediately selectable inline — see
 * [TransactionComposerEvent.SelectBuyAgainItem]) coexist in one state object precisely so a
 * Composable screen never needs to navigate away to move between them. "New SKUs" (the third
 * section) is deliberately not modeled here at all — that is simply Catalogue's own existing
 * product list, read directly by whatever screen hosts this composer, never duplicated into this
 * feature's own state.
 */
data class TransactionComposerUiState(
    val isLoading: Boolean = true,
    val draft: TransactionDraft? = null,
    val buyAgainEntries: List<BuyAgainEntry> = emptyList(),
    val message: String? = null,
)

sealed interface TransactionComposerEvent {
    data class AddOrIncrementProduct(
        val linkedProductId: String,
        val snapshotProductName: String,
        val snapshotUnit: String?,
        val snapshotSku: String?,
        val priceState: TransactionDraftPriceState,
        val quantityToAdd: String = "1",
    ) : TransactionComposerEvent

    /** Selecting a Buy Again entry immediately adds/increments it into the selected-products
     * section (task's own "Selecting one should immediately add/populate it into the
     * selected-products area") — never a second, separate selection flow. */
    data class SelectBuyAgainItem(val entry: BuyAgainEntry, val priceState: TransactionDraftPriceState) : TransactionComposerEvent

    data class SetQuantity(val linkedProductId: String, val quantity: String) : TransactionComposerEvent
    data class RemoveProduct(val linkedProductId: String) : TransactionComposerEvent
    data class SubmissionTypeChanged(val type: TransactionSubmissionType) : TransactionComposerEvent

    /** WhatsApp path (Q6/Q7's `WHATSAPP_SHARED` channel) — the only delivery channel that is
     * fully, safely implemented end-to-end today. */
    data object ShareViaWhatsApp : TransactionComposerEvent

    /** In-app path (`IN_APP_SUBMITTED`). Only legitimately supported for the same-company case
     * ([com.budcom.android.feature.transaction.data.port.LocalTransactionSubmissionPort]'s own
     * documented scope) — a seller drafting on behalf of a walk-in/phone buyer they are directly
     * serving. This is NOT cross-company transport and does not attempt to be; exposing this event
     * at all (rather than disabling it) is deliberate, per this phase's own instruction to "expose
     * it only where the current local implementation legitimately supports it." */
    data object SubmitInApp : TransactionComposerEvent

    data object DismissMessage : TransactionComposerEvent
}

sealed interface TransactionComposerEffect {
    /** Hand this directly to `context.startActivity(...)` — the exact same OS-chooser mechanism
     * every other share flow in this app already uses; no WhatsApp-specific handling needed or
     * present anywhere in this class. */
    data class LaunchShareIntent(val intent: Intent) : TransactionComposerEffect
}
