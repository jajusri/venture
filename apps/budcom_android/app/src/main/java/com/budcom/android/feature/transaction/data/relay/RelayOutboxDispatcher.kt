package com.budcom.android.feature.transaction.data.relay

import com.budcom.android.core.util.DispatcherProvider
import com.budcom.android.feature.transaction.data.local.OrderOutboxDao
import com.budcom.android.feature.transaction.domain.model.OrderDeliveryEnvelope
import com.budcom.android.feature.transaction.domain.model.OrderTransportState
import com.budcom.android.feature.transaction.domain.model.TransactionClock
import com.budcom.android.feature.transaction.domain.model.TransactionTimestamp
import com.budcom.android.feature.transaction.domain.port.OrderSentFromRelayEvidence
import com.budcom.android.feature.transaction.domain.port.RelayOutboxDispatcher
import com.budcom.android.feature.transaction.domain.port.TransportResult
import com.budcom.android.feature.transaction.domain.port.TransportRouterResult
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultRelayOutboxDispatcher @Inject constructor(
    private val orderOutboxDao: OrderOutboxDao,
    private val router: RelayAwareTransportRouter,
    private val clock: TransactionClock,
    private val dispatchers: DispatcherProvider,
    private val orderSent: OrderSentFromRelayEvidence,
) : RelayOutboxDispatcher {
    override suspend fun submitPending(companyId: String) = withContext(dispatchers.io) {
        val pending = orderOutboxDao.findPending(companyId)
        pending.forEach { entity ->
            val envelope = entity.toDispatcherEnvelope()
            val result = router.submit(envelope)
            val now = clock.now()
            when (result) {
                TransportRouterResult.NoAvailableTransport -> Unit
                is TransportRouterResult.Submitted -> {
                    persistAttempt(entity.companyId, entity.envelopeId, entity.attemptCount, now, result.result)
                    val accepted = result.result as? TransportResult.Accepted
                    val evidence = accepted?.relayAcceptance
                    if (evidence != null) {
                        orderSent.markOrderSentFromRelayEvidence(entity.companyId, envelope, evidence)
                    }
                }
            }
        }
    }

    private suspend fun persistAttempt(
        companyId: String,
        envelopeId: String,
        previousAttempts: Int,
        now: TransactionTimestamp,
        result: TransportResult,
    ) {
        val attempts = previousAttempts + 1
        val (state, error) = when (result) {
            is TransportResult.Accepted -> OrderTransportState.RelayAccepted to null
            is TransportResult.RetryableFailure -> OrderTransportState.Retrying to result.reason
            is TransportResult.TemporarilyUnavailable -> OrderTransportState.Retrying to result.reason
            is TransportResult.PermanentRejection -> OrderTransportState.Failed to result.reason
            is TransportResult.Delivered -> OrderTransportState.Failed to "relay must not claim delivery"
        }
        orderOutboxDao.updateTransportAttempt(
            companyId = companyId,
            envelopeId = envelopeId,
            state = state.columnValue,
            attemptCount = attempts,
            lastAttemptAt = now.epochMillis,
            lastAttemptAtSource = now.source.name,
            lastError = error,
        )
    }
}

private fun com.budcom.android.feature.transaction.data.local.OrderDeliveryEnvelopeEntity.toDispatcherEnvelope(): OrderDeliveryEnvelope =
    OrderDeliveryEnvelope(
        companyId = companyId,
        envelopeId = envelopeId,
        idempotencyKey = idempotencyKey,
        objectType = objectType,
        orderId = orderId,
        orderVersion = orderVersion,
        senderCompanyId = senderCompanyId,
        recipientPartyId = recipientPartyId,
        createdAt = com.budcom.android.feature.transaction.domain.model.TransactionTimestamp(
            createdAt,
            com.budcom.android.feature.transaction.domain.model.TransactionTimestampSource.valueOf(createdAtSource),
        ),
        state = OrderTransportState.fromColumn(state),
        attemptCount = attemptCount,
        lastAttemptAt = if (lastAttemptAt != null && lastAttemptAtSource != null) {
            com.budcom.android.feature.transaction.domain.model.TransactionTimestamp(
                lastAttemptAt,
                com.budcom.android.feature.transaction.domain.model.TransactionTimestampSource.valueOf(lastAttemptAtSource),
            )
        } else {
            null
        },
        lastError = lastError,
    )
