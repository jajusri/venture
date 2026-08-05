package com.budcom.android.core.pairing.domain.model

import com.budcom.android.core.util.TimeProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64

private const val NOW_EPOCH_MILLIS = 1_800_000_000_000L

private fun validFingerprint(): String {
    val keyPair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
    val digest = MessageDigest.getInstance("SHA-256").digest(keyPair.public.encoded)
    return "sha256/" + Base64.getEncoder().encodeToString(digest)
}

private fun validPayloadJson(
    schemaVersion: String = "1",
    pairingSessionId: String = "11111111-1111-1111-1111-111111111111",
    secret: String = "one-time-secret-value",
    connectorId: String = "connector-abc",
    connectorName: String = "Front Desk",
    host: String = "10.100.141.231",
    port: Int = 8080,
    securePort: Int = 8443,
    transportProtocol: String = "https",
    transportFingerprint: String = validFingerprint(),
    fingerprintAlgorithm: String = "sha256",
    transportIdentityVersion: Int = 1,
    expiresAt: String = Instant.ofEpochMilli(NOW_EPOCH_MILLIS + 120_000L).toString(),
): String = """
    {
      "schemaVersion": "$schemaVersion",
      "pairingSessionId": "$pairingSessionId",
      "secret": "$secret",
      "connectorId": "$connectorId",
      "connectorName": "$connectorName",
      "host": "$host",
      "port": $port,
      "securePort": $securePort,
      "transportProtocol": "$transportProtocol",
      "transportFingerprint": "$transportFingerprint",
      "fingerprintAlgorithm": "$fingerprintAlgorithm",
      "transportIdentityVersion": $transportIdentityVersion,
      "expiresAt": "$expiresAt"
    }
""".trimIndent()

class SecurePairingQrPayloadParserTest {

    private fun parserOf(nowEpochMillis: Long = NOW_EPOCH_MILLIS) = SecurePairingQrPayloadParser(TimeProvider { nowEpochMillis })

    // 1. valid secure QR parses
    @Test
    fun `valid secure QR payload parses successfully`() {
        val fingerprint = validFingerprint()
        val result = parserOf().parse(validPayloadJson(transportFingerprint = fingerprint, host = "192.168.1.50"))

        assertTrue(result is SecurePairingQrPayloadParseResult.Valid)
        val payload = (result as SecurePairingQrPayloadParseResult.Valid).payload
        assertEquals("connector-abc", payload.connectorId)
        assertEquals("192.168.1.50", payload.host)
        assertEquals(8080, payload.port)
        assertEquals(8443, payload.securePort)
        assertEquals(fingerprint, payload.transportFingerprint)
    }

    // 2. unsupported schema rejected
    @Test
    fun `unsupported schema version is rejected`() {
        val result = parserOf().parse(validPayloadJson(schemaVersion = "999"))
        assertRejection<SecurePairingQrPayloadRejection.UnsupportedSchema>(result)
    }

    // 3. missing secret rejected
    @Test
    fun `missing secret field is rejected`() {
        val json = validPayloadJson().let { removeJsonField(it, "secret") }
        val result = parserOf().parse(json)
        val rejection = assertRejection<SecurePairingQrPayloadRejection.MissingFields>(result)
        assertTrue(rejection.fields.contains("secret"))
    }

    // 4. missing fingerprint rejected
    @Test
    fun `missing transportFingerprint field is rejected`() {
        val json = removeJsonField(validPayloadJson(), "transportFingerprint")
        val result = parserOf().parse(json)
        val rejection = assertRejection<SecurePairingQrPayloadRejection.MissingFields>(result)
        assertTrue(rejection.fields.contains("transportFingerprint"))
    }

    // 5. malformed fingerprint rejected
    @Test
    fun `malformed fingerprint without the sha256 prefix is rejected`() {
        val result = parserOf().parse(validPayloadJson(transportFingerprint = "not-the-right-shape"))
        assertRejection<SecurePairingQrPayloadRejection.MalformedFingerprint>(result)
    }

    @Test
    fun `fingerprint with invalid base64 content is rejected`() {
        val result = parserOf().parse(validPayloadJson(transportFingerprint = "sha256/not!valid!base64!!"))
        assertRejection<SecurePairingQrPayloadRejection.MalformedFingerprint>(result)
    }

    // 6. non-32-byte fingerprint rejected
    @Test
    fun `fingerprint that does not decode to 32 bytes is rejected`() {
        val shortDigest = Base64.getEncoder().encodeToString(ByteArray(16) { it.toByte() })
        val result = parserOf().parse(validPayloadJson(transportFingerprint = "sha256/$shortDigest"))
        assertRejection<SecurePairingQrPayloadRejection.InvalidFingerprintLength>(result)
    }

    // 7. HTTP protocol rejected
    @Test
    fun `non-https transportProtocol is rejected`() {
        val result = parserOf().parse(validPayloadJson(transportProtocol = "http"))
        assertRejection<SecurePairingQrPayloadRejection.UnsupportedTransportProtocol>(result)
    }

    // 8. expired payload rejected
    @Test
    fun `already-expired payload is rejected`() {
        val past = Instant.ofEpochMilli(NOW_EPOCH_MILLIS - 60_000L).toString()
        val result = parserOf().parse(validPayloadJson(expiresAt = past))
        assertRejection<SecurePairingQrPayloadRejection.ExpiryNotInFuture>(result)
    }

    // 9. excessive expiry rejected
    @Test
    fun `expiry far beyond the approved pairing-session limit is rejected`() {
        val farFuture = Instant.ofEpochMilli(NOW_EPOCH_MILLIS + 3_600_000L).toString() // 1 hour out
        val result = parserOf().parse(validPayloadJson(expiresAt = farFuture))
        assertRejection<SecurePairingQrPayloadRejection.ExpiryTooFarInFuture>(result)
    }

    @Test
    fun `malformed expiresAt timestamp is rejected`() {
        val result = parserOf().parse(validPayloadJson(expiresAt = "not-a-timestamp"))
        assertRejection<SecurePairingQrPayloadRejection.MalformedExpiry>(result)
    }

    // 10. invalid port rejected
    @Test
    fun `securePort out of range is rejected`() {
        val result = parserOf().parse(validPayloadJson(securePort = 70_000))
        assertRejection<SecurePairingQrPayloadRejection.InvalidPort>(result)
    }

    @Test
    fun `plain port out of range is rejected`() {
        val result = parserOf().parse(validPayloadJson(port = 0))
        assertRejection<SecurePairingQrPayloadRejection.InvalidPort>(result)
    }

    // 11. wildcard host rejected
    @Test
    fun `wildcard IPv4 host is rejected`() {
        val result = parserOf().parse(validPayloadJson(host = "0.0.0.0"))
        assertRejection<SecurePairingQrPayloadRejection.InvalidHost>(result)
    }

    // 12. loopback host rejected for physical mode
    @Test
    fun `loopback host is rejected under the default physical-device policy`() {
        val result = parserOf().parse(validPayloadJson(host = "127.0.0.1"), SecurePairingQrValidationPolicy(allowLoopbackHost = false))
        assertRejection<SecurePairingQrPayloadRejection.InvalidHost>(result)
    }

    @Test
    fun `loopback host is accepted only via the explicit injected test-emulator policy`() {
        val result = parserOf().parse(validPayloadJson(host = "127.0.0.1"), SecurePairingQrValidationPolicy(allowLoopbackHost = true))
        assertTrue(result is SecurePairingQrPayloadParseResult.Valid)
    }

    // 13. public internet host rejected
    @Test
    fun `public internet IPv4 host is rejected`() {
        val result = parserOf().parse(validPayloadJson(host = "8.8.8.8"))
        assertRejection<SecurePairingQrPayloadRejection.InvalidHost>(result)
    }

    // 14. private IPv4 endpoint accepted
    @Test
    fun `private IPv4 ranges (10-8, 172-16-12, 192-168-16) are all accepted`() {
        for (host in listOf("10.0.0.5", "172.16.5.5", "172.31.255.254", "192.168.29.34")) {
            val result = parserOf().parse(validPayloadJson(host = host))
            assertTrue("expected $host to be accepted", result is SecurePairingQrPayloadParseResult.Valid)
        }
    }

    // 15. supported local/private IPv6 accepted where applicable
    @Test
    fun `IPv6 unique-local and link-local addresses are accepted`() {
        for (host in listOf("fc00::1", "fd12:3456:789a:1::1", "fe80::1")) {
            val result = parserOf().parse(validPayloadJson(host = host))
            assertTrue("expected $host to be accepted", result is SecurePairingQrPayloadParseResult.Valid)
        }
    }

    @Test
    fun `IPv6 loopback is rejected under the default physical-device policy`() {
        val result = parserOf().parse(validPayloadJson(host = "::1"))
        assertRejection<SecurePairingQrPayloadRejection.InvalidHost>(result)
    }

    @Test
    fun `IPv6 globally routable address is rejected as public`() {
        val result = parserOf().parse(validPayloadJson(host = "2001:4860:4860::8888"))
        assertRejection<SecurePairingQrPayloadRejection.InvalidHost>(result)
    }

    // 16. scheme/path/query/userinfo injection rejected
    @Test
    fun `host containing a scheme prefix is rejected`() {
        val result = parserOf().parse(validPayloadJson(host = "http://10.0.0.5"))
        assertRejection<SecurePairingQrPayloadRejection.InvalidHost>(result)
    }

    @Test
    fun `host containing a path is rejected`() {
        val result = parserOf().parse(validPayloadJson(host = "10.0.0.5/admin"))
        assertRejection<SecurePairingQrPayloadRejection.InvalidHost>(result)
    }

    @Test
    fun `host containing userinfo injection is rejected`() {
        val result = parserOf().parse(validPayloadJson(host = "attacker@10.0.0.5"))
        assertRejection<SecurePairingQrPayloadRejection.InvalidHost>(result)
    }

    @Test
    fun `host containing a query string is rejected`() {
        val result = parserOf().parse(validPayloadJson(host = "10.0.0.5?x=1"))
        assertRejection<SecurePairingQrPayloadRejection.InvalidHost>(result)
    }

    // 17. parser does not log or expose secret through toString()
    @Test
    fun `payload toString never exposes the secret or any other field`() {
        val result = parserOf().parse(validPayloadJson(secret = "super-secret-value-should-never-leak"))
        val payload = (result as SecurePairingQrPayloadParseResult.Valid).payload
        assertEquals("SecurePairingQrPayload(redacted)", payload.toString())
        assertFalse(payload.toString().contains("super-secret-value-should-never-leak"))
    }

    // Extra: duplicate top-level keys rejected (Phase 3 requirement, not separately numbered above)
    @Test
    fun `duplicate top-level JSON keys are rejected`() {
        val json = """{"schemaVersion":"1","schemaVersion":"1","pairingSessionId":"x","secret":"y",
            |"connectorId":"z","connectorName":"n","host":"10.0.0.1","port":8080,"securePort":8443,
            |"transportProtocol":"https","transportFingerprint":"sha256/AAAA","fingerprintAlgorithm":"sha256",
            |"transportIdentityVersion":1,"expiresAt":"${Instant.ofEpochMilli(NOW_EPOCH_MILLIS + 60_000L)}"}
        """.trimMargin()
        val result = parserOf().parse(json)
        assertRejection<SecurePairingQrPayloadRejection.DuplicateFields>(result)
    }

    @Test
    fun `a legitimate value containing key-shaped text is not misdetected as a duplicate key`() {
        val trickySecret = "\\\"connectorId\\\": \\\"fake\\\""
        val result = parserOf().parse(validPayloadJson(secret = trickySecret))
        assertTrue(result is SecurePairingQrPayloadParseResult.Valid)
    }

    @Test
    fun `oversized payload is rejected before any JSON parsing`() {
        val hugeSecret = "x".repeat(10_000)
        val result = parserOf().parse(validPayloadJson(secret = hugeSecret))
        assertRejection<SecurePairingQrPayloadRejection.PayloadTooLarge>(result)
    }

    @Test
    fun `malformed JSON is rejected`() {
        val result = parserOf().parse("not json at all { [ }")
        assertRejection<SecurePairingQrPayloadRejection.MalformedJson>(result)
    }

    private inline fun <reified T : SecurePairingQrPayloadRejection> assertRejection(
        result: SecurePairingQrPayloadParseResult,
    ): T {
        assertTrue("expected Invalid but was $result", result is SecurePairingQrPayloadParseResult.Invalid)
        val rejection = (result as SecurePairingQrPayloadParseResult.Invalid).rejection
        assertTrue("expected ${T::class.simpleName} but was $rejection", rejection is T)
        assertNotNull(rejection)
        return rejection as T
    }
}

/** Removes a top-level `"field": value,` (or trailing, no-comma-following) entry from a small test-fixture JSON string. */
private fun removeJsonField(json: String, field: String): String {
    val regex = Regex(""""$field"\s*:\s*(".*?"|[^,\n}]+),?\s*\n?""")
    return regex.replace(json, "")
}
