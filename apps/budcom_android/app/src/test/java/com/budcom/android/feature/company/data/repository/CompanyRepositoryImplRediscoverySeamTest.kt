package com.budcom.android.feature.company.data.repository

import com.budcom.android.core.common.AppResult
import com.budcom.android.core.connectorauth.data.remote.OkHttpAuthenticatedConnectorApiClient
import com.budcom.android.core.connectorauth.domain.DefaultAuthenticatedConnectorContextProvider
import com.budcom.android.core.connectorauth.domain.DefaultAuthenticatedConnectorEndpointResolver
import com.budcom.android.core.connectorauth.domain.DefaultAuthenticatedRepositoryFailurePolicy
import com.budcom.android.core.connectorauth.domain.DefaultConnectorTransportSelectionGate
import com.budcom.android.core.discovery.DiscoveredConnector
import com.budcom.android.core.discovery.FakeConnectorDiscoveryPort
import com.budcom.android.core.network.ApiResult
import com.budcom.android.core.network.ErrorMapper
import com.budcom.android.core.network.NetworkError
import com.budcom.android.core.pairing.data.local.FakeSecureCredentialVault
import com.budcom.android.core.pairing.data.remote.OkHttpPinnedHttpClientFactory
import com.budcom.android.core.pairing.data.remote.PinnedHttpClientFactory
import com.budcom.android.core.pairing.data.remote.TLS_TEST_NOW_EPOCH_MILLIS
import com.budcom.android.core.pairing.data.remote.endpointFor
import com.budcom.android.core.pairing.data.remote.startTlsMockWebServer
import com.budcom.android.core.pairing.data.remote.testHeldCertificate
import com.budcom.android.core.security.DefaultSpkiFingerprintVerifier
import com.budcom.android.core.util.DispatcherProvider
import com.budcom.android.core.util.TimeProvider
import com.budcom.android.feature.company.data.local.CompanyLocalDataSource
import com.budcom.android.feature.company.data.remote.CompanyRemoteDataSource
import com.budcom.android.feature.company.data.remote.DefaultAuthenticatedCompanyRemoteDataSource
import com.budcom.android.feature.company.domain.model.CompanyDiscoverySnapshot
import com.budcom.android.feature.company.domain.model.CompanySelectionOutcome
import com.budcom.android.feature.company.domain.model.ConnectorSessionSnapshot
import com.budcom.android.feature.company.domain.model.SessionSelectedCompany
import com.budcom.android.feature.company.domain.model.SessionValidationOutcome
import com.budcom.android.core.common.AppError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val CONNECTOR_ID = "connector-front-desk"
private const val SAVED_COMPANY_ID = "estimation"
private const val VALID_SESSION_JSON = """
    {"sessionId":"s1","selectedCompany":{"id":"$SAVED_COMPANY_ID","name":"ESTIMATION"},
     "connectionStatus":"ACTIVE","connectorVersion":"0.4.0","erpType":"tally",
     "selectedAt":"2026-08-09T00:00:00.000Z","lastValidatedAt":"2026-08-09T00:00:00.000Z",
     "createdAt":"2026-08-01T00:00:00.000Z"}
"""

/**
 * TD-017 x TD-013 "seam" test: the single highest-value gap the distributed-recovery-gate plan
 * identified — proving the two independently-shipped recovery mechanisms compose correctly within
 * ONE operation cycle, rather than each merely passing its own isolated suite.
 *
 * Narrative: a securely paired device's persisted endpoint has gone stale (Connector moved via an
 * ordinary DHCP/Wi-Fi change). The very first authenticated call that surfaces this is a session
 * validation — which, after TD-017's rediscovery silently repairs the endpoint and retries, turns
 * out to ALSO need TD-013's NO_COMPANY_SELECTED recovery (the Connector at the new address hasn't
 * had a company reselected yet). Both must resolve within the same outer call, with no re-pairing,
 * no manual IP, no generic "Connector rejected the request", and no double rediscovery.
 */
class CompanyRepositoryImplRediscoverySeamTest {

    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = dispatcher
        override val io: CoroutineDispatcher = dispatcher
        override val default: CoroutineDispatcher = dispatcher
    }
    private val timeProvider = TimeProvider { TLS_TEST_NOW_EPOCH_MILLIS }
    private val factory: PinnedHttpClientFactory = OkHttpPinnedHttpClientFactory(DefaultSpkiFingerprintVerifier(), timeProvider)
    private val servers = mutableListOf<MockWebServer>()
    private val errorMapper = object : ErrorMapper {
        override fun toNetworkError(throwable: Throwable): NetworkError = NetworkError.Unknown()
        override fun toAppError(error: NetworkError): AppError = AppError.Unexpected(IllegalStateException("legacy path must never be reached"))
    }

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
    fun `a stale endpoint that rediscovers mid-call and then needs NO_COMPANY_SELECTED recovery resolves fully in one outer call`() = runTest(dispatcher) {
        val certificate = testHeldCertificate()

        // The persisted (stale) endpoint A: started, captured, then shut down — connection refused.
        val deadServer = startTlsMockWebServer(certificate)
        val staleEndpoint = endpointFor(deadServer, certificate, connectorId = CONNECTOR_ID)
        deadServer.shutdown()

        // The Connector's real current address, endpoint B — same identity/certificate as A.
        val liveServer = startServer(certificate)
        liveServer.enqueue(MockResponse().setResponseCode(200)) // TD-017 verification probe (/health)
        liveServer.enqueue( // retried ValidateSession -> Connector genuinely has no company selected yet
            MockResponse().setResponseCode(400).setBody(
                """{"status":"NO_COMPANY_SELECTED","session":{},"reason":"No company is selected for this connector session"}""",
            ),
        )
        liveServer.enqueue( // TD-013's SelectCompany(savedId) recovery attempt — succeeds directly against B
            MockResponse().setResponseCode(200).setBody("""{"status":"SUCCESS","session":$VALID_SESSION_JSON}"""),
        )
        liveServer.enqueue( // validateAndPersist's confirming re-validate — succeeds directly against B
            MockResponse().setResponseCode(200).setBody(
                """{"status":"SUCCESS","session":$VALID_SESSION_JSON,"companyId":"$SAVED_COMPANY_ID","companyName":"ESTIMATION"}""",
            ),
        )

        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", "device-bearer-token", staleEndpoint, 1_000L)
        vault.markActive("cred-1", 2_000L)

        val discovery = FakeConnectorDiscoveryPort(
            listOf(
                DiscoveredConnector(
                    connectorId = CONNECTOR_ID,
                    name = "Front Desk",
                    host = liveServer.hostName,
                    port = liveServer.port,
                    apiVersion = "1",
                    authRequired = true,
                ),
            ),
        )
        val resolver = DefaultAuthenticatedConnectorEndpointResolver(discovery, factory)
        val contextProvider = DefaultAuthenticatedConnectorContextProvider(vault)
        val port = OkHttpAuthenticatedConnectorApiClient(contextProvider, factory, resolver, vault)
        val transportGate = DefaultConnectorTransportSelectionGate(vault)
        val authenticatedRemote = DefaultAuthenticatedCompanyRemoteDataSource(
            port,
            DefaultAuthenticatedRepositoryFailurePolicy(vault),
            Json { ignoreUnknownKeys = true },
        )
        val store = SeamFakeSelectedCompanyStore(initial = SAVED_COMPANY_ID)
        val repository = CompanyRepositoryImpl(
            SeamUnreachableLegacyRemote,
            SeamFakeCompanyLocal(),
            store,
            errorMapper,
            dispatchers,
            transportGate,
            authenticatedRemote,
        )

        val result = repository.validateSession()

        assertTrue("expected Success but got $result", result is AppResult.Success)
        // TD-017's endpoint fix persisted durably — the vault now points at B, never back to A.
        assertEquals(liveServer.hostName, vault.read()?.endpoint?.host)
        assertEquals(liveServer.port, vault.read()?.endpoint?.securePort)
        // TD-013's recovery re-selected and re-persisted the same intended company — never
        // silently switched to a different one, never left cleared.
        assertEquals(SAVED_COMPANY_ID, store.currentId)
        // Exactly the four requests the narrative predicts — no double rediscovery, no extra
        // verification probe on the SelectCompany/re-validate calls (endpoint was already
        // verified and persisted after the first rediscovery).
        assertEquals(4, liveServer.requestCount)
        assertEquals(0, deadServer.requestCount)
        // Trust/credential/state were never touched by any of this.
        assertEquals("cred-1", vault.read()?.credentialId)
    }

    @Test
    fun `an unrelated 400 during the same stale-endpoint rediscovery is never mistaken for NO_COMPANY_SELECTED`() = runTest(dispatcher) {
        val certificate = testHeldCertificate()
        val deadServer = startTlsMockWebServer(certificate)
        val staleEndpoint = endpointFor(deadServer, certificate, connectorId = CONNECTOR_ID)
        deadServer.shutdown()

        val liveServer = startServer(certificate)
        liveServer.enqueue(MockResponse().setResponseCode(200)) // verification probe
        liveServer.enqueue(MockResponse().setResponseCode(400).setBody("""{"status":"SESSION_INVALID","session":{}}"""))

        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", "device-bearer-token", staleEndpoint, 1_000L)
        vault.markActive("cred-1", 2_000L)
        val discovery = FakeConnectorDiscoveryPort(
            listOf(DiscoveredConnector(CONNECTOR_ID, "Front Desk", liveServer.hostName, liveServer.port, "1", true)),
        )
        val port = OkHttpAuthenticatedConnectorApiClient(
            DefaultAuthenticatedConnectorContextProvider(vault),
            factory,
            DefaultAuthenticatedConnectorEndpointResolver(discovery, factory),
            vault,
        )
        val authenticatedRemote = DefaultAuthenticatedCompanyRemoteDataSource(
            port,
            DefaultAuthenticatedRepositoryFailurePolicy(vault),
            Json { ignoreUnknownKeys = true },
        )
        val store = SeamFakeSelectedCompanyStore(initial = SAVED_COMPANY_ID)
        val repository = CompanyRepositoryImpl(
            SeamUnreachableLegacyRemote,
            SeamFakeCompanyLocal(),
            store,
            errorMapper,
            dispatchers,
            DefaultConnectorTransportSelectionGate(vault),
            authenticatedRemote,
        )

        val result = repository.validateSession()

        assertTrue(result is AppResult.Failure) // never silently recovered into a false Success
        // No SelectCompany recovery attempt was ever made — the endpoint was still correctly
        // rediscovered (2 requests: probe + validate), but company-reselection recovery must
        // never trigger for an unrelated 400.
        assertEquals(2, liveServer.requestCount)
        assertEquals(SAVED_COMPANY_ID, store.currentId) // untouched — no reselection attempted
    }
}

private object SeamUnreachableLegacyRemote : CompanyRemoteDataSource {
    override suspend fun fetchCompanies(): ApiResult<CompanyDiscoverySnapshot> = unreachable()
    override suspend fun fetchSession(): ApiResult<ConnectorSessionSnapshot> = unreachable()
    override suspend fun selectCompany(companyId: String): ApiResult<CompanySelectionOutcome> = unreachable()
    override suspend fun validateSession(): ApiResult<SessionValidationOutcome> = unreachable()
    override suspend fun clearSession(): ApiResult<ConnectorSessionSnapshot> = unreachable()
    private fun unreachable(): Nothing = error("legacy transport must never be reached on the AUTHENTICATED seam path")
}

private class SeamFakeCompanyLocal : CompanyLocalDataSource {
    private var cached: CompanyDiscoverySnapshot? = null
    override suspend fun hasCache(): Boolean = cached != null
    override suspend fun readSnapshot(): CompanyDiscoverySnapshot? = cached
    override suspend fun replaceSnapshot(snapshot: CompanyDiscoverySnapshot) {
        cached = snapshot
    }
}

private class SeamFakeSelectedCompanyStore(initial: String?) : SelectedCompanyStore {
    private val flow = MutableStateFlow(initial)
    var currentId: String? = initial
        private set

    override fun observeSelectedCompanyId(): Flow<String?> = flow
    override suspend fun getSelectedCompanyId(): String? = currentId
    override suspend fun saveSelectedCompanyId(companyId: String) {
        currentId = companyId
        flow.value = companyId
    }
    override suspend fun clearSelectedCompanyId() {
        currentId = null
        flow.value = null
    }
    override suspend fun saveSelectedCompany(company: SessionSelectedCompany) {
        saveSelectedCompanyId(company.id)
    }
}
