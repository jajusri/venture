package com.budcom.android.feature.transaction.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budcom.android.feature.catalogue.domain.model.CataloguePriceState
import com.budcom.android.feature.catalogue.domain.model.CataloguePublishedSnapshot
import com.budcom.android.feature.catalogue.domain.model.resolveCataloguePriceState
import com.budcom.android.feature.catalogue.domain.repository.CatalogueRepository
import com.budcom.android.feature.company.domain.port.CompanySessionPort
import com.budcom.android.feature.transaction.domain.model.BuyAgainEntry
import com.budcom.android.feature.transaction.domain.model.BuyAgainListBuilder
import com.budcom.android.feature.transaction.domain.model.CanonicalOrderState
import com.budcom.android.feature.transaction.domain.model.CanonicalOrderStatusLabels
import com.budcom.android.feature.transaction.domain.model.CommercialTransaction
import com.budcom.android.feature.transaction.domain.model.CommercialTransactionState
import com.budcom.android.feature.transaction.domain.model.ReorderOperations
import com.budcom.android.feature.transaction.domain.model.TransactionClock
import com.budcom.android.feature.transaction.domain.model.TransactionDraft
import com.budcom.android.feature.transaction.domain.model.TransactionDraftOperations
import com.budcom.android.feature.transaction.domain.model.TransactionDraftOperations.DraftToSubmissionResult
import com.budcom.android.feature.transaction.domain.model.TransactionDraftPriceState
import com.budcom.android.feature.transaction.domain.model.TransactionEntryPointType
import com.budcom.android.feature.transaction.domain.model.TransactionSubmissionType
import com.budcom.android.feature.transaction.domain.port.RelayOutboxDispatcher
import com.budcom.android.feature.transaction.domain.repository.TransactionRepository
import com.budcom.android.feature.transaction.domain.model.TransactionDeliveryChannel
import com.budcom.android.feature.transaction.sharing.TransactionShareCoordinator
import com.budcom.android.feature.transaction.sharing.TransactionShareResult
import com.budcom.android.feature.transaction.sharing.TransactionSharePriceVisibility
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * The buyer transaction composer's application/orchestration layer — the first real caller of
 * [TransactionDraftOperations]/[BuyAgainListBuilder]/[TransactionRepository.createEstimatePo]/
 * [TransactionShareCoordinator], wired together exactly as
 * `docs/status/BUDCOM-DEVELOPMENT-LEDGER.md` §57/§58 already documented they compose (no glue code
 * needed beyond this orchestration itself). No `@Composable` screen calls this yet — see this
 * phase's own Ledger entry for why that boundary was drawn where it was.
 *
 * Mirrors [com.budcom.android.feature.catalogue.presentation.CatalogueDetailViewModel]'s own shape
 * exactly: `SavedStateHandle` arg, `MutableStateFlow` ui state, `MutableSharedFlow` one-shot
 * effects, an `onEvent(event)` dispatcher.
 */
@HiltViewModel
class TransactionComposerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: TransactionRepository,
    private val catalogueRepository: CatalogueRepository,
    private val shareCoordinator: TransactionShareCoordinator,
    private val companySession: CompanySessionPort,
    private val clock: TransactionClock,
    private val relayOutboxDispatcher: RelayOutboxDispatcher = RelayOutboxDispatcher { },
) : ViewModel() {

    private val buyerPartyId: String? = savedStateHandle.get<String>(BUYER_PARTY_ID_ARG)

    private val _uiState = MutableStateFlow(TransactionComposerUiState())
    val uiState: StateFlow<TransactionComposerUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<TransactionComposerEffect>(extraBufferCapacity = 1)
    val effects = _effects.asSharedFlow()

    private var companyId: String? = null

    /** Cached by [loadLastOrder], never exposed directly via [uiState] — only
     * [TransactionComposerUiState.lastOrderAvailable] (a plain boolean) is. Never written to by
     * anything in this class: [reorderLastOrder] only ever *reads* this transaction to build a new
     * [TransactionDraft], the same structural "cannot mutate the original" guarantee
     * [ReorderOperations] itself already relies on (no repository write dependency at all). */
    private var lastCompletedTransaction: CommercialTransaction? = null
    private val draftOrderCreationKey: String = savedStateHandle.get<String>(DRAFT_ORDER_CREATION_KEY)
        ?: UUID.randomUUID().toString().also { savedStateHandle[DRAFT_ORDER_CREATION_KEY] = it }

    init {
        viewModelScope.launch {
            val resolvedCompanyId = companySession.observeSelectedCompanyId().first()
            companyId = resolvedCompanyId
            if (resolvedCompanyId == null) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }
            _uiState.update {
                it.copy(draft = TransactionDraftOperations.empty(resolvedCompanyId, buyerPartyId, TransactionSubmissionType.Estimate))
            }
            loadBuyAgain(resolvedCompanyId)
            loadNewSkus(resolvedCompanyId)
            loadLastOrder(resolvedCompanyId)
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun onEvent(event: TransactionComposerEvent) {
        when (event) {
            is TransactionComposerEvent.AddOrIncrementProduct -> updateDraft {
                TransactionDraftOperations.addOrIncrementLine(
                    it, event.linkedProductId, event.snapshotProductName, event.snapshotUnit,
                    event.snapshotSku, event.priceState, event.quantityToAdd,
                )
            }
            is TransactionComposerEvent.SelectBuyAgainItem -> selectBuyAgainItem(event.entry, event.priceState)
            is TransactionComposerEvent.SetQuantity -> updateDraft { TransactionDraftOperations.setQuantity(it, event.linkedProductId, event.quantity) }
            is TransactionComposerEvent.RemoveProduct -> updateDraft { TransactionDraftOperations.removeLine(it, event.linkedProductId) }
            is TransactionComposerEvent.SubmissionTypeChanged -> updateDraft { it.copy(submissionType = event.type) }
            TransactionComposerEvent.ReorderLastOrder -> reorderLastOrder()
            TransactionComposerEvent.CreateDraftOrder -> createDraftOrder()
            TransactionComposerEvent.SendOrder -> sendOrder()
            TransactionComposerEvent.ShareViaWhatsApp -> submit(TransactionDeliveryChannel.WhatsAppShared)
            TransactionComposerEvent.SubmitInApp -> submit(TransactionDeliveryChannel.InAppSubmitted)
            TransactionComposerEvent.DismissMessage -> _uiState.update { it.copy(message = null) }
        }
    }

    private fun updateDraft(transform: (TransactionDraft) -> TransactionDraft) {
        val current = _uiState.value.draft ?: return
        _uiState.update { it.copy(draft = transform(current)) }
    }

    private fun selectBuyAgainItem(entry: BuyAgainEntry, priceState: TransactionDraftPriceState) {
        updateDraft {
            TransactionDraftOperations.addOrIncrementLine(
                it, entry.linkedProductId, entry.mostRecentSnapshotProductName, entry.mostRecentSnapshotUnit,
                entry.mostRecentSnapshotSku, priceState, entry.mostRecentQuantity,
            )
        }
    }

    private suspend fun loadBuyAgain(companyId: String) {
        val partyId = buyerPartyId ?: return
        val history = repository.findCompletedPurchaseHistory(companyId, partyId)
        _uiState.update { it.copy(buyAgainEntries = BuyAgainListBuilder.build(history)) }
    }

    /**
     * Phase A integration point: the smallest clean read into Catalogue's existing repository —
     * `listAllPublished` is the exact same customer-visible-only read
     * [com.budcom.android.feature.catalogue.sharing.CatalogueShareContent] already uses, so a
     * Draft/Review/Archived product structurally cannot reach this buyer-facing list. No duplicate
     * product table, no second pricing engine — [resolveCataloguePriceState] is Catalogue's own
     * existing three-state resolver, reused unchanged.
     */
    private suspend fun loadNewSkus(companyId: String) {
        val published = catalogueRepository.listAllPublished(companyId)
        // Unit/SKU gap fix: the published snapshot itself carries neither field, but the full
        // CatalogueProduct domain object (read-only, already-existing repository method) does —
        // this is a real data source, not a guess, and CatalogueProduct is never used to bypass
        // the Published-only visibility guarantee above (only listAllPublished decides *which*
        // products appear here at all; findProduct here only enriches an already-decided row).
        val rows = published.map { snapshot ->
            val fullProduct = catalogueRepository.findProduct(companyId, snapshot.productId)
            snapshot.toNewSkuRow(unit = fullProduct?.unit, sku = fullProduct?.sku)
        }
        // Cart-photo fix: same primary-asset resolution CatalogueViewModel's own reload() already
        // uses (listAssets -> resolveAssetFile, both companyId/productId-scoped) -- reused here
        // verbatim, not reimplemented, and keyed by productId so every row type on this screen can
        // share the one map rather than each resolving its own copy.
        val photos = published.associate { it.productId to primaryAssetFile(companyId, it.productId) }
        _uiState.update { it.copy(newSkus = rows, productPhotos = photos) }
    }

    /** Mirrors [com.budcom.android.feature.catalogue.presentation.CatalogueViewModel.primaryAssetFile]
     * exactly (same two existing repository calls) -- not shared code, matching this codebase's own
     * established convention of mirroring rather than cross-feature-sharing small ViewModel-local
     * helpers. Never throws: a product with no photo, or one whose file has gone missing on disk,
     * both resolve to `null`. */
    private suspend fun primaryAssetFile(companyId: String, productId: String): java.io.File? {
        return runCatching {
            val assets = catalogueRepository.listAssets(companyId, productId)
            val primary = assets.firstOrNull { it.isPrimary } ?: assets.firstOrNull() ?: return null
            catalogueRepository.resolveAssetFile(companyId, productId, primary.filePath)
        }.getOrNull()
    }

    private suspend fun loadLastOrder(companyId: String) {
        val partyId = buyerPartyId ?: return
        val last = repository.findTransactionsForCounterparty(companyId, partyId)
            .filter { it.state == CommercialTransactionState.Completed }
            .maxByOrNull { it.acceptedAt.epochMillis }
        lastCompletedTransaction = last
        _uiState.update { it.copy(lastOrderAvailable = last != null) }
    }

    /**
     * Task's own explicit rule: "CREATE NEW TRANSACTION FROM OLD TRANSACTION, not MUTATE THE OLD
     * TRANSACTION." [ReorderOperations.fromCompletedTransaction] has no repository/DAO dependency
     * at all, so this function is structurally incapable of writing back to
     * [lastCompletedTransaction] or its line items — the only repository call here is the
     * read-only [TransactionRepository.findEstimatePoById]. The current draft's selected lines are
     * fully **replaced** (not merged) — "create a NEW TransactionDraft," matching the task's own
     * wording, not an ambiguous partial merge.
     *
     * Price state is resolved from the CURRENT `state.newSkus` list, never the historical price
     * the original order actually used (a reorder must reflect today's price, not a stale one —
     * the exact discipline [ReorderOperations]'s own `resolvePriceState` callback was designed
     * for). A product no longer in `newSkus` (e.g. since archived) safely falls back to
     * Contact-for-price rather than guessing or showing a stale number.
     */
    private fun reorderLastOrder() {
        val co = companyId ?: return
        val transaction = lastCompletedTransaction
        if (transaction == null) {
            _uiState.update { it.copy(message = "No previous completed order yet.") }
            return
        }
        viewModelScope.launch {
            val estimatePo = repository.findEstimatePoById(co, transaction.estimatePoId) ?: return@launch
            val currentNewSkus = _uiState.value.newSkus.associateBy { it.linkedProductId }
            val submissionType = _uiState.value.draft?.submissionType ?: TransactionSubmissionType.Estimate
            val newDraft = ReorderOperations.fromCompletedTransaction(
                co, transaction.buyerPartyId, submissionType, estimatePo.lineItems,
            ) { item -> currentNewSkus[item.linkedProductId]?.priceState ?: TransactionDraftPriceState.ContactForPrice }
            _uiState.update { it.copy(draft = newDraft) }
        }
    }

    private fun createDraftOrder() {
        val draft = _uiState.value.draft ?: return
        viewModelScope.launch {
            runCatching {
                repository.createDraftOrder(draft, draftOrderCreationKey, timestamp = clock.now())
            }.onSuccess { order ->
                _uiState.update { it.copy(canonicalDraftOrder = order, message = "Draft order saved locally.") }
            }.onFailure { failure ->
                val message = if (failure is IllegalArgumentException && draft.lines.any { it.priceState == TransactionDraftPriceState.Hidden }) {
                    "Some selected items are not priced for this buyer yet."
                } else {
                    "Draft order could not be saved locally."
                }
                _uiState.update { it.copy(message = message) }
            }
        }
    }

    private fun sendOrder() {
        val order = _uiState.value.canonicalDraftOrder
        if (order == null) {
            _uiState.update { it.copy(message = "Save the draft order before sending.") }
            return
        }
        viewModelScope.launch {
            runCatching { repository.enqueueOrderDelivery(order, clock.now()) }
                .onSuccess { envelope ->
                    runCatching { relayOutboxDispatcher.submitPending(order.companyId) }
                    refreshCanonicalOrderStatus(order.companyId, order.orderId)
                    refreshTransportStatus(order.companyId, order.orderId, order.version)
                    _uiState.update { it.copy(deliveryQueued = true, message = "Waiting to send.") }
                }
                .onFailure { _uiState.update { it.copy(message = "Order is still saved locally and can be sent later.") } }
        }
    }

    private fun refreshTransportStatus(companyId: String, orderId: String, orderVersion: Int) {
        viewModelScope.launch {
            val envelope = repository.findOrderDeliveryEnvelope(companyId, orderId, orderVersion) ?: return@launch
            _uiState.update {
                it.copy(
                    transportStatusLabel = OrderTransportStatusLabels.testerFacing(envelope.state),
                    transportDetail = OrderTransportStatusLabels.detail(envelope.state, envelope.lastError),
                )
            }
        }
    }

    private fun submit(channel: TransactionDeliveryChannel) {
        val co = companyId ?: return
        val draft = _uiState.value.draft ?: return
        when (val result = TransactionDraftOperations.toSubmission(draft)) {
            DraftToSubmissionResult.EmptyDraft ->
                _uiState.update { it.copy(message = "Add at least one product first.") }
            is DraftToSubmissionResult.UnauthorizedPriceLines ->
                _uiState.update { it.copy(message = "Some selected items aren't priced for this buyer yet.") }
            is DraftToSubmissionResult.Success -> viewModelScope.launch {
                val now = clock.now()
                val estimatePo = repository.createEstimatePo(
                    co, TransactionEntryPointType.Catalogue, draft.submissionType, channel, draft.buyerPartyId, result.lineItems, now,
                )
                // Found via live device testing: the draft must only reset when the follow-up
                // action actually succeeded -- resetting unconditionally silently discarded the
                // buyer's selection on a share-generation failure, even though nothing was
                // actually shared. The EstimatePo record itself is always created regardless
                // (that part cannot fail the same way), matching Submit's own always-succeeds shape.
                val succeeded = if (channel == TransactionDeliveryChannel.WhatsAppShared) {
                    shareViaWhatsApp(co, estimatePo)
                } else {
                    _uiState.update { it.copy(message = "Sent to your seller inbox.") }
                    true
                }
                if (succeeded) {
                    _uiState.update { it.copy(draft = TransactionDraftOperations.empty(co, draft.buyerPartyId, draft.submissionType)) }
                }
            }
        }
    }

    /** @return true if the OS share intent was successfully created and handed off (matching how
     * [com.budcom.android.feature.catalogue.presentation.CatalogueRoute]'s own analogous share
     * flow never waits for the user to actually complete the chooser either) — false if generation
     * itself failed, in which case the caller must not discard the buyer's draft. */
    private suspend fun shareViaWhatsApp(companyId: String, estimatePo: com.budcom.android.feature.transaction.domain.model.EstimatePo): Boolean {
        // toSubmission() already refused any Hidden-price line before this point (see
        // DraftToSubmissionResult.UnauthorizedPriceLines above) — every line that reached here is
        // guaranteed authorized, so Visible is correct by construction, not an assumption made here.
        return when (val prepared = shareCoordinator.prepareShare(companyId, estimatePo, TransactionSharePriceVisibility.Visible, null, null, null)) {
            is TransactionShareResult.Failure -> {
                _uiState.update { it.copy(message = prepared.message) }
                false
            }
            is TransactionShareResult.Success -> when (val intentResult = shareCoordinator.createShareIntent(prepared.value)) {
                is TransactionShareResult.Success -> {
                    _effects.emit(TransactionComposerEffect.LaunchShareIntent(intentResult.value))
                    true
                }
                is TransactionShareResult.Failure -> {
                    _uiState.update { it.copy(message = intentResult.message) }
                    false
                }
            }
        }
    }

    private fun refreshCanonicalOrderStatus(companyId: String, orderId: String) {
        viewModelScope.launch {
            val latest = repository.findCanonicalOrderById(companyId, orderId) ?: return@launch
            _uiState.update {
                it.copy(
                    canonicalDraftOrder = latest,
                    canonicalOrderStatusLabel = CanonicalOrderStatusLabels.buyerFacing(latest.state),
                )
            }
        }
    }

    companion object {
        const val BUYER_PARTY_ID_ARG = "buyerPartyId"
        private const val DRAFT_ORDER_CREATION_KEY = "draftOrderCreationKey"
    }
}

private fun CataloguePublishedSnapshot.toNewSkuRow(unit: String?, sku: String?): TransactionNewSkuRow = TransactionNewSkuRow(
    linkedProductId = productId,
    displayName = displayName,
    unit = unit,
    sku = sku,
    priceState = when (val state = resolveCataloguePriceState(priceDisplayMode, resolvedPriceAmount, resolvedPriceCurrencyCode)) {
        is CataloguePriceState.ActualPrice -> com.budcom.android.feature.transaction.domain.model.TransactionDraftPriceState.ActualPrice(state.amount, state.currencyCode)
        CataloguePriceState.NoPriceSupplied -> com.budcom.android.feature.transaction.domain.model.TransactionDraftPriceState.NoPriceSupplied
        CataloguePriceState.ContactForPrice -> com.budcom.android.feature.transaction.domain.model.TransactionDraftPriceState.ContactForPrice
    },
)
