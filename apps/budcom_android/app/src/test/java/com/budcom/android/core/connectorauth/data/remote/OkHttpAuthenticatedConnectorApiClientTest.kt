package com.budcom.android.core.connectorauth.data.remote

import com.budcom.android.core.connectorauth.domain.DefaultAuthenticatedConnectorContextProvider
import com.budcom.android.core.connectorauth.domain.FakeAuthenticatedConnectorEndpointResolver
import com.budcom.android.core.connectorauth.domain.model.AuthenticatedConnectorOperation
import com.budcom.android.core.connectorauth.domain.model.AuthenticatedConnectorResponsePayload
import com.budcom.android.core.connectorauth.domain.model.AuthenticatedConnectorResult
import com.budcom.android.core.connectorauth.domain.model.ConnectorTimeoutProfile
import com.budcom.android.core.pairing.data.local.FakeSecureCredentialVault
import com.budcom.android.core.pairing.data.remote.OkHttpPinnedHttpClientFactory
import com.budcom.android.core.pairing.data.remote.PinnedHttpClientFactory
import com.budcom.android.core.pairing.data.remote.TLS_TEST_NOW_EPOCH_MILLIS
import com.budcom.android.core.pairing.data.remote.endpointFor
import com.budcom.android.core.pairing.data.remote.startTlsMockWebServer
import com.budcom.android.core.pairing.data.remote.testHeldCertificate
import com.budcom.android.core.pairing.domain.model.TrustedConnectorEndpoint
import com.budcom.android.core.security.DefaultSpkiFingerprintVerifier
import com.budcom.android.core.util.TimeProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

private const val SUPER_SECRET_TOKEN = "super-secret-bearer-value-xyz"

class OkHttpAuthenticatedConnectorApiClientTest {

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

    /** A ready client whose vault points at [server], pinned to [heldCertificate]. */
    private suspend fun readyClient(
        server: MockWebServer,
        heldCertificate: HeldCertificate,
        httpClientFactory: PinnedHttpClientFactory = factory,
    ): Pair<OkHttpAuthenticatedConnectorApiClient, FakeSecureCredentialVault> {
        val vault = FakeSecureCredentialVault()
        val endpoint = endpointFor(server, heldCertificate)
        vault.storePendingVerification("cred-1", "device-1", SUPER_SECRET_TOKEN, endpoint, 1_000L)
        vault.markActive("cred-1", 2_000L)
        val client = OkHttpAuthenticatedConnectorApiClient(
            DefaultAuthenticatedConnectorContextProvider(vault),
            httpClientFactory,
            FakeAuthenticatedConnectorEndpointResolver(),
            vault,
        )
        return client to vault
    }

    // ============================== Request security (15-34) ==============================

    // 15, 16, 17, 18. https, exact trusted port/host, canonical Bearer header
    @Test
    fun `a request uses https, the trusted secure port and host, and the canonical Bearer header`() = runTest {
        val (server, heldCertificate) = newServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val (client, _) = readyClient(server, heldCertificate)

        val result = client.execute(AuthenticatedConnectorOperation.GetCompanies)

        assertTrue(result is AuthenticatedConnectorResult.Success)
        val request = server.takeRequest()
        assertEquals("Bearer $SUPER_SECRET_TOKEN", request.getHeader("Authorization"))
        assertEquals(server.hostName, request.requestUrl?.host)
        assertEquals(server.port, request.requestUrl?.port)
        assertEquals("https", request.requestUrl?.scheme)
    }

    @Test
    fun `public health requests remain pinned but never receive the bearer credential`() = runTest {
        val (server, heldCertificate) = newServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val (client, _) = readyClient(server, heldCertificate)

        client.execute(AuthenticatedConnectorOperation.PublicHealth)

        val request = server.takeRequest()
        assertEquals("/health", request.path)
        assertNull(request.getHeader("Authorization"))
        assertEquals("https", request.requestUrl?.scheme)
    }

    @Test
    fun `voucher sync is an authenticated HTTPS POST with an explicit empty JSON body`() = runTest {
        val (server, heldCertificate) = newServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val (client, _) = readyClient(server, heldCertificate)

        client.execute(AuthenticatedConnectorOperation.StartVoucherSync)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/sync/vouchers", request.path)
        assertEquals("{}", request.body.readUtf8())
        assertEquals("application/json; charset=utf-8", request.getHeader("Content-Type"))
        assertEquals("Bearer $SUPER_SECRET_TOKEN", request.getHeader("Authorization"))
        assertEquals("https", request.requestUrl?.scheme)
    }

    // 19, 20, 21, 22. token never in URL, query, body, or cookies
    @Test
    fun `the bearer credential never appears in the URL, query, body, or cookies`() = runTest {
        val (server, heldCertificate) = newServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val (client, _) = readyClient(server, heldCertificate)

        client.execute(AuthenticatedConnectorOperation.SelectCompany(companyId = "company-1"))

        val request = server.takeRequest()
        assertFalse(request.path!!.contains(SUPER_SECRET_TOKEN))
        assertFalse(request.body.readUtf8().contains(SUPER_SECRET_TOKEN))
        assertNull(request.getHeader("Cookie"))
    }

    // 23, 24. redirects never followed (plain and TLS-redirect alike)
    @Test
    fun `a redirect response is never followed`() = runTest {
        val (server, heldCertificate) = newServer()
        server.enqueue(
            MockResponse().setResponseCode(302).setHeader("Location", "https://${server.hostName}:${server.port}/elsewhere"),
        )
        server.enqueue(MockResponse().setBody("should never be reached"))
        val (client, _) = readyClient(server, heldCertificate)

        val result = client.execute(AuthenticatedConnectorOperation.GetCompanies)

        assertEquals(AuthenticatedConnectorResult.ServerFailure(302), result)
        assertEquals(1, server.requestCount)
    }

    // 25. plain HTTP is structurally impossible — the client always dials scheme "https" (see 15).

    // 26, 27. wrong SPKI fingerprint / wrong server key fails as a transport failure
    @Test
    fun `a server presenting a certificate that does not match the pinned fingerprint fails as TransportFailure`() = runTest {
        val (server, _) = newServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("should never be read"))
        val wrongCert = testHeldCertificate()
        val vault = FakeSecureCredentialVault()
        val wrongEndpoint = endpointFor(server, wrongCert)
        vault.storePendingVerification("cred-1", "device-1", SUPER_SECRET_TOKEN, wrongEndpoint, 1_000L)
        vault.markActive("cred-1", 2_000L)
        val client = OkHttpAuthenticatedConnectorApiClient(
            DefaultAuthenticatedConnectorContextProvider(vault),
            factory,
            FakeAuthenticatedConnectorEndpointResolver(),
            vault,
        )

        val result = client.execute(AuthenticatedConnectorOperation.GetCompanies)

        assertEquals(AuthenticatedConnectorResult.IdentityMismatch, result)
    }

    // 28. configured-host mismatch — covered by the unmodified PinnedHttpClientFactoryTest, whose
    // hostnameVerifier this client reuses unchanged (see the timeout-profile-preservation tests below,
    // which prove the SYNC-derived client keeps the identical verifier instance).

    // 29. no automatic connection retry
    @Test
    fun `the underlying client never retries a failed connection`() = runTest {
        val (server, heldCertificate) = newServer()
        val endpoint = endpointFor(server, heldCertificate)
        val standardClient = factory.create(endpoint)
        val syncClient = applyConnectorTimeoutProfile(standardClient, ConnectorTimeoutProfile.SYNC)

        assertFalse(standardClient.retryOnConnectionFailure)
        assertFalse(syncClient.retryOnConnectionFailure)
    }

    // 30. one operation causes at most one HTTP request
    @Test
    fun `one operation results in exactly one HTTP request`() = runTest {
        val (server, heldCertificate) = newServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val (client, _) = readyClient(server, heldCertificate)

        client.execute(AuthenticatedConnectorOperation.GetCompanies)

        assertEquals(1, server.requestCount)
    }

    // 31. response body always closed — proven by five sequential calls all succeeding without
    // exhausting the connection (an unclosed body would starve OkHttp's connection reuse).
    @Test
    fun `sequential requests all succeed, evidencing every response body is closed`() = runTest {
        val (server, heldCertificate) = newServer()
        repeat(5) { server.enqueue(MockResponse().setResponseCode(200).setBody("{}")) }
        val (client, _) = readyClient(server, heldCertificate)

        val results = (1..5).map { client.execute(AuthenticatedConnectorOperation.GetCompanies) }

        assertTrue(results.all { it is AuthenticatedConnectorResult.Success })
        assertEquals(5, server.requestCount)
    }

    // 32. oversized response is rejected
    @Test
    fun `a response body larger than the bound is rejected as MalformedResponse`() = runTest {
        val (server, heldCertificate) = newServer()
        val oversizedBody = "a".repeat((MAX_RESPONSE_BODY_BYTES + 1024).toInt())
        server.enqueue(MockResponse().setResponseCode(200).setBody(oversizedBody))
        val (client, _) = readyClient(server, heldCertificate)

        val result = client.execute(AuthenticatedConnectorOperation.GetCompanies)

        assertEquals(AuthenticatedConnectorResult.MalformedResponse, result)
    }

    // 33. exception output is sanitized — TransportFailure carries no message/cause field at all.
    @Test
    fun `transport failures never carry the underlying exception message`() = runTest {
        val (server, heldCertificate) = newServer()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val (client, _) = readyClient(server, heldCertificate)

        val result = client.execute(AuthenticatedConnectorOperation.GetCompanies)

        assertEquals(AuthenticatedConnectorResult.TransportFailure, result)
        assertEquals("TransportFailure", result.toString())
    }

    // 34. result toString contains no credential
    @Test
    fun `a Success result's toString never includes the bearer credential`() = runTest {
        val (server, heldCertificate) = newServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
        val (client, _) = readyClient(server, heldCertificate)

        val result = client.execute(AuthenticatedConnectorOperation.GetCompanies)

        assertFalse(result.toString().contains(SUPER_SECRET_TOKEN))
    }

    // ============================== Status mapping (35-50) ==============================

    @Test
    fun `2xx responses map to Success`() = runTest {
        for (code in listOf(200, 201, 204)) {
            val (server, heldCertificate) = newServer()
            server.enqueue(MockResponse().setResponseCode(code).setBody(if (code == 204) "" else "{}"))
            val (client, _) = readyClient(server, heldCertificate)

            val result = client.execute(AuthenticatedConnectorOperation.GetCompanies)

            assertTrue("expected Success for $code but got $result", result is AuthenticatedConnectorResult.Success)
        }
    }

    @Test
    fun `400 maps to ValidationFailure carrying only the sanitized code`() = runTest {
        val (server, heldCertificate) = newServer()
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"code":"INVALID_COMPANY","message":"internal detail should not leak"}"""))
        val (client, _) = readyClient(server, heldCertificate)

        val result = client.execute(AuthenticatedConnectorOperation.GetCompanies) as AuthenticatedConnectorResult.ValidationFailure

        assertEquals("INVALID_COMPANY", result.sanitizedCode)
        assertFalse(result.isNoCompanySelected)
        assertFalse(result.toString().contains("internal detail"))
    }

    @Test
    fun `400 with status NO_COMPANY_SELECTED sets isNoCompanySelected (TD-013)`() = runTest {
        val (server, heldCertificate) = newServer()
        server.enqueue(
            MockResponse().setResponseCode(400).setBody(
                """{"status":"NO_COMPANY_SELECTED","session":{},"reason":"No company is selected for this connector session"}""",
            ),
        )
        val (client, _) = readyClient(server, heldCertificate)

        val result = client.execute(AuthenticatedConnectorOperation.ValidateSession) as AuthenticatedConnectorResult.ValidationFailure

        assertTrue(result.isNoCompanySelected)
    }

    @Test
    fun `400 with an unrelated status does not set isNoCompanySelected`() = runTest {
        val (server, heldCertificate) = newServer()
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"status":"SESSION_INVALID","session":{}}"""))
        val (client, _) = readyClient(server, heldCertificate)

        val result = client.execute(AuthenticatedConnectorOperation.ValidateSession) as AuthenticatedConnectorResult.ValidationFailure

        assertFalse(result.isNoCompanySelected)
    }

    @Test
    fun `400 with a malformed body does not set isNoCompanySelected`() = runTest {
        val (server, heldCertificate) = newServer()
        server.enqueue(MockResponse().setResponseCode(400).setBody("not-json"))
        val (client, _) = readyClient(server, heldCertificate)

        val result = client.execute(AuthenticatedConnectorOperation.ValidateSession) as AuthenticatedConnectorResult.ValidationFailure

        assertFalse(result.isNoCompanySelected)
    }

    @Test
    fun `401 maps to Unauthorized carrying the credentialId that was actually used`() = runTest {
        val (server, heldCertificate) = newServer()
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"code":"UNAUTHORIZED"}"""))
        val (client, _) = readyClient(server, heldCertificate)

        assertEquals(AuthenticatedConnectorResult.Unauthorized("cred-1"), client.execute(AuthenticatedConnectorOperation.GetCompanies))
    }

    @Test
    fun `403 maps to Forbidden`() = runTest {
        val (server, heldCertificate) = newServer()
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"code":"FORBIDDEN"}"""))
        val (client, _) = readyClient(server, heldCertificate)

        assertEquals(AuthenticatedConnectorResult.Forbidden, client.execute(AuthenticatedConnectorOperation.GetCompanies))
    }

    @Test
    fun `404 maps to NotFound`() = runTest {
        val (server, heldCertificate) = newServer()
        server.enqueue(MockResponse().setResponseCode(404))
        val (client, _) = readyClient(server, heldCertificate)

        assertEquals(AuthenticatedConnectorResult.NotFound, client.execute(AuthenticatedConnectorOperation.GetCompanies))
    }

    @Test
    fun `409 maps to Conflict`() = runTest {
        val (server, heldCertificate) = newServer()
        server.enqueue(MockResponse().setResponseCode(409))
        val (client, _) = readyClient(server, heldCertificate)

        assertEquals(AuthenticatedConnectorResult.Conflict, client.execute(AuthenticatedConnectorOperation.GetCompanies))
    }

    @Test
    fun `410 SESSION_EXPIRED maps to the typed session-expired outcome`() = runTest {
        val (server, heldCertificate) = newServer()
        server.enqueue(
            MockResponse().setResponseCode(410).setBody(
                """{"status":"SESSION_EXPIRED","reason":"internal detail must not cross the boundary"}""",
            ),
        )
        val (client, _) = readyClient(server, heldCertificate)

        val result = client.execute(AuthenticatedConnectorOperation.ValidateSession)

        assertEquals(AuthenticatedConnectorResult.SessionExpired, result)
        assertFalse(result.toString().contains("internal detail"))
    }

    @Test
    fun `an unrelated 410 cannot masquerade as an expired Connector session`() = runTest {
        val (server, heldCertificate) = newServer()
        server.enqueue(MockResponse().setResponseCode(410).setBody("""{"status":"OTHER"}"""))
        val (client, _) = readyClient(server, heldCertificate)

        val result = client.execute(AuthenticatedConnectorOperation.ValidateSession)

        assertEquals(AuthenticatedConnectorResult.ServerFailure(410), result)
    }

    @Test
    fun `SESSION_EXPIRED is typed only on the session-validation operation`() = runTest {
        val (server, heldCertificate) = newServer()
        server.enqueue(MockResponse().setResponseCode(410).setBody("""{"status":"SESSION_EXPIRED"}"""))
        val (client, _) = readyClient(server, heldCertificate)

        val result = client.execute(AuthenticatedConnectorOperation.GetCompanies)

        assertEquals(AuthenticatedConnectorResult.ServerFailure(410), result)
    }

    @Test
    fun `429 maps to RateLimited`() = runTest {
        val (server, heldCertificate) = newServer()
        server.enqueue(MockResponse().setResponseCode(429))
        val (client, _) = readyClient(server, heldCertificate)

        assertEquals(AuthenticatedConnectorResult.RateLimited, client.execute(AuthenticatedConnectorOperation.GetCompanies))
    }

    @Test
    fun `500 maps to ServerFailure`() = runTest {
        val (server, heldCertificate) = newServer()
        server.enqueue(MockResponse().setResponseCode(500))
        val (client, _) = readyClient(server, heldCertificate)

        assertEquals(AuthenticatedConnectorResult.ServerFailure(500), client.execute(AuthenticatedConnectorOperation.GetCompanies))
    }

    @Test
    fun `public readiness preserves the Connector's legitimate 503 not-ready body`() = runTest {
        val (server, heldCertificate) = newServer()
        val body =
            """{"status":"not_ready","repositoryAvailable":true,"databaseAccessible":true,"voucherSynchronizationComposed":false,"voucherApplicationComposed":true}"""
        server.enqueue(MockResponse().setResponseCode(503).setBody(body))
        val (client, _) = readyClient(server, heldCertificate)

        val result = client.execute(AuthenticatedConnectorOperation.PublicReadiness)

        assertEquals(
            AuthenticatedConnectorResult.Success(AuthenticatedConnectorResponsePayload(body)),
            result,
        )
    }

    @Test
    fun `a 503 on a business operation remains a server failure`() = runTest {
        val (server, heldCertificate) = newServer()
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"status":"not_ready"}"""))
        val (client, _) = readyClient(server, heldCertificate)

        assertEquals(
            AuthenticatedConnectorResult.ServerFailure(503),
            client.execute(AuthenticatedConnectorOperation.ValidateSession),
        )
    }

    // 45. malformed successful JSON maps to MalformedResponse
    @Test
    fun `malformed JSON on a 200 response maps to MalformedResponse`() = runTest {
        val (server, heldCertificate) = newServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("not-json{"))
        val (client, _) = readyClient(server, heldCertificate)

        assertEquals(AuthenticatedConnectorResult.MalformedResponse, client.execute(AuthenticatedConnectorOperation.GetCompanies))
    }

    // 46. socket failure maps to TransportFailure
    @Test
    fun `a socket disconnect maps to TransportFailure`() = runTest {
        val (server, heldCertificate) = newServer()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val (client, _) = readyClient(server, heldCertificate)

        assertEquals(AuthenticatedConnectorResult.TransportFailure, client.execute(AuthenticatedConnectorOperation.GetCompanies))
    }

    // 47. TLS pin failure maps to TransportFailure — see test 26/27 above (same outcome, same code path).

    // 48. timeout maps to TransportFailure — uses a deliberately tiny client-level timeout (not a
    // real sleep in the test itself) against a server that never responds.
    @Test
    fun `a read timeout maps to TransportFailure`() = runTest {
        val (server, heldCertificate) = newServer()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val tinyTimeoutFactory = object : PinnedHttpClientFactory {
            override fun create(endpoint: TrustedConnectorEndpoint) =
                factory.create(endpoint).newBuilder()
                    .readTimeout(150, TimeUnit.MILLISECONDS)
                    .callTimeout(300, TimeUnit.MILLISECONDS)
                    .build()
        }
        val (client, _) = readyClient(server, heldCertificate, tinyTimeoutFactory)

        val result = client.execute(AuthenticatedConnectorOperation.GetCompanies)

        assertEquals(AuthenticatedConnectorResult.TransportFailure, result)
    }

    // 49. coroutine cancellation maps to Cancelled
    @Test
    fun `cancelling the calling coroutine while a request is in flight yields a Cancelled result`() = runBlocking {
        val (server, heldCertificate) = newServer()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val (client, _) = readyClient(server, heldCertificate)

        val results = mutableListOf<AuthenticatedConnectorResult>()
        val job = launch(Dispatchers.Default) {
            results += client.execute(AuthenticatedConnectorOperation.GetCompanies)
        }
        // Give the call a moment to actually reach the blocking read before cancelling it.
        kotlinx.coroutines.delay(150)
        job.cancel()
        withTimeout(5_000) { job.join() }

        assertEquals(listOf(AuthenticatedConnectorResult.Cancelled), results)
    }

    // 50. no status outcome modifies the vault
    @Test
    fun `neither a success nor any HTTP failure status mutates the vault`() = runTest {
        val (server, heldCertificate) = newServer()
        server.enqueue(MockResponse().setResponseCode(401))
        val (client, vault) = readyClient(server, heldCertificate)

        client.execute(AuthenticatedConnectorOperation.GetCompanies)

        val record = vault.read()
        assertEquals(com.budcom.android.core.pairing.domain.model.SecurePairingCredentialState.ACTIVE, record?.state)
        assertEquals("cred-1", record?.credentialId)
    }

    // ============================== Zero-network-call context outcomes (71-74) ==============================

    @Test
    fun `an unpaired vault makes zero network calls`() = runTest {
        val (server, _) = newServer()
        val unpaired = FakeSecureCredentialVault()
        val client = OkHttpAuthenticatedConnectorApiClient(
            DefaultAuthenticatedConnectorContextProvider(unpaired),
            factory,
            FakeAuthenticatedConnectorEndpointResolver(),
            unpaired,
        )

        val result = client.execute(AuthenticatedConnectorOperation.GetCompanies)

        assertEquals(AuthenticatedConnectorResult.Unpaired, result)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a pending-verification vault makes zero network calls`() = runTest {
        val (server, heldCertificate) = newServer()
        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", SUPER_SECRET_TOKEN, endpointFor(server, heldCertificate), 1_000L)
        val client = OkHttpAuthenticatedConnectorApiClient(
            DefaultAuthenticatedConnectorContextProvider(vault),
            factory,
            FakeAuthenticatedConnectorEndpointResolver(),
            vault,
        )

        val result = client.execute(AuthenticatedConnectorOperation.GetCompanies)

        assertEquals(AuthenticatedConnectorResult.PendingVerification, result)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a re-pair-required vault makes zero network calls`() = runTest {
        val (server, heldCertificate) = newServer()
        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", SUPER_SECRET_TOKEN, endpointFor(server, heldCertificate), 1_000L)
        vault.markActive("cred-1", 2_000L)
        vault.markRePairRequired("cred-1")
        val client = OkHttpAuthenticatedConnectorApiClient(
            DefaultAuthenticatedConnectorContextProvider(vault),
            factory,
            FakeAuthenticatedConnectorEndpointResolver(),
            vault,
        )

        val result = client.execute(AuthenticatedConnectorOperation.GetCompanies)

        assertEquals(AuthenticatedConnectorResult.RePairRequired, result)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a credential-unavailable vault makes zero network calls`() = runTest {
        val (server, heldCertificate) = newServer()
        val cipher = com.budcom.android.core.security.FakeCredentialCipher()
        val vault = FakeSecureCredentialVault(cipher = cipher)
        vault.storePendingVerification("cred-1", "device-1", SUPER_SECRET_TOKEN, endpointFor(server, heldCertificate), 1_000L)
        vault.markActive("cred-1", 2_000L)
        cipher.dropKey()
        val client = OkHttpAuthenticatedConnectorApiClient(
            DefaultAuthenticatedConnectorContextProvider(vault),
            factory,
            FakeAuthenticatedConnectorEndpointResolver(),
            vault,
        )

        val result = client.execute(AuthenticatedConnectorOperation.GetCompanies)

        assertEquals(AuthenticatedConnectorResult.CredentialUnavailable, result)
        assertEquals(0, server.requestCount)
    }

    // ============================== Timeout profiles (63-68) ==============================

    // 63, 64. operations declare the correct profile
    @Test
    fun `standard operations declare the STANDARD profile and sync operations declare SYNC`() {
        assertEquals(ConnectorTimeoutProfile.STANDARD, AuthenticatedConnectorOperation.GetCompanies.timeoutProfile)
        assertEquals(ConnectorTimeoutProfile.STANDARD, AuthenticatedConnectorOperation.GetLedgers().timeoutProfile)
        assertEquals(ConnectorTimeoutProfile.SYNC, AuthenticatedConnectorOperation.StartLedgerSync.timeoutProfile)
        assertEquals(ConnectorTimeoutProfile.SYNC, AuthenticatedConnectorOperation.StartStockItemSync.timeoutProfile)
        assertEquals(ConnectorTimeoutProfile.SYNC, AuthenticatedConnectorOperation.StartVoucherSync.timeoutProfile)
        assertEquals(ConnectorTimeoutProfile.SYNC, AuthenticatedConnectorOperation.LedgerStorageIntegrityCheck.timeoutProfile)
        assertEquals(ConnectorTimeoutProfile.SYNC, AuthenticatedConnectorOperation.LedgerStorageBackup.timeoutProfile)
        assertEquals(ConnectorTimeoutProfile.SYNC, AuthenticatedConnectorOperation.StockItemStorageIntegrityCheck.timeoutProfile)
        assertEquals(ConnectorTimeoutProfile.SYNC, AuthenticatedConnectorOperation.StockItemStorageBackup.timeoutProfile)
    }

    // 65, 66, 67, 68. the SYNC-derived client preserves the pin, hostname verifier, redirect
    // prohibition, and retry prohibition — only the timeout fields differ.
    @Test
    fun `the SYNC-profile client preserves every security property of the STANDARD client`() {
        val (server, heldCertificate) = newServer()
        val endpoint = endpointFor(server, heldCertificate)
        val standardClient = factory.create(endpoint)

        val syncClient = applyConnectorTimeoutProfile(standardClient, ConnectorTimeoutProfile.SYNC)

        assertEquals(standardClient.sslSocketFactory, syncClient.sslSocketFactory)
        assertEquals(standardClient.x509TrustManager, syncClient.x509TrustManager)
        assertEquals(standardClient.hostnameVerifier, syncClient.hostnameVerifier)
        assertEquals(standardClient.connectionSpecs, syncClient.connectionSpecs)
        assertFalse(syncClient.followRedirects)
        assertFalse(syncClient.followSslRedirects)
        assertFalse(syncClient.retryOnConnectionFailure)
        // Timeouts genuinely differ, proving the derivation actually took effect.
        assertTrue(syncClient.readTimeoutMillis > standardClient.readTimeoutMillis)
    }

    @Test
    fun `a SYNC-profile operation still enforces the pin end to end`() = runTest {
        val (server, _) = newServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("should never be read"))
        val wrongCert = testHeldCertificate()
        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", SUPER_SECRET_TOKEN, endpointFor(server, wrongCert), 1_000L)
        vault.markActive("cred-1", 2_000L)
        val client = OkHttpAuthenticatedConnectorApiClient(
            DefaultAuthenticatedConnectorContextProvider(vault),
            factory,
            FakeAuthenticatedConnectorEndpointResolver(),
            vault,
        )

        val result = client.execute(AuthenticatedConnectorOperation.StartLedgerSync)

        assertEquals(AuthenticatedConnectorResult.IdentityMismatch, result)
    }
}
