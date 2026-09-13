package com.jajusri.venture.feature.transaction.domain.port

import com.jajusri.venture.feature.serverconfig.domain.validation.ConnectorUrlValidator

fun interface RelayEndpointProvider {
    fun snapshot(): String?
}

class ConfiguredRelayEndpointProvider(private val configuredBaseUrl: String) : RelayEndpointProvider {
    override fun snapshot(): String? = ConnectorUrlValidator.normalizeOrNull(configuredBaseUrl)
}

object EmptyRelayEndpointProvider : RelayEndpointProvider {
    override fun snapshot(): String? = null
}

/**
 * Builds Relay wire authority proof (Trust-issued credential claims + Trust signature + a fresh
 * Keystore device signature over the canonical request bytes) for each of the three authenticated
 * Relay operations. One interface, one Keystore-touching implementation
 * ([com.jajusri.venture.feature.transaction.data.relay.KeystoreRelayEnvelopeAuthenticator]) -- callers
 * (submission, mailbox fetch, acknowledgement) never construct or sign a Relay wire envelope
 * themselves, matching the certified Relay verifier's shared
 * `verifyCredentialAgainstCurrentAuthority()` pipeline (one proof shape for all three operations).
 */
interface RelayEnvelopeAuthenticator {
    suspend fun authenticate(envelope: com.jajusri.venture.feature.transaction.domain.model.OrderDeliveryEnvelope): AuthenticatedTransportEnvelope?

    suspend fun authenticateMailboxFetch(
        businessId: String,
        mailboxId: String,
        cursor: String?,
        limit: Int,
    ): AuthenticatedRelayRequest?

    suspend fun authenticateAcknowledgement(
        businessId: String,
        envelopeId: String,
        receivedAtEpochMillis: Long,
    ): AuthenticatedRelayRequest?
}

/** Returns the full Trust-issued signed credential this device holds for `businessId`/`deviceId`,
 * or null if none is on file, expired, or bound to a different business/device. Never a summarized
 * or reconstructed credential -- see [TrustedBusinessDeviceCredential]'s own doc comment. */
fun interface RelayCredentialSource {
    suspend fun credentialFor(businessId: String, deviceId: String): TrustedBusinessDeviceCredential?
}

fun interface RelayOutboxDispatcher {
    suspend fun submitPending(companyId: String)
}

fun interface OrderSentFromRelayEvidence {
    suspend fun markOrderSentFromRelayEvidence(
        companyId: String,
        envelope: com.jajusri.venture.feature.transaction.domain.model.OrderDeliveryEnvelope,
        evidence: com.jajusri.venture.feature.transaction.domain.model.RelayAcceptanceEvidence,
    ): com.jajusri.venture.feature.transaction.domain.model.CanonicalOrder?
}
