package com.budcom.android.feature.transaction.data.relay

import com.budcom.android.feature.transaction.domain.model.OrderDeliveryEnvelope
import com.budcom.android.feature.transaction.domain.port.AuthenticatedEnvelopeBinder
import com.budcom.android.feature.transaction.domain.port.AuthenticatedRelayRequest
import com.budcom.android.feature.transaction.domain.port.AuthenticatedTransportEnvelope
import com.budcom.android.feature.transaction.domain.port.EnvelopeSubmission
import com.budcom.android.feature.transaction.domain.port.RecipientBinding
import com.budcom.android.feature.transaction.domain.port.RelayCredentialSource
import com.budcom.android.feature.transaction.domain.port.RelayEnvelopeAuthenticator
import com.budcom.android.feature.transaction.domain.port.VartalapDeviceKeyStore
import java.time.Instant
import java.util.UUID
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

    override suspend fun authenticateMailboxFetch(
        businessId: String,
        mailboxId: String,
        cursor: String?,
        limit: Int,
    ): AuthenticatedRelayRequest? {
        val identity = keyStore.getCurrentIdentity() ?: return null
        val credential = credentials.credentialFor(businessId, identity.deviceId) ?: return null
        return binder.bindRequest(
            identity, credential, action = ACTION_MAILBOX_FETCH, target = mailboxId,
            parameters = listOf(cursor.orEmpty(), limit.toString()),
            requestId = UUID.randomUUID().toString(), timestampIso = Instant.now().toString(),
        )
    }

    override suspend fun authenticateAcknowledgement(
        businessId: String,
        envelopeId: String,
        receivedAtEpochMillis: Long,
    ): AuthenticatedRelayRequest? {
        val identity = keyStore.getCurrentIdentity() ?: return null
        val credential = credentials.credentialFor(businessId, identity.deviceId) ?: return null
        return binder.bindRequest(
            identity, credential, action = ACTION_ACKNOWLEDGE, target = envelopeId,
            parameters = listOf(Instant.ofEpochMilli(receivedAtEpochMillis).toString()),
            requestId = UUID.randomUUID().toString(), timestampIso = Instant.now().toString(),
        )
    }

    private companion object {
        const val DEFAULT_MAILBOX = "orders"
        const val ACTION_MAILBOX_FETCH = "mailbox_fetch"
        const val ACTION_ACKNOWLEDGE = "acknowledge"
    }
}
