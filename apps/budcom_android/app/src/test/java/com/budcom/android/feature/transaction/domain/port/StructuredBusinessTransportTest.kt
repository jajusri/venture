package com.budcom.android.feature.transaction.domain.port

import com.budcom.android.feature.transaction.domain.model.OrderDeliveryEnvelope
import com.budcom.android.feature.transaction.domain.model.OrderTransportState
import com.budcom.android.feature.transaction.domain.model.TransactionTimestamp
import com.budcom.android.feature.transaction.domain.model.TransactionTimestampSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StructuredBusinessTransportTest {
    @Test
    fun `router leaves queued envelope untouched when no transport is available`() = runTest {
        val envelope = envelope()
        val result = StructuredBusinessTransportRouter(emptyList()).submit(envelope)

        assertEquals(TransportRouterResult.NoAvailableTransport, result)
        assertEquals(OrderTransportState.Queued, envelope.state)
        assertEquals("order-1", envelope.orderId)
    }

    @Test
    fun `router returns adapter failure truthfully without fabricating delivery`() = runTest {
        val result = StructuredBusinessTransportRouter(
            listOf(StructuredBusinessTransport { TransportResult.RetryableFailure("offline") }),
        ).submit(envelope())

        assertTrue((result as TransportRouterResult.Submitted).result is TransportResult.RetryableFailure)
    }

    @Test
    fun `router preserves adapter delivery evidence without changing commercial state`() = runTest {
        val result = StructuredBusinessTransportRouter(
            listOf(StructuredBusinessTransport {
                TransportResult.Delivered(TransportEvidence(it.envelopeId, 20L))
            }),
        ).submit(envelope())

        val delivered = (result as TransportRouterResult.Submitted).result as TransportResult.Delivered
        assertEquals("envelope-1", delivered.evidence.envelopeId)
    }

    private fun envelope() = OrderDeliveryEnvelope(
        companyId = "co-1", envelopeId = "envelope-1", idempotencyKey = "order:order-1:v1",
        objectType = "CANONICAL_ORDER", orderId = "order-1", orderVersion = 1,
        senderCompanyId = "co-1", recipientPartyId = "buyer-1",
        createdAt = TransactionTimestamp(10L, TransactionTimestampSource.DeviceLocalProvisional),
        state = OrderTransportState.Queued, attemptCount = 0, lastAttemptAt = null, lastError = null,
    )
}