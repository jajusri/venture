package com.budcom.android.feature.transaction.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budcom.android.feature.company.domain.port.CompanySessionPort
import com.budcom.android.feature.transaction.domain.model.BuyAgainEntry
import com.budcom.android.feature.transaction.domain.model.BuyAgainListBuilder
import com.budcom.android.feature.transaction.domain.model.TransactionClock
import com.budcom.android.feature.transaction.domain.model.TransactionDraft
import com.budcom.android.feature.transaction.domain.model.TransactionDraftOperations
import com.budcom.android.feature.transaction.domain.model.TransactionDraftOperations.DraftToSubmissionResult
import com.budcom.android.feature.transaction.domain.model.TransactionDraftPriceState
import com.budcom.android.feature.transaction.domain.model.TransactionEntryPointType
import com.budcom.android.feature.transaction.domain.model.TransactionSubmissionType
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
    private val shareCoordinator: TransactionShareCoordinator,
    private val companySession: CompanySessionPort,
    private val clock: TransactionClock,
) : ViewModel() {

    private val buyerPartyId: String? = savedStateHandle.get<String>(BUYER_PARTY_ID_ARG)

    private val _uiState = MutableStateFlow(TransactionComposerUiState())
    val uiState: StateFlow<TransactionComposerUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<TransactionComposerEffect>(extraBufferCapacity = 1)
    val effects = _effects.asSharedFlow()

    private var companyId: String? = null

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
        val partyId = buyerPartyId
        if (partyId == null) {
            _uiState.update { it.copy(isLoading = false) }
            return
        }
        val history = repository.findCompletedPurchaseHistory(companyId, partyId)
        _uiState.update { it.copy(buyAgainEntries = BuyAgainListBuilder.build(history), isLoading = false) }
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
                if (channel == TransactionDeliveryChannel.WhatsAppShared) {
                    shareViaWhatsApp(co, estimatePo)
                } else {
                    _uiState.update { it.copy(message = "Sent to your seller inbox.") }
                }
                _uiState.update { it.copy(draft = TransactionDraftOperations.empty(co, draft.buyerPartyId, draft.submissionType)) }
            }
        }
    }

    private suspend fun shareViaWhatsApp(companyId: String, estimatePo: com.budcom.android.feature.transaction.domain.model.EstimatePo) {
        // toSubmission() already refused any Hidden-price line before this point (see
        // DraftToSubmissionResult.UnauthorizedPriceLines above) — every line that reached here is
        // guaranteed authorized, so Visible is correct by construction, not an assumption made here.
        when (val prepared = shareCoordinator.prepareShare(companyId, estimatePo, TransactionSharePriceVisibility.Visible, null, null, null)) {
            is TransactionShareResult.Failure -> _uiState.update { it.copy(message = prepared.message) }
            is TransactionShareResult.Success -> when (val intentResult = shareCoordinator.createShareIntent(prepared.value)) {
                is TransactionShareResult.Success -> _effects.emit(TransactionComposerEffect.LaunchShareIntent(intentResult.value))
                is TransactionShareResult.Failure -> _uiState.update { it.copy(message = intentResult.message) }
            }
        }
    }

    companion object {
        const val BUYER_PARTY_ID_ARG = "buyerPartyId"
    }
}
