package com.budcom.android.feature.transaction.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budcom.android.feature.company.domain.port.CompanySessionPort
import com.budcom.android.feature.transaction.domain.model.CanonicalOrder
import com.budcom.android.feature.transaction.domain.model.CanonicalOrderStatusLabels
import com.budcom.android.feature.transaction.domain.model.CanonicalOrderState
import com.budcom.android.feature.transaction.domain.model.OrderConfirmAuthority
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
        val device = keyStore.getCurrentIdentity() ?: keyStore.getOrCreateIdentity("local-device")
        val now = clock.now()
        val open = OrderStructuredOpenEvent(
            eventId = UUID.randomUUID().toString(),
            idempotencyKey = "seen:$orderId:v$orderVersion:$companyId",
            orderId = orderId,
            orderVersion = orderVersion,
            objectType = "CANONICAL_ORDER",
            viewerBusinessId = companyId,
            viewerActorId = "actor-local",
            viewerDeviceId = device.deviceId,
            senderBusinessId = senderBusinessId,
            openedAt = now,
        )
        repository.recordOrderSeenFromOpenEvent(companyId, envelopeId, open)
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
            val device = keyStore.getCurrentIdentity() ?: keyStore.getOrCreateIdentity("local-device")
            val authority = OrderConfirmAuthority(
                businessId = companyId,
                actorId = "actor-local",
                deviceId = device.deviceId,
                authorityScope = setOf(OrderConfirmAuthority.CONFIRM_ORDERS_CAPABILITY),
                authorityEpoch = 1,
            )
            val eventId = UUID.randomUUID().toString()
            val recorded = repository.recordOrderConfirmFromSellerAction(
                companyId,
                envelopeId,
                authority,
                eventId,
                "confirm:$orderId:v$orderVersion:$companyId",
                clock.now(),
            )
            _uiState.update {
                it.copy(
                    canConfirm = recorded == null,
                    statusLabel = if (recorded != null) CanonicalOrderStatusLabels.buyerFacing(CanonicalOrderState.Confirmed) else it.statusLabel,
                    message = if (recorded != null) "Order confirmed." else "Order could not be confirmed.",
                )
            }
        }
    }

    companion object {
        const val ENVELOPE_ID_ARG = "envelopeId"
        const val SENDER_BUSINESS_ID_ARG = "senderBusinessId"
        const val ORDER_ID_ARG = "orderId"
        const val ORDER_VERSION_ARG = "orderVersion"
    }
}
