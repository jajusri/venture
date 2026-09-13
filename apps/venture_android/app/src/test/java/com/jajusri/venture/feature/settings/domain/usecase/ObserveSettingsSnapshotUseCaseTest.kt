package com.jajusri.venture.feature.settings.domain.usecase

import com.jajusri.venture.core.common.AppResult
import com.jajusri.venture.core.connectorauth.domain.ConnectorTransportSelection
import com.jajusri.venture.core.connectorauth.domain.ConnectorTransportSelectionGate
import com.jajusri.venture.core.network.NetworkConnectivityObserver
import com.jajusri.venture.feature.company.domain.port.CompanySessionPort
import com.jajusri.venture.feature.company.domain.port.SelectedCompanyStatus
import com.jajusri.venture.feature.company.domain.port.SessionValidationStatus
import com.jajusri.venture.feature.company.domain.port.SessionValidity
import com.jajusri.venture.feature.serverconfig.domain.model.ConnectorConnectionProbe
import com.jajusri.venture.feature.serverconfig.domain.port.ConnectorStatusPort
import com.jajusri.venture.feature.settings.domain.model.ApplicationInformation
import com.jajusri.venture.feature.settings.domain.model.ThemePreference
import com.jajusri.venture.feature.settings.domain.port.ApplicationIdentityPort
import com.jajusri.venture.feature.settings.domain.repository.ThemeObservation
import com.jajusri.venture.feature.settings.domain.repository.ThemePreferencesRepository
import com.jajusri.venture.feature.sync.domain.model.SyncStatusSummary
import com.jajusri.venture.feature.sync.domain.port.ObserveSyncStatusPort
import app.cash.turbine.test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObserveSettingsSnapshotUseCaseTest {
    @Test
    fun emitsConfirmedSnapshotWithoutInventingConnectorVersion() = runTest {
        val useCase = ObserveSettingsSnapshotUseCase(
            applicationIdentity = ApplicationIdentityPort {
                ApplicationInformation("Venture", "com.jajusri.venture", "0.1.0", 1, "Debug")
            },
            themePreferences = object : ThemePreferencesRepository {
                override fun observeTheme() =
                    flowOf(ThemeObservation.Available(ThemePreference.Light))
                override suspend fun setTheme(preference: ThemePreference) = AppResult.Success(Unit)
            },
            connectorStatus = object : ConnectorStatusPort {
                override fun observeBaseUrl() = flowOf("http://example/")
                override fun currentBaseUrl() = "http://example/"
                override suspend fun probeConnection(): AppResult<ConnectorConnectionProbe> =
                    error("must not probe")
            },
            companySession = object : CompanySessionPort {
                override fun observeSelectedCompanyId() = flowOf("c1")
                override suspend fun readSelectedCompany() =
                    AppResult.Success(SelectedCompanyStatus("c1", "Co"))
                override suspend fun validateSessionStatus() =
                    AppResult.Success(SessionValidationStatus(SessionValidity.Valid, "c1", "Co"))
            },
            syncStatus = object : ObserveSyncStatusPort {
                override val summary: StateFlow<SyncStatusSummary> = MutableStateFlow(
                    SyncStatusSummary(
                        companyId = "c1",
                        isAnySyncActive = false,
                        activeTarget = null,
                        activeStatus = null,
                        latestSuccessfulAt = "t1",
                        latestFailedMessage = null,
                        targets = emptyList(),
                        lastUpdatedEpochMillis = 1L,
                    ),
                )
            },
            connectivity = object : NetworkConnectivityObserver {
                override val isOnline: Flow<Boolean> = flowOf(true)
                override fun current(): Boolean = true
            },
            transportGate = object : ConnectorTransportSelectionGate {
                override suspend fun resolve() = ConnectorTransportSelection.LEGACY
            },
        )
        useCase().test {
            val snapshot = awaitItem()
            assertEquals(ThemePreference.Light, snapshot.themePreference)
            assertEquals("http://example/", snapshot.baseUrl)
            assertEquals("c1", snapshot.companyId)
            assertTrue(snapshot.syncStatusLabel.contains("t1"))
            assertEquals(null, snapshot.connectorVersion)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `authenticated snapshot never exposes a stale legacy endpoint`() = runTest {
        val staleLegacyUrl = "http://10.0.2.2:8080/"
        val useCase = ObserveSettingsSnapshotUseCase(
            applicationIdentity = ApplicationIdentityPort {
                ApplicationInformation("Venture", "com.jajusri.venture", "0.1.0", 1, "Release")
            },
            themePreferences = object : ThemePreferencesRepository {
                override fun observeTheme() = flowOf(ThemeObservation.Available(ThemePreference.System))
                override suspend fun setTheme(preference: ThemePreference) = AppResult.Success(Unit)
            },
            connectorStatus = object : ConnectorStatusPort {
                override fun observeBaseUrl() = flowOf(staleLegacyUrl)
                override fun currentBaseUrl() = staleLegacyUrl
                override suspend fun probeConnection(): AppResult<ConnectorConnectionProbe> = error("must not probe")
            },
            companySession = object : CompanySessionPort {
                override fun observeSelectedCompanyId() = flowOf("c1")
                override suspend fun readSelectedCompany() =
                    AppResult.Success(SelectedCompanyStatus("c1", "ESTIMATION"))
                override suspend fun validateSessionStatus() =
                    AppResult.Success(SessionValidationStatus(SessionValidity.Valid, "c1", "ESTIMATION"))
            },
            syncStatus = object : ObserveSyncStatusPort {
                override val summary: StateFlow<SyncStatusSummary> = MutableStateFlow(
                    SyncStatusSummary(
                        companyId = "c1",
                        isAnySyncActive = false,
                        activeTarget = null,
                        activeStatus = null,
                        latestSuccessfulAt = null,
                        latestFailedMessage = null,
                        targets = emptyList(),
                        lastUpdatedEpochMillis = 1L,
                    ),
                )
            },
            connectivity = object : NetworkConnectivityObserver {
                override val isOnline: Flow<Boolean> = flowOf(true)
                override fun current(): Boolean = true
            },
            transportGate = object : ConnectorTransportSelectionGate {
                override suspend fun resolve() = ConnectorTransportSelection.AUTHENTICATED
            },
        )

        useCase().test {
            val snapshot = awaitItem()
            assertEquals("Secure paired Connector", snapshot.baseUrl)
            assertTrue(snapshot.baseUrl != staleLegacyUrl)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
