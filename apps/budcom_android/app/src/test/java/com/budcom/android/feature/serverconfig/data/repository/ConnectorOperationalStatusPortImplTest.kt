package com.budcom.android.feature.serverconfig.data.repository

import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.connectorauth.data.remote.AuthenticatedConnectorApiPort
import com.budcom.android.core.connectorauth.domain.AuthenticatedConnectorContextProvider
import com.budcom.android.core.connectorauth.domain.AuthenticatedConnectorContextResolution
import com.budcom.android.core.connectorauth.domain.ConnectorTransportSelection
import com.budcom.android.core.connectorauth.domain.ConnectorTransportSelectionGate
import com.budcom.android.core.connectorauth.domain.model.AuthenticatedConnectorContext
import com.budcom.android.core.connectorauth.domain.model.AuthenticatedConnectorOperation
import com.budcom.android.core.connectorauth.domain.model.AuthenticatedConnectorResponsePayload
import com.budcom.android.core.connectorauth.domain.model.AuthenticatedConnectorResult
import com.budcom.android.core.connectorauth.domain.model.RedactedBearerCredential
import com.budcom.android.core.pairing.domain.model.TrustedConnectorEndpoint
import com.budcom.android.core.util.TimeProvider
import com.budcom.android.feature.serverconfig.domain.model.ConnectorConnectionProbe
import com.budcom.android.feature.serverconfig.domain.model.ConnectorHealth
import com.budcom.android.feature.serverconfig.domain.model.ConnectorReadiness
import com.budcom.android.feature.serverconfig.domain.port.ConnectorOperationalStatus
import com.budcom.android.feature.serverconfig.domain.port.ConnectorStatusPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TD-016: proves [ConnectorOperationalStatusPortImpl] resolves purely from
 * [ConnectorTransportSelectionGate] (itself sourced only from [com.budcom.android.core.pairing.data.local.SecureCredentialVault])
 * plus the authenticated transport's own result — never from any legacy base URL, never from
 * Desktop's mobile-pairing-admission toggle, which Android has no representation of at all. See
 * TD-016's registry entry, "Not an acceptable fix direction".
 */
class ConnectorOperationalStatusPortImplTest {
    private fun port(
        transportGate: ConnectorTransportSelectionGate,
        legacyStatus: ConnectorStatusPort = UnreachableLegacyStatus,
        authenticatedApi: AuthenticatedConnectorApiPort = UnreachableAuthenticatedApi,
        contextProvider: AuthenticatedConnectorContextProvider = UnreachableContextProvider,
        timeProvider: TimeProvider = TimeProvider { 5_000L },
    ): ConnectorOperationalStatusPortImpl = ConnectorOperationalStatusPortImpl(
        transportGate = transportGate,
        legacyStatus = legacyStatus,
        authenticatedApi = authenticatedApi,
        contextProvider = contextProvider,
        timeProvider = timeProvider,
    )

    @Test
    fun `LEGACY transport reports the legacy status port unchanged and never touches the authenticated transport`() = runTest {
        val probe = AppResult.Success(sampleProbe())
        val legacy = FakeLegacyStatus(baseUrl = "http://10.0.2.2:8080/", probe = probe)
        val impl = port(transportGate = FakeGate(ConnectorTransportSelection.LEGACY), legacyStatus = legacy)

        val status = impl.currentStatus() as ConnectorOperationalStatus.Legacy

        assertEquals("http://10.0.2.2:8080/", status.baseUrl)
        assertEquals(probe, status.healthProbe)
    }

    @Test
    fun `AUTHENTICATED transport with a successful diagnostics round-trip reports AuthenticatedHealthy with the real endpoint and never consults the legacy status port`() = runTest {
        val impl = port(
            transportGate = FakeGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedApi = FakeAuthenticatedApi(
                result = AuthenticatedConnectorResult.Success(AuthenticatedConnectorResponsePayload("{}")),
            ),
            contextProvider = FakeContextProvider(readyResolution("connector.lan", 8443)),
            timeProvider = TimeProvider { 42_000L },
        )

        val status = impl.currentStatus() as ConnectorOperationalStatus.AuthenticatedHealthy

        assertEquals("https://connector.lan:8443/", status.endpointDisplay)
        assertEquals(42_000L, status.checkedAtEpochMillis)
        assertTrue(status.endpointDisplay != "http://10.0.2.2:8080/")
    }

    @Test
    fun `AUTHENTICATED transport uses DiagnosticsConnection as the reachability probe, never health or ready`() = runTest {
        val api = FakeAuthenticatedApi(result = AuthenticatedConnectorResult.Success(AuthenticatedConnectorResponsePayload("{}")))
        val impl = port(
            transportGate = FakeGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedApi = api,
            contextProvider = FakeContextProvider(readyResolution("connector.lan", 8443)),
        )

        impl.currentStatus()

        assertEquals(listOf(AuthenticatedConnectorOperation.DiagnosticsConnection), api.executedOperations)
    }

    @Test
    fun `an ACTIVE credential resolves AuthenticatedHealthy regardless of any pairing-admission-like signal, because this port has no such dependency to consult`() = runTest {
        // ConnectorOperationalStatusPortImpl's constructor takes exactly transportGate,
        // legacyStatus, authenticatedApi, contextProvider, timeProvider — there is no
        // pairing-admission input to wire in even if a test wanted to. An ACTIVE vault record
        // (the only thing FakeGate/FakeContextProvider represent here) is therefore, by
        // construction, sufficient on its own to report AuthenticatedHealthy.
        val impl = port(
            transportGate = FakeGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedApi = FakeAuthenticatedApi(
                result = AuthenticatedConnectorResult.Success(AuthenticatedConnectorResponsePayload("{}")),
            ),
            contextProvider = FakeContextProvider(readyResolution("connector.lan", 8443)),
        )

        val status = impl.currentStatus()

        assertTrue(status is ConnectorOperationalStatus.AuthenticatedHealthy)
    }

    @Test
    fun `RePairRequired maps to AuthenticatedUnavailable and never falls back to legacy`() = runTest {
        val impl = port(
            transportGate = FakeGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedApi = FakeAuthenticatedApi(result = AuthenticatedConnectorResult.RePairRequired),
            contextProvider = FakeContextProvider(AuthenticatedConnectorContextResolution.RePairRequired),
        )

        val status = impl.currentStatus() as ConnectorOperationalStatus.AuthenticatedUnavailable
        assertTrue(status.error is AppError.Message)
    }

    @Test
    fun `PendingVerification maps to AuthenticatedPreparing and never falls back to legacy`() = runTest {
        val impl = port(
            transportGate = FakeGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedApi = FakeAuthenticatedApi(result = AuthenticatedConnectorResult.PendingVerification),
            contextProvider = FakeContextProvider(AuthenticatedConnectorContextResolution.PendingVerification),
        )

        val status = impl.currentStatus()
        assertTrue(status is ConnectorOperationalStatus.AuthenticatedPreparing)
    }

    @Test
    fun `a TransportFailure maps to AuthenticatedUnavailable and the endpoint display placeholder is used, not 10-0-2-2`() = runTest {
        val impl = port(
            transportGate = FakeGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedApi = FakeAuthenticatedApi(result = AuthenticatedConnectorResult.TransportFailure),
            contextProvider = FakeContextProvider(AuthenticatedConnectorContextResolution.Unpaired),
        )

        val status = impl.currentStatus() as ConnectorOperationalStatus.AuthenticatedUnavailable
        assertTrue(status.error is AppError.Message)
    }

    @Test
    fun `identity mismatch reports explicit re-pair without replacing trust or falling back to legacy`() = runTest {
        val impl = port(
            transportGate = FakeGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedApi = FakeAuthenticatedApi(result = AuthenticatedConnectorResult.IdentityMismatch),
            contextProvider = FakeContextProvider(AuthenticatedConnectorContextResolution.Unpaired),
        )

        val status = impl.currentStatus() as ConnectorOperationalStatus.AuthenticatedUnavailable
        val message = (status.error as AppError.Message).message
        assertTrue(message.contains("identity changed"))
        assertTrue(message.contains("Trust was not replaced"))
        assertTrue(message.contains("fresh Desktop QR"))
    }

    @Test
    fun `invalid certificate reports a trust failure without changing trust or falling back to legacy`() = runTest {
        val impl = port(
            transportGate = FakeGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedApi = FakeAuthenticatedApi(result = AuthenticatedConnectorResult.CertificateInvalid),
            contextProvider = FakeContextProvider(AuthenticatedConnectorContextResolution.Unpaired),
        )

        val status = impl.currentStatus() as ConnectorOperationalStatus.AuthenticatedUnavailable
        val message = (status.error as AppError.Message).message
        assertTrue(message.contains("certificate is invalid"))
        assertTrue(message.contains("Trust was not changed"))
    }

    @Test
    fun `every call re-resolves the transport gate rather than caching a prior selection`() = runTest {
        val gate = CountingGate(ConnectorTransportSelection.LEGACY)
        val impl = port(transportGate = gate, legacyStatus = FakeLegacyStatus())

        impl.currentStatus()
        impl.currentStatus()

        assertEquals(2, gate.resolveCount)
    }
}

private fun sampleProbe() = ConnectorConnectionProbe(
    health = ConnectorHealth(
        status = "ok",
        schemaVersion = "1.0.0",
        connectorVersion = "0.1.0",
        tallyReachable = true,
        readOnly = true,
        bindHost = "0.0.0.0",
        bindPort = 8080,
        networkExposure = "loopback",
        networkExposureWarning = null,
        networkPolicySatisfied = true,
        authenticatedLanAccessEnabled = false,
        services = emptyList(),
        startupCorrelationId = null,
        repositoryAvailable = true,
        databaseAccessible = true,
    ),
    readiness = ConnectorReadiness(
        status = "ready",
        repositoryAvailable = true,
        databaseAccessible = true,
        voucherSynchronizationComposed = false,
        voucherApplicationComposed = true,
        httpStatus = 200,
    ),
    checkedAtEpochMillis = 1L,
)

private fun readyResolution(host: String, securePort: Int): AuthenticatedConnectorContextResolution.Ready =
    AuthenticatedConnectorContextResolution.Ready(
        AuthenticatedConnectorContext(
            endpoint = TrustedConnectorEndpoint.fromTrustedPublicMetadata(
                connectorId = "connector-abc",
                connectorName = "Front Desk",
                host = host,
                securePort = securePort,
                transportFingerprint = "sha256/AAAA",
                fingerprintAlgorithm = "sha256",
                transportIdentityVersion = 1,
            ),
            logicalDeviceId = "device-1",
            credentialId = "cred-1",
            bearerCredential = RedactedBearerCredential("token"),
        ),
    )

private class FakeGate(private val selection: ConnectorTransportSelection) : ConnectorTransportSelectionGate {
    override suspend fun resolve(): ConnectorTransportSelection = selection
}

private class CountingGate(private val selection: ConnectorTransportSelection) : ConnectorTransportSelectionGate {
    var resolveCount = 0
        private set

    override suspend fun resolve(): ConnectorTransportSelection {
        resolveCount++
        return selection
    }
}

private class FakeLegacyStatus(
    private val baseUrl: String = "http://10.0.2.2:8080/",
    private val probe: AppResult<ConnectorConnectionProbe> = AppResult.Success(sampleProbe()),
) : ConnectorStatusPort {
    override fun observeBaseUrl(): Flow<String> = flowOf(baseUrl)
    override fun currentBaseUrl(): String = baseUrl
    override suspend fun probeConnection(): AppResult<ConnectorConnectionProbe> = probe
}

/** Proves the AUTHENTICATED path never reaches the legacy status port. */
private object UnreachableLegacyStatus : ConnectorStatusPort {
    override fun observeBaseUrl(): Flow<String> = unreachable()
    override fun currentBaseUrl(): String = unreachable()
    override suspend fun probeConnection(): AppResult<ConnectorConnectionProbe> = unreachable()
    private fun unreachable(): Nothing = error("UnreachableLegacyStatus must never be called on the AUTHENTICATED path")
}

private class FakeAuthenticatedApi(
    private val result: AuthenticatedConnectorResult,
) : AuthenticatedConnectorApiPort {
    val executedOperations = mutableListOf<AuthenticatedConnectorOperation>()
    override suspend fun execute(operation: AuthenticatedConnectorOperation): AuthenticatedConnectorResult {
        executedOperations.add(operation)
        return result
    }
}

/** Proves the LEGACY path never reaches the authenticated transport. */
private object UnreachableAuthenticatedApi : AuthenticatedConnectorApiPort {
    override suspend fun execute(operation: AuthenticatedConnectorOperation): AuthenticatedConnectorResult =
        error("UnreachableAuthenticatedApi must never be called on the LEGACY path")
}

private class FakeContextProvider(
    private val resolution: AuthenticatedConnectorContextResolution,
) : AuthenticatedConnectorContextProvider {
    override suspend fun resolve(): AuthenticatedConnectorContextResolution = resolution
}

/** Proves the LEGACY path never reaches the authenticated context provider. */
private object UnreachableContextProvider : AuthenticatedConnectorContextProvider {
    override suspend fun resolve(): AuthenticatedConnectorContextResolution =
        error("UnreachableContextProvider must never be called on the LEGACY path")
}
