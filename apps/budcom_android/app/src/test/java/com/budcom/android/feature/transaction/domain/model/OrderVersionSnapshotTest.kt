package com.budcom.android.feature.transaction.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderVersionSnapshotTest {
    @Test
    fun `encoding is deterministic and preserves quantity and authorized price`() {
        val first = snapshot()
        val second = snapshot()
        assertEquals(first.deterministicEncoding(), second.deterministicEncoding())
        assertEquals(first.fingerprint(), second.fingerprint())
        val parsed = OrderVersionSnapshot.parse(first.deterministicEncoding())!!
        assertEquals("order-1", parsed.orderId)
        assertEquals(1, parsed.orderVersion)
        assertEquals("10", parsed.lines.single().quantity)
        assertEquals("100", parsed.lines.single().unitPriceAmount)
        assertEquals("ACTUAL", parsed.lines.single().priceState)
        assertTrue(parsed.matchesEnvelope("order-1", 1, "seller-co", "buyer-co"))
        assertFalse(parsed.matchesEnvelope("order-1", 2, "seller-co", "buyer-co"))
    }

    @Test
    fun `hidden price is not leaked and tamper is detected`() {
        val hidden = snapshot(
            line = OrderVersionLineSnapshot(
                "line-1", "p1", "Widget", "Nos", "SKU-1", "10", null, null, OrderVersionLineSnapshot.HIDDEN, null,
            ),
        )
        val encoded = hidden.deterministicEncoding()
        assertFalse(encoded.contains("9999"))
        assertFalse(encoded.contains("unitPrice:9999"))
        assertTrue(encoded.contains("priceState:HIDDEN"))
        assertNull(hidden.lines.single().unitPriceAmount)
        val tampered = encoded.replace("qty:10", "qty:99")
        val parsedTamper = OrderVersionSnapshot.parse(tampered)!!
        assertTrue(hidden.detectTamper(parsedTamper.fingerprint()) || parsedTamper.fingerprint() != hidden.fingerprint())
        assertNotEquals(hidden.fingerprint(), parsedTamper.fingerprint())
    }

    @Test
    fun `wrong version and oversized payload are rejected`() {
        assertNull(OrderVersionSnapshot.parse(snapshot().deterministicEncoding().replace("v=1", "v=99")))
        assertNull(OrderVersionSnapshot.parse("x".repeat(OrderVersionSnapshot.MAX_PAYLOAD + 1)))
        val v2 = snapshot().copy(orderVersion = 2)
        assertEquals(2, v2.orderVersion)
        assertTrue(v2.deterministicEncoding().contains("version=2"))
        assertFalse(v2.matchesEnvelope("order-1", 1, "seller-co", "buyer-co"))
    }

    private fun snapshot(
        line: OrderVersionLineSnapshot = OrderVersionLineSnapshot(
            "line-1", "p1", "Widget", "Nos", "SKU-1", "10", "100", "INR", OrderVersionLineSnapshot.ACTUAL, "1000",
        ),
    ) = OrderVersionSnapshot(
        contractVersion = 1,
        orderId = "order-1",
        orderVersion = 1,
        senderBusinessId = "seller-co",
        recipientBusinessId = "buyer-co",
        createdAtEpochMillis = 10,
        note = "Deliver Friday",
        source = "CATALOGUE",
        submissionType = "ESTIMATE",
        envelopeId = "env-1",
        lines = listOf(line),
    )
}
