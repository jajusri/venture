package com.budcom.android.core.connectorauth.domain

import com.budcom.android.core.discovery.DiscoveredConnector
import com.budcom.android.core.discovery.FakeConnectorDiscoveryPort
import com.budcom.android.core.pairing.data.remote.OkHttpPinnedHttpClientFactory
import com.budcom.android.core.pairing.data.remote.PinnedHttpClientFactory
import com.budcom.android.core.pairing.data.remote.TLS_TEST_NOW_EPOCH_MILLIS
import com.budcom.android.core.pairing.data.remote.endpointFor
import com.budcom.android.core.pairing.data.remote.fingerprintOf
import com.budcom.android.core.pairing.data.remote.startTlsMockWebServer
import com.budcom.android.core.pairing.data.remote.testHeldCertificate
import com.budcom.android.core.pairing.domain.model.TrustedConnectorEndpoint
import com.budcom.android.core.security.DefaultSpkiFingerprintVerifier
import com.budcom.android.core.util.TimeProvider
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val EXPECTED_CONNECTOR_ID = "connector-abc"

class AuthenticatedConnectorEndpointResolverTest {

    private val timeProvider = TimeProvider { TLS_TEST_NOW_EPOCH_MILLIS }
    private val factory: PinnedHttpClientFactory = OkHttpPinnedHttpClientFactory(DefaultSpkiFingerprintVerifier(), timeProvider)
    private val servers = mutableListOf<MockWebServer>()

    @After
    fun tearDown() {
        servers.forEach { it.shutdown() }
    }

    private fun newServer(): Pair<MockWebServer, HeldCertificate> {
        val heldCertificate = testHeldCertificate()
        val server = startTlsMockWebServer(heldCertificate)
        servers += server
        return server to heldCertificate
    }

    private val expected = TrustedConnectorEndpointFixture.endpoint(connectorId = EXPECTED_CONNECTOR_ID)

    @Test
    fun `no discovered candidates yields Unavailable`() = runTest {
        val discovery = FakeConnectorDiscoveryPort(emptyList())
        val resolver = DefaultAuthenticatedConnectorEndpointResolver(discovery, factory)

        val result = resolver.resolveVerifiedEndpoint(expected)

        assertEquals(VerifiedEndpointResolution.Unavailable, result)
    }

    @Test
    fun `a candidate with a different connectorId is ignored without any connection attempt`() = runTest {
        val (server, heldCertificate) = newServer()
        server.enqueue(MockResponse().setResponseCode(200)) // would succeed if ever dialed
        val discovery = FakeConnectorDiscoveryPort(
            listOf(discoveredFrom(server, heldCertificate, connectorId = "a-different-connector")),
        )
        val resolver = DefaultAuthenticatedConnectorEndpointResolver(discovery, factory)

        val result = resolver.resolveVerifiedEndpoint(expected)

        assertEquals(VerifiedEndpointResolution.Unavailable, result)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a matching-connectorId candidate whose certificate satisfies the pinned fingerprint is Verified`() = runTest {
        val (server, heldCertificate) = newServer()
        server.enqueue(MockResponse().setResponseCode(200))
        val expectedWithFingerprint = expected.copy(transportFingerprint = fingerprintOf(heldCertificate))
        val discovery = FakeConnectorDiscoveryPort(
            listOf(discoveredFrom(server, heldCertificate, connectorId = EXPECTED_CONNECTOR_ID)),
        )
        val resolver = DefaultAuthenticatedConnectorEndpointResolver(discovery, factory)

        val result = resolver.resolveVerifiedEndpoint(expectedWithFingerprint) as VerifiedEndpointResolution.Verified

        assertEquals(server.hostName, result.endpoint.host)
        assertEquals(server.port, result.endpoint.securePort)
        // connectorId/fingerprint/name/algorithm/version all carried through from `expected`, never
        // from the candidate's own (untrusted) advertisement.
        assertEquals(expectedWithFingerprint.connectorId, result.endpoint.connectorId)
        assertEquals(expectedWithFingerprint.transportFingerprint, result.endpoint.transportFingerprint)
        assertEquals(expectedWithFingerprint.connectorName, result.endpoint.connectorName)
        assertEquals(expectedWithFingerprint.fingerprintAlgorithm, result.endpoint.fingerprintAlgorithm)
        assertEquals(expectedWithFingerprint.transportIdentityVersion, result.endpoint.transportIdentityVersion)
    }

    @Test
    fun `a matching-connectorId candidate whose certificate does NOT satisfy the pinned fingerprint is rejected`() = runTest {
        val (server, wrongCert) = newServer() // server presents a cert unrelated to `expected`'s pinned fingerprint
        server.enqueue(MockResponse().setResponseCode(200).setBody("should never be trusted"))
        val discovery = FakeConnectorDiscoveryPort(
            listOf(discoveredFrom(server, wrongCert, connectorId = EXPECTED_CONNECTOR_ID)),
        )
        val resolver = DefaultAuthenticatedConnectorEndpointResolver(discovery, factory)

        val result = resolver.resolveVerifiedEndpoint(expected) // expected's fingerprint matches nothing real here

        assertEquals(VerifiedEndpointResolution.Unavailable, result)
    }

    @Test
    fun `an impostor advertising the expected connectorId but a forged certificate is rejected, not trusted`() = runTest {
        val (legitServer, legitCert) = newServer()
        val (impostorServer, impostorCert) = newServer()
        impostorServer.enqueue(MockResponse().setResponseCode(200).setBody("attacker response"))
        val expectedWithLegitFingerprint = expected.copy(transportFingerprint = fingerprintOf(legitCert))
        // Only the impostor is discoverable — the legitimate server never even gets a request.
        val discovery = FakeConnectorDiscoveryPort(
            listOf(discoveredFrom(impostorServer, impostorCert, connectorId = EXPECTED_CONNECTOR_ID)),
        )
        val resolver = DefaultAuthenticatedConnectorEndpointResolver(discovery, factory)

        val result = resolver.resolveVerifiedEndpoint(expectedWithLegitFingerprint)

        assertEquals(VerifiedEndpointResolution.Unavailable, result)
        assertEquals(0, legitServer.requestCount)
    }

    @Test
    fun `the first candidate failing verification does not stop a later matching candidate from being found`() = runTest {
        val (wrongServer, wrongCert) = newServer()
        wrongServer.enqueue(MockResponse().setResponseCode(200))
        val (rightServer, rightCert) = newServer()
        rightServer.enqueue(MockResponse().setResponseCode(200))
        val expectedWithFingerprint = expected.copy(transportFingerprint = fingerprintOf(rightCert))
        val discovery = FakeConnectorDiscoveryPort(
            listOf(
                discoveredFrom(wrongServer, wrongCert, connectorId = EXPECTED_CONNECTOR_ID),
                discoveredFrom(rightServer, rightCert, connectorId = EXPECTED_CONNECTOR_ID),
            ),
        )
        val resolver = DefaultAuthenticatedConnectorEndpointResolver(discovery, factory)

        val result = resolver.resolveVerifiedEndpoint(expectedWithFingerprint) as VerifiedEndpointResolution.Verified

        assertEquals(rightServer.hostName, result.endpoint.host)
        assertEquals(rightServer.port, result.endpoint.securePort)
    }

    @Test
    fun `verification never presents any Authorization header — the probe is unauthenticated`() = runTest {
        val (server, heldCertificate) = newServer()
        server.enqueue(MockResponse().setResponseCode(200))
        val expectedWithFingerprint = expected.copy(transportFingerprint = fingerprintOf(heldCertificate))
        val discovery = FakeConnectorDiscoveryPort(
            listOf(discoveredFrom(server, heldCertificate, connectorId = EXPECTED_CONNECTOR_ID)),
        )
        val resolver = DefaultAuthenticatedConnectorEndpointResolver(discovery, factory)

        resolver.resolveVerifiedEndpoint(expectedWithFingerprint)

        val request = server.takeRequest()
        assertNull(request.getHeader("Authorization"))
        assertEquals("/health", request.path)
    }

    @Test
    fun `discovery is invoked with a bounded, non-zero timeout`() = runTest {
        val discovery = FakeConnectorDiscoveryPort(emptyList())
        val resolver = DefaultAuthenticatedConnectorEndpointResolver(discovery, factory)

        resolver.resolveVerifiedEndpoint(expected)

        assertEquals(1, discovery.callCount)
        assertTrue((discovery.lastRequestedTimeoutMs ?: 0L) in 1..30_000L)
    }
}

private fun discoveredFrom(server: MockWebServer, heldCertificate: HeldCertificate, connectorId: String): DiscoveredConnector =
    DiscoveredConnector(
        connectorId = connectorId,
        name = "Some Advertised Name",
        host = server.hostName,
        port = server.port,
        apiVersion = "1",
        authRequired = true,
    )

/** A [TrustedConnectorEndpoint] not tied to any real server — the "already trusted" baseline a resolver call starts from. */
private object TrustedConnectorEndpointFixture {
    fun endpoint(connectorId: String) = TrustedConnectorEndpoint.fromTrustedPublicMetadata(
        connectorId = connectorId,
        connectorName = "Front Desk",
        host = "10.0.0.5",
        securePort = 8443,
        transportFingerprint = "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        fingerprintAlgorithm = "sha256",
        transportIdentityVersion = 1,
    )
}
