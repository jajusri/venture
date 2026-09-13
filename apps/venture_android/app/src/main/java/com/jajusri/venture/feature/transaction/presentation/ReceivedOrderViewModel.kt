package com.jajusri.venture.feature.transaction.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jajusri.venture.feature.company.domain.port.CompanySessionPort
import com.jajusri.venture.feature.transaction.domain.CanonicalBuyingCycleCoordinator
import com.jajusri.venture.feature.transaction.domain.model.CanonicalOrderStatusLabels
import com.jajusri.venture.feature.transaction.domain.model.CanonicalOrderState
import com.jajusri.venture.feature.transaction.domain.model.CommercialAction
import com.jajusri.venture.feature.transaction.domain.model.CommercialActionAuthorityContext
import com.jajusri.venture.feature.transaction.domain.model.CommercialActionAuthorityOutcome
import com.jajusri.venture.feature.transaction.domain.model.CommercialActionAuthorityRequest
import com.jajusri.venture.feature.transaction.domain.model.CommercialActionAuthorityResolver
import com.jajusri.venture.feature.transaction.domain.model.OrderStructuredOpenEvent
import com.jajusri.venture.feature.transaction.domain.model.TransactionClock
import com.jajusri.venture.feature.transaction.domain.port.VartalapDeviceKeyStore
import com.jajusri.venture.feature.transaction.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ReceivedOrderUiState(
    val isLoading: Boolean = true,
    val envelopeId: String = "",
    val orderId: String? = null,
    val orderVersion: Int? = null,
    val statusLabel: String? = null,
    val canConfirm: Boolean = false,
    val message: String? = null,
)

sealed interface ReceivedOrderEvent {
    data object ConfirmOrder : ReceivedOrderEvent
    data object DismissMessage : ReceivedOrderEvent
}

@HiltViewModel
class ReceivedOrderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: TransactionRepository,
    private val companySession: CompanySessionPort,
    private val keyStore: VartalapDeviceKeyStore,
    private val clock: TransactionClock,
    private val authorityResolver: CommercialActionAuthorityResolver,
    private val coordinator: CanonicalBuyingCycleCoordinator,
) : ViewModel() {
    private val envelopeId: String = requireNotNull(savedStateHandle.get<String>(ENVELOPE_ID_ARG))
    private val senderBusinessId: String = requireNotNull(savedStateHandle.get<String>(SENDER_BUSINESS_ID_ARG))
    private val orderId: String = requireNotNull(savedStateHandle.get<String>(ORDER_ID_ARG))
    private val orderVersion: Int = requireNotNull(savedStateHandle.get<Int>(ORDER_VERSION_ARG))

    private val _uiState = MutableStateFlow(ReceivedOrderUiState(envelopeId = envelopeId))
    val uiState: StateFlow<ReceivedOrderUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { openAndRefresh() }
    }

    fun onEvent(event: ReceivedOrderEvent) {
        when (event) {
            ReceivedOrderEvent.ConfirmOrder -> confirmOrder()
            ReceivedOrderEvent.DismissMessage -> _uiState.update { it.copy(message = null) }
        }
    }

    private suspend fun openAndRefresh() {
        val companyId = companySession.observeSelectedCompanyId().first() ?: return
        val authority = verifiedAuthority(companyId, CommercialAction.OpenReceived) ?: run {
            _uiState.update { it.copy(isLoading = false, canConfirm = false, message = "Trusted authority is required.") }
            return
        }
        val deviceKeyVersion = keyStore.getCurrentIdentity()?.keyVersion ?: return
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
        repository.recordOrderSeenFromOpenEvent(
            companyId, envelopeId, open,
            CommercialActionAuthorityRequest(
                action = CommercialAction.ReturnSeen, viewerBusinessId = companyId,
                expectedActorId = authority.actorId, expectedDeviceId = authority.deviceId,
                expectedDeviceKeyVersion = deviceKeyVersion,
                orderId = orderId, orderVersion = orderVersion, inboxOrderId = orderId,
                inboxOrderVersion = orderVersion, sellerBusinessId = companyId,
                buyerBusinessId = senderBusinessId, nowEpochMillis = now.epochMillis,
            ),
        )
        val seen = repository.findOrderSeenEvidence(companyId, orderId, orderVersion)
        val localOrder = repository.findCanonicalOrderById(companyId, orderId)
        val status = when {
            localOrder != null -> CanonicalOrderStatusLabels.buyerFacing(localOrder.state)
            seen != null -> CanonicalOrderStatusLabels.buyerFacing(CanonicalOrderState.Seen)
            else -> CanonicalOrderStatusLabels.buyerFacing(CanonicalOrderState.Sent)
        }
        _uiState.update {
            it.copy(
                isLoading = false,
                orderId = orderId,
                orderVersion = orderVersion,
                statusLabel = status,
                canConfirm = seen != null && localOrder?.state != CanonicalOrderState.Confirmed,
            )
        }
    }

    private fun confirmOrder() {
        viewModelScope.launch {
            val companyId = companySession.observeSelectedCompanyId().first() ?: return@launch
            val device = keyStore.getCurrentIdentity()
            if (device == null) {
                _uiState.update { it.copy(message = "Order could not be confirmed.") }
                return@launch
            }
            val now = clock.now()
            val confirmed = coordinator.confirmSellerOrder(
                CommercialActionAuthorityRequest(
                    action = CommercialAction.SellerConfirm,
                    viewerBusinessId = companyId,
                    expectedActorId = null,
                    expectedDeviceId = device.deviceId,
                    expectedDeviceKeyVersion = device.keyVersion,
                    orderId = orderId,
                    orderVersion = orderVersion,
                    inboxOrderId = orderId,
                    inboxOrderVersion = orderVersion,
                    sellerBusinessId = companyId,
                    buyerBusinessId = senderBusinessId,
                    nowEpochMillis = now.epochMillis,
                ),
                envelopeId,
                UUID.randomUUID().toString(),
                "confirm:$orderId:v$orderVersion:$companyId",
                now,
                senderBusinessId,
            )
            _uiState.update {
                it.copy(
                    canConfirm = confirmed == null,
                    statusLabel = if (confirmed != null) CanonicalOrderStatusLabels.buyerFacing(CanonicalOrderState.Confirmed) else it.statusLabel,
                    message = if (confirmed != null) "Order confirmed." else "Order could not be confirmed.",
                )
            }
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
                    sellerBusinessId = companyId,
                    buyerBusinessId = senderBusinessId,
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
