package com.jajusri.venture.core.connectorauth

import com.jajusri.venture.core.connectorauth.data.remote.OkHttpAuthenticatedConnectorApiClient
import com.jajusri.venture.core.connectorauth.domain.DefaultAuthenticatedConnectorContextProvider
import com.jajusri.venture.core.connectorauth.domain.DefaultAuthenticatedConnectorEndpointResolver
import com.jajusri.venture.core.connectorauth.domain.model.AuthenticatedConnectorOperation
import com.jajusri.venture.core.connectorauth.domain.model.AuthenticatedConnectorResult
import com.jajusri.venture.core.discovery.DiscoveredConnector
import com.jajusri.venture.core.discovery.FakeConnectorDiscoveryPort
import com.jajusri.venture.core.pairing.data.local.FakeSecureCredentialVault
import com.jajusri.venture.core.pairing.data.local.InMemoryVaultBackingStore
import com.jajusri.venture.core.pairing.data.remote.OkHttpPinnedHttpClientFactory
import com.jajusri.venture.core.pairing.data.remote.PinnedHttpClientFactory
import com.jajusri.venture.core.pairing.data.remote.TLS_TEST_NOW_EPOCH_MILLIS
import com.jajusri.venture.core.pairing.data.remote.endpointFor
import com.jajusri.venture.core.pairing.data.remote.startTlsMockWebServer
import com.jajusri.venture.core.pairing.data.remote.testHeldCertificate
import com.jajusri.venture.core.pairing.domain.model.SecurePairingCredentialState
import com.jajusri.venture.core.security.DefaultSpkiFingerprintVerifier
import com.jajusri.venture.core.security.FakeCredentialCipher
import com.jajusri.venture.core.util.TimeProvider
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SUPER_SECRET_TOKEN = "customer-device-bearer-token"
private const val CONNECTOR_ID = "connector-front-desk"

/**
 * TD-017 Phase 14: the exact end-to-end customer workflow this fix exists for, plus the attacker
 * variant it must never fall for — both run against real TLS (MockWebServer + real certificates),
 * a real [DefaultAuthenticatedConnectorEndpointResolver], and a real [PinnedHttpClientFactory],
 * with the trust vault's *backing store* (not the repository object) carried across a simulated
 * Android process restart, exactly like [com.jajusri.venture.core.pairing.data.local.SecureCredentialVaultTest]'s
 * own "survives a new instance" tests.
 */
class AuthenticatedEndpointRediscoveryIntegrationTest {

    private val timeProvider = TimeProvider { TLS_TEST_NOW_EPOCH_MILLIS }
    private val factory: PinnedHttpClientFactory = OkHttpPinnedHttpClientFactory(DefaultSpkiFingerprintVerifier(), timeProvider)
    private val servers = mutableListOf<MockWebServer>()

    @After
    fun tearDown() {
        servers.forEach { runCatching { it.shutdown() } }
    }

    private fun startServer(heldCertificate: HeldCertificate): MockWebServer {
        val server = startTlsMockWebServer(heldCertificate)
        servers += server
        return server
    }

    @Test
    fun `full customer workflow — pair at A, move to B via ordinary DHCP change, reconnect automatically, survive a process restart`() = runTest {
        val certificate = testHeldCertificate() // the Connector's identity never changes across the move
        val cipher = FakeCredentialCipher()
        val backingStore = InMemoryVaultBackingStore()

        // 1-3. Trusted Connector paired at endpoint A; trust ACTIVE; a business call succeeds.
        val serverA = startServer(certificate)
        serverA.enqueue(MockResponse().setResponseCode(200).setBody("""{"companies":[]}"""))
        val endpointA = endpointFor(serverA, certificate, connectorId = CONNECTOR_ID)
        run {
            val vault = FakeSecureCredentialVault(cipher, backingStore)
            vault.storePendingVerification("cred-1", "device-1", SUPER_SECRET_TOKEN, endpointA, 1_000L)
            vault.markActive("cred-1", 1_500L)
            val client = OkHttpAuthenticatedConnectorApiClient(
                DefaultAuthenticatedConnectorContextProvider(vault),
                factory,
                DefaultAuthenticatedConnectorEndpointResolver(FakeConnectorDiscoveryPort(emptyList()), factory),
                vault,
            )

            val initialCall = client.execute(AuthenticatedConnectorOperation.GetCompanies)

            assertTrue(initialCall is AuthenticatedConnectorResult.Success)
        }

        // 4. Android process reconstructed (new vault repository instance, same underlying store —
        //    exactly what actually happens across an app restart, since DataStore's backing file
        //    is the real persistence boundary, not the repository object).
        val vaultAfterRestart = FakeSecureCredentialVault(cipher, backingStore)

        // 5-6. Connector moves to endpoint B (ordinary DHCP renewal / Wi-Fi reconnect); A becomes
        //    unreachable (A's server is shut down — its port now refuses connections).
        serverA.shutdown()
        val serverB = startServer(certificate) // same identity/certificate, new host:port
        serverB.enqueue(MockResponse().setResponseCode(200)) // 7-8. verified rediscovery probe
        serverB.enqueue(MockResponse().setResponseCode(200).setBody("""{"companies":[]}""")) // 10-11. retried operation
        val discovery = FakeConnectorDiscoveryPort(
            listOf(
                DiscoveredConnector(
                    connectorId = CONNECTOR_ID,
                    name = "Front Desk",
                    host = serverB.hostName,
                    // TD-017 (real root cause): a distinct dummy plain-HTTP port, deliberately
                    // never equal to securePort below — the resolver must use securePort only.
                    port = serverB.port + 10_000,
                    apiVersion = "1",
                    authRequired = true,
                    securePort = serverB.port,
                ),
            ),
        )
        val clientAfterMove = OkHttpAuthenticatedConnectorApiClient(
            DefaultAuthenticatedConnectorContextProvider(vaultAfterRestart),
            factory,
            DefaultAuthenticatedConnectorEndpointResolver(discovery, factory),
            vaultAfterRestart,
        )

        // 7-11. mDNS discovers connectorId at B; the SAME expected fingerprint (from the trusted
        //    record, never from B's own advertisement) is verified over a real TLS handshake;
        //    vault updates to B; the original operation retries once and succeeds.
        val resultAfterMove = clientAfterMove.execute(AuthenticatedConnectorOperation.GetCompanies)

        assertTrue("expected recovery but got $resultAfterMove", resultAfterMove is AuthenticatedConnectorResult.Success)
        assertEquals(serverB.hostName, vaultAfterRestart.read()?.endpoint?.host)
        assertEquals(serverB.port, vaultAfterRestart.read()?.endpoint?.securePort)
        assertEquals(CONNECTOR_ID, vaultAfterRestart.read()?.endpoint?.connectorId)
        assertEquals(endpointA.transportFingerprint, vaultAfterRestart.read()?.endpoint?.transportFingerprint)

        // 12-14. Restart the client again; B is loaded straight from the vault; a subsequent
        //    request succeeds with no further discovery at all.
        serverB.enqueue(MockResponse().setResponseCode(200).setBody("""{"companies":[]}"""))
        val vaultAfterSecondRestart = FakeSecureCredentialVault(cipher, backingStore)
        val secondDiscovery = FakeConnectorDiscoveryPort(emptyList()) // must never be needed
        val clientAfterSecondRestart = OkHttpAuthenticatedConnectorApiClient(
            DefaultAuthenticatedConnectorContextProvider(vaultAfterSecondRestart),
            factory,
            DefaultAuthenticatedConnectorEndpointResolver(secondDiscovery, factory),
            vaultAfterSecondRestart,
        )

        val resultAfterSecondRestart = clientAfterSecondRestart.execute(AuthenticatedConnectorOperation.GetCompanies)

        assertTrue(resultAfterSecondRestart is AuthenticatedConnectorResult.Success)
        assertEquals(0, secondDiscovery.callCount)
    }

    @Test
    fun `attacker candidate — same advertised connectorId, forged certificate — is rejected and no endpoint is ever persisted`() = runTest {
        val legitimateCertificate = testHeldCertificate()
        val attackerCertificate = testHeldCertificate() // a different key pair entirely
        val cipher = FakeCredentialCipher()
        val backingStore = InMemoryVaultBackingStore()

        // A was paired against the legitimate Connector's real certificate.
        val serverA = startServer(legitimateCertificate)
        val endpointA = endpointFor(serverA, legitimateCertificate, connectorId = CONNECTOR_ID)
        serverA.shutdown() // A is now unreachable, forcing rediscovery

        // An attacker on the same LAN advertises the identical connectorId over mDNS/NSD and
        // would happily answer any request — but presents a forged certificate.
        val attackerServer = startServer(attackerCertificate)
        attackerServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"companies":["attacker-controlled-data"]}"""))
        val discovery = FakeConnectorDiscoveryPort(
            listOf(
                DiscoveredConnector(
                    connectorId = CONNECTOR_ID, // forged/reused identity claim
                    name = "Front Desk", // forged/reused friendly name
                    host = attackerServer.hostName,
                    port = attackerServer.port + 10_000,
                    apiVersion = "1",
                    authRequired = true,
                    securePort = attackerServer.port,
                ),
            ),
        )

        val vault = FakeSecureCredentialVault(cipher, backingStore)
        vault.storePendingVerification("cred-1", "device-1", SUPER_SECRET_TOKEN, endpointA, 1_000L)
        vault.markActive("cred-1", 1_500L)
        val client = OkHttpAuthenticatedConnectorApiClient(
            DefaultAuthenticatedConnectorContextProvider(vault),
            factory,
            DefaultAuthenticatedConnectorEndpointResolver(discovery, factory),
            vault,
        )

        val result = client.execute(AuthenticatedConnectorOperation.GetCompanies)

        // Rejected — never Success, never any hint of the attacker's response payload.
        assertTrue(result !is AuthenticatedConnectorResult.Success)
        assertEquals(AuthenticatedConnectorResult.IdentityMismatch, result)
        // No persisted update: the vault still points at the original (now-dead) endpoint A,
        // never the attacker's host/port, and the credential/trust state is completely intact.
        assertEquals(endpointA.host, vault.read()?.endpoint?.host)
        assertEquals(endpointA.securePort, vault.read()?.endpoint?.securePort)
        assertEquals(endpointA.transportFingerprint, vault.read()?.endpoint?.transportFingerprint)
        assertEquals("cred-1", vault.read()?.credentialId)
        assertEquals(SecurePairingCredentialState.ACTIVE, vault.read()?.state)
    }
}
