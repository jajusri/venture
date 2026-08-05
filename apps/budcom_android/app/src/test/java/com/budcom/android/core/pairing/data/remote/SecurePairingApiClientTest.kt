package com.budcom.android.core.pairing.data.remote

import com.budcom.android.core.pairing.domain.model.PairingDeviceIdentity
import com.budcom.android.core.pairing.domain.model.SecurePairingQrPayload
import com.budcom.android.core.security.DefaultSpkiFingerprintVerifier
import com.budcom.android.core.util.TimeProvider
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun testPayload(host: String, port: Int, securePort: Int, connectorId: String = "connector-abc") = SecurePairingQrPayload(
    schemaVersion = "1",
    pairingSessionId = "11111111-1111-1111-1111-111111111111",
    secret = "one-time-secret-value",
    connectorId = connectorId,
    connectorName = "Front Desk",
    host = host,
    port = port,
    securePort = securePort,
    transportProtocol = "https",
    transportFingerprint = "sha256/AAAA",
    fingerprintAlgorithm = "sha256",
    transportIdentityVersion = 1,
    expiresAtEpochMillis = TLS_TEST_NOW_EPOCH_MILLIS + 60_000L,
)

class SecurePairingApiClientTest {

    private val timeProvider = TimeProvider { TLS_TEST_NOW_EPOCH_MILLIS }
    private val factory = OkHttpPinnedHttpClientFactory(DefaultSpkiFingerprintVerifier(), timeProvider)
    private val client = OkHttpSecurePairingApiClient(factory)
    private val servers = mutableListOf<MockWebServer>()

    @After
    fun tearDown() {
        servers.forEach { it.shutdown() }
    }

    private fun newServer(): Pair<MockWebServer, okhttp3.tls.HeldCertificate> {
        val heldCertificate = testHeldCertificate()
        val server = startTlsMockWebServer(heldCertificate)
        servers += server
        return server to heldCertificate
    }

    // 39/40. QR redemption sends the exact required fields, including device id/label
    @Test
    fun `redeemQr sends every required field and the device identity, over the pinned connection`() = runTest {
        val (server, heldCertificate) = newServer()
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """{"ok":true,"connectorId":"connector-abc","connectorName":"Front Desk","credentialId":"cred-1","token":"raw-token"}""",
            ),
        )
        val payload = testPayload(server.hostName, port = 8080, securePort = server.port).let {
            it.copy(transportFingerprint = fingerprintOf(heldCertificate))
        }
        val deviceIdentity = PairingDeviceIdentity("device-xyz", "Sri's Phone")

        val outcome = client.redeemQr(payload, deviceIdentity)

        assertTrue(outcome is PairingRedeemOutcome.Success)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/device/pairing-session/redeem", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"pairingSessionId\":\"11111111-1111-1111-1111-111111111111\""))
        assertTrue(body.contains("\"secret\":\"one-time-secret-value\""))
        assertTrue(body.contains("\"connectorId\":\"connector-abc\""))
        assertTrue(body.contains("\"host\":"))
        assertTrue(body.contains("\"port\":8080"))
        assertTrue(body.contains("\"deviceId\":\"device-xyz\""))
        assertTrue(body.contains("\"deviceLabel\":\"Sri's Phone\""))
    }

    // 42. canonical Bearer header used
    @Test
    fun `getCredentialSelf sends the canonical Authorization Bearer header`() = runTest {
        val (server, heldCertificate) = newServer()
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"credentialId":"cred-1","deviceId":"device-1","deviceLabel":"Phone","connectorId":"connector-abc","createdAt":"2026-01-01T00:00:00Z","lastUsedAt":null,"status":"active"}""",
            ),
        )
        val endpoint = endpointFor(server, heldCertificate)

        client.getCredentialSelf(endpoint, "my-raw-token")

        val request = server.takeRequest()
        assertEquals("Bearer my-raw-token", request.getHeader("Authorization"))
    }

    // 43. token never sent through query/body/cookie
    @Test
    fun `the bearer credential never appears in the request path, query, or body`() = runTest {
        val (server, heldCertificate) = newServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"ok\":true,\"message\":\"revoked\"}"))
        val endpoint = endpointFor(server, heldCertificate)

        client.revokeSelf(endpoint, "super-secret-bearer-value")

        val request = server.takeRequest()
        assertFalse(request.path!!.contains("super-secret-bearer-value"))
        assertFalse(request.body.readUtf8().contains("super-secret-bearer-value"))
        assertEquals(null, request.getHeader("Cookie"))
        assertEquals("Bearer super-secret-bearer-value", request.getHeader("Authorization"))
    }

    @Test
    fun `revokeSelf reports Unauthorized on a 401 response`() = runTest {
        val (server, heldCertificate) = newServer()
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"code":"UNAUTHORIZED","message":"nope"}"""))
        val endpoint = endpointFor(server, heldCertificate)

        val outcome = client.revokeSelf(endpoint, "some-token")

        assertEquals(PairingSelfRevokeOutcome.Unauthorized, outcome)
    }

    @Test
    fun `getCredentialSelf reports Unauthorized on a 401 response`() = runTest {
        val (server, heldCertificate) = newServer()
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"code":"UNAUTHORIZED","message":"nope"}"""))
        val endpoint = endpointFor(server, heldCertificate)

        val outcome = client.getCredentialSelf(endpoint, "some-token")

        assertEquals(PairingSelfStatusOutcome.Unauthorized, outcome)
    }

    // 52. fingerprint mismatch never falls back — a genuine transport failure, never a silent success
    @Test
    fun `a self-status call against a server whose certificate does not match the pin fails as a transport failure`() = runTest {
        val (server, _) = newServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("should never be read"))
        val wrongCert = testHeldCertificate()
        val endpoint = endpointFor(server, wrongCert) // pinned to a fingerprint the server does NOT present

        val outcome = client.getCredentialSelf(endpoint, "some-token")

        assertEquals(PairingSelfStatusOutcome.TransportFailure, outcome)
    }

    @Test
    fun `a redeem call against a server whose certificate does not match the pin fails as a transport failure, never falling back`() = runTest {
        val (server, _) = newServer()
        server.enqueue(MockResponse().setResponseCode(201).setBody("should never be read"))
        val wrongCert = testHeldCertificate()
        val payload = testPayload(server.hostName, port = 8080, securePort = server.port).copy(
            transportFingerprint = fingerprintOf(wrongCert),
        )

        val outcome = client.redeemQr(payload, PairingDeviceIdentity("device-1", "Phone"))

        assertEquals(PairingRedeemOutcome.TransportFailure, outcome)
    }

    @Test
    fun `redeemShortCode sends only the short code and device identity, using the caller-supplied endpoint`() = runTest {
        val (server, heldCertificate) = newServer()
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """{"ok":true,"connectorId":"connector-abc","connectorName":"Front Desk","credentialId":"cred-1","token":"raw-token"}""",
            ),
        )
        val endpoint = endpointFor(server, heldCertificate)

        val outcome = client.redeemShortCode("ABCDEFGH", endpoint, PairingDeviceIdentity("device-1", "Phone"))

        assertTrue(outcome is PairingRedeemOutcome.Success)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"shortCode\":\"ABCDEFGH\""))
    }

    @Test
    fun `a rejected redemption maps the Connector's reason code and status through`() = runTest {
        val (server, heldCertificate) = newServer()
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"ok":false,"code":"REDEMPTION_FAILED","message":"nope"}"""))
        val payload = testPayload(server.hostName, port = 8080, securePort = server.port).copy(
            transportFingerprint = fingerprintOf(heldCertificate),
        )

        val outcome = client.redeemQr(payload, PairingDeviceIdentity("device-1", "Phone")) as PairingRedeemOutcome.Rejected

        assertEquals("REDEMPTION_FAILED", outcome.reasonCode)
        assertEquals(401, outcome.httpStatus)
    }
}
