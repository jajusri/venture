package com.budcom.android.feature.transaction.domain.port

import com.budcom.android.feature.transaction.domain.model.OrderDeliveryEnvelope

/** Future Vartalap adapter boundary. No network, relay, authentication, or cryptography is
 * selected here; the durable outbox remains usable while this adapter is unavailable. */
fun interface StructuredBusinessTransport {
    suspend fun submit(envelope: OrderDeliveryEnvelope): TransportResult
}

sealed interface TransportResult {
    data class Accepted(val evidence: TransportEvidence) : TransportResult
    data class Delivered(val evidence: TransportEvidence) : TransportResult
    data class TemporarilyUnavailable(val reason: String) : TransportResult
    data class RetryableFailure(val reason: String) : TransportResult
    data class PermanentRejection(val reason: String) : TransportResult
}

data class TransportEvidence(val envelopeId: String, val observedAtEpochMillis: Long)

sealed interface TransportRouterResult {
    data class Submitted(val result: TransportResult) : TransportRouterResult
    data object NoAvailableTransport : TransportRouterResult
}

class StructuredBusinessTransportRouter(
    private val transports: List<StructuredBusinessTransport>,
) {
    suspend fun submit(envelope: OrderDeliveryEnvelope): TransportRouterResult {
        val transport = transports.firstOrNull() ?: return TransportRouterResult.NoAvailableTransport
        return TransportRouterResult.Submitted(transport.submit(envelope))
    }
}