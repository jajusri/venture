package com.budcom.android.feature.transaction.domain.port

import com.budcom.android.feature.serverconfig.domain.validation.ConnectorUrlValidator

fun interface RelayEndpointProvider {
    fun snapshot(): String?
}

class ConfiguredRelayEndpointProvider(private val configuredBaseUrl: String) : RelayEndpointProvider {
    override fun snapshot(): String? = ConnectorUrlValidator.normalizeOrNull(configuredBaseUrl)
}

object EmptyRelayEndpointProvider : RelayEndpointProvider {
    override fun snapshot(): String? = null
}

fun interface RelayEnvelopeAuthenticator {
    suspend fun authenticate(envelope: com.budcom.android.feature.transaction.domain.model.OrderDeliveryEnvelope): AuthenticatedTransportEnvelope?
}

fun interface RelayCredentialSource {
    suspend fun credentialFor(businessId: String, deviceId: String): BusinessDeviceCredential?
}

fun interface RelayOutboxDispatcher {
    suspend fun submitPending(companyId: String)
}

fun interface OrderSentFromRelayEvidence {
    suspend fun markOrderSentFromRelayEvidence(
        companyId: String,
        envelope: com.budcom.android.feature.transaction.domain.model.OrderDeliveryEnvelope,
        evidence: com.budcom.android.feature.transaction.domain.model.RelayAcceptanceEvidence,
    ): com.budcom.android.feature.transaction.domain.model.CanonicalOrder?
}
