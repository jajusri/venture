package com.budcom.android.feature.transaction.data.relay

import com.budcom.android.core.util.DispatcherProvider
import com.budcom.android.feature.transaction.domain.model.StructuredRecipientInboxValidation
import com.budcom.android.feature.transaction.domain.model.TransactionClock
import com.budcom.android.feature.transaction.domain.port.RelayCredentialSource
import com.budcom.android.feature.transaction.domain.port.RelayEndpointProvider
import com.budcom.android.feature.transaction.domain.port.RelayRecipientInboxIngester
import com.budcom.android.feature.transaction.domain.port.VartalapDeviceKeyStore
import com.budcom.android.feature.transaction.domain.port.ORDER_SNAPSHOT_CONTENT_TYPE
import com.budcom.android.feature.transaction.domain.port.ORDER_SNAPSHOT_CONTENT_VERSION
import com.budcom.android.feature.transaction.domain.repository.StructuredRecipientInboxRepository
import com.budcom.android.feature.transaction.domain.model.OrderVersionSnapshot
import com.budcom.android.feature.transaction.domain.model.CommercialReturnEvent
import com.budcom.android.feature.transaction.domain.model.COMMERCIAL_EVENT_CONTENT_TYPE
import com.budcom.android.feature.transaction.domain.model.COMMERCIAL_EVENT_CONTENT_VERSION
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
            for (item in page.items) {
                if (!StructuredRecipientInboxValidation.validate(item, companyId)) return@withContext
                val ingestedAt = clock.now()
                val committed = when (item.commercialContentType) {
                    ORDER_SNAPSHOT_CONTENT_TYPE -> {
                        if (item.commercialContentVersion != ORDER_SNAPSHOT_CONTENT_VERSION) return@withContext
                        val snapshot = item.commercialSnapshotCanonical?.let { OrderVersionSnapshot.parse(it) } ?: return@withContext
                        orders.ingestReceivedOrderVersion(companyId, item, snapshot, ingestedAt)
                    }
                    COMMERCIAL_EVENT_CONTENT_TYPE -> {
                        if (item.commercialContentVersion != COMMERCIAL_EVENT_CONTENT_VERSION) return@withContext
                        val event = item.commercialSnapshotCanonical?.let { CommercialReturnEvent.parse(it) } ?: return@withContext
                        orders.ingestReceivedCommercialEvent(companyId, item, event, ingestedAt)
                    }
                    else -> return@withContext
                }
                if (!committed) return@withContext
                val acknowledged = client.acknowledgeDelivery(
                    envelopeId = item.envelopeId,
                    recipientBusinessId = companyId,
                    recipientActorId = credential.actorId,
                    recipientDeviceId = identity.deviceId,
                    receivedAtEpochMillis = ingestedAt.epochMillis,
                )
                if (!acknowledged) return@withContext
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
