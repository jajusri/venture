package com.budcom.android.feature.dashboard.domain.usecase

import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.network.NetworkConnectivityObserver
import com.budcom.android.core.util.TimeProvider
import com.budcom.android.feature.company.domain.port.CompanySessionPort
import com.budcom.android.feature.company.domain.port.SelectedCompanyStatus
import com.budcom.android.feature.company.domain.port.SessionValidationStatus
import com.budcom.android.feature.company.domain.port.SessionValidity
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
    fun `offline state`() = runTest {
        val snapshot = useCase(
            online = false,
            probe = AppResult.Failure(AppError.Offline()),
        )()
        assertEquals(false, snapshot.isOnline)
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
    private val selectedFlow = MutableStateFlow(selectedId)
    override fun observeSelectedCompanyId(): Flow<String?> = selectedFlow
    override suspend fun readSelectedCompany(): AppResult<SelectedCompanyStatus> = selected
    override suspend fun validateSessionStatus(): AppResult<SessionValidationStatus> = validate
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
