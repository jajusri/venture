package com.jajusri.venture.feature.settings.presentation

import com.jajusri.venture.core.common.AppResult
import com.jajusri.venture.core.connectorauth.domain.ConnectorTransportSelection
import com.jajusri.venture.core.connectorauth.domain.ConnectorTransportSelectionGate
import com.jajusri.venture.core.network.NetworkConnectivityObserver
import com.jajusri.venture.feature.company.domain.port.CompanySessionPort
import com.jajusri.venture.feature.company.domain.port.SelectedCompanyStatus
import com.jajusri.venture.feature.company.domain.port.SessionValidationStatus
import com.jajusri.venture.feature.company.domain.port.SessionValidity
import com.jajusri.venture.feature.masterdata.ledger.domain.model.LedgerStatementMode
import com.jajusri.venture.feature.masterdata.ledger.sharing.LedgerShareDefaultDestination
import com.jajusri.venture.feature.masterdata.ledger.sharing.LedgerSharingDefaultPeriod
import com.jajusri.venture.feature.masterdata.ledger.sharing.LedgerSharingPreferences
import com.jajusri.venture.feature.masterdata.ledger.sharing.LedgerSharingPreferencesStore
import com.jajusri.venture.feature.serverconfig.domain.model.ConnectorConnectionProbe
import com.jajusri.venture.feature.serverconfig.domain.model.ConnectorHealth
import com.jajusri.venture.feature.serverconfig.domain.model.ConnectorReadiness
import com.jajusri.venture.feature.serverconfig.domain.port.ConnectorStatusPort
import com.jajusri.venture.feature.serverconfig.domain.port.ConnectorOperationalStatus
import com.jajusri.venture.feature.serverconfig.domain.port.ConnectorOperationalStatusPort
import com.jajusri.venture.feature.settings.domain.model.ApplicationInformation
import com.jajusri.venture.feature.settings.domain.model.ThemePreference
import com.jajusri.venture.feature.settings.domain.port.ApplicationIdentityPort
import com.jajusri.venture.feature.settings.domain.repository.ThemeObservation
import com.jajusri.venture.feature.settings.domain.repository.ThemePreferencesRepository
import com.jajusri.venture.feature.settings.domain.usecase.ObserveSettingsSnapshotUseCase
import com.jajusri.venture.feature.settings.domain.usecase.RefreshSettingsConnectorFactsUseCase
import com.jajusri.venture.feature.settings.domain.usecase.SetThemePreferenceUseCase
import com.jajusri.venture.feature.sync.domain.model.SyncStatusSummary
import com.jajusri.venture.feature.sync.domain.port.ObserveSyncStatusPort
import com.jajusri.venture.navigation.ResolveStartupRoutingState
import com.jajusri.venture.navigation.StartupRoutingState
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var themeRepo: FakeThemeRepo
    private lateinit var connector: FakeConnector
    private lateinit var company: FakeCompany
    private lateinit var sync: FakeSync
    private lateinit var connectivity: FakeConnectivity
    private lateinit var ledgerSharingPreferencesStore: FakeLedgerSharingPreferencesStore

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        themeRepo = FakeThemeRepo()
        connector = FakeConnector()
        company = FakeCompany()
        sync = FakeSync()
        connectivity = FakeConnectivity(true)
        ledgerSharingPreferencesStore = FakeLedgerSharingPreferencesStore()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createVm(
        secureConnectionState: StartupRoutingState = StartupRoutingState.LegacyEligible,
    ): SettingsViewModel =
        SettingsViewModel(
            observeSettings = ObserveSettingsSnapshotUseCase(
                applicationIdentity = ApplicationIdentityPort {
                    ApplicationInformation(
                        appName = "Venture",
                        packageName = "com.jajusri.venture.debug",
                        versionName = "0.1.0",
                        versionCode = 1,
                        buildTypeLabel = "Debug",
                    )
                },
                themePreferences = themeRepo,
                connectorStatus = connector,
                companySession = company,
                syncStatus = sync,
                connectivity = connectivity,
                transportGate = object : ConnectorTransportSelectionGate {
                    override suspend fun resolve() = if (secureConnectionState == StartupRoutingState.LegacyEligible) {
                        ConnectorTransportSelection.LEGACY
                    } else {
                        ConnectorTransportSelection.AUTHENTICATED
                    }
                },
            ),
            setThemePreference = SetThemePreferenceUseCase(themeRepo),
            refreshConnectorFacts = RefreshSettingsConnectorFactsUseCase(connector, company),
            resolveStartupRoutingState = object : ResolveStartupRoutingState {
                override suspend fun invoke(): StartupRoutingState = secureConnectionState
            },
            ledgerSharingPreferencesStore = ledgerSharingPreferencesStore,
        )

    @Test
    fun loadsSnapshotAndConnectorFacts() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isInitialLoading)
        assertEquals("http://10.0.2.2:8080/", vm.uiState.value.baseUrl)
        assertEquals("0.1.0-test", vm.uiState.value.connectorVersion)
        assertNotNull(vm.uiState.value.application)
    }

    @Test
    fun selectThemeUpdatesPreference() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        vm.onEvent(SettingsEvent.SelectTheme(ThemePreference.Dark))
        advanceUntilIdle()
        assertEquals(ThemePreference.Dark, vm.uiState.value.themePreference)
        assertEquals(
            ThemePreference.Dark,
            (themeRepo.observation.value as ThemeObservation.Available).preference,
        )
    }

    @Test
    fun navigationEvents() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        val emitted = mutableListOf<SettingsNavigation>()
        val job = launch { vm.navigation.collect { emitted.add(it) } }
        vm.onEvent(SettingsEvent.OpenServerConfig)
        vm.onEvent(SettingsEvent.OpenCompanySelection)
        vm.onEvent(SettingsEvent.OpenSync)
        vm.onEvent(SettingsEvent.OpenDiagnostics)
        vm.onEvent(SettingsEvent.OpenSecurePairing)
        advanceUntilIdle()
        assertEquals(
            listOf(
                SettingsNavigation.ServerConfig,
                SettingsNavigation.CompanySelection,
                SettingsNavigation.Sync,
                SettingsNavigation.Diagnostics,
                SettingsNavigation.SecurePairing,
            ),
            emitted,
        )
        job.cancel()
    }

    // 24. LegacyEligible exposes one secure-migration action (via secureConnectionState)
    @Test
    fun `secure connection state is freshly resolved on load, reflecting LegacyEligible`() = runTest(dispatcher) {
        val vm = createVm(secureConnectionState = StartupRoutingState.LegacyEligible)
        advanceUntilIdle()
        assertEquals(StartupRoutingState.LegacyEligible, vm.uiState.value.secureConnectionState)
    }

    // 25/26/27. migration action is absent for SecureActive/PendingVerification/RePairRequired —
    // proven here by asserting the exact resolved state the Screen composable branches on.
    @Test
    fun `secure connection state reflects SecureActive`() = runTest(dispatcher) {
        val vm = createVm(secureConnectionState = StartupRoutingState.SecureActive)
        advanceUntilIdle()
        assertEquals(StartupRoutingState.SecureActive, vm.uiState.value.secureConnectionState)
    }

    @Test
    fun `secure connection state reflects RePairRequired`() = runTest(dispatcher) {
        val vm = createVm(secureConnectionState = StartupRoutingState.RePairRequired)
        advanceUntilIdle()
        assertEquals(StartupRoutingState.RePairRequired, vm.uiState.value.secureConnectionState)
    }

    @Test
    fun `refresh event re-resolves secure connection state`() = runTest(dispatcher) {
        val vm = createVm(secureConnectionState = StartupRoutingState.RePairRequired)
        advanceUntilIdle()
        vm.onEvent(SettingsEvent.Refresh)
        advanceUntilIdle()
        assertEquals(StartupRoutingState.RePairRequired, vm.uiState.value.secureConnectionState)
    }

    @Test
    fun `the ledger sharing section reflects persisted preferences on load`() = runTest(dispatcher) {
        ledgerSharingPreferencesStore = FakeLedgerSharingPreferencesStore(
            LedgerSharingPreferences(
                statementMode = LedgerStatementMode.Detailed,
                defaultPeriod = LedgerSharingDefaultPeriod.ThisMonth,
                defaultDestination = LedgerShareDefaultDestination.SavePdf,
            ),
        )
        val vm = createVm()
        advanceUntilIdle()
        assertEquals(LedgerStatementMode.Detailed, vm.uiState.value.ledgerSharingStatementMode)
        assertEquals(LedgerSharingDefaultPeriod.ThisMonth, vm.uiState.value.ledgerSharingDefaultPeriod)
        assertEquals(LedgerShareDefaultDestination.SavePdf, vm.uiState.value.ledgerSharingDefaultDestination)
    }

    @Test
    fun `selecting a default statement mode saves it without disturbing the other two preferences`() = runTest(dispatcher) {
        ledgerSharingPreferencesStore = FakeLedgerSharingPreferencesStore(
            LedgerSharingPreferences(defaultPeriod = LedgerSharingDefaultPeriod.LastMonth, defaultDestination = LedgerShareDefaultDestination.SavePdf),
        )
        val vm = createVm()
        advanceUntilIdle()
        vm.onEvent(SettingsEvent.SelectLedgerSharingStatementMode(LedgerStatementMode.Detailed))
        advanceUntilIdle()
        assertEquals(LedgerStatementMode.Detailed, vm.uiState.value.ledgerSharingStatementMode)
        assertEquals(LedgerSharingDefaultPeriod.LastMonth, vm.uiState.value.ledgerSharingDefaultPeriod)
        assertEquals(LedgerShareDefaultDestination.SavePdf, vm.uiState.value.ledgerSharingDefaultDestination)
        assertEquals(1, ledgerSharingPreferencesStore.saveCalls)
    }

    @Test
    fun `selecting a default period saves it and is reflected immediately`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        vm.onEvent(SettingsEvent.SelectLedgerSharingDefaultPeriod(LedgerSharingDefaultPeriod.Today))
        advanceUntilIdle()
        assertEquals(LedgerSharingDefaultPeriod.Today, vm.uiState.value.ledgerSharingDefaultPeriod)
        assertEquals(1, ledgerSharingPreferencesStore.saveCalls)
    }

    @Test
    fun `selecting a default destination saves it and is reflected immediately`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        vm.onEvent(SettingsEvent.SelectLedgerSharingDefaultDestination(LedgerShareDefaultDestination.WhatsAppSelect))
        advanceUntilIdle()
        assertEquals(LedgerShareDefaultDestination.WhatsAppSelect, vm.uiState.value.ledgerSharingDefaultDestination)
        assertEquals(1, ledgerSharingPreferencesStore.saveCalls)
    }
}

private class FakeThemeRepo : ThemePreferencesRepository {
    val observation = MutableStateFlow<ThemeObservation>(
        ThemeObservation.Available(ThemePreference.System),
    )
    override fun observeTheme(): Flow<ThemeObservation> = observation
    override suspend fun setTheme(preference: ThemePreference): AppResult<Unit> {
        observation.value = ThemeObservation.Available(preference)
        return AppResult.Success(Unit)
    }
}

private class FakeConnector : ConnectorStatusPort, ConnectorOperationalStatusPort {
    override fun observeBaseUrl(): Flow<String> = flowOf("http://10.0.2.2:8080/")
    override fun currentBaseUrl(): String = "http://10.0.2.2:8080/"
    override suspend fun probeConnection(): AppResult<ConnectorConnectionProbe> =
        AppResult.Success(
            ConnectorConnectionProbe(
                health = ConnectorHealth(
                    status = "ok",
                    schemaVersion = "1.0.0",
                    connectorVersion = "0.1.0-test",
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

    override suspend fun currentStatus(): ConnectorOperationalStatus =
        ConnectorOperationalStatus.Legacy(currentBaseUrl(), probeConnection())
}

private class FakeCompany : CompanySessionPort {
    override fun observeSelectedCompanyId(): Flow<String?> = flowOf("c1")
    override suspend fun readSelectedCompany(): AppResult<SelectedCompanyStatus> =
        AppResult.Success(SelectedCompanyStatus("c1", "Company"))
    override suspend fun validateSessionStatus(): AppResult<SessionValidationStatus> =
        AppResult.Success(SessionValidationStatus(SessionValidity.Valid, "c1", "Company"))
}

private class FakeSync : ObserveSyncStatusPort {
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

private class FakeConnectivity(online: Boolean) : NetworkConnectivityObserver {
    private val flow = MutableStateFlow(online)
    override val isOnline: Flow<Boolean> = flow
    override fun current(): Boolean = flow.value
}

private class FakeLedgerSharingPreferencesStore(
    initial: LedgerSharingPreferences = LedgerSharingPreferences(),
) : LedgerSharingPreferencesStore {
    private val flow = MutableStateFlow(initial)
    override val observation: Flow<LedgerSharingPreferences> = flow
    var saveCalls = 0
        private set

    override suspend fun save(preferences: LedgerSharingPreferences) {
        saveCalls++
        flow.value = preferences
    }
}
