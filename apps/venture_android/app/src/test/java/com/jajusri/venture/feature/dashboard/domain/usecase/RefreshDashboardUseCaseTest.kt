package com.jajusri.venture.feature.dashboard.domain.usecase

import com.jajusri.venture.core.common.AppError
import com.jajusri.venture.core.common.AppResult
import com.jajusri.venture.core.network.NetworkConnectivityObserver
import com.jajusri.venture.core.util.TimeProvider
import com.jajusri.venture.feature.company.domain.port.CompanySessionPort
import com.jajusri.venture.feature.company.domain.port.SelectedCompanyStatus
import com.jajusri.venture.feature.company.domain.port.SessionValidationStatus
import com.jajusri.venture.feature.company.domain.port.SessionValidity
import com.jajusri.venture.feature.company.domain.repository.CompanyRepository
import com.jajusri.venture.feature.company.domain.usecase.RestoreCompanySelectionUseCase
import com.jajusri.venture.feature.dashboard.domain.model.DashboardOperationalMode
import com.jajusri.venture.feature.dashboard.domain.model.DashboardSessionValidity
import com.jajusri.venture.feature.serverconfig.domain.model.ConnectorConnectionProbe
import com.jajusri.venture.feature.serverconfig.domain.model.ConnectorHealth
import com.jajusri.venture.feature.serverconfig.domain.model.ConnectorReadiness
import com.jajusri.venture.feature.serverconfig.domain.port.ConnectorOperationalStatus
import com.jajusri.venture.feature.serverconfig.domain.port.ConnectorOperationalStatusPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshDashboardUseCaseTest {

    @Test
    fun `fully operational snapshot`() = runTest {
        val snapshot = useCase(
            probe = AppResult.Success(sampleProbe(ready = true)),
            selectedId = "estimation",
            validate = AppResult.Success(
                SessionValidationStatus(
                    validity = SessionValidity.Valid,
                    companyId = "estimation",
                    companyName = "ESTIMATION",
                ),
            ),
        )()
        assertEquals(DashboardOperationalMode.FullyOperational, snapshot.operationalMode())
        assertEquals(DashboardSessionValidity.Valid, snapshot.sessionValidity)
        assertEquals("ESTIMATION", snapshot.selectedCompanyName)
    }

    @Test
    fun `health success readiness 503 is not ready`() = runTest {
        val snapshot = useCase(
            probe = AppResult.Success(sampleProbe(ready = false)),
            selectedId = "estimation",
            validate = AppResult.Success(
                SessionValidationStatus(
                    validity = SessionValidity.Valid,
                    companyId = "estimation",
                    companyName = "ESTIMATION",
                ),
            ),
        )()
        assertEquals("not_ready", snapshot.readinessStatus)
        assertEquals(DashboardOperationalMode.NotReady, snapshot.operationalMode())
    }

    @Test
    fun `no company selected`() = runTest {
        val snapshot = useCase(probe = AppResult.Success(sampleProbe(ready = true)))()
        assertEquals(DashboardSessionValidity.NoCompany, snapshot.sessionValidity)
        assertEquals(DashboardOperationalMode.NoCompanySelected, snapshot.operationalMode())
        assertNull(snapshot.selectedCompanyId)
    }

    @Test
    fun `hydrates selected company from restore when local cache empty`() = runTest {
        val companySession = FakeCompanySession(
            selectedId = null,
            selected = AppResult.Success(SelectedCompanyStatus("venture-test-01", "Venture-Test-01")),
            validate = AppResult.Success(
                SessionValidationStatus(
                    validity = SessionValidity.Valid,
                    companyId = "venture-test-01",
                    companyName = "Venture-Test-01",
                ),
            ),
        )
        val restoreRepo = object : CompanyRepository by NoOpCompanyRepository {
            override suspend fun restoreSelection(): AppResult<com.jajusri.venture.feature.company.domain.model.SessionValidationOutcome?> {
                companySession.selectedFlow.value = "venture-test-01"
                return AppResult.Success(null)
            }
        }
        val snapshot = RefreshDashboardUseCase(
            operationalStatus = FakeOperationalStatus(
                ConnectorOperationalStatus.Legacy("http://10.0.2.2:8080/", AppResult.Success(sampleProbe(ready = true))),
            ),
            companySession = companySession,
            restoreCompanySelection = RestoreCompanySelectionUseCase(restoreRepo),
            connectivityObserver = FakeConnectivity(true),
            timeProvider = TimeProvider { 5_000L },
        )()
        assertEquals("venture-test-01", snapshot.selectedCompanyId)
        assertEquals("Venture-Test-01", snapshot.selectedCompanyName)
        assertEquals(DashboardSessionValidity.Valid, snapshot.sessionValidity)
    }

    @Test
    fun `offline state`() = runTest {
        val snapshot = useCase(
            online = false,
            probe = AppResult.Failure(AppError.Offline()),
        )()
        assertEquals(false, snapshot.isOnline)
        assertEquals(DashboardOperationalMode.Offline, snapshot.operationalMode())
    }

    @Test
    fun `offline with cached company skips live session validation`() = runTest {
        val snapshot = useCase(
            online = false,
            probe = AppResult.Failure(AppError.Offline()),
            selectedId = "venture-test-01",
            selected = AppResult.Failure(AppError.Offline()),
            validate = AppResult.Success(
                SessionValidationStatus(
                    validity = SessionValidity.Valid,
                    companyId = "venture-test-01",
                    companyName = "Venture-Test-01",
                ),
            ),
        )()
        assertEquals("venture-test-01", snapshot.selectedCompanyId)
        assertEquals(DashboardSessionValidity.Unknown, snapshot.sessionValidity)
        assertNull(snapshot.sessionError)
        assertEquals(DashboardOperationalMode.Offline, snapshot.operationalMode())
    }

    @Test
    fun `partial success keeps company when health fails`() = runTest {
        val snapshot = useCase(
            probe = AppResult.Failure(AppError.Timeout()),
            selectedId = "estimation",
            selected = AppResult.Success(
                SelectedCompanyStatus("estimation", "ESTIMATION"),
            ),
            validate = AppResult.Success(
                SessionValidationStatus(
                    validity = SessionValidity.Unknown,
                    companyId = "estimation",
                    companyName = null,
                    error = AppError.Timeout(),
                ),
            ),
        )()
        assertEquals("estimation", snapshot.selectedCompanyId)
        assertEquals("ESTIMATION", snapshot.selectedCompanyName)
        assertTrue(snapshot.connectorError is AppError.Timeout)
        assertEquals(DashboardOperationalMode.ConnectorUnavailable, snapshot.operationalMode())
    }

    // TD-016 regression coverage below. These exercise the AUTHENTICATED branches of
    // ConnectorOperationalStatus directly (not through the real port implementation, which is
    // covered separately by ConnectorOperationalStatusPortImplTest) - the point here is that
    // RefreshDashboardUseCase itself reacts correctly to each status variant.

    @Test
    fun `TD-016 authenticated healthy device never shows the emulator default and unblocks session validation`() = runTest {
        // This is the exact mechanism that previously left securely paired devices stuck on
        // "Session = Unknown / Last successful validation = Never": session validation below is
        // gated behind healthPresent, which was always false for these devices because the
        // legacy probe (targeting 10.0.2.2) could never succeed. AuthenticatedHealthy setting
        // healthPresent=true is what allows validateSessionStatus() to actually run.
        val companySession = FakeCompanySession(
            selectedId = "estimation",
            selected = AppResult.Success(SelectedCompanyStatus("estimation", "ESTIMATION")),
            validate = AppResult.Success(
                SessionValidationStatus(
                    validity = SessionValidity.Valid,
                    companyId = "estimation",
                    companyName = "ESTIMATION",
                ),
            ),
        )
        val snapshot = RefreshDashboardUseCase(
            operationalStatus = FakeOperationalStatus(
                ConnectorOperationalStatus.AuthenticatedHealthy(
                    endpointDisplay = "https://trusted-connector.example:8443/",
                    checkedAtEpochMillis = 4_242L,
                    health = sampleProbe(ready = true).health,
                    readiness = sampleProbe(ready = true).readiness,
                ),
            ),
            companySession = companySession,
            restoreCompanySelection = RestoreCompanySelectionUseCase(NoOpCompanyRepository),
            connectivityObserver = FakeConnectivity(true),
            timeProvider = TimeProvider { 5_000L },
        )()

        assertEquals("https://trusted-connector.example:8443/", snapshot.baseUrl)
        assertTrue(!snapshot.baseUrl.contains("10.0.2.2"))
        assertTrue(snapshot.healthPresent)
        assertEquals("ready", snapshot.readinessStatus)
        assertNull(snapshot.connectorError)
        assertEquals(DashboardSessionValidity.Valid, snapshot.sessionValidity)
        assertEquals("ESTIMATION", snapshot.selectedCompanyName)
    }

    @Test
    fun `TD-016 authenticated unavailable reports the real authenticated failure, never a fabricated connected state, never 10-0-2-2`() = runTest {
        val snapshot = RefreshDashboardUseCase(
            operationalStatus = FakeOperationalStatus(
                ConnectorOperationalStatus.AuthenticatedUnavailable(
                    AppError.Message("Could not reach the Connector."),
                ),
            ),
            companySession = FakeCompanySession(null, AppResult.Success(SelectedCompanyStatus(null, null)), AppResult.Success(SessionValidationStatus(SessionValidity.NoCompany, null, null))),
            restoreCompanySelection = RestoreCompanySelectionUseCase(NoOpCompanyRepository),
            connectivityObserver = FakeConnectivity(true),
            timeProvider = TimeProvider { 5_000L },
        )()

        assertEquals(ConnectorOperationalStatus.AUTHENTICATED_ENDPOINT_PLACEHOLDER, snapshot.baseUrl)
        assertTrue(!snapshot.baseUrl.contains("10.0.2.2"))
        assertEquals(false, snapshot.healthPresent)
        assertTrue(snapshot.connectorError is AppError.Message)
        assertEquals(DashboardOperationalMode.ConnectorUnavailable, snapshot.operationalMode())
    }

    @Test
    fun `TD-016 authenticated preparing state is bounded and neutral, never the emulator default`() = runTest {
        val snapshot = RefreshDashboardUseCase(
            operationalStatus = FakeOperationalStatus(
                ConnectorOperationalStatus.AuthenticatedPreparing("Verifying secure pairing…"),
            ),
            companySession = FakeCompanySession(null, AppResult.Success(SelectedCompanyStatus(null, null)), AppResult.Success(SessionValidationStatus(SessionValidity.NoCompany, null, null))),
            restoreCompanySelection = RestoreCompanySelectionUseCase(NoOpCompanyRepository),
            connectivityObserver = FakeConnectivity(true),
            timeProvider = TimeProvider { 5_000L },
        )()

        assertEquals(ConnectorOperationalStatus.AUTHENTICATED_ENDPOINT_PLACEHOLDER, snapshot.baseUrl)
        assertTrue(!snapshot.baseUrl.contains("10.0.2.2"))
        assertEquals(false, snapshot.healthPresent)
        assertEquals("Verifying secure pairing…", (snapshot.connectorError as AppError.Message).message)
    }

    @Test
    fun `TD-016 authenticated healthy status resolution never consults any pairing-admission setting`() = runTest {
        // Android has no representation of Desktop's "Secure Mobile Pairing" toggle anywhere in
        // its source (confirmed by repo-wide search) - it is a Desktop-only gate on creating NEW
        // pairing sessions. An already-ACTIVE credential's operational status here is derived
        // solely from local vault state via ConnectorOperationalStatusPort, so there is no
        // "pairing admission off" code path to disable for an already-trusted device - this test
        // documents and pins that architectural fact rather than exercising a real toggle.
        val snapshot = RefreshDashboardUseCase(
            operationalStatus = FakeOperationalStatus(
                ConnectorOperationalStatus.AuthenticatedHealthy(
                    endpointDisplay = "https://trusted-connector.example:8443/",
                    checkedAtEpochMillis = 1L,
                ),
            ),
            companySession = FakeCompanySession(null, AppResult.Success(SelectedCompanyStatus(null, null)), AppResult.Success(SessionValidationStatus(SessionValidity.NoCompany, null, null))),
            restoreCompanySelection = RestoreCompanySelectionUseCase(NoOpCompanyRepository),
            connectivityObserver = FakeConnectivity(true),
            timeProvider = TimeProvider { 5_000L },
        )()

        assertTrue(snapshot.healthPresent)
        assertNull(snapshot.connectorError)
    }

    private fun useCase(
        baseUrl: String = "http://10.0.2.2:8080/",
        online: Boolean = true,
        probe: AppResult<ConnectorConnectionProbe> = AppResult.Success(sampleProbe(ready = true)),
        selectedId: String? = null,
        selected: AppResult<SelectedCompanyStatus> = AppResult.Success(
            SelectedCompanyStatus(selectedId, selectedId?.let { "ESTIMATION" }),
        ),
        validate: AppResult<SessionValidationStatus> = AppResult.Success(
            SessionValidationStatus(SessionValidity.NoCompany, null, null),
        ),
    ) = RefreshDashboardUseCase(
        operationalStatus = FakeOperationalStatus(ConnectorOperationalStatus.Legacy(baseUrl, probe)),
        companySession = FakeCompanySession(selectedId, selected, validate),
        restoreCompanySelection = RestoreCompanySelectionUseCase(NoOpCompanyRepository),
        connectivityObserver = FakeConnectivity(online),
        timeProvider = TimeProvider { 5_000L },
    )
}

private class FakeOperationalStatus(
    private val status: ConnectorOperationalStatus,
) : ConnectorOperationalStatusPort {
    override suspend fun currentStatus(): ConnectorOperationalStatus = status
}

private class FakeCompanySession(
    selectedId: String?,
    private val selected: AppResult<SelectedCompanyStatus>,
    private val validate: AppResult<SessionValidationStatus>,
) : CompanySessionPort {
    val selectedFlow = MutableStateFlow(selectedId)
    override fun observeSelectedCompanyId(): Flow<String?> = selectedFlow
    override suspend fun readSelectedCompany(): AppResult<SelectedCompanyStatus> = selected
    override suspend fun validateSessionStatus(): AppResult<SessionValidationStatus> = validate
}

private object NoOpCompanyRepository : CompanyRepository {
    override fun observeSelectedCompanyId(): Flow<String?> = flowOf(null)
    override suspend fun loadCompanies() = error("unused")
    override suspend fun refreshCompanies() = error("unused")
    override suspend fun getSession() = error("unused")
    override suspend fun restoreSelection(): AppResult<com.jajusri.venture.feature.company.domain.model.SessionValidationOutcome?> =
        AppResult.Success(null)
    override suspend fun selectCompany(companyId: String) = error("unused")
    override suspend fun validateSession() = error("unused")
    override suspend fun clearSelection() = error("unused")
}

private class FakeConnectivity(private val online: Boolean) : NetworkConnectivityObserver {
    override val isOnline: Flow<Boolean> = flowOf(online)
    override fun current(): Boolean = online
}

private fun sampleProbe(ready: Boolean) = ConnectorConnectionProbe(
    health = ConnectorHealth(
        status = "ok",
        schemaVersion = "1.0.0",
        connectorVersion = "0.4.0",
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
        status = if (ready) "ready" else "not_ready",
        repositoryAvailable = ready,
        databaseAccessible = ready,
        voucherSynchronizationComposed = ready,
        voucherApplicationComposed = ready,
        httpStatus = if (ready) 200 else 503,
    ),
    checkedAtEpochMillis = 1_700L,
)
