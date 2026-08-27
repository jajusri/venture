package com.budcom.android.feature.transaction.data.relay

import com.budcom.android.core.util.DispatcherProvider
import com.budcom.android.feature.transaction.data.local.OrderDeliveryEnvelopeEntity
import com.budcom.android.feature.transaction.data.repository.FakeOrderOutboxDao
import com.budcom.android.feature.transaction.domain.model.CanonicalOrder
import com.budcom.android.feature.transaction.domain.model.OrderDeliveryEnvelope
import com.budcom.android.feature.transaction.domain.model.OrderTransportState
import com.budcom.android.feature.transaction.domain.model.RelayAcceptanceEvidence
import com.budcom.android.feature.transaction.domain.model.TransactionClock
import com.budcom.android.feature.transaction.domain.model.TransactionTimestamp
import com.budcom.android.feature.transaction.domain.model.TransactionTimestampSource
import com.budcom.android.feature.transaction.domain.port.EmptyRelayEndpointProvider
import com.budcom.android.feature.transaction.domain.port.OrderSentFromRelayEvidence
import com.budcom.android.feature.transaction.domain.port.StructuredBusinessTransport
import com.budcom.android.feature.transaction.domain.port.TransportEvidence
import com.budcom.android.feature.transaction.domain.port.TransportResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RelayOutboxDispatcherTest {
    private val dispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
        override val io: CoroutineDispatcher = Dispatchers.Unconfined
        override val default: CoroutineDispatcher = Dispatchers.Unconfined
    }
    private val clock = TransactionClock { TransactionTimestamp(50, TransactionTimestampSource.DeviceLocalProvisional) }
    private val orderSent = OrderSentFromRelayEvidence { _, _, _ -> null }

    @Test
    fun `missing relay endpoint leaves durable queued envelope untouched`() = runTest {
        val dao = FakeOrderOutboxDao()
        dao.insert(queued())
        DefaultRelayOutboxDispatcher(
            dao,
            RelayAwareTransportRouter(EmptyRelayEndpointProvider, StructuredBusinessTransport { TransportResult.RetryableFailure("unused") }),
            clock,
            dispatchers,
            orderSent,
        ).submitPending("co-1")
        assertEquals("QUEUED", dao.envelopes.single().state)
        assertEquals(0, dao.envelopes.single().attemptCount)
    }

    @Test
    fun `relay acceptance updates transport state without deleting local intent`() = runTest {
        val dao = FakeOrderOutboxDao()
        dao.insert(queued())
        DefaultRelayOutboxDispatcher(
            dao,
            RelayAwareTransportRouter(
                { "http://relay.test/" },
                StructuredBusinessTransport { TransportResult.Accepted(TransportEvidence(it.envelopeId, 50, "accept-1")) },
            ),
            clock,
            dispatchers,
            orderSent,
        ).submitPending("co-1")
        val stored = dao.envelopes.single()
        assertEquals(OrderTransportState.RelayAccepted.columnValue, stored.state)
        assertEquals(1, stored.attemptCount)
        assertNull(stored.lastError)
        assertEquals("envelope-1", stored.envelopeId)
    }

    @Test
    fun `matching relay acceptance evidence marks commercial order sent without delivered or seen`() = runTest {
        val dao = FakeOrderOutboxDao()
        dao.insert(queued())
        val recording = RecordingOrders()
        val evidence = RelayAcceptanceEvidence(
            "accept-1", "envelope-1", "CANONICAL_ORDER", "order-1", 1, "co-1", "buyer-1", 50, "relay_accepted",
        )
        DefaultRelayOutboxDispatcher(
            dao,
            RelayAwareTransportRouter(
                { "http://relay.test/" },
                StructuredBusinessTransport {
                    TransportResult.Accepted(TransportEvidence(it.envelopeId, 50, "accept-1"), evidence)
                },
            ),
            clock,
            dispatchers,
            recording,
        ).submitPending("co-1")
        assertEquals(OrderTransportState.RelayAccepted.columnValue, dao.envelopes.single().state)
        assertEquals(1, recording.marked.size)
        assertEquals("envelope-1", recording.marked.single().envelopeId)
        assertEquals("relay_accepted", recording.marked.single().status)
    }

    @Test
    fun `retryable failure keeps the envelope for later submission`() = runTest {
        val dao = FakeOrderOutboxDao()
        dao.insert(queued())
        DefaultRelayOutboxDispatcher(
            dao,
            RelayAwareTransportRouter(
                { "http://relay.test/" },
                StructuredBusinessTransport { TransportResult.RetryableFailure("offline") },
            ),
            clock,
            dispatchers,
            orderSent,
        ).submitPending("co-1")
        assertEquals(OrderTransportState.Retrying.columnValue, dao.envelopes.single().state)
        assertEquals("offline", dao.envelopes.single().lastError)
    }

    private fun queued() = OrderDeliveryEnvelopeEntity(
        companyId = "co-1", envelopeId = "envelope-1", idempotencyKey = "order:order-1:v1",
        objectType = "CANONICAL_ORDER", orderId = "order-1", orderVersion = 1,
        senderCompanyId = "co-1", recipientPartyId = "buyer-1", createdAt = 10,
        createdAtSource = TransactionTimestampSource.DeviceLocalProvisional.name, state = "QUEUED", attemptCount = 0,
        lastAttemptAt = null, lastAttemptAtSource = null, lastError = null,
    )

    private class RecordingOrders : OrderSentFromRelayEvidence {
        val marked = mutableListOf<RelayAcceptanceEvidence>()
        override suspend fun markOrderSentFromRelayEvidence(
            companyId: String,
            envelope: OrderDeliveryEnvelope,
            evidence: RelayAcceptanceEvidence,
        ): CanonicalOrder? {
            marked += evidence
            return null
        }
    }
}
