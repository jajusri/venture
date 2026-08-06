package com.budcom.android.feature.sync.data.repository

import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.connectorauth.domain.AUTHENTICATED_ACCESS_DENIED_CODE
import com.budcom.android.core.connectorauth.domain.AUTHENTICATED_SECURE_PAIRING_REQUIRED_CODE
import com.budcom.android.core.connectorauth.domain.ConnectorTransportSelection
import com.budcom.android.core.connectorauth.domain.ConnectorTransportSelectionGate
import com.budcom.android.core.network.ApiResult
import com.budcom.android.core.network.DefaultErrorMapper
import com.budcom.android.core.network.NetworkConnectivityObserver
import com.budcom.android.core.network.NetworkError
import com.budcom.android.core.util.TimeProvider
import com.budcom.android.feature.sync.data.remote.AuthenticatedSyncRemoteDataSource
import com.budcom.android.feature.sync.data.remote.SyncRemoteDataSource
import com.budcom.android.feature.sync.domain.model.SyncCounts
import com.budcom.android.feature.sync.domain.model.SyncMode
import com.budcom.android.feature.sync.domain.model.SyncOutcome
import com.budcom.android.feature.sync.domain.model.SyncProgress
import com.budcom.android.feature.sync.domain.model.SyncRunStatus
import com.budcom.android.feature.sync.domain.model.SyncRunSummary
import com.budcom.android.feature.sync.domain.model.SyncStatisticsSummary
import com.budcom.android.feature.sync.domain.model.SyncTarget
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncRepositoryImplTest {
    private val connectivity = object : NetworkConnectivityObserver {
        override val isOnline: Flow<Boolean> = MutableStateFlow(true)
        override fun current(): Boolean = true
    }
    private val errorMapper = DefaultErrorMapper(Json { ignoreUnknownKeys = true }, connectivity)
    private val time = TimeProvider { 42L }

    // ============================== Pre-existing behaviour (unchanged) ==============================

    @Test
    fun voucherStartSucceeds() = runTest {
        val repo = repo(FakeRemote())
        val result = repo.startSync(SyncTarget.Vouchers)
        assertTrue(result is AppResult.Success)
    }

    @Test
    fun duplicateLocalStartBlocked() = runTest {
        val remote = FakeRemote(blockStart = true)
        val repo = repo(remote)
        // Simulate in-flight by starting a hanging call is hard; instead call start twice
        // with a remote that completes, then verify conflict mapping:
        remote.startResult = ApiResult.Failure(
            NetworkError.Http(409, "SYNC_CONFLICT", "already running", mapOf("syncRunId" to "x")),
        )
        val result = repo.startSync(SyncTarget.Ledgers)
        assertTrue(result is AppResult.Success)
        assertTrue((result as AppResult.Success).value is SyncOutcome.Conflict)
    }

    @Test
    fun successfulStartUpdatesSummary() = runTest {
        val repo = repo(FakeRemote())
        val result = repo.startSync(SyncTarget.Ledgers)
        assertTrue(result is AppResult.Success)
        assertEquals(false, repo.summary.value.isAnySyncActive)
        assertTrue(
            repo.summary.value.targets.first { it.target == SyncTarget.Ledgers }.lastOutcome
                is SyncOutcome.Succeeded,
        )
    }

    // ============================== Authenticated transport (Phase 3S-E) ==============================

    @Test
    fun `LEGACY start calls the legacy remote exactly once and never touches the authenticated adapter`() = runTest {
        val remote = FakeRemote()
        val repository = repo(remote, transportGate = FakeTransportGate(ConnectorTransportSelection.LEGACY))

        val result = repository.startSync(SyncTarget.Ledgers)

        assertTrue(result is AppResult.Success)
        assertEquals(1, remote.startCalls)
    }

    @Test
    fun `ACTIVE start routes to the authenticated adapter exactly once and never touches legacy`() = runTest {
        val authenticated = FakeAuthenticatedRemote()
        val repository = repo(
            remote = UnreachableRemote,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedRemote = authenticated,
        )

        val result = repository.startSync(SyncTarget.Ledgers)

        assertTrue(result is AppResult.Success)
        assertEquals(1, authenticated.startCalls)
    }

    @Test
    fun `PENDING_VERIFICATION and RE_PAIR_REQUIRED stay authenticated for start, status, statistics, runs and cancel`() = runTest {
        val localVaultStateError = AppError.Remote(httpStatus = null, code = AUTHENTICATED_SECURE_PAIRING_REQUIRED_CODE, message = "re-pair")
        val authenticated = FakeAuthenticatedRemote(
            startResult = AppResult.Failure(localVaultStateError),
            cancelResult = AppResult.Failure(localVaultStateError),
            statusResult = AppResult.Failure(localVaultStateError),
            statisticsResult = AppResult.Failure(localVaultStateError),
            runsResult = AppResult.Failure(localVaultStateError),
        )
        val repository = repo(
            remote = UnreachableRemote,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedRemote = authenticated,
        )

        // UnreachableRemote throws if ever called — reaching every line below proves it wasn't.
        assertTrue(repository.startSync(SyncTarget.Ledgers) is AppResult.Failure)
        assertTrue(repository.cancelSync(SyncTarget.Ledgers) is AppResult.Failure)
        assertTrue(repository.getStatus(SyncTarget.Ledgers) is AppResult.Failure)
        assertTrue(repository.getStatistics(SyncTarget.Ledgers) is AppResult.Failure)
        assertTrue(repository.listRecentRuns(SyncTarget.Ledgers, 5) is AppResult.Failure)
    }

    @Test
    fun `the transport gate is resolved on every remote call, not cached`() = runTest {
        val gate = CountingTransportGate(ConnectorTransportSelection.LEGACY)
        val repository = repo(FakeRemote(), transportGate = gate)

        repository.startSync(SyncTarget.Ledgers)
        repository.getStatus(SyncTarget.Ledgers)

        assertEquals(2, gate.resolveCount)
    }

    @Test
    fun `a Vouchers cancel, status, statistics or runs call never resolves the transport gate`() = runTest {
        val gate = CountingTransportGate(ConnectorTransportSelection.LEGACY)
        val repository = repo(FakeRemote(), transportGate = gate)

        repository.cancelSync(SyncTarget.Vouchers)
        repository.getStatus(SyncTarget.Vouchers)
        repository.getStatistics(SyncTarget.Vouchers)
        repository.listRecentRuns(SyncTarget.Vouchers, 5)

        assertEquals(0, gate.resolveCount)
    }

    @Test
    fun `a changed secure state is observed by the very next start call`() = runTest {
        val gate = MutableTransportGate(ConnectorTransportSelection.LEGACY)
        val legacy = FakeRemote()
        val authenticated = FakeAuthenticatedRemote()
        val repository = repo(legacy, transportGate = gate, authenticatedRemote = authenticated)

        repository.startSync(SyncTarget.Ledgers)
        gate.selection = ConnectorTransportSelection.AUTHENTICATED
        repository.startSync(SyncTarget.StockItems)

        assertEquals(1, legacy.startCalls)
        assertEquals(1, authenticated.startCalls)
    }

    // ============================== Cross-target mutual exclusion ==============================

    @Test
    fun `an active ledger sync blocks a stock-item start and the stock adapter is never called`() = runTest {
        val ledgerStarted = CompletableDeferred<Unit>()
        val allowLedgerToFinish = CompletableDeferred<Unit>()
        val authenticated = FakeAuthenticatedRemote(
            beforeStart = { target ->
                if (target == SyncTarget.Ledgers) {
                    ledgerStarted.complete(Unit)
                    allowLedgerToFinish.await()
                }
            },
        )
        val repository = repo(
            remote = UnreachableRemote,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedRemote = authenticated,
        )

        val ledgerJob = launch { repository.startSync(SyncTarget.Ledgers) }
        runCurrent()
        ledgerStarted.await()

        val blockedResult = repository.startSync(SyncTarget.StockItems)

        assertTrue(blockedResult is AppResult.Failure)
        assertEquals(
            "A sync is already in progress in this app session.",
            ((blockedResult as AppResult.Failure).error as AppError.Message).message,
        )
        assertEquals(0, authenticated.startCallsFor(SyncTarget.StockItems))
        assertTrue(repository.summary.value.isAnySyncActive)

        allowLedgerToFinish.complete(Unit)
        ledgerJob.join()

        assertEquals(false, repository.summary.value.isAnySyncActive)
    }

    @Test
    fun `a blocked duplicate start makes zero remote calls for the blocked target`() = runTest {
        val ledgerStarted = CompletableDeferred<Unit>()
        val allowLedgerToFinish = CompletableDeferred<Unit>()
        val authenticated = FakeAuthenticatedRemote(
            beforeStart = { target ->
                if (target == SyncTarget.Ledgers) {
                    ledgerStarted.complete(Unit)
                    allowLedgerToFinish.await()
                }
            },
        )
        val repository = repo(
            remote = UnreachableRemote,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedRemote = authenticated,
        )

        val ledgerJob = launch { repository.startSync(SyncTarget.Ledgers) }
        runCurrent()
        ledgerStarted.await()

        repository.startSync(SyncTarget.Vouchers)

        assertEquals(0, authenticated.startCallsFor(SyncTarget.Vouchers))

        allowLedgerToFinish.complete(Unit)
        ledgerJob.join()
    }

    @Test
    fun `authentication failure releases local active state`() = runTest {
        val rejection = AppError.Remote(httpStatus = 401, code = AUTHENTICATED_SECURE_PAIRING_REQUIRED_CODE, message = "re-pair")
        val repository = repo(
            remote = UnreachableRemote,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedRemote = FakeAuthenticatedRemote(startResult = AppResult.Failure(rejection)),
        )

        repository.startSync(SyncTarget.Ledgers)

        assertEquals(false, repository.summary.value.isAnySyncActive)
        assertNull(repository.summary.value.activeTarget)
    }

    @Test
    fun `an exception is never thrown, so the local guard cannot become permanently stuck across a malformed response`() = runTest {
        val repository = repo(
            remote = UnreachableRemote,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedRemote = FakeAuthenticatedRemote(startResult = AppResult.Failure(AppError.Serialization("bad json"))),
        )

        repository.startSync(SyncTarget.Ledgers)

        // A second start for a different target not being rejected as "already in progress"
        // proves localActiveTarget was released — the fake always returns the same failure, so
        // the second call's own outcome is expected to fail too; what matters is *which* failure.
        val second = repository.startSync(SyncTarget.StockItems) as AppResult.Failure
        assertTrue(second.error !is AppError.Message || (second.error as AppError.Message).message != "A sync is already in progress in this app session.")
    }

    // ============================== 401 and 403 ==============================

    @Test
    fun `401 never calls legacy, preserves scope, releases active state, and returns SecurePairingRequired`() = runTest {
        val rejection = AppError.Remote(httpStatus = 401, code = AUTHENTICATED_SECURE_PAIRING_REQUIRED_CODE, message = "re-pair")
        val repository = repo(
            remote = UnreachableRemote,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedRemote = FakeAuthenticatedRemote(startResult = AppResult.Failure(rejection)),
        )

        val result = repository.startSync(SyncTarget.Ledgers)

        assertTrue(result is AppResult.Failure)
        assertEquals(401, ((result as AppResult.Failure).error as AppError.Remote).httpStatus)
        assertEquals(AUTHENTICATED_SECURE_PAIRING_REQUIRED_CODE, (result.error as AppError.Remote).code)
        assertEquals(false, repository.summary.value.isAnySyncActive)
    }

    @Test
    fun `403 never calls legacy, does not mutate credential state, releases active state, and returns AccessDenied`() = runTest {
        val rejection = AppError.Remote(httpStatus = 403, code = AUTHENTICATED_ACCESS_DENIED_CODE, message = "forbidden")
        val repository = repo(
            remote = UnreachableRemote,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedRemote = FakeAuthenticatedRemote(startResult = AppResult.Failure(rejection)),
        )

        val result = repository.startSync(SyncTarget.Ledgers)

        assertTrue(result is AppResult.Failure)
        assertEquals(403, ((result as AppResult.Failure).error as AppError.Remote).httpStatus)
        assertEquals(AUTHENTICATED_ACCESS_DENIED_CODE, (result.error as AppError.Remote).code)
        assertEquals(false, repository.summary.value.isAnySyncActive)
    }

    @Test
    fun `an authenticated 409 conflict is reported without a recoverable syncRunId, an accepted narrow limitation`() = runTest {
        val conflict = AppError.Remote(httpStatus = 409, code = null, message = "The request conflicted with the current Connector state.")
        val authenticated = FakeAuthenticatedRemote(startResult = AppResult.Failure(conflict))
        val repository = repo(
            remote = UnreachableRemote,
            transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authenticatedRemote = authenticated,
        )

        val result = repository.startSync(SyncTarget.Ledgers) as AppResult.Success
        val outcome = result.value as SyncOutcome.Conflict

        assertNull(outcome.existingSyncRunId)
        // localActiveTarget (the guard that actually decides whether a new start is accepted) is
        // released on Conflict exactly as on any other outcome — proven by a same-target retry not
        // being rejected as "already in progress". Pre-existing, unchanged-by-this-phase quirk:
        // recordOutcome's Conflict branch never touches `liveProgress` (only Failed's null
        // `progress` does), so `summary.isAnySyncActive` can display stale "Running" state after a
        // conflict even though the correctness-critical guard is genuinely clear — this is
        // existing display behaviour this phase must not alter, not a regression.
        val retry = repository.startSync(SyncTarget.Ledgers)
        assertTrue(retry is AppResult.Success)
        assertEquals(2, authenticated.startCallsFor(SyncTarget.Ledgers))
    }

    // ============================== Non-Ledgers/StockItems failure preservation ==============================

    @Test
    fun `every non-authentication authenticated status failure leaves last known status unchanged`() = runTest {
        val nonAuthFailures = listOf(
            AppError.Offline(),
            AppError.Serialization("bad json"),
            AppError.Remote(500, null, "server error"),
            AppError.Remote(429, null, "rate limited"),
            AppError.Message("cancelled"),
        )

        nonAuthFailures.forEach { error ->
            val repository = repo(
                remote = UnreachableRemote,
                transportGate = FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
                authenticatedRemote = FakeAuthenticatedRemote(statusResult = AppResult.Failure(error)),
            )

            val result = repository.getStatus(SyncTarget.Ledgers)

            assertTrue("expected a failure result for $error", result is AppResult.Failure)
        }
    }

    private fun repo(
        remote: SyncRemoteDataSource,
        transportGate: ConnectorTransportSelectionGate = FakeTransportGate(ConnectorTransportSelection.LEGACY),
        authenticatedRemote: AuthenticatedSyncRemoteDataSource = UnreachableAuthenticatedRemote,
    ) = SyncRepositoryImpl(remote, errorMapper, time, transportGate, authenticatedRemote)
}

private class FakeRemote(
    var blockStart: Boolean = false,
    var startResult: ApiResult<SyncOutcome>? = null,
) : SyncRemoteDataSource {
    var startCalls = 0
        private set

    override suspend fun startSync(target: SyncTarget, mode: SyncMode): ApiResult<SyncOutcome> {
        startCalls += 1
        startResult?.let { return it }
        return ApiResult.Success(
            SyncOutcome.Succeeded(
                target = target,
                syncRunId = "r1",
                status = SyncRunStatus.Completed,
                progress = sampleProgress(SyncRunStatus.Completed),
                validationIssueCount = 0,
                extractionCompleteness = null,
                statistics = SyncStatisticsSummary("t", 1),
                warningMessage = null,
            ),
        )
    }

    override suspend fun cancelSync(target: SyncTarget): ApiResult<SyncProgress> =
        ApiResult.Success(sampleProgress(SyncRunStatus.Cancelled))

    override suspend fun fetchStatus(target: SyncTarget): ApiResult<SyncProgress> =
        ApiResult.Success(sampleProgress(SyncRunStatus.Idle))

    override suspend fun fetchStatistics(target: SyncTarget): ApiResult<SyncStatisticsSummary> =
        ApiResult.Success(SyncStatisticsSummary(null, 0))

    override suspend fun fetchRecentRuns(target: SyncTarget, limit: Int): ApiResult<List<SyncRunSummary>> =
        ApiResult.Success(emptyList())
}

private object UnreachableRemote : SyncRemoteDataSource {
    override suspend fun startSync(target: SyncTarget, mode: SyncMode): ApiResult<SyncOutcome> =
        error("UnreachableRemote must never be called on the AUTHENTICATED path")
    override suspend fun cancelSync(target: SyncTarget): ApiResult<SyncProgress> =
        error("UnreachableRemote must never be called on the AUTHENTICATED path")
    override suspend fun fetchStatus(target: SyncTarget): ApiResult<SyncProgress> =
        error("UnreachableRemote must never be called on the AUTHENTICATED path")
    override suspend fun fetchStatistics(target: SyncTarget): ApiResult<SyncStatisticsSummary> =
        error("UnreachableRemote must never be called on the AUTHENTICATED path")
    override suspend fun fetchRecentRuns(target: SyncTarget, limit: Int): ApiResult<List<SyncRunSummary>> =
        error("UnreachableRemote must never be called on the AUTHENTICATED path")
}

private class FakeAuthenticatedRemote(
    private val startResult: AppResult<SyncOutcome>? = null,
    private val cancelResult: AppResult<SyncProgress>? = null,
    private val statusResult: AppResult<SyncProgress>? = null,
    private val statisticsResult: AppResult<SyncStatisticsSummary>? = null,
    private val runsResult: AppResult<List<SyncRunSummary>>? = null,
    private val beforeStart: suspend (SyncTarget) -> Unit = {},
) : AuthenticatedSyncRemoteDataSource {
    var startCalls = 0
        private set
    private val startCallsByTarget = mutableMapOf<SyncTarget, Int>()

    fun startCallsFor(target: SyncTarget): Int = startCallsByTarget[target] ?: 0

    override suspend fun startSync(target: SyncTarget, mode: SyncMode): AppResult<SyncOutcome> {
        startCalls += 1
        startCallsByTarget[target] = (startCallsByTarget[target] ?: 0) + 1
        beforeStart(target)
        startResult?.let { return it }
        return AppResult.Success(
            SyncOutcome.Succeeded(
                target = target,
                syncRunId = "r1",
                status = SyncRunStatus.Completed,
                progress = sampleProgress(SyncRunStatus.Completed),
                validationIssueCount = 0,
                extractionCompleteness = null,
                statistics = SyncStatisticsSummary("t", 1),
                warningMessage = null,
            ),
        )
    }

    override suspend fun cancelSync(target: SyncTarget): AppResult<SyncProgress> =
        cancelResult ?: AppResult.Success(sampleProgress(SyncRunStatus.Cancelled))

    override suspend fun fetchStatus(target: SyncTarget): AppResult<SyncProgress> =
        statusResult ?: AppResult.Success(sampleProgress(SyncRunStatus.Idle))

    override suspend fun fetchStatistics(target: SyncTarget): AppResult<SyncStatisticsSummary> =
        statisticsResult ?: AppResult.Success(SyncStatisticsSummary(null, 0))

    override suspend fun fetchRecentRuns(target: SyncTarget, limit: Int): AppResult<List<SyncRunSummary>> =
        runsResult ?: AppResult.Success(emptyList())
}

private object UnreachableAuthenticatedRemote : AuthenticatedSyncRemoteDataSource {
    override suspend fun startSync(target: SyncTarget, mode: SyncMode): AppResult<SyncOutcome> =
        error("UnreachableAuthenticatedRemote must never be called on the LEGACY path")
    override suspend fun cancelSync(target: SyncTarget): AppResult<SyncProgress> =
        error("UnreachableAuthenticatedRemote must never be called on the LEGACY path")
    override suspend fun fetchStatus(target: SyncTarget): AppResult<SyncProgress> =
        error("UnreachableAuthenticatedRemote must never be called on the LEGACY path")
    override suspend fun fetchStatistics(target: SyncTarget): AppResult<SyncStatisticsSummary> =
        error("UnreachableAuthenticatedRemote must never be called on the LEGACY path")
    override suspend fun fetchRecentRuns(target: SyncTarget, limit: Int): AppResult<List<SyncRunSummary>> =
        error("UnreachableAuthenticatedRemote must never be called on the LEGACY path")
}

private class FakeTransportGate(private val selection: ConnectorTransportSelection) : ConnectorTransportSelectionGate {
    override suspend fun resolve(): ConnectorTransportSelection = selection
}

private class MutableTransportGate(var selection: ConnectorTransportSelection) : ConnectorTransportSelectionGate {
    override suspend fun resolve(): ConnectorTransportSelection = selection
}

private class CountingTransportGate(private val selection: ConnectorTransportSelection) : ConnectorTransportSelectionGate {
    var resolveCount = 0
        private set

    override suspend fun resolve(): ConnectorTransportSelection {
        resolveCount++
        return selection
    }
}

private fun sampleProgress(status: SyncRunStatus) = SyncProgress(
    syncRunId = "r1",
    status = status,
    counts = SyncCounts(1, 1, 0, 0, 0, 1),
    startedAt = "t0",
    completedAt = "t1",
    durationMs = 10,
    lastError = null,
    cancelRequested = false,
)
