package com.jajusri.venture.feature.transaction.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderVersionSnapshotTest {
    @Test
    fun `canonical json v3 binds stable buyer and seller roles into immutable bytes`() {
        val snapshot = snapshot().copy(
            contractVersion = OrderVersionSnapshot.CURRENT_CONTRACT_VERSION,
            buyerBusinessId = "buyer-co",
            sellerBusinessId = "seller-co",
        )
        val encoded = snapshot.deterministicEncoding()
        val parsed = requireNotNull(OrderVersionSnapshot.parse(encoded))
        assertEquals("buyer-co", parsed.buyerBusinessId)
        assertEquals("seller-co", parsed.sellerBusinessId)
        assertNotEquals(encoded, snapshot.copy(sellerBusinessId = "other-seller").deterministicEncoding())
        assertNull(OrderVersionSnapshot.parse(encoded.replace("\"buyerBusinessId\":\"buyer-co\",", "")))
    }

    @Test
    fun `canonical json v2 round trips authorized commercial content`() {
        val original = snapshot()
        val encoded = original.deterministicEncoding()
        val parsed = requireNotNull(OrderVersionSnapshot.parse(encoded))
        assertTrue(encoded.startsWith("{\"schemaVersion\":2,"))
        assertEquals(original, parsed)
        assertEquals("10", parsed.lines.single().quantity)
        assertEquals("100", parsed.lines.single().unitPriceAmount)
        assertTrue(parsed.matchesEnvelope("order-1", 1, "seller-co", "buyer-co"))
        assertFalse(parsed.matchesEnvelope("order-1", 2, "seller-co", "buyer-co"))
    }

    @Test
    fun `json safely preserves delimiters slashes quotes controls unicode and emoji`() {
        val text = "Pipe | semicolon ; slash \\ quote \" newline\nहिंदी தமிழ் 🚚"
        val original = snapshot(note = text, line = actualLine(name = text, sku = "SKU|;\\\""))
        val encoded = original.deterministicEncoding()
        val parsed = requireNotNull(OrderVersionSnapshot.parse(encoded))
        assertEquals(text, parsed.note)
        assertEquals(text, parsed.lines.single().snapshotProductName)
        assertEquals("SKU|;\\\"", parsed.lines.single().snapshotSku)
        assertEquals(encoded, parsed.deterministicEncoding())
    }

    @Test
    fun `explicit nulls and long permitted values round trip`() {
        val longName = "न".repeat(OrderVersionLineSnapshot.MAX_FIELD)
        val longNote = "x".repeat(OrderVersionSnapshot.MAX_NOTE)
        val original = snapshot(
            note = longNote,
            line = OrderVersionLineSnapshot(
                "line-1", null, longName, null, null, "1", null, null,
                OrderVersionLineSnapshot.NO_PRICE, null,
            ),
        )
        val encoded = original.deterministicEncoding()
        val parsed = requireNotNull(OrderVersionSnapshot.parse(encoded))
        assertTrue(encoded.contains("\"linkedProductId\":null"))
        assertEquals(original, parsed)
        assertEquals(encoded, parsed.deterministicEncoding())
    }

    @Test
    fun `same content repeats exact utf8 bytes and line input order is irrelevant`() {
        val a = actualLine("line-a", "A")
        val b = actualLine("line-b", "B")
        val first = snapshot(lines = listOf(b, a))
        val second = snapshot(lines = listOf(a, b))
        assertTrue(
            first.deterministicEncoding().toByteArray(Charsets.UTF_8)
                .contentEquals(first.deterministicEncoding().toByteArray(Charsets.UTF_8)),
        )
        assertEquals(first.deterministicEncoding(), second.deterministicEncoding())
        assertEquals(first.fingerprint(), second.fingerprint())
        assertEquals(first.deterministicEncoding(), requireNotNull(OrderVersionSnapshot.parse(first.deterministicEncoding())).deterministicEncoding())
    }

    @Test
    fun `decimal strings have one stable representation`() {
        val variant = snapshot(line = actualLine(quantity = "10.00", unitPrice = "100.000", total = "1000.0"))
        val canonical = snapshot(line = actualLine(quantity = "10", unitPrice = "100", total = "1000"))
        assertEquals(canonical.deterministicEncoding(), variant.deterministicEncoding())
        assertEquals(canonical.fingerprint(), variant.fingerprint())
    }

    @Test
    fun `hidden price emits no numeric pricing and leakage attempt fails closed`() {
        val hidden = snapshot(
            line = OrderVersionLineSnapshot(
                "line-1", "p1", "Widget", "Nos", "SKU-1", "10", null, null,
                OrderVersionLineSnapshot.HIDDEN, null,
            ),
        )
        val encoded = hidden.deterministicEncoding()
        val leaked = encoded.replace("\"unitPriceAmount\":null", "\"unitPriceAmount\":\"9999\"")
        assertFalse(encoded.contains("9999"))
        assertTrue(encoded.contains("\"priceState\":\"HIDDEN\""))
        assertNull(OrderVersionSnapshot.parse(leaked))
    }

    @Test
    fun `duplicate lines invalid quantities and impossible price combinations are rejected`() {
        assertTrue(runCatching { snapshot(lines = listOf(actualLine(), actualLine())) }.isFailure)
        assertTrue(runCatching { snapshot(line = actualLine(quantity = "0")) }.isFailure)
        assertTrue(runCatching { snapshot(line = actualLine(quantity = "not-a-number")) }.isFailure)
        assertTrue(
            runCatching {
                snapshot(
                    line = OrderVersionLineSnapshot(
                        "line-1", "p1", "Widget", null, null, "1", "10", "INR",
                        OrderVersionLineSnapshot.HIDDEN, "10",
                    ),
                )
            }.isFailure,
        )
    }

    @Test
    fun `unsupported malformed oversized and noncanonical json are rejected`() {
        val encoded = snapshot().deterministicEncoding()
        assertNull(OrderVersionSnapshot.parse(encoded.replace("\"schemaVersion\":2", "\"schemaVersion\":99")))
        assertNull(OrderVersionSnapshot.parse("{not-json"))
        assertNull(OrderVersionSnapshot.parse("x".repeat(OrderVersionSnapshot.MAX_PAYLOAD + 1)))
        assertNull(OrderVersionSnapshot.parse(" $encoded"))
        assertNull(OrderVersionSnapshot.parse(encoded.replace("\"orderId\":", "\"unknown\":1,\"orderId\":")))
    }

    @Test
    fun `maximum line count is accepted and excess is rejected`() {
        val maximum = snapshot(
            lines = (1..OrderVersionSnapshot.MAX_LINES).map { index ->
                actualLine("line-${index.toString().padStart(3, '0')}", "P$index", quantity = "1", unitPrice = "1", total = "1")
            },
        )
        assertEquals(OrderVersionSnapshot.MAX_LINES, requireNotNull(OrderVersionSnapshot.parse(maximum.deterministicEncoding())).lines.size)
        assertTrue(
            runCatching {
                snapshot(
                    lines = (1..OrderVersionSnapshot.MAX_LINES + 1).map { index ->
                        actualLine("line-$index", "P$index", quantity = "1", unitPrice = "1", total = "1")
                    },
                )
            }.isFailure,
        )
    }

    @Test
    fun `tamper changes exact authenticated fingerprint`() {
        val original = snapshot()
        val tamperedEncoding = original.deterministicEncoding().replace("\"quantity\":\"10\"", "\"quantity\":\"99\"")
        val tampered = requireNotNull(OrderVersionSnapshot.parse(tamperedEncoding))
        assertNotEquals(original.fingerprint(), tampered.fingerprint())
        assertTrue(original.detectTamper(tampered.fingerprint()))
    }

    @Test
    fun `legacy delimiter contract is rejected`() {
        assertNull(OrderVersionSnapshot.parse("v=1|order=order-1"))
    }

    @Test
    fun `json root arrays are rejected`() {
        assertNull(OrderVersionSnapshot.parse("[]"))
    }

    @Test
    fun `wrong scalar types are rejected`() {
        val encoded = snapshot().deterministicEncoding()
        assertNull(OrderVersionSnapshot.parse(encoded.replace("\"orderVersion\":1", "\"orderVersion\":\"1\"")))
    }

    @Test
    fun `unknown line properties are rejected`() {
        val encoded = snapshot().deterministicEncoding()
        assertNull(OrderVersionSnapshot.parse(encoded.replace("\"lineId\":", "\"extra\":null,\"lineId\":")))
    }

    @Test
    fun `missing required properties are rejected`() {
        val encoded = snapshot().deterministicEncoding()
        assertNull(OrderVersionSnapshot.parse(encoded.replace("\"note\":\"Deliver Friday\",", "")))
    }

    @Test
    fun `noncanonical property order is rejected`() {
        val encoded = snapshot().deterministicEncoding()
        val reordered = encoded.replace(
            "\"orderId\":\"order-1\",\"orderVersion\":1",
            "\"orderVersion\":1,\"orderId\":\"order-1\"",
        )
        assertNull(OrderVersionSnapshot.parse(reordered))
    }

    @Test
    fun `noncanonical decimal spellings are rejected on parse`() {
        val encoded = snapshot().deterministicEncoding()
        assertNull(OrderVersionSnapshot.parse(encoded.replace("\"quantity\":\"10\"", "\"quantity\":\"10.0\"")))
    }

    @Test
    fun `blank canonical identifiers are rejected`() {
        assertTrue(runCatching { snapshot().copy(orderId = "") }.isFailure)
        assertTrue(runCatching { snapshot().copy(envelopeId = " ") }.isFailure)
    }

    @Test
    fun `negative timestamps are rejected`() {
        assertTrue(runCatching { snapshot().copy(createdAtEpochMillis = -1) }.isFailure)
    }

    @Test
    fun `all nonactual price states enforce full redaction`() {
        listOf(
            OrderVersionLineSnapshot.HIDDEN,
            OrderVersionLineSnapshot.CONTACT,
            OrderVersionLineSnapshot.NO_PRICE,
        ).forEach { state ->
            val line = OrderVersionLineSnapshot("line-$state", null, "Widget", null, null, "1", null, null, state, null)
            val encoded = snapshot(line = line).deterministicEncoding()
            assertFalse(encoded.contains("9999"))
            assertEquals(state, requireNotNull(OrderVersionSnapshot.parse(encoded)).lines.single().priceState)
        }
    }

    private fun snapshot(
        note: String? = "Deliver Friday",
        line: OrderVersionLineSnapshot = actualLine(),
        lines: List<OrderVersionLineSnapshot> = listOf(line),
    ) = OrderVersionSnapshot(
        contractVersion = OrderVersionSnapshot.LEGACY_CONTRACT_VERSION,
        orderId = "order-1",
        orderVersion = 1,
        senderBusinessId = "seller-co",
        recipientBusinessId = "buyer-co",
        createdAtEpochMillis = 10,
        note = note,
        source = "CATALOGUE",
        submissionType = "ESTIMATE",
        envelopeId = "env-1",
        lines = lines,
    )

    private fun actualLine(
        lineId: String = "line-1",
        name: String = "Widget",
        quantity: String = "10",
        unitPrice: String = "100",
        total: String = "1000",
        sku: String? = "SKU-1",
    ) = OrderVersionLineSnapshot(
        lineId, "p-$lineId", name, "Nos", sku, quantity, unitPrice, "INR",
        OrderVersionLineSnapshot.ACTUAL, total,
    )
}
