package com.budcom.android.feature.transaction.presentation

import android.content.Intent
import java.io.File
import com.budcom.android.feature.transaction.domain.model.BuyAgainEntry
import com.budcom.android.feature.transaction.domain.model.CanonicalOrder
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
    /** Real Catalogue Published products (architecture: only the customer-visible published
     * snapshot may ever appear on a buyer-facing surface — the same read
     * [com.budcom.android.feature.catalogue.sharing.CatalogueShareContent] itself uses). Loaded by
     * [TransactionComposerViewModel] directly from the existing `CatalogueRepository` — no
     * duplicate product table, no second pricing engine. */
    val newSkus: List<TransactionNewSkuRow> = emptyList(),
    val canonicalDraftOrder: CanonicalOrder? = null,
    val canonicalOrderStatusLabel: String? = null,
    val deliveryQueued: Boolean = false,
    /** Cart-photo fix: the same Catalogue primary-photo file already resolved for the Catalogue
     * product-list row (`CatalogueViewModel`'s own `primaryAssetFile`, same
     * `CatalogueRepository.listAssets`/`resolveAssetFile` calls, no new resolution logic) --
     * keyed by `linkedProductId` so any row on this screen (Selected, Previously Bought, New SKUs)
     * can look up the same product's photo without a separate resolution path. A missing entry or
     * a `null` value both mean "no photo," never a crash. */
    val productPhotos: Map<String, File?> = emptyMap(),
    /** True once loading has determined a completed transaction exists for this buyer — drives
     * whether the "Last Order" action is shown at all (task's own "if a previous completed
     * transaction does not exist, show a normal empty state, do not crash"). */
    val lastOrderAvailable: Boolean = false,
    val message: String? = null,
)

/** A "New SKUs" row — mapped 1:1 from `CataloguePublishedSnapshot` (architecture, Catalogue's own
 * customer-visible read model). `unit`/`sku` are `null` here because the published snapshot itself
 * does not carry those fields — an honest limitation, not a placeholder pretending otherwise; a
 * submitted line item's `snapshotUnit`/`snapshotSku` will simply be null for a product selected
 * this way, which is an already-supported nullable case, not a new one.
 *
 * [priceState] never reflects [TransactionDraftPriceState.Hidden] when mapped from a published
 * snapshot directly — that would require resolving this specific buyer's
 * [com.budcom.android.feature.transaction.domain.model.CatalogueAccessGrant], the still-unresolved
 * Q18 boundary (architecture Finding 3). This mapping deliberately stops at Catalogue's own
 * company-wide resolved state (Open+price / Open+no-price / Contact-for-price) and goes no further
 * — flagged here rather than guessed at. */
data class TransactionNewSkuRow(
    val linkedProductId: String,
    val displayName: String,
    val unit: String?,
    val sku: String?,
    val priceState: TransactionDraftPriceState,
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
    /** Q17/task's own "Last Order": replaces the current draft with a fresh one built from the
     * buyer's most recent completed transaction via
     * [com.budcom.android.feature.transaction.domain.model.ReorderOperations] — never mutates that
     * transaction (see [TransactionComposerViewModel]'s own KDoc on `reorderLastOrder`). A no-op
     * with a user-facing message if no completed transaction exists yet. */
    data object ReorderLastOrder : TransactionComposerEvent
    data object CreateDraftOrder : TransactionComposerEvent
    data object SendOrder : TransactionComposerEvent

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
