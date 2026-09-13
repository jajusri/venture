package com.jajusri.venture.feature.transaction.domain.port

import com.jajusri.venture.feature.transaction.domain.model.OrderDeliveryEnvelope
import com.jajusri.venture.feature.transaction.domain.model.OrderTransportState
import com.jajusri.venture.feature.transaction.domain.model.TransactionTimestamp
import com.jajusri.venture.feature.transaction.domain.model.TransactionTimestampSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TransportEnvelopeContractTest {
    @Test
    fun `encoding is deterministic and references the canonical object`() {
        val envelope = envelope()
        val first = EnvelopeSubmission.fromEnvelope(envelope, "device-1", "business-2")
        val second = EnvelopeSubmission.fromEnvelope(envelope, "device-1", "business-2")

        assertEquals(EnvelopeSubmission.CURRENT_VERSION, first.contractVersion)
        assertEquals(envelope.orderId, first.objectId)
        assertEquals(envelope.orderVersion, first.objectVersion)
        assertEquals(first.deterministicEncoding(), second.deterministicEncoding())
        assertFalse(first.deterministicEncoding().contains("9999"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `sender device identity is required`() {
        EnvelopeSubmission.fromEnvelope(envelope(), "")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `metadata is bounded`() {
        EnvelopeSubmission.fromEnvelope(envelope(), "device-1").copy(
            boundedMetadata = (1..17).associate { "k$it" to "v" },
        )
    }

    private fun envelope() = OrderDeliveryEnvelope(
        companyId = "co-1", envelopeId = "env-1", idempotencyKey = "order:order-1:v1",
        objectType = "CANONICAL_ORDER", orderId = "order-1", orderVersion = 1,
        senderCompanyId = "co-1", recipientPartyId = "party-1",
        createdAt = TransactionTimestamp(10L, TransactionTimestampSource.DeviceLocalProvisional),
        state = OrderTransportState.Queued, attemptCount = 0, lastAttemptAt = null, lastError = null,
    )
}