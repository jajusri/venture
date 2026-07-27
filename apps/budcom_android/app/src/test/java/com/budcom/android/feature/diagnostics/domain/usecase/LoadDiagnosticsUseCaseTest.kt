package com.budcom.android.feature.diagnostics.domain.usecase

import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.util.TimeProvider
import com.budcom.android.feature.company.domain.port.CompanySessionPort
import com.budcom.android.feature.company.domain.port.SelectedCompanyStatus
import com.budcom.android.feature.company.domain.port.SessionValidationStatus
import com.budcom.android.feature.company.domain.port.SessionValidity
import com.budcom.android.feature.diagnostics.domain.model.ConnectionDiagnostics
import com.budcom.android.feature.diagnostics.domain.port.ConnectionDiagnosticsPort
import com.budcom.android.feature.serverconfig.domain.model.ConnectorConnectionProbe
import com.budcom.android.feature.serverconfig.domain.model.ConnectorHealth
import com.budcom.android.feature.serverconfig.domain.model.ConnectorReadiness
import com.budcom.android.feature.serverconfig.domain.port.ConnectorStatusPort
import com.budcom.android.feature.sync.domain.model.SyncRunSummary
import com.budcom.android.feature.sync.domain.model.SyncStatusSummary
import com.budcom.android.feature.sync.domain.model.SyncTarget
import com.budcom.android.feature.sync.domain.model.SyncTargetSnapshot
import com.budcom.android.feature.sync.domain.port.ObserveSyncStatusPort
import com.budcom.android.feature.sync.domain.repository.SyncRepository
import com.budcom.android.feature.sync.domain.usecase.RefreshSyncOverviewUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadDiagnosticsUseCaseTest {

    @Test
    fun aggregatesConfirmedSectionsWithoutInventingHealth() = runTest {
        val useCase = LoadDiagnosticsUseCase(
            connectorStatus = FakeConnectorStatus(probeSuccess = true),
            companySession = FakeCompanySession(),
            connectionDiagnostics = FakeConnectionPort(success = true),
            syncStatus = FakeSyncStatus(),
            refreshSyncOverview = RefreshSyncOverviewUseCase(FakeSyncRepository(), FakeCompanySession()),
            timeProvider = TimeProvider { 99L },
        )
        val snapshot = useCase(refreshSync = false)
        assertEquals(99L, snapshot.loadedAtEpochMillis)
        assertEquals("http://10.0.2.2:8080/", snapshot.baseUrl)
        assertNotNull(snapshot.health)
        assertNotNull(snapshot.readiness)
        assertNotNull(snapshot.connection)
        assertEquals(SessionValidity.Valid, snapshot.company.sessionValidity)
        assertTrue(snapshot.searchAvailabilityNote.contains("no Connector search-availability"))
        assertTrue(snapshot.voucherNote.contains("not available"))
        assertNull(snapshot.healthError)
    }

    @Test
    fun preservesPartialFailureWhenHealthFails() = runTest {
        val useCase = LoadDiagnosticsUseCase(
            connectorStatus = FakeConnectorStatus(probeSuccess = false),
            companySession = FakeCompanySession(),
            connectionDiagnostics = FakeConnectionPort(success = true),
            syncStatus = FakeSyncStatus(),
            refreshSyncOverview = RefreshSyncOverviewUseCase(FakeSyncRepository(), FakeCompanySession()),
            timeProvider = TimeProvider { 1L },
        )
        val snapshot = useCase(refreshSync = false)
        assertNull(snapshot.health)
        assertNull(snapshot.readiness)
        assertNotNull(snapshot.healthError)
        assertNotNull(snapshot.connection)
        assertEquals("connected", snapshot.connection!!.state)
    }
}

private class FakeConnectorStatus(
    private val probeSuccess: Boolean,
) : ConnectorStatusPort {
    override fun observeBaseUrl(): Flow<String> = flowOf("http://10.0.2.2:8080/")
    override fun currentBaseUrl(): String = "http://10.0.2.2:8080/"
    override suspend fun probeConnection(): AppResult<ConnectorConnectionProbe> =
        if (probeSuccess) {
            AppResult.Success(
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
                        startupCorrelationId = "s1",
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
        } else {
            AppResult.Failure(AppError.Timeout())
        }
}

private class FakeCompanySession : CompanySessionPort {
    override fun observeSelectedCompanyId(): Flow<String?> = flowOf("c1")
    override suspend fun readSelectedCompany(): AppResult<SelectedCompanyStatus> =
        AppResult.Success(SelectedCompanyStatus("c1", "Company One"))
    override suspend fun validateSessionStatus(): AppResult<SessionValidationStatus> =
        AppResult.Success(
            SessionValidationStatus(
                validity = SessionValidity.Valid,
                companyId = "c1",
                companyName = "Company One",
            ),
        )
}

private class FakeConnectionPort(
    private val success: Boolean,
) : ConnectionDiagnosticsPort {
    override suspend fun loadConnectionDiagnostics(): AppResult<ConnectionDiagnostics> =
        if (success) {
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
                    averageLatencyMs = 1.0,
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
        } else {
            AppResult.Failure(AppError.Offline())
        }
}

private class FakeSyncStatus : ObserveSyncStatusPort {
    override val summary: StateFlow<SyncStatusSummary> = MutableStateFlow(
        SyncStatusSummary(
            companyId = "c1",
            isAnySyncActive = false,
            activeTarget = null,
            activeStatus = null,
            latestSuccessfulAt = "2026-07-27T00:00:00.000Z",
            latestFailedMessage = null,
            targets = listOf(
                SyncTargetSnapshot(target = SyncTarget.Ledgers, available = true),
                SyncTargetSnapshot(target = SyncTarget.StockItems, available = true),
            ),
            lastUpdatedEpochMillis = 10L,
        ),
    )
}

private class FakeSyncRepository : SyncRepository {
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
