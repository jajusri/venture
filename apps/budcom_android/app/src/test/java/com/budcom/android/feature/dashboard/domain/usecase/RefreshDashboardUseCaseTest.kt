package com.budcom.android.feature.dashboard.domain.usecase

import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.network.NetworkConnectivityObserver
import com.budcom.android.core.util.TimeProvider
import com.budcom.android.feature.company.domain.port.CompanySessionPort
import com.budcom.android.feature.company.domain.port.SelectedCompanyStatus
import com.budcom.android.feature.company.domain.port.SessionValidationStatus
import com.budcom.android.feature.company.domain.port.SessionValidity
import com.budcom.android.feature.company.domain.repository.CompanyRepository
import com.budcom.android.feature.company.domain.usecase.RestoreCompanySelectionUseCase
import com.budcom.android.feature.dashboard.domain.model.DashboardOperationalMode
import com.budcom.android.feature.dashboard.domain.model.DashboardSessionValidity
import com.budcom.android.feature.serverconfig.domain.model.ConnectorConnectionProbe
import com.budcom.android.feature.serverconfig.domain.model.ConnectorHealth
import com.budcom.android.feature.serverconfig.domain.model.ConnectorReadiness
import com.budcom.android.feature.serverconfig.domain.port.ConnectorStatusPort
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
            selected = AppResult.Success(SelectedCompanyStatus("budcom-test-01", "Budcom-Test-01")),
            validate = AppResult.Success(
                SessionValidationStatus(
                    validity = SessionValidity.Valid,
                    companyId = "budcom-test-01",
                    companyName = "Budcom-Test-01",
                ),
            ),
        )
        val restoreRepo = object : CompanyRepository by NoOpCompanyRepository {
            override suspend fun restoreSelection(): AppResult<com.budcom.android.feature.company.domain.model.SessionValidationOutcome?> {
                companySession.selectedFlow.value = "budcom-test-01"
                return AppResult.Success(null)
            }
        }
        val snapshot = RefreshDashboardUseCase(
            connectorStatus = FakeConnectorStatus("http://10.0.2.2:8080/", AppResult.Success(sampleProbe(ready = true))),
            companySession = companySession,
            restoreCompanySelection = RestoreCompanySelectionUseCase(restoreRepo),
            connectivityObserver = FakeConnectivity(true),
            timeProvider = TimeProvider { 5_000L },
        )()
        assertEquals("budcom-test-01", snapshot.selectedCompanyId)
        assertEquals("Budcom-Test-01", snapshot.selectedCompanyName)
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
            selectedId = "budcom-test-01",
            selected = AppResult.Failure(AppError.Offline()),
            validate = AppResult.Success(
                SessionValidationStatus(
                    validity = SessionValidity.Valid,
                    companyId = "budcom-test-01",
                    companyName = "Budcom-Test-01",
                ),
            ),
        )()
        assertEquals("budcom-test-01", snapshot.selectedCompanyId)
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
        connectorStatus = FakeConnectorStatus(baseUrl, probe),
        companySession = FakeCompanySession(selectedId, selected, validate),
        restoreCompanySelection = RestoreCompanySelectionUseCase(NoOpCompanyRepository),
        connectivityObserver = FakeConnectivity(online),
        timeProvider = TimeProvider { 5_000L },
    )
}

private class FakeConnectorStatus(
    private val baseUrl: String,
    private val probe: AppResult<ConnectorConnectionProbe>,
) : ConnectorStatusPort {
    override fun observeBaseUrl(): Flow<String> = flowOf(baseUrl)
    override fun currentBaseUrl(): String = baseUrl
    override suspend fun probeConnection(): AppResult<ConnectorConnectionProbe> = probe
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
    override suspend fun restoreSelection(): AppResult<com.budcom.android.feature.company.domain.model.SessionValidationOutcome?> =
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
