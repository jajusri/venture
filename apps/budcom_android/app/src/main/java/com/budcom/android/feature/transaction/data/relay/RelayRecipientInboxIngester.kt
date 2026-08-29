package com.budcom.android.feature.transaction.data.relay

import com.budcom.android.core.util.DispatcherProvider
import com.budcom.android.feature.transaction.domain.model.StructuredRecipientInboxValidation
import com.budcom.android.feature.transaction.domain.model.TransactionClock
import com.budcom.android.feature.transaction.domain.port.RelayEndpointProvider
import com.budcom.android.feature.transaction.domain.port.RelayEnvelopeAuthenticator
import com.budcom.android.feature.transaction.domain.port.RelayRecipientInboxIngester
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
    private val authenticator: RelayEnvelopeAuthenticator,
    private val clock: TransactionClock,
    private val dispatchers: DispatcherProvider,
) : RelayRecipientInboxIngester {
    override suspend fun ingestPending(companyId: String) = withContext(dispatchers.io) {
        if (endpoint.snapshot() == null) return@withContext
        var cursor = inbox.loadMailboxCursor(companyId, DEFAULT_MAILBOX)
        var pages = 0
        while (pages < MAX_MAILBOX_PAGES_PER_INGEST) {
            val fetchAuth = authenticator.authenticateMailboxFetch(companyId, DEFAULT_MAILBOX, cursor, DEFAULT_PAGE_SIZE)
                ?: return@withContext
            val page = client.fetchMailbox(
                authenticated = fetchAuth,
                recipientBusinessId = companyId,
                mailboxId = DEFAULT_MAILBOX,
                cursor = cursor,
                limit = DEFAULT_PAGE_SIZE,
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
                val ackAuth = authenticator.authenticateAcknowledgement(companyId, item.envelopeId, ingestedAt.epochMillis)
                    ?: return@withContext
                val acknowledged = client.acknowledgeDelivery(
                    authenticated = ackAuth,
                    envelopeId = item.envelopeId,
                    recipientBusinessId = companyId,
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
        const val DEFAULT_PAGE_SIZE = 25
    }
}
