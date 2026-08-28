package com.budcom.android.feature.transaction.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommercialReturnEventTest {
    private val event = CommercialReturnEvent(
        contractVersion = 1,
        eventType = CommercialReturnEvent.TYPE_ORDER_CONFIRMED,
        originBusinessId = "buyer|\u0915",
        respondingBusinessId = "seller;\\",
        orderId = "order-1",
        orderVersion = 1,
        eventId = "event-1",
        idempotencyKey = "confirm:order-1:v1:seller",
        occurredAtEpochMillis = 42,
        occurredAtSource = TransactionTimestampSource.DeviceLocalProvisional.name,
    )

    @Test
    fun `canonical event round trips adversarial identity characters exactly`() {
        val canonical = event.deterministicEncoding()
        assertEquals(event, CommercialReturnEvent.parse(canonical))
        assertEquals(canonical, CommercialReturnEvent.parse(canonical)?.deterministicEncoding())
    }

    @Test
    fun `seen return is strict deterministic commercial content`() {
        val seen = event.copy(eventType = CommercialReturnEvent.TYPE_ORDER_SEEN, idempotencyKey = "seen:order-1:v1:seller")
        val canonical = seen.deterministicEncoding()
        assertEquals(seen, CommercialReturnEvent.parse(canonical))
        assertEquals(canonical, CommercialReturnEvent.parse(canonical)?.deterministicEncoding())
    }

    @Test
    fun `unsupported malformed and non canonical events fail closed`() {
        assertNull(CommercialReturnEvent.parse(event.deterministicEncoding().replace("\"contractVersion\":1", "\"contractVersion\":2")))
        assertNull(CommercialReturnEvent.parse(event.deterministicEncoding().replace("ORDER_CONFIRMED", "UNKNOWN")))
        assertNull(CommercialReturnEvent.parse("{}"))
        assertNull(CommercialReturnEvent.parse(" ${event.deterministicEncoding()}"))
    }
}
