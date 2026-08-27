package com.budcom.android.feature.transaction.domain.port

import com.budcom.android.feature.transaction.domain.model.OrderDeliveryEnvelope

/** Future Vartalap adapter boundary. No network, relay, authentication, or cryptography is
 * selected here; the durable outbox remains usable while this adapter is unavailable. */
interface StructuredBusinessTransport {
    suspend fun submit(envelope: OrderDeliveryEnvelope): TransportEvidence
}

data class TransportEvidence(
    val envelopeId: String,
    val accepted: Boolean,
    val observedAtEpochMillis: Long,
)