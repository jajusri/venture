package com.budcom.android.feature.dashboard.presentation

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
import com.budcom.android.feature.dashboard.domain.usecase.ObserveDashboardContextUseCase
import com.budcom.android.feature.dashboard.domain.usecase.ProbeConnectorConnectionUseCase
import com.budcom.android.feature.dashboard.domain.usecase.RefreshDashboardUseCase
import com.budcom.android.feature.dashboard.domain.usecase.ValidateDashboardSessionUseCase
import com.budcom.android.feature.serverconfig.domain.model.ConnectorConnectionProbe
import com.budcom.android.feature.serverconfig.domain.model.ConnectorHealth
import com.budcom.android.feature.serverconfig.domain.model.ConnectorReadiness
import com.budcom.android.feature.serverconfig.domain.port.ConnectorStatusPort
import com.budcom.android.feature.sync.domain.model.SyncStatusSummary
import com.budcom.android.feature.sync.domain.model.SyncTarget
import com.budcom.android.feature.sync.domain.model.SyncTargetSnapshot
import com.budcom.android.feature.sync.domain.port.ObserveSyncStatusPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var connector: FakeConnectorStatus
    private lateinit var company: FakeCompanySession
    private lateinit var connectivity: FakeConnectivity
    private lateinit var syncStatus: FakeSyncStatus

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        connector = FakeConnectorStatus()
        company = FakeCompanySession()
        connectivity = FakeConnectivity(true)
        syncStatus = FakeSyncStatus()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): DashboardViewModel {
        val refresh = RefreshDashboardUseCase(
            connectorStatus = connector,
            companySession = company,
            connectivityObserver = connectivity,
            timeProvider = TimeProvider { 9_000L },
        )
        return DashboardViewModel(
            refreshDashboard = refresh,
            probeConnectorConnection = ProbeConnectorConnectionUseCase(connector),
            validateDashboardSession = ValidateDashboardSessionUseCase(
                companySession = company,
                timeProvider = TimeProvider { 9_000L },
            ),
            observeDashboardContext = ObserveDashboardContextUseCase(
                connectorStatus = connector,
                companySession = company,
                connectivityObserver = connectivity,
            ),
            observeSyncStatus = syncStatus,
        )
    }

    @Test
    fun `initial refresh reaches fully operational`() = runTest(dispatcher) {
        company.selectedIdFlow.value = "estimation"
        company.validateResult = AppResult.Success(
            SessionValidationStatus(SessionValidity.Valid, "estimation", "ESTIMATION"),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertEquals(DashboardOperationalMode.FullyOperational, viewModel.uiState.value.operationalMode)
        assertEquals(DashboardSessionValidity.Valid, viewModel.uiState.value.sessionValidity)
    }

    @Test
    fun `offline connectivity updates operational mode`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        connectivity.online.value = false
        advanceUntilIdle()
        assertEquals(DashboardOperationalMode.Offline, viewModel.uiState.value.operationalMode)
    }

    @Test
    fun `not ready when readiness 503`() = runTest(dispatcher) {
        connector.probe = AppResult.Success(sampleProbe(ready = false))
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertEquals(ReadinessLabel.NotReady, viewModel.uiState.value.readinessLabel)
        assertEquals(DashboardOperationalMode.NotReady, viewModel.uiState.value.operationalMode)
    }

    @Test
    fun `duplicate refresh ignored`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        connector.delayMillis = 1_000
        connector.probeCalls = 0
        viewModel.onEvent(DashboardEvent.Refresh)
        viewModel.onEvent(DashboardEvent.Refresh)
        viewModel.onEvent(DashboardEvent.Refresh)
        advanceUntilIdle()
        assertEquals(1, connector.probeCalls)
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun `navigation events emitted`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val emitted = mutableListOf<DashboardNavigation>()
        val job = launch { viewModel.navigation.collect { emitted.add(it) } }
        advanceUntilIdle()
        viewModel.onEvent(DashboardEvent.OpenServerConfig)
        viewModel.onEvent(DashboardEvent.OpenCompanySelection)
        viewModel.onEvent(DashboardEvent.OpenMasterData)
        viewModel.onEvent(DashboardEvent.OpenVouchers)
        viewModel.onEvent(DashboardEvent.OpenSearch)
        viewModel.onEvent(DashboardEvent.OpenSync)
        viewModel.onEvent(DashboardEvent.OpenDiagnostics)
        viewModel.onEvent(DashboardEvent.OpenSettings)
        advanceUntilIdle()
        assertEquals(
            listOf(
                DashboardNavigation.ServerConfig,
                DashboardNavigation.CompanySelection,
                DashboardNavigation.MasterData,
                DashboardNavigation.Vouchers,
                DashboardNavigation.Search,
                DashboardNavigation.Sync,
                DashboardNavigation.Diagnostics,
                DashboardNavigation.Settings,
            ),
            emitted,
        )
        job.cancel()
    }

    @Test
    fun `partial success preserves prior health timestamp`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val prior = viewModel.uiState.value.lastSuccessfulHealthCheckEpochMillis
        connector.probe = AppResult.Failure(AppError.Timeout())
        viewModel.onEvent(DashboardEvent.Refresh)
        advanceUntilIdle()
        assertEquals(prior, viewModel.uiState.value.lastSuccessfulHealthCheckEpochMillis)
        assertTrue(viewModel.uiState.value.connectorError is DashboardUiError.Timeout)
        assertEquals(false, viewModel.uiState.value.connectorConnected)
    }
}

private class FakeConnectorStatus : ConnectorStatusPort {
    val baseUrl = MutableStateFlow("http://10.0.2.2:8080/")
    var probe: AppResult<ConnectorConnectionProbe> = AppResult.Success(sampleProbe(ready = true))
    var delayMillis: Long = 0
    var probeCalls = 0
    override fun observeBaseUrl(): Flow<String> = baseUrl
    override fun currentBaseUrl(): String = baseUrl.value
    override suspend fun probeConnection(): AppResult<ConnectorConnectionProbe> {
        probeCalls++
        if (delayMillis > 0) delay(delayMillis)
        return probe
    }
}

private class FakeCompanySession : CompanySessionPort {
    val selectedIdFlow = MutableStateFlow<String?>(null)
    var selectedResult: AppResult<SelectedCompanyStatus> =
        AppResult.Success(SelectedCompanyStatus(null, null))
    var validateResult: AppResult<SessionValidationStatus> =
        AppResult.Success(SessionValidationStatus(SessionValidity.NoCompany, null, null))

    override fun observeSelectedCompanyId(): Flow<String?> = selectedIdFlow
    override suspend fun readSelectedCompany(): AppResult<SelectedCompanyStatus> {
        val id = selectedIdFlow.value
        return if (selectedResult is AppResult.Success && id != null) {
            AppResult.Success(SelectedCompanyStatus(id, "ESTIMATION"))
        } else {
            selectedResult
        }
    }
    override suspend fun validateSessionStatus(): AppResult<SessionValidationStatus> = validateResult
}

private class FakeConnectivity(initiallyOnline: Boolean) : NetworkConnectivityObserver {
    val online = MutableStateFlow(initiallyOnline)
    override val isOnline: Flow<Boolean> = online
    override fun current(): Boolean = online.value
}

private class FakeSyncStatus : ObserveSyncStatusPort {
    override val summary: StateFlow<SyncStatusSummary> = MutableStateFlow(
        SyncStatusSummary(
            companyId = null,
            isAnySyncActive = false,
            activeTarget = null,
            activeStatus = null,
            latestSuccessfulAt = null,
            latestFailedMessage = null,
            targets = listOf(
                SyncTargetSnapshot(SyncTarget.Ledgers, available = true),
                SyncTargetSnapshot(SyncTarget.StockItems, available = true),
                SyncTargetSnapshot(
                    SyncTarget.Vouchers,
                    available = false,
                    unavailableReason = "Public voucher sync is not available on the Connector.",
                ),
            ),
            lastUpdatedEpochMillis = 0L,
        ),
    )
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
