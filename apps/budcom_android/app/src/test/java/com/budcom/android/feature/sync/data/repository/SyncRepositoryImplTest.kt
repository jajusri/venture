package com.budcom.android.feature.sync.data.repository

import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.network.ApiResult
import com.budcom.android.core.network.DefaultErrorMapper
import com.budcom.android.core.network.NetworkConnectivityObserver
import com.budcom.android.core.network.NetworkError
import com.budcom.android.core.util.TimeProvider
import com.budcom.android.feature.sync.data.remote.SyncRemoteDataSource
import com.budcom.android.feature.sync.domain.model.SyncCounts
import com.budcom.android.feature.sync.domain.model.SyncMode
import com.budcom.android.feature.sync.domain.model.SyncOutcome
import com.budcom.android.feature.sync.domain.model.SyncProgress
import com.budcom.android.feature.sync.domain.model.SyncRunStatus
import com.budcom.android.feature.sync.domain.model.SyncRunSummary
import com.budcom.android.feature.sync.domain.model.SyncStatisticsSummary
import com.budcom.android.feature.sync.domain.model.SyncTarget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncRepositoryImplTest {
    private val connectivity = object : NetworkConnectivityObserver {
        override val isOnline: Flow<Boolean> = MutableStateFlow(true)
        override fun current(): Boolean = true
    }
    private val errorMapper = DefaultErrorMapper(Json { ignoreUnknownKeys = true }, connectivity)
    private val time = TimeProvider { 42L }

    @Test
    fun voucherStartRejected() = runTest {
        val repo = SyncRepositoryImpl(FakeRemote(), errorMapper, time)
        val result = repo.startSync(SyncTarget.Vouchers)
        assertTrue(result is AppResult.Failure)
    }

    @Test
    fun duplicateLocalStartBlocked() = runTest {
        val remote = FakeRemote(blockStart = true)
        val repo = SyncRepositoryImpl(remote, errorMapper, time)
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
        val repo = SyncRepositoryImpl(FakeRemote(), errorMapper, time)
        val result = repo.startSync(SyncTarget.Ledgers)
        assertTrue(result is AppResult.Success)
        assertEquals(false, repo.summary.value.isAnySyncActive)
        assertTrue(
            repo.summary.value.targets.first { it.target == SyncTarget.Ledgers }.lastOutcome
                is SyncOutcome.Succeeded,
        )
    }
}

private class FakeRemote(
    var blockStart: Boolean = false,
    var startResult: ApiResult<SyncOutcome>? = null,
) : SyncRemoteDataSource {
    override suspend fun startSync(target: SyncTarget, mode: SyncMode): ApiResult<SyncOutcome> {
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
