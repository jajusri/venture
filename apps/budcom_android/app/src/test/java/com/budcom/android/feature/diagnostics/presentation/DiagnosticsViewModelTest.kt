package com.budcom.android.feature.diagnostics.presentation

import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.network.NetworkConnectivityObserver
import com.budcom.android.core.util.TimeProvider
import com.budcom.android.feature.company.domain.port.CompanySessionPort
import com.budcom.android.feature.company.domain.port.SelectedCompanyStatus
import com.budcom.android.feature.company.domain.port.SessionValidationStatus
import com.budcom.android.feature.company.domain.port.SessionValidity
import com.budcom.android.feature.diagnostics.domain.model.ConnectionDiagnostics
import com.budcom.android.feature.diagnostics.domain.port.ConnectionDiagnosticsPort
import com.budcom.android.feature.diagnostics.domain.usecase.LoadDiagnosticsUseCase
import com.budcom.android.feature.serverconfig.domain.model.ConnectorConnectionProbe
import com.budcom.android.feature.serverconfig.domain.model.ConnectorHealth
import com.budcom.android.feature.serverconfig.domain.model.ConnectorReadiness
import com.budcom.android.feature.serverconfig.domain.port.ConnectorStatusPort
import com.budcom.android.feature.sync.domain.model.SyncRunSummary
import com.budcom.android.feature.sync.domain.model.SyncStatusSummary
import com.budcom.android.feature.sync.domain.model.SyncTarget
import com.budcom.android.feature.sync.domain.port.ObserveSyncStatusPort
import com.budcom.android.feature.sync.domain.repository.SyncRepository
import com.budcom.android.feature.sync.domain.usecase.RefreshSyncOverviewUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var connector: VmFakeConnector
    private lateinit var connectivity: VmFakeConnectivity

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        connector = VmFakeConnector()
        connectivity = VmFakeConnectivity(true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createVm(): DiagnosticsViewModel {
        val company = VmFakeCompany()
        val load = LoadDiagnosticsUseCase(
            connectorStatus = connector,
            companySession = company,
            connectionDiagnostics = VmFakeConnectionPort(),
            syncStatus = VmFakeSyncStatus(),
            refreshSyncOverview = RefreshSyncOverviewUseCase(VmFakeSyncRepository(), company),
            timeProvider = TimeProvider { 11L },
        )
        return DiagnosticsViewModel(
            loadDiagnostics = load,
            connectorStatus = connector,
            connectivityObserver = connectivity,
        )
    }

    @Test
    fun loadsConfirmedSectionsOnInit() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isInitialLoading)
        assertNotNull(vm.uiState.value.application)
        assertEquals("ok", vm.uiState.value.connector.health?.status)
        assertEquals("ready", vm.uiState.value.connector.readiness?.status)
        assertEquals("connected", vm.uiState.value.connector.connectionState)
    }

    @Test
    fun recheckHealthUpdatesConnectorSection() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        connector.probe = AppResult.Failure(AppError.Timeout())
        vm.onEvent(DiagnosticsEvent.RecheckHealth)
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.connector.healthError)
    }

    @Test
    fun navigationEvents() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        val emitted = mutableListOf<DiagnosticsNavigation>()
        val job = launch { vm.navigation.collect { emitted.add(it) } }
        vm.onEvent(DiagnosticsEvent.OpenServerConfig)
        vm.onEvent(DiagnosticsEvent.OpenCompanySelection)
        advanceUntilIdle()
        assertEquals(
            listOf(
                DiagnosticsNavigation.ServerConfig,
                DiagnosticsNavigation.CompanySelection,
            ),
            emitted,
        )
        job.cancel()
    }
}

private class VmFakeConnector : ConnectorStatusPort {
    var probe: AppResult<ConnectorConnectionProbe> = AppResult.Success(
        ConnectorConnectionProbe(
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
        ),
    )
    override fun observeBaseUrl(): Flow<String> = flowOf("http://10.0.2.2:8080/")
    override fun currentBaseUrl(): String = "http://10.0.2.2:8080/"
    override suspend fun probeConnection(): AppResult<ConnectorConnectionProbe> = probe
}

private class VmFakeCompany : CompanySessionPort {
    override fun observeSelectedCompanyId(): Flow<String?> = flowOf("c1")
    override suspend fun readSelectedCompany(): AppResult<SelectedCompanyStatus> =
        AppResult.Success(SelectedCompanyStatus("c1", "Company"))
    override suspend fun validateSessionStatus(): AppResult<SessionValidationStatus> =
        AppResult.Success(SessionValidationStatus(SessionValidity.Valid, "c1", "Company"))
}

private class VmFakeConnectionPort : ConnectionDiagnosticsPort {
    override suspend fun loadConnectionDiagnostics(): AppResult<ConnectionDiagnostics> =
        AppResult.Success(
            ConnectionDiagnostics(
                state = "connected",
                host = "127.0.0.1",
                port = 9000,
                lastSuccessfulPingAt = null,
                lastErrorAt = null,
                lastErrorCode = null,
                lastErrorMessage = null,
                totalRequests = 1,
                failedRequests = 0,
                reconnectAttempts = 0,
                averageLatencyMs = 2.0,
                poolActiveConnections = 0,
                poolWaitingRequests = 0,
                safeMode = false,
                circuitState = "closed",
                lastRequestCorrelationId = null,
                lastRequestSentAt = null,
                lastRequestOutcome = null,
                runtimeTimeoutMs = null,
            ),
        )
}

private class VmFakeSyncStatus : ObserveSyncStatusPort {
    override val summary: StateFlow<SyncStatusSummary> = MutableStateFlow(
        SyncStatusSummary(
            companyId = "c1",
            isAnySyncActive = false,
            activeTarget = null,
            activeStatus = null,
            latestSuccessfulAt = null,
            latestFailedMessage = null,
            targets = emptyList(),
            lastUpdatedEpochMillis = 0L,
        ),
    )
}

private class VmFakeSyncRepository : SyncRepository {
    override fun bindCompany(companyId: String?) = Unit
    override suspend fun startSync(
        target: SyncTarget,
        mode: com.budcom.android.feature.sync.domain.model.SyncMode,
    ) = AppResult.Failure(AppError.Message("unused"))
    override suspend fun getStatus(target: SyncTarget) =
        AppResult.Failure(AppError.Message("unused"))
    override suspend fun cancelSync(target: SyncTarget) =
        AppResult.Failure(AppError.Message("unused"))
    override suspend fun getStatistics(target: SyncTarget) =
        AppResult.Failure(AppError.Message("unused"))
    override suspend fun listRecentRuns(target: SyncTarget, limit: Int) =
        AppResult.Success(emptyList<SyncRunSummary>())
}

private class VmFakeConnectivity(
    online: Boolean,
) : NetworkConnectivityObserver {
    private val flow = MutableStateFlow(online)
    override val isOnline: Flow<Boolean> = flow
    override fun current(): Boolean = flow.value
}
