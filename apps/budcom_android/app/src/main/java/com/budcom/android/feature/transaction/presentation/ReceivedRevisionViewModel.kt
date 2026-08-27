package com.budcom.android.feature.transaction.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budcom.android.feature.company.domain.port.CompanySessionPort
import com.budcom.android.feature.transaction.domain.model.CanonicalOrder
import com.budcom.android.feature.transaction.domain.model.CanonicalOrderState
import com.budcom.android.feature.transaction.domain.model.CanonicalOrderStatusLabels
import com.budcom.android.feature.transaction.domain.model.CommercialAction
import com.budcom.android.feature.transaction.domain.model.CommercialActionAuthorityContext
import com.budcom.android.feature.transaction.domain.model.CommercialActionAuthorityOutcome
import com.budcom.android.feature.transaction.domain.model.CommercialActionAuthorityRequest
import com.budcom.android.feature.transaction.domain.model.CommercialActionAuthorityResolver
import com.budcom.android.feature.transaction.domain.model.OrderCommercialEvent
import com.budcom.android.feature.transaction.domain.model.OrderRevisionAcceptEvidence
import com.budcom.android.feature.transaction.domain.model.OrderStructuredOpenEvent
import com.budcom.android.feature.transaction.domain.model.TransactionClock
import com.budcom.android.feature.transaction.domain.port.VartalapDeviceKeyStore
import com.budcom.android.feature.transaction.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ReceivedRevisionUiState(
    val isLoading: Boolean = true,
    val envelopeId: String = "",
    val orderId: String? = null,
    val orderVersion: Int? = null,
    val statusLabel: String? = null,
    val canAcceptChanges: Boolean = false,
    val message: String? = null,
)

sealed interface ReceivedRevisionEvent {
    data object AcceptChanges : ReceivedRevisionEvent
    data object DismissMessage : ReceivedRevisionEvent
}

@HiltViewModel
class ReceivedRevisionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: TransactionRepository,
    private val companySession: CompanySessionPort,
    private val keyStore: VartalapDeviceKeyStore,
    private val clock: TransactionClock,
    private val authorityResolver: CommercialActionAuthorityResolver,
) : ViewModel() {
    private val envelopeId: String = requireNotNull(savedStateHandle.get<String>(ENVELOPE_ID_ARG))
    private val senderBusinessId: String = requireNotNull(savedStateHandle.get<String>(SENDER_BUSINESS_ID_ARG))
    private val orderId: String = requireNotNull(savedStateHandle.get<String>(ORDER_ID_ARG))
    private val orderVersion: Int = requireNotNull(savedStateHandle.get<Int>(ORDER_VERSION_ARG))

    private val _uiState = MutableStateFlow(ReceivedRevisionUiState(envelopeId = envelopeId))
    val uiState: StateFlow<ReceivedRevisionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { openAndRefresh() }
    }

    fun onEvent(event: ReceivedRevisionEvent) {
        when (event) {
            ReceivedRevisionEvent.AcceptChanges -> acceptChanges()
            ReceivedRevisionEvent.DismissMessage -> _uiState.update { it.copy(message = null) }
        }
    }

    private suspend fun openAndRefresh() {
        val companyId = companySession.observeSelectedCompanyId().first() ?: return
        val authority = verifiedAuthority(companyId, CommercialAction.OpenReceived) ?: run {
            _uiState.update { it.copy(isLoading = false, canAcceptChanges = false, message = "Trusted authority is required.") }
            return
        }
        val now = clock.now()
        val open = OrderStructuredOpenEvent(
            eventId = UUID.randomUUID().toString(),
            idempotencyKey = "seen:$orderId:v$orderVersion:$companyId",
            orderId = orderId,
            orderVersion = orderVersion,
            objectType = "CANONICAL_ORDER",
            viewerBusinessId = authority.businessId,
            viewerActorId = authority.actorId,
            viewerDeviceId = authority.deviceId,
            senderBusinessId = senderBusinessId,
            openedAt = now,
        )
        repository.recordOrderSeenFromOpenEvent(companyId, envelopeId, open)
        val seenEvidence = repository.findOrderSeenEvidence(companyId, orderId, orderVersion)
            ?: return refreshUi(companyId, repository.findCanonicalOrderById(companyId, orderId))
        val order = repository.applyOrderSeenEvidence(companyId, seenEvidence)
        refreshUi(companyId, order)
    }

    private fun acceptChanges() {
        viewModelScope.launch {
            val companyId = companySession.observeSelectedCompanyId().first() ?: return@launch
            val authority = verifiedAuthority(companyId, CommercialAction.BuyerAcceptRevision)
            if (authority == null) {
                _uiState.update { it.copy(message = "Changes could not be accepted.") }
                return@launch
            }
            val eventId = UUID.randomUUID().toString()
            val recorded = repository.recordOrderRevisionAcceptFromBuyerAction(
                companyId,
                envelopeId,
                authority.toConfirmAuthority(),
                eventId,
                "accept-revision:$orderId:v$orderVersion:$companyId",
                clock.now(),
            )
            if (recorded == null) {
                _uiState.update { it.copy(message = "Changes could not be accepted.") }
                return@launch
            }
            val accepted = repository.applyOrderRevisionAcceptEvidence(companyId, recorded.toAcceptEvidence(authority.authorityEpoch))
            _uiState.update {
                it.copy(
                    canAcceptChanges = false,
                    statusLabel = accepted?.let { order -> CanonicalOrderStatusLabels.buyerFacing(order.state) }
                        ?: CanonicalOrderStatusLabels.buyerFacing(CanonicalOrderState.Confirmed),
                    message = if (accepted != null) "Changes accepted." else "Changes could not be accepted.",
                )
            }
        }
    }

    private suspend fun refreshUi(companyId: String, order: CanonicalOrder?) {
        val current = order ?: repository.findCanonicalOrderById(companyId, orderId)
        val seen = repository.findOrderSeenEvidence(companyId, orderId, orderVersion)
        _uiState.update {
            it.copy(
                isLoading = false,
                orderId = orderId,
                orderVersion = orderVersion,
                statusLabel = current?.let { o -> CanonicalOrderStatusLabels.buyerFacing(o.state) }
                    ?: CanonicalOrderStatusLabels.buyerFacing(CanonicalOrderState.RevisionSent),
                canAcceptChanges = seen != null && current?.state == CanonicalOrderState.RevisionSeen,
            )
        }
    }

    private suspend fun verifiedAuthority(companyId: String, action: CommercialAction): CommercialActionAuthorityContext? {
        val device = keyStore.getCurrentIdentity() ?: return null
        val now = clock.now()
        return when (
            val outcome = authorityResolver.resolve(
                CommercialActionAuthorityRequest(
                    action = action,
                    viewerBusinessId = companyId,
                    expectedActorId = null,
                    expectedDeviceId = device.deviceId,
                    expectedDeviceKeyVersion = device.keyVersion,
                    orderId = orderId,
                    orderVersion = orderVersion,
                    inboxOrderId = orderId,
                    inboxOrderVersion = orderVersion,
                    sellerBusinessId = senderBusinessId,
                    buyerBusinessId = companyId,
                    nowEpochMillis = now.epochMillis,
                ),
            )
        ) {
            is CommercialActionAuthorityOutcome.Verified -> outcome.context
            else -> null
        }
    }

    companion object {
        const val ENVELOPE_ID_ARG = "envelopeId"
        const val SENDER_BUSINESS_ID_ARG = "senderBusinessId"
        const val ORDER_ID_ARG = "orderId"
        const val ORDER_VERSION_ARG = "orderVersion"
    }
}

private fun OrderCommercialEvent.toAcceptEvidence(authorityEpoch: Long) = OrderRevisionAcceptEvidence(
    eventId = eventId,
    orderId = orderId,
    orderVersion = orderVersion,
    acceptingBusinessId = actorBusinessId,
    acceptingActorId = actorId,
    acceptingDeviceId = actorDeviceId.orEmpty(),
    counterpartyBusinessId = counterpartyBusinessId,
    authorityEpoch = authorityEpoch,
    acceptedAt = occurredAt,
)
