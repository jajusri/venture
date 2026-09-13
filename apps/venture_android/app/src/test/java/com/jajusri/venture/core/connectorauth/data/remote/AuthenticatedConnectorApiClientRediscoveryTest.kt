package com.jajusri.venture.core.connectorauth.data.remote

import com.jajusri.venture.core.connectorauth.domain.DefaultAuthenticatedConnectorContextProvider
import com.jajusri.venture.core.connectorauth.domain.DefaultAuthenticatedConnectorEndpointResolver
import com.jajusri.venture.core.connectorauth.domain.FakeAuthenticatedConnectorEndpointResolver
import com.jajusri.venture.core.connectorauth.domain.VerifiedEndpointResolution
import com.jajusri.venture.core.connectorauth.domain.model.AuthenticatedConnectorOperation
import com.jajusri.venture.core.connectorauth.domain.model.AuthenticatedConnectorResult
import com.jajusri.venture.core.discovery.DiscoveredConnector
import com.jajusri.venture.core.discovery.FakeConnectorDiscoveryPort
import com.jajusri.venture.core.pairing.data.local.FakeSecureCredentialVault
import com.jajusri.venture.core.pairing.data.local.SecureCredentialVault
import com.jajusri.venture.core.pairing.data.remote.OkHttpPinnedHttpClientFactory
import com.jajusri.venture.core.pairing.data.remote.PinnedHttpClientFactory
import com.jajusri.venture.core.pairing.data.remote.TLS_TEST_NOW_EPOCH_MILLIS
import com.jajusri.venture.core.pairing.data.remote.endpointFor
import com.jajusri.venture.core.pairing.data.remote.startTlsMockWebServer
import com.jajusri.venture.core.pairing.data.remote.testHeldCertificate
import com.jajusri.venture.core.pairing.domain.model.SecurePairingCredentialState
import com.jajusri.venture.core.pairing.domain.model.TrustedConnectorEndpoint
import com.jajusri.venture.core.security.DefaultSpkiFingerprintVerifier
import com.jajusri.venture.core.util.TimeProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SUPER_SECRET_TOKEN = "super-secret-bearer-value-xyz"
private const val CONNECTOR_ID = "connector-abc"

/**
 * TD-017 end-to-end coverage: [OkHttpAuthenticatedConnectorApiClient.execute]'s bounded
 * rediscover-verify-persist-retry-once cycle, wired against a real [PinnedHttpClientFactory] and
 * a real [DefaultAuthenticatedConnectorEndpointResolver] (never mocked away) so the cryptographic
 * fingerprint check is genuinely exercised, not merely assumed.
 */
class AuthenticatedConnectorApiClientRediscoveryTest {

    private val timeProvider = TimeProvider { TLS_TEST_NOW_EPOCH_MILLIS }
    private val factory: PinnedHttpClientFactory = OkHttpPinnedHttpClientFactory(DefaultSpkiFingerprintVerifier(), timeProvider)
    private val servers = mutableListOf<MockWebServer>()

    @After
    fun tearDown() {
        servers.forEach { runCatching { it.shutdown() } }
    }

    private fun newServer(): Pair<MockWebServer, HeldCertificate> {
        val heldCertificate = testHeldCertificate()
        val server = startTlsMockWebServer(heldCertificate)
        servers += server
        return server to heldCertificate
    }

    /** Starts a server, captures its endpoint, then shuts it down — its port now refuses connections. */
    private fun deadEndpoint(heldCertificate: HeldCertificate): TrustedConnectorEndpoint {
        val server = startTlsMockWebServer(heldCertificate)
        val endpoint = endpointFor(server, heldCertificate, connectorId = CONNECTOR_ID)
        server.shutdown()
        return endpoint
    }

    private fun clientWithRealResolver(
        vault: SecureCredentialVault,
        discovery: FakeConnectorDiscoveryPort,
    ): OkHttpAuthenticatedConnectorApiClient = OkHttpAuthenticatedConnectorApiClient(
        DefaultAuthenticatedConnectorContextProvider(vault),
        factory,
        DefaultAuthenticatedConnectorEndpointResolver(discovery, factory),
        vault,
    )

    // ============================== Healthy existing endpoint (1-4) ==============================

    @Test
    fun `a reachable persisted endpoint never triggers discovery and leaves the vault unchanged`() = runTest {
        val (server, heldCertificate) = newServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val endpoint = endpointFor(server, heldCertificate, connectorId = CONNECTOR_ID)
        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", SUPER_SECRET_TOKEN, endpoint, 1_000L)
        vault.markActive("cred-1", 2_000L)
        val discovery = FakeConnectorDiscoveryPort(emptyList())
        val client = clientWithRealResolver(vault, discovery)

        val result = client.execute(AuthenticatedConnectorOperation.GetCompanies)

        assertTrue(result is AuthenticatedConnectorResult.Success)
        assertEquals(0, discovery.callCount)
        assertEquals(endpoint.host, vault.read()?.endpoint?.host)
        assertEquals(endpoint.securePort, vault.read()?.endpoint?.securePort)
        assertEquals(1, server.requestCount)
    }

    // ============================== Stale endpoint / IP change (5-19) ==============================

    @Test
    fun `a stale persisted endpoint recovers via verified rediscovery, updates the vault, and retries once successfully`() = runTest {
        val (newServer, heldCertificate) = newServer()
        newServer.enqueue(MockResponse().setResponseCode(200)) // verification probe (/health)
        newServer.enqueue(MockResponse().setResponseCode(200).setBody("{}")) // retried operation
        val staleEndpoint = deadEndpoint(heldCertificate) // endpoint A: same connectorId/fingerprint, dead host:port
        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", SUPER_SECRET_TOKEN, staleEndpoint, 1_000L)
        vault.markActive("cred-1", 2_000L)
        val discovery = FakeConnectorDiscoveryPort(
            listOf(discoveredFrom(newServer, connectorId = CONNECTOR_ID)),
        )
        val client = clientWithRealResolver(vault, discovery)

        val result = client.execute(AuthenticatedConnectorOperation.GetCompanies)

        assertTrue("expected Success but got $result", result is AuthenticatedConnectorResult.Success)
        assertEquals(1, discovery.callCount)
        assertEquals(newServer.hostName, vault.read()?.endpoint?.host)
        assertEquals(newServer.port, vault.read()?.endpoint?.securePort)
        // connectorId/fingerprint/credential are all unchanged by the endpoint move.
        assertEquals(CONNECTOR_ID, vault.read()?.endpoint?.connectorId)
        assertEquals("cred-1", vault.read()?.credentialId)
    }

    @Test
    fun `the retried request after rediscovery presents the same bearer credential and never re-pairs`() = runTest {
        val (newServer, heldCertificate) = newServer()
        newServer.enqueue(MockResponse().setResponseCode(200))
        newServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val staleEndpoint = deadEndpoint(heldCertificate)
        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", SUPER_SECRET_TOKEN, staleEndpoint, 1_000L)
        vault.markActive("cred-1", 2_000L)
        val discovery = FakeConnectorDiscoveryPort(listOf(discoveredFrom(newServer, connectorId = CONNECTOR_ID)))
        val client = clientWithRealResolver(vault, discovery)

        client.execute(AuthenticatedConnectorOperation.GetCompanies)

        // newServer received two requests: the unauthenticated verification probe, then the
        // real, bearer-authenticated retried operation.
        assertEquals(2, newServer.requestCount)
        val verificationRequest = newServer.takeRequest()
        assertNull(verificationRequest.getHeader("Authorization"))
        val retriedRequest = newServer.takeRequest()
        assertEquals("Bearer $SUPER_SECRET_TOKEN", retriedRequest.getHeader("Authorization"))
        assertEquals(SecurePairingCredentialState.ACTIVE, vault.read()?.state)
    }

    @Test
    fun `a stale endpoint with no reachable candidate stays Unavailable and never overwrites the vault with garbage`() = runTest {
        val heldCertificate = testHeldCertificate()
        val staleEndpoint = deadEndpoint(heldCertificate)
        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", SUPER_SECRET_TOKEN, staleEndpoint, 1_000L)
        vault.markActive("cred-1", 2_000L)
        val discovery = FakeConnectorDiscoveryPort(emptyList()) // nothing found
        val client = clientWithRealResolver(vault, discovery)

        val result = client.execute(AuthenticatedConnectorOperation.GetCompanies)

        assertEquals(AuthenticatedConnectorResult.TransportFailure, result)
        assertEquals(staleEndpoint.host, vault.read()?.endpoint?.host)
        assertEquals(staleEndpoint.securePort, vault.read()?.endpoint?.securePort)
    }

    @Test
    fun `endpoint A moving to endpoint B is fully reflected — subsequent calls use B directly with no further discovery`() = runTest {
        val (serverB, heldCertificate) = newServer()
        serverB.enqueue(MockResponse().setResponseCode(200)) // verification for the first call's rediscovery
        serverB.enqueue(MockResponse().setResponseCode(200).setBody("{}")) // first call's retry
        serverB.enqueue(MockResponse().setResponseCode(200).setBody("{}")) // second call, direct hit on B
        val endpointA = deadEndpoint(heldCertificate)
        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", SUPER_SECRET_TOKEN, endpointA, 1_000L)
        vault.markActive("cred-1", 2_000L)
        val discovery = FakeConnectorDiscoveryPort(listOf(discoveredFrom(serverB, connectorId = CONNECTOR_ID)))
        val client = clientWithRealResolver(vault, discovery)

        val first = client.execute(AuthenticatedConnectorOperation.GetCompanies)
        assertTrue(first is AuthenticatedConnectorResult.Success)
        assertEquals(1, discovery.callCount)

        val second = client.execute(AuthenticatedConnectorOperation.GetSession)

        assertTrue(second is AuthenticatedConnectorResult.Success)
        assertEquals(1, discovery.callCount) // still 1 — no rediscovery needed once B is persisted and healthy
    }

    // ============================== Fingerprint mismatch (20-26) ==============================

    @Test
    fun `a candidate advertising the expected connectorId but a mismatched fingerprint is rejected end to end`() = runTest {
        val (impostorServer, _) = newServer()
        impostorServer.enqueue(MockResponse().setResponseCode(200).setBody("attacker response"))
        val staleEndpoint = deadEndpoint(testHeldCertificate()) // pins the LEGITIMATE fingerprint, not the impostor's
        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", SUPER_SECRET_TOKEN, staleEndpoint, 1_000L)
        vault.markActive("cred-1", 2_000L)
        val discovery = FakeConnectorDiscoveryPort(listOf(discoveredFrom(impostorServer, connectorId = CONNECTOR_ID)))
        val client = clientWithRealResolver(vault, discovery)

        val result = client.execute(AuthenticatedConnectorOperation.GetCompanies)

        assertEquals(AuthenticatedConnectorResult.IdentityMismatch, result)
        // Neither the endpoint nor the credential was touched by the rejected impostor.
        assertEquals(staleEndpoint.host, vault.read()?.endpoint?.host)
        assertEquals("cred-1", vault.read()?.credentialId)
        assertEquals(SecurePairingCredentialState.ACTIVE, vault.read()?.state)
    }

    @Test
    fun `a fingerprint mismatch never falls back to an unauthenticated request against the impostor`() = runTest {
        val (impostorServer, _) = newServer()
        // If the client ever fell back to trusting the impostor unauthenticated, this response
        // would be read as Success — proving it never is read at all is the point of this test.
        impostorServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
        val staleEndpoint = deadEndpoint(testHeldCertificate())
        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", SUPER_SECRET_TOKEN, staleEndpoint, 1_000L)
        vault.markActive("cred-1", 2_000L)
        val discovery = FakeConnectorDiscoveryPort(listOf(discoveredFrom(impostorServer, connectorId = CONNECTOR_ID)))
        val client = clientWithRealResolver(vault, discovery)

        val result = client.execute(AuthenticatedConnectorOperation.GetCompanies)

        assertTrue(result !is AuthenticatedConnectorResult.Success)
    }

    // ============================== Wrong connector ID (27-29) ==============================

    @Test
    fun `a candidate with the wrong connectorId is ignored and never mutates trust`() = runTest {
        val (server, heldCertificate) = newServer()
        server.enqueue(MockResponse().setResponseCode(200))
        val staleEndpoint = deadEndpoint(testHeldCertificate())
        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", SUPER_SECRET_TOKEN, staleEndpoint, 1_000L)
        vault.markActive("cred-1", 2_000L)
        val discovery = FakeConnectorDiscoveryPort(listOf(discoveredFrom(server, connectorId = "some-other-connector")))
        val client = clientWithRealResolver(vault, discovery)

        val result = client.execute(AuthenticatedConnectorOperation.GetCompanies)

        assertEquals(AuthenticatedConnectorResult.TransportFailure, result)
        assertEquals(0, server.requestCount) // never even dialed — filtered before any connection attempt
        assertEquals(staleEndpoint.host, vault.read()?.endpoint?.host)
    }

    // ============================== Discovery timeout / no candidate (30-33) ==============================

    @Test
    fun `no discovered candidates at all leaves existing trust and endpoint completely untouched`() = runTest {
        val staleEndpoint = deadEndpoint(testHeldCertificate())
        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", SUPER_SECRET_TOKEN, staleEndpoint, 1_000L)
        vault.markActive("cred-1", 2_000L)
        val discovery = FakeConnectorDiscoveryPort(emptyList())
        val client = clientWithRealResolver(vault, discovery)

        val result = client.execute(AuthenticatedConnectorOperation.GetCompanies)

        assertEquals(AuthenticatedConnectorResult.TransportFailure, result)
        assertEquals("cred-1", vault.read()?.credentialId)
        assertEquals(staleEndpoint.host, vault.read()?.endpoint?.host)
        assertEquals(staleEndpoint.securePort, vault.read()?.endpoint?.securePort)
    }

    // ============================== Security: rediscovery never fires for non-transport failures (45-50) ==============================

    @Test
    fun `a 400 response never triggers rediscovery`() = runTest {
        val (server, heldCertificate) = newServer()
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"code":"INVALID_COMPANY"}"""))
        val vault = readyVault(server, heldCertificate)
        val resolver = FakeAuthenticatedConnectorEndpointResolver()
        val client = OkHttpAuthenticatedConnectorApiClient(DefaultAuthenticatedConnectorContextProvider(vault), factory, resolver, vault)

        client.execute(AuthenticatedConnectorOperation.GetCompanies)

        assertEquals(0, resolver.callCount)
    }

    @Test
    fun `a 401 does not trigger endpoint rediscovery as trust repair`() = runTest {
        val (server, heldCertificate) = newServer()
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"code":"UNAUTHORIZED"}"""))
        val vault = readyVault(server, heldCertificate)
        val resolver = FakeAuthenticatedConnectorEndpointResolver()
        val client = OkHttpAuthenticatedConnectorApiClient(DefaultAuthenticatedConnectorContextProvider(vault), factory, resolver, vault)

        client.execute(AuthenticatedConnectorOperation.GetCompanies)

        assertEquals(0, resolver.callCount)
    }

    @Test
    fun `a 403 does not trigger endpoint rediscovery`() = runTest {
        val (server, heldCertificate) = newServer()
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"code":"FORBIDDEN"}"""))
        val vault = readyVault(server, heldCertificate)
        val resolver = FakeAuthenticatedConnectorEndpointResolver()
        val client = OkHttpAuthenticatedConnectorApiClient(DefaultAuthenticatedConnectorContextProvider(vault), factory, resolver, vault)

        client.execute(AuthenticatedConnectorOperation.GetCompanies)

        assertEquals(0, resolver.callCount)
    }

    @Test
    fun `NO_COMPANY_SELECTED (TD-013) remains its own recovery path, not endpoint discovery`() = runTest {
        val (server, heldCertificate) = newServer()
        server.enqueue(
            MockResponse().setResponseCode(400).setBody(
                """{"status":"NO_COMPANY_SELECTED","session":{},"reason":"No company is selected"}""",
            ),
        )
        val vault = readyVault(server, heldCertificate)
        val resolver = FakeAuthenticatedConnectorEndpointResolver()
        val client = OkHttpAuthenticatedConnectorApiClient(DefaultAuthenticatedConnectorContextProvider(vault), factory, resolver, vault)

        val result = client.execute(AuthenticatedConnectorOperation.ValidateSession) as AuthenticatedConnectorResult.ValidationFailure

        assertTrue(result.isNoCompanySelected)
        assertEquals(0, resolver.callCount)
    }

    @Test
    fun `a verified endpoint is never accepted merely because mDNS TXT data alone claims a match`() = runTest {
        // The resolver-level tests already prove a wrong-fingerprint candidate is rejected; this
        // test asserts the end-to-end contract from the API client's point of view: the fake
        // resolver returning Verified is the ONLY way a discovered candidate ever reaches the
        // vault — there is no other write path from discovery straight into trust.
        val staleEndpoint = deadEndpoint(testHeldCertificate())
        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", SUPER_SECRET_TOKEN, staleEndpoint, 1_000L)
        vault.markActive("cred-1", 2_000L)
        val resolver = FakeAuthenticatedConnectorEndpointResolver(VerifiedEndpointResolution.Unavailable)
        val client = OkHttpAuthenticatedConnectorApiClient(DefaultAuthenticatedConnectorContextProvider(vault), factory, resolver, vault)

        client.execute(AuthenticatedConnectorOperation.GetCompanies)

        assertEquals(1, resolver.callCount)
        assertEquals(staleEndpoint.host, vault.read()?.endpoint?.host) // never mutated without Verified
    }

    // ============================== Concurrent recovery (34-37) ==============================

    @Test
    fun `two operations failing concurrently against the same stale endpoint both recover, and the vault ends up consistent`() = runTest {
        val (newServer, heldCertificate) = newServer()
        repeat(4) { newServer.enqueue(MockResponse().setResponseCode(200).setBody("{}")) }
        val staleEndpoint = deadEndpoint(heldCertificate)
        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", SUPER_SECRET_TOKEN, staleEndpoint, 1_000L)
        vault.markActive("cred-1", 2_000L)
        val discovery = FakeConnectorDiscoveryPort(listOf(discoveredFrom(newServer, connectorId = CONNECTOR_ID)))
        val client = clientWithRealResolver(vault, discovery)

        val results = listOf(
            async { client.execute(AuthenticatedConnectorOperation.GetCompanies) },
            async { client.execute(AuthenticatedConnectorOperation.GetSession) },
        ).awaitAll()

        assertTrue(results.all { it is AuthenticatedConnectorResult.Success })
        assertEquals(newServer.hostName, vault.read()?.endpoint?.host)
        assertEquals(newServer.port, vault.read()?.endpoint?.securePort)
    }

    private suspend fun readyVault(server: MockWebServer, heldCertificate: HeldCertificate): FakeSecureCredentialVault {
        val vault = FakeSecureCredentialVault()
        val endpoint = endpointFor(server, heldCertificate, connectorId = CONNECTOR_ID)
        vault.storePendingVerification("cred-1", "device-1", SUPER_SECRET_TOKEN, endpoint, 1_000L)
        vault.markActive("cred-1", 2_000L)
        return vault
    }
}

private fun discoveredFrom(server: MockWebServer, connectorId: String): DiscoveredConnector = DiscoveredConnector(
    connectorId = connectorId,
    name = "Front Desk",
    host = server.hostName,
    // TD-017 (real root cause): distinct dummy plain-HTTP port; the resolver must use securePort.
    port = server.port + 10_000,
    apiVersion = "1",
    authRequired = true,
    securePort = server.port,
)
