package com.budcom.android.feature.transaction.data.relay

import com.budcom.android.feature.transaction.domain.model.OrderDeliveryEnvelope
import com.budcom.android.feature.transaction.domain.port.AuthenticatedEnvelopeBinder
import com.budcom.android.feature.transaction.domain.port.AuthenticatedTransportEnvelope
import com.budcom.android.feature.transaction.domain.port.EnvelopeSubmission
import com.budcom.android.feature.transaction.domain.port.RecipientBinding
import com.budcom.android.feature.transaction.domain.port.RelayCredentialSource
import com.budcom.android.feature.transaction.domain.port.RelayEnvelopeAuthenticator
import com.budcom.android.feature.transaction.domain.port.VartalapDeviceKeyStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeystoreRelayEnvelopeAuthenticator @Inject constructor(
    private val keyStore: VartalapDeviceKeyStore,
    private val credentials: RelayCredentialSource,
    private val snapshots: CanonicalOrderVersionSnapshotFactory,
) : RelayEnvelopeAuthenticator {
    private val binder = AuthenticatedEnvelopeBinder(keyStore)

    override suspend fun authenticate(envelope: OrderDeliveryEnvelope): AuthenticatedTransportEnvelope? {
        val identity = keyStore.getCurrentIdentity() ?: return null
        val credential = credentials.credentialFor(envelope.senderCompanyId, identity.deviceId) ?: return null
        val recipientBusinessId = envelope.recipientBusinessId ?: envelope.recipientPartyId ?: return null
        val canonical = envelope.commercialContentCanonical ?: snapshots.forEnvelope(envelope)?.deterministicEncoding() ?: return null
        val contentType = envelope.commercialContentType ?: com.budcom.android.feature.transaction.domain.port.ORDER_SNAPSHOT_CONTENT_TYPE
        val contentVersion = envelope.commercialContentVersion ?: com.budcom.android.feature.transaction.domain.port.ORDER_SNAPSHOT_CONTENT_VERSION
        val recipient = RecipientBinding(
            businessId = recipientBusinessId,
            partyId = envelope.recipientPartyId,
            mailboxReference = DEFAULT_MAILBOX,
        )
        val submission = EnvelopeSubmission.fromEnvelope(envelope, identity.deviceId, recipientBusinessId)
        return binder.bind(submission, identity, credential, recipient, canonical, contentType, contentVersion)
    }

    private companion object {
        const val DEFAULT_MAILBOX = "orders"
    }
}
