package com.budcom.android.feature.settings.presentation

import com.budcom.android.core.common.AppResult
import com.budcom.android.core.network.NetworkConnectivityObserver
import com.budcom.android.feature.company.domain.port.CompanySessionPort
import com.budcom.android.feature.company.domain.port.SelectedCompanyStatus
import com.budcom.android.feature.company.domain.port.SessionValidationStatus
import com.budcom.android.feature.company.domain.port.SessionValidity
import com.budcom.android.feature.serverconfig.domain.model.ConnectorConnectionProbe
import com.budcom.android.feature.serverconfig.domain.model.ConnectorHealth
import com.budcom.android.feature.serverconfig.domain.model.ConnectorReadiness
import com.budcom.android.feature.serverconfig.domain.port.ConnectorStatusPort
import com.budcom.android.feature.settings.domain.model.ApplicationInformation
import com.budcom.android.feature.settings.domain.model.ThemePreference
import com.budcom.android.feature.settings.domain.port.ApplicationIdentityPort
import com.budcom.android.feature.settings.domain.repository.ThemeObservation
import com.budcom.android.feature.settings.domain.repository.ThemePreferencesRepository
import com.budcom.android.feature.settings.domain.usecase.ObserveSettingsSnapshotUseCase
import com.budcom.android.feature.settings.domain.usecase.RefreshSettingsConnectorFactsUseCase
import com.budcom.android.feature.settings.domain.usecase.SetThemePreferenceUseCase
import com.budcom.android.feature.sync.domain.model.SyncStatusSummary
import com.budcom.android.feature.sync.domain.port.ObserveSyncStatusPort
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

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        themeRepo = FakeThemeRepo()
        connector = FakeConnector()
        company = FakeCompany()
        sync = FakeSync()
        connectivity = FakeConnectivity(true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createVm(): SettingsViewModel =
        SettingsViewModel(
            observeSettings = ObserveSettingsSnapshotUseCase(
                applicationIdentity = ApplicationIdentityPort {
                    ApplicationInformation(
                        appName = "BudCom",
                        packageName = "com.budcom.android.debug",
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
            ),
            setThemePreference = SetThemePreferenceUseCase(themeRepo),
            refreshConnectorFacts = RefreshSettingsConnectorFactsUseCase(connector, company),
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
        advanceUntilIdle()
        assertEquals(
            listOf(
                SettingsNavigation.ServerConfig,
                SettingsNavigation.CompanySelection,
                SettingsNavigation.Sync,
                SettingsNavigation.Diagnostics,
            ),
            emitted,
        )
        job.cancel()
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

private class FakeConnector : ConnectorStatusPort {
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
