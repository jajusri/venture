package com.budcom.android.feature.transaction.data.relay

import com.budcom.android.core.util.DispatcherProvider
import com.budcom.android.feature.transaction.domain.model.StructuredRecipientInboxValidation
import com.budcom.android.feature.transaction.domain.model.TransactionClock
import com.budcom.android.feature.transaction.domain.port.RelayCredentialSource
import com.budcom.android.feature.transaction.domain.port.RelayEndpointProvider
import com.budcom.android.feature.transaction.domain.port.RelayRecipientInboxIngester
import com.budcom.android.feature.transaction.domain.port.VartalapDeviceKeyStore
import com.budcom.android.feature.transaction.domain.repository.StructuredRecipientInboxRepository
import com.budcom.android.feature.transaction.domain.model.OrderVersionSnapshot
import com.budcom.android.feature.transaction.domain.repository.TransactionRepository
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultRelayRecipientInboxIngester @Inject constructor(
    private val client: HttpRelayClient,
    private val endpoint: RelayEndpointProvider,
    private val inbox: StructuredRecipientInboxRepository,
    private val orders: TransactionRepository,
    private val keyStore: VartalapDeviceKeyStore,
    private val credentials: RelayCredentialSource,
    private val clock: TransactionClock,
    private val dispatchers: DispatcherProvider,
) : RelayRecipientInboxIngester {
    override suspend fun ingestPending(companyId: String) = withContext(dispatchers.io) {
        if (endpoint.snapshot() == null) return@withContext
        val identity = keyStore.getCurrentIdentity() ?: return@withContext
        val credential = credentials.credentialFor(companyId, identity.deviceId) ?: return@withContext
        var cursor = inbox.loadMailboxCursor(companyId, DEFAULT_MAILBOX)
        var pages = 0
        while (pages < MAX_MAILBOX_PAGES_PER_INGEST) {
            val page = client.fetchMailbox(
                recipientBusinessId = companyId,
                recipientActorId = credential.actorId,
                recipientDeviceId = identity.deviceId,
                mailboxId = DEFAULT_MAILBOX,
                cursor = cursor,
            ) ?: break
            page.items.forEach { item ->
                if (StructuredRecipientInboxValidation.validate(item, companyId)) {
                    val snapshot = item.commercialSnapshotCanonical?.let { OrderVersionSnapshot.parse(it) }
                    if (snapshot == null ||
                        snapshot.orderId != item.objectId ||
                        snapshot.orderVersion != item.objectVersion ||
                        snapshot.senderBusinessId != item.senderBusinessId ||
                        snapshot.recipientBusinessId != companyId
                    ) {
                        return@forEach
                    }
                    val stored = inbox.persistIfNew(companyId, item, clock.now())
                    if (stored != null) {
                        orders.materializeReceivedOrderVersion(companyId, stored.envelopeId, snapshot, stored.ingestedAt)
                        client.acknowledgeDelivery(
                            envelopeId = item.envelopeId,
                            recipientBusinessId = companyId,
                            recipientActorId = credential.actorId,
                            recipientDeviceId = identity.deviceId,
                            receivedAtEpochMillis = stored.ingestedAt.epochMillis,
                        )
                    }
                }
            }
            cursor = page.nextCursor
            inbox.saveMailboxCursor(companyId, DEFAULT_MAILBOX, cursor)
            pages += 1
            if (cursor == null) break
        }
    }

    private companion object {
        const val DEFAULT_MAILBOX = "orders"
        const val MAX_MAILBOX_PAGES_PER_INGEST = 5
    }
}
