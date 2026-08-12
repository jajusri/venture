package com.budcom.android.feature.dashboard.presentation

import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.connectorauth.domain.ConnectorTransportSelection
import com.budcom.android.core.connectorauth.domain.ConnectorTransportSelectionGate
import com.budcom.android.core.network.NetworkConnectivityObserver
import com.budcom.android.core.util.TimeProvider
import com.budcom.android.feature.company.domain.port.CompanySessionPort
import com.budcom.android.feature.company.domain.port.SelectedCompanyStatus
import com.budcom.android.feature.company.domain.port.SessionValidationStatus
import com.budcom.android.feature.company.domain.port.SessionValidity
import com.budcom.android.feature.company.domain.repository.CompanyRepository
import com.budcom.android.feature.company.domain.model.SessionSelectedCompany
import com.budcom.android.feature.company.domain.usecase.RestoreCompanySelectionUseCase
import com.budcom.android.feature.dashboard.domain.model.DashboardOperationalMode
import com.budcom.android.feature.dashboard.domain.model.DashboardSessionValidity
import com.budcom.android.feature.dashboard.domain.usecase.ObserveDashboardContextUseCase
import com.budcom.android.feature.dashboard.domain.usecase.ProbeConnectorConnectionUseCase
import com.budcom.android.feature.dashboard.domain.usecase.RefreshDashboardUseCase
import com.budcom.android.feature.dashboard.domain.usecase.ValidateDashboardSessionUseCase
import com.budcom.android.feature.serverconfig.domain.model.ConnectorConnectionProbe
import com.budcom.android.feature.serverconfig.domain.model.ConnectorHealth
import com.budcom.android.feature.serverconfig.domain.model.ConnectorReadiness
import com.budcom.android.feature.serverconfig.domain.port.ConnectorOperationalStatus
import com.budcom.android.feature.serverconfig.domain.port.ConnectorOperationalStatusPort
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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

    private fun createViewModel(
        restoreRepository: CompanyRepository = DashboardNoOpCompanyRepository,
    ): DashboardViewModel {
        val refresh = RefreshDashboardUseCase(
            operationalStatus = connector,
            companySession = company,
            restoreCompanySelection = RestoreCompanySelectionUseCase(restoreRepository),
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
                transportGate = object : ConnectorTransportSelectionGate {
                    override suspend fun resolve() = ConnectorTransportSelection.LEGACY
                },
            ),
            observeSyncStatus = syncStatus,
            connectivityObserver = connectivity,
        )
    }

    @Test
    fun `initial refresh reaches fully operational`() = runTest(dispatcher) {
        company.selectedIdFlow.value = "estimation"
        company.selectedCompanyFlow.value = SessionSelectedCompany("estimation", "ESTIMATION")
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
    fun `network loss immediately marks connector unavailable without a network probe`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertEquals(true, viewModel.uiState.value.connectorConnected)
        val probeCallsBeforeLoss = connector.probeCalls

        connectivity.online.value = false
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.connectorConnected)
        assertEquals(DashboardOperationalMode.Offline, viewModel.uiState.value.operationalMode)
        assertTrue(viewModel.uiState.value.connectorError is DashboardUiError.Offline)
        // Invalidation is a local, immediate state update — no network round-trip attempted.
        assertEquals(probeCallsBeforeLoss, connector.probeCalls)
    }

    @Test
    fun `network restoration triggers exactly one bounded refresh and returns to fully operational`() = runTest(dispatcher) {
        company.selectedIdFlow.value = "estimation"
        company.selectedCompanyFlow.value = SessionSelectedCompany("estimation", "ESTIMATION")
        company.validateResult = AppResult.Success(
            SessionValidationStatus(SessionValidity.Valid, "estimation", "ESTIMATION"),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertEquals(DashboardOperationalMode.FullyOperational, viewModel.uiState.value.operationalMode)

        connectivity.online.value = false
        advanceUntilIdle()
        val probeCallsAfterLoss = connector.probeCalls

        connectivity.online.value = true
        advanceUntilIdle()

        assertEquals(probeCallsAfterLoss + 1, connector.probeCalls)
        assertEquals(DashboardOperationalMode.FullyOperational, viewModel.uiState.value.operationalMode)
        assertEquals(true, viewModel.uiState.value.connectorConnected)
    }

    @Test
    fun `paired endpoint and selected company survive a loss-restore cycle`() = runTest(dispatcher) {
        company.selectedIdFlow.value = "estimation"
        company.selectedCompanyFlow.value = SessionSelectedCompany("estimation", "ESTIMATION")
        val viewModel = createViewModel()
        advanceUntilIdle()
        val baseUrlBefore = viewModel.uiState.value.baseUrl

        connectivity.online.value = false
        advanceUntilIdle()
        assertEquals("estimation", viewModel.uiState.value.selectedCompanyId)
        assertEquals(baseUrlBefore, viewModel.uiState.value.baseUrl)

        connectivity.online.value = true
        advanceUntilIdle()
        assertEquals("estimation", viewModel.uiState.value.selectedCompanyId)
        assertEquals(baseUrlBefore, viewModel.uiState.value.baseUrl)
    }

    @Test
    fun `dashboard context re-emissions unrelated to connectivity do not trigger extra refreshes`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        connectivity.online.value = false
        advanceUntilIdle()
        connectivity.online.value = true
        advanceUntilIdle()
        val probeCallsAfterRestore = connector.probeCalls

        // Company selection changes re-emit observeDashboardContext() while isOnline stays
        // true — must not be misread as another online transition (duplicate reconnect).
        company.selectedCompanyFlow.value = SessionSelectedCompany("estimation", "ESTIMATION")
        advanceUntilIdle()

        assertEquals(probeCallsAfterRestore, connector.probeCalls)
    }

    @Test
    fun `repeated loss-restore cycles never leave stale offline state`() = runTest(dispatcher) {
        company.selectedIdFlow.value = "estimation"
        company.selectedCompanyFlow.value = SessionSelectedCompany("estimation", "ESTIMATION")
        company.validateResult = AppResult.Success(
            SessionValidationStatus(SessionValidity.Valid, "estimation", "ESTIMATION"),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        repeat(3) {
            connectivity.online.value = false
            advanceUntilIdle()
            assertEquals(DashboardOperationalMode.Offline, viewModel.uiState.value.operationalMode)
            assertEquals(false, viewModel.uiState.value.connectorConnected)

            connectivity.online.value = true
            advanceUntilIdle()
            assertEquals(DashboardOperationalMode.FullyOperational, viewModel.uiState.value.operationalMode)
            assertEquals(true, viewModel.uiState.value.connectorConnected)
        }
    }

    @Test
    fun `manual refresh still works after an automatic reconnect`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        connectivity.online.value = false
        advanceUntilIdle()
        connectivity.online.value = true
        advanceUntilIdle()
        val probeCallsAfterAutoReconnect = connector.probeCalls

        viewModel.onEvent(DashboardEvent.Refresh)
        advanceUntilIdle()

        assertEquals(probeCallsAfterAutoReconnect + 1, connector.probeCalls)
        assertEquals(true, viewModel.uiState.value.connectorConnected)
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

    @Test
    fun `switching in both directions updates company id and name together without stale name`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        company.selectedCompanyFlow.value = SessionSelectedCompany("estimation", "ESTIMATION")
        advanceUntilIdle()
        assertEquals("estimation", viewModel.uiState.value.selectedCompanyId)
        assertEquals("ESTIMATION", viewModel.uiState.value.selectedCompanyName)

        company.selectedCompanyFlow.value = SessionSelectedCompany("budcom-test-01", "Budcom-Test-01")
        advanceUntilIdle()
        assertEquals("budcom-test-01", viewModel.uiState.value.selectedCompanyId)
        assertEquals("Budcom-Test-01", viewModel.uiState.value.selectedCompanyName)
    }

    @Test
    fun `legacy migration shows no stale name and runs only once`() = runTest(dispatcher) {
        company.selectedCompanyFlow.value = SessionSelectedCompany("estimation", "ESTIMATION")
        val repository = DashboardMigrationRepository(company)
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        company.selectedIdFlow.value = "budcom-test-01"
        company.selectedCompanyFlow.value = null
        advanceUntilIdle()
        assertEquals(null, viewModel.uiState.value.selectedCompanyName)

        viewModel.onEvent(DashboardEvent.Refresh)
        advanceUntilIdle()
        assertEquals("budcom-test-01", viewModel.uiState.value.selectedCompanyId)
        assertEquals("Budcom-Test-01", viewModel.uiState.value.selectedCompanyName)
        viewModel.onEvent(DashboardEvent.Refresh)
        advanceUntilIdle()
        assertEquals(1, repository.restoreCalls)
    }

    @Test
    fun `already migrated selection remains unchanged without migration`() = runTest(dispatcher) {
        company.selectedIdFlow.value = "estimation"
        company.selectedCompanyFlow.value = SessionSelectedCompany("estimation", "ESTIMATION")
        val repository = DashboardMigrationRepository(company)
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        assertEquals("ESTIMATION", viewModel.uiState.value.selectedCompanyName)
        assertEquals(0, repository.restoreCalls)
    }

    // --- Foreground reconciliation backstop (P1 iQOO hardening) ---
    //
    // Physical testing showed the OS network-loss callback fires reliably only ~1 in 10 times,
    // so these tests deliberately leave `connectivity.online` untouched (simulating a callback
    // that never fires) and instead flip the Connector *health probe* result directly — the only
    // way reconcileWhileActive()'s fresh re-probe can be proven to be the thing that caught the
    // change, not the existing wentOffline/cameBackOnline network-flow path (already covered by
    // the tests above, which remain unchanged and must keep passing).

    @Test
    fun `foreground reconciliation discovers connector unreachable despite missed network-loss callback`() = runTest(dispatcher) {
        company.selectedIdFlow.value = "estimation"
        company.selectedCompanyFlow.value = SessionSelectedCompany("estimation", "ESTIMATION")
        company.validateResult = AppResult.Success(
            SessionValidationStatus(SessionValidity.Valid, "estimation", "ESTIMATION"),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertEquals(DashboardOperationalMode.FullyOperational, viewModel.uiState.value.operationalMode)

        // Connector becomes unreachable, but isOnline is deliberately left untouched — the OS
        // callback never told the app anything changed.
        connector.probe = AppResult.Failure(AppError.Offline())
        assertEquals(
            "state must still be stale before reconciliation runs",
            DashboardOperationalMode.FullyOperational,
            viewModel.uiState.value.operationalMode,
        )

        val job = launch { viewModel.reconcileWhileActive() }
        runCurrent()

        // mapSnapshotToUiState()'s pre-existing keepStaleHealth debounce (anti-flicker for a
        // single transient probe failure right after a healthy state) means the *first* failed
        // reconciliation tick updates connectorConnected but not yet operationalMode — this is
        // unrelated pre-existing behavior shared by every refresh() caller, not something this
        // backstop should override. A second consecutive failed tick clears it, which is still a
        // short bounded interval (2x FOREGROUND_RECONCILE_INTERVAL_MS at worst), never
        // indefinite.
        assertEquals(false, viewModel.uiState.value.connectorConnected)
        assertEquals(DashboardOperationalMode.PartiallyAvailable, viewModel.uiState.value.operationalMode)

        advanceTimeBy(DashboardViewModel.FOREGROUND_RECONCILE_INTERVAL_MS)
        runCurrent()

        assertEquals(DashboardOperationalMode.ConnectorUnavailable, viewModel.uiState.value.operationalMode)
        assertEquals(false, viewModel.uiState.value.connectorConnected)

        job.cancel()
        advanceUntilIdle()
    }

    @Test
    fun `foreground reconciliation restores fully operational despite missed network-restore callback`() = runTest(dispatcher) {
        company.selectedIdFlow.value = "estimation"
        company.selectedCompanyFlow.value = SessionSelectedCompany("estimation", "ESTIMATION")
        company.validateResult = AppResult.Success(
            SessionValidationStatus(SessionValidity.Valid, "estimation", "ESTIMATION"),
        )
        connector.probe = AppResult.Failure(AppError.Offline())
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertEquals(DashboardOperationalMode.ConnectorUnavailable, viewModel.uiState.value.operationalMode)

        // Connector recovers, but isOnline is deliberately left untouched throughout — the OS
        // never signalled a restore either.
        connector.probe = AppResult.Success(sampleProbe(ready = true))

        val job = launch { viewModel.reconcileWhileActive() }
        runCurrent()

        assertEquals(DashboardOperationalMode.FullyOperational, viewModel.uiState.value.operationalMode)
        assertEquals(true, viewModel.uiState.value.connectorConnected)

        job.cancel()
        advanceUntilIdle()
    }

    @Test
    fun `pairing and selected company survive reconciliation discovering unreachability`() = runTest(dispatcher) {
        // Room-backed local data (vouchers/ledgers) is out of scope for this assertion: neither
        // DashboardViewModel nor any use case it calls holds a Room DAO reference at all, so
        // reconciliation is architecturally incapable of touching it — see the task's final
        // report for the inspection trail backing that claim.
        company.selectedIdFlow.value = "estimation"
        company.selectedCompanyFlow.value = SessionSelectedCompany("estimation", "ESTIMATION")
        val viewModel = createViewModel()
        advanceUntilIdle()
        val baseUrlBefore = viewModel.uiState.value.baseUrl

        connector.probe = AppResult.Failure(AppError.Offline())
        val job = launch { viewModel.reconcileWhileActive() }
        runCurrent()
        // See the sibling "discovers connector unreachable" test for why a second tick is
        // needed: mapSnapshotToUiState()'s pre-existing single-blip anti-flicker debounce.
        advanceTimeBy(DashboardViewModel.FOREGROUND_RECONCILE_INTERVAL_MS)
        runCurrent()

        assertEquals(DashboardOperationalMode.ConnectorUnavailable, viewModel.uiState.value.operationalMode)
        assertEquals(baseUrlBefore, viewModel.uiState.value.baseUrl)
        assertEquals("estimation", viewModel.uiState.value.selectedCompanyId)
        assertEquals("ESTIMATION", viewModel.uiState.value.selectedCompanyName)

        job.cancel()
        advanceUntilIdle()
    }

    // --- Known-network-loss short-circuit (P1 immediate-offline hardening) ---
    //
    // These use `connectivity.currentOverride` (not `connectivity.online`) so the isOnline Flow
    // driving the pre-existing wentOffline collector in init{} stays untouched — proving the new
    // reconcileOnce() gate itself is what reacts, not the older Flow-driven path, exactly
    // mirroring the production gap between a missed callback and a fresh current() query.

    @Test
    fun `foreground reconciliation with known network loss immediately clears stale fully operational without a Connector probe`() = runTest(dispatcher) {
        company.selectedIdFlow.value = "estimation"
        company.selectedCompanyFlow.value = SessionSelectedCompany("estimation", "ESTIMATION")
        company.validateResult = AppResult.Success(
            SessionValidationStatus(SessionValidity.Valid, "estimation", "ESTIMATION"),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertEquals(DashboardOperationalMode.FullyOperational, viewModel.uiState.value.operationalMode)
        val baseUrlBefore = viewModel.uiState.value.baseUrl
        val probeCallsBefore = connector.probeCalls

        // The OS callback never fires (isOnline Flow stays stuck at true) but a fresh current()
        // query — exactly what reconciliation performs — already knows there is no usable
        // network.
        connectivity.currentOverride = false

        val job = launch { viewModel.reconcileWhileActive() }
        runCurrent()

        assertEquals(DashboardOperationalMode.Offline, viewModel.uiState.value.operationalMode)
        assertEquals(false, viewModel.uiState.value.connectorConnected)
        assertEquals(false, viewModel.uiState.value.isOnline)
        // No Connector HTTP probe was spent on a probe that cannot possibly succeed.
        assertEquals(probeCallsBefore, connector.probeCalls)
        // Pairing (endpoint) and selected company survive.
        assertEquals(baseUrlBefore, viewModel.uiState.value.baseUrl)
        assertEquals("estimation", viewModel.uiState.value.selectedCompanyId)
        assertEquals("ESTIMATION", viewModel.uiState.value.selectedCompanyName)

        job.cancel()
        advanceUntilIdle()
    }

    @Test
    fun `foreground reconciliation with a usable network still runs the existing refresh path`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val probeCallsBefore = connector.probeCalls

        val job = launch { viewModel.reconcileWhileActive() }
        runCurrent()

        assertEquals(probeCallsBefore + 1, connector.probeCalls)

        job.cancel()
        advanceUntilIdle()
    }

    @Test
    fun `repeated resumes do not create duplicate concurrent probes`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        connector.delayMillis = 5_000
        connector.probeCalls = 0

        // Simulates rapid resume/pause/resume (e.g. quick app-switcher flicks), each starting a
        // fresh reconcileWhileActive() call while the previous probe is still in flight.
        val job1 = launch { viewModel.reconcileWhileActive() }
        val job2 = launch { viewModel.reconcileWhileActive() }
        val job3 = launch { viewModel.reconcileWhileActive() }
        runCurrent()

        assertEquals(1, connector.probeCalls)

        job1.cancel()
        job2.cancel()
        job3.cancel()
    }

    @Test
    fun `repeating foreground reconciliation stops immediately when lifecycle leaves active state`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        connector.probeCalls = 0

        // Standing in for repeatOnLifecycle(RESUMED): the coroutine hosting reconcileWhileActive
        // is what a real screen-exit cancels.
        val job = launch { viewModel.reconcileWhileActive() }
        runCurrent()
        assertEquals(1, connector.probeCalls)

        advanceTimeBy(DashboardViewModel.FOREGROUND_RECONCILE_INTERVAL_MS)
        runCurrent()
        assertEquals(2, connector.probeCalls)

        job.cancel()
        advanceUntilIdle()

        assertEquals(2, connector.probeCalls)
    }

    @Test
    fun `foreground reconciliation ticks stay bounded to one call per interval across repeated cycles`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        connector.probeCalls = 0

        val job = launch { viewModel.reconcileWhileActive() }
        runCurrent()
        repeat(4) {
            advanceTimeBy(DashboardViewModel.FOREGROUND_RECONCILE_INTERVAL_MS)
            runCurrent()
        }

        // 1 immediate tick + 4 interval ticks — never more than one probe per interval, no
        // reconnect-loop pile-up.
        assertEquals(5, connector.probeCalls)

        job.cancel()
        advanceUntilIdle()
    }

    @Test
    fun `manual refresh still works while reconciliation loop is active and after it stops`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val job = launch { viewModel.reconcileWhileActive() }
        runCurrent()
        val callsAfterFirstTick = connector.probeCalls

        viewModel.onEvent(DashboardEvent.Refresh)
        runCurrent()
        assertTrue(connector.probeCalls >= callsAfterFirstTick)

        job.cancel()
        advanceUntilIdle()
        val callsAfterCancel = connector.probeCalls

        viewModel.onEvent(DashboardEvent.Refresh)
        advanceUntilIdle()

        assertEquals(callsAfterCancel + 1, connector.probeCalls)
        assertEquals(true, viewModel.uiState.value.connectorConnected)
    }
}

private class FakeConnectorStatus : ConnectorStatusPort, ConnectorOperationalStatusPort {
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

    // TD-016: this ViewModel test exercises RefreshDashboardUseCase through the LEGACY branch
    // of ConnectorOperationalStatus only - AUTHENTICATED-branch behavior is covered directly in
    // RefreshDashboardUseCaseTest, which does not need a ViewModel/Hilt harness.
    override suspend fun currentStatus(): ConnectorOperationalStatus {
        probeCalls++
        if (delayMillis > 0) delay(delayMillis)
        return ConnectorOperationalStatus.Legacy(baseUrl.value, probe)
    }
}

private class FakeCompanySession : CompanySessionPort {
    val selectedCompanyFlow = MutableStateFlow<SessionSelectedCompany?>(null)
    val selectedIdFlow = MutableStateFlow<String?>(null)
    var selectedResult: AppResult<SelectedCompanyStatus> =
        AppResult.Success(SelectedCompanyStatus(null, null))
    var validateResult: AppResult<SessionValidationStatus> =
        AppResult.Success(SessionValidationStatus(SessionValidity.NoCompany, null, null))

    override fun observeSelectedCompany(): Flow<SessionSelectedCompany?> = selectedCompanyFlow
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

private object DashboardNoOpCompanyRepository : CompanyRepository {
    override fun observeSelectedCompanyId(): Flow<String?> = flowOf(null)
    override suspend fun loadCompanies() = error("unused")
    override suspend fun refreshCompanies() = error("unused")
    override suspend fun getSession() = error("unused")
    override suspend fun restoreSelection() = AppResult.Success(null)
    override suspend fun selectCompany(companyId: String) = error("unused")
    override suspend fun validateSession() = error("unused")
    override suspend fun clearSelection() = error("unused")
}

private class DashboardMigrationRepository(
    private val company: FakeCompanySession,
) : CompanyRepository {
    var restoreCalls = 0
    override fun observeSelectedCompany(): Flow<SessionSelectedCompany?> = company.selectedCompanyFlow
    override fun observeSelectedCompanyId(): Flow<String?> = company.selectedIdFlow
    override suspend fun restoreSelection(): AppResult<com.budcom.android.feature.company.domain.model.SessionValidationOutcome?> {
        restoreCalls++
        val id = company.selectedIdFlow.value ?: return AppResult.Success(null)
        val name = if (id == "estimation") "ESTIMATION" else "Budcom-Test-01"
        company.selectedCompanyFlow.value = SessionSelectedCompany(id, name)
        return AppResult.Success(null)
    }
    override suspend fun loadCompanies() = error("unused")
    override suspend fun refreshCompanies() = error("unused")
    override suspend fun getSession() = error("unused")
    override suspend fun selectCompany(companyId: String) = error("unused")
    override suspend fun validateSession() = error("unused")
    override suspend fun clearSelection() = error("unused")
}

private class FakeConnectivity(initiallyOnline: Boolean) : NetworkConnectivityObserver {
    val online = MutableStateFlow(initiallyOnline)
    override val isOnline: Flow<Boolean> = online

    /**
     * Lets a test simulate the production gap this fake otherwise can't: real
     * [NetworkConnectivityObserver.current] is a fresh synchronous OS query, independent of
     * whether the callback-driven [isOnline] Flow ever emitted (a missed/delayed OS callback).
     * `null` (the default) keeps every existing test's behavior unchanged — [current] simply
     * mirrors [online]. Setting this lets a test hold [online] fixed (simulating "the callback
     * never fired") while [current] reports the true fresh value on its own.
     */
    var currentOverride: Boolean? = null
    override fun current(): Boolean = currentOverride ?: online.value
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
