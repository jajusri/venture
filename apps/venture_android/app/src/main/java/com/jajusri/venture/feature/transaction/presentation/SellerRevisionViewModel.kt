package com.jajusri.venture.feature.transaction.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jajusri.venture.feature.company.domain.port.CompanySessionPort
import com.jajusri.venture.feature.transaction.domain.CanonicalBuyingCycleCoordinator
import com.jajusri.venture.feature.transaction.domain.model.CanonicalOrderStatusLabels
import com.jajusri.venture.feature.transaction.domain.model.CommercialAction
import com.jajusri.venture.feature.transaction.domain.model.CommercialActionAuthorityRequest
import com.jajusri.venture.feature.transaction.domain.model.OrderRevisionLineChange
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
import javax.inject.Inject

data class SellerRevisionUiState(
    val isLoading: Boolean = true,
    val statusLabel: String? = null,
    val quantity: String = "",
    val message: String? = null,
)

sealed interface SellerRevisionEvent {
    data class QuantityChanged(val value: String) : SellerRevisionEvent
    data object SendRevision : SellerRevisionEvent
}

@HiltViewModel
class SellerRevisionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: TransactionRepository,
    private val companySession: CompanySessionPort,
    private val keyStore: VartalapDeviceKeyStore,
    private val clock: TransactionClock,
    private val coordinator: CanonicalBuyingCycleCoordinator,
) : ViewModel() {
    private val envelopeId: String = requireNotNull(savedStateHandle.get<String>(ENVELOPE_ID_ARG))
    private val buyerBusinessId: String = requireNotNull(savedStateHandle.get<String>(BUYER_BUSINESS_ID_ARG))
    private val orderId: String = requireNotNull(savedStateHandle.get<String>(ORDER_ID_ARG))
    private val orderVersion: Int = requireNotNull(savedStateHandle.get<Int>(ORDER_VERSION_ARG))

    private val _uiState = MutableStateFlow(SellerRevisionUiState())
    val uiState: StateFlow<SellerRevisionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val companyId = companySession.observeSelectedCompanyId().first() ?: return@launch
            val order = repository.findCanonicalOrderById(companyId, orderId)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    statusLabel = order?.let { o -> CanonicalOrderStatusLabels.buyerFacing(o.state) },
                    quantity = order?.lines?.firstOrNull()?.quantity.orEmpty(),
                )
            }
        }
    }

    fun onEvent(event: SellerRevisionEvent) {
        when (event) {
            is SellerRevisionEvent.QuantityChanged -> _uiState.update { it.copy(quantity = event.value) }
            SellerRevisionEvent.SendRevision -> sendRevision()
        }
    }

    private fun sendRevision() {
        viewModelScope.launch {
            val companyId = companySession.observeSelectedCompanyId().first() ?: return@launch
            val device = keyStore.getCurrentIdentity() ?: return@launch
            val baseline = repository.findCanonicalOrderById(companyId, orderId) ?: return@launch
            val quantity = _uiState.value.quantity
            val lines = baseline.lines.map { line ->
                OrderRevisionLineChange(
                    lineId = line.lineId,
                    linkedProductId = line.linkedProductId,
                    snapshotProductName = line.snapshotProductName,
                    snapshotUnit = line.snapshotUnit,
                    snapshotSku = line.snapshotSku,
                    quantity = quantity,
                    unitPriceAmount = line.unitPriceAmount,
                    unitPriceCurrencyCode = line.unitPriceCurrencyCode,
                    priceState = line.priceState,
                    lineTotalAmount = line.lineTotalAmount,
                )
            }
            val now = clock.now()
            val sent = coordinator.proposeAndSendRevision(
                CommercialActionAuthorityRequest(
                    action = CommercialAction.SellerRevise,
                    viewerBusinessId = companyId,
                    expectedActorId = null,
                    expectedDeviceId = device.deviceId,
                    expectedDeviceKeyVersion = device.keyVersion,
                    orderId = orderId,
                    orderVersion = orderVersion,
                    inboxOrderId = orderId,
                    inboxOrderVersion = orderVersion,
                    sellerBusinessId = companyId,
                    buyerBusinessId = buyerBusinessId,
                    nowEpochMillis = now.epochMillis,
                ),
                envelopeId,
                baseline,
                lines,
                "Quantity revision",
                now,
                "revision:$orderId:v${orderVersion + 1}",
            )
            _uiState.update {
                it.copy(
                    statusLabel = sent?.let { order -> CanonicalOrderStatusLabels.buyerFacing(order.state) },
                    message = if (sent != null) "Revision sent." else "Revision could not be sent.",
                )
            }
        }
    }

    companion object {
        const val ENVELOPE_ID_ARG = "envelopeId"
        const val BUYER_BUSINESS_ID_ARG = "buyerBusinessId"
        const val ORDER_ID_ARG = "orderId"
        const val ORDER_VERSION_ARG = "orderVersion"
    }
}
