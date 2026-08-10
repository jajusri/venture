package com.budcom.android.feature.sync.presentation

import com.budcom.android.core.common.AppResult
import com.budcom.android.core.network.NetworkConnectivityObserver
import com.budcom.android.feature.company.domain.port.CompanySessionPort
import com.budcom.android.feature.company.domain.port.SelectedCompanyStatus
import com.budcom.android.feature.company.domain.port.SessionValidationStatus
import com.budcom.android.feature.sync.domain.model.SyncCounts
import com.budcom.android.feature.sync.domain.model.SyncMode
import com.budcom.android.feature.sync.domain.model.SyncOutcome
import com.budcom.android.feature.sync.domain.model.SyncProgress
import com.budcom.android.feature.sync.domain.model.SyncRunStatus
import com.budcom.android.feature.sync.domain.model.SyncRunSummary
import com.budcom.android.feature.sync.domain.model.SyncStatisticsSummary
import com.budcom.android.feature.sync.domain.model.SyncStatusSummary
import com.budcom.android.feature.sync.domain.model.SyncTarget
import com.budcom.android.feature.sync.domain.model.SyncTargetSnapshot
import com.budcom.android.feature.sync.domain.port.ObserveSyncStatusPort
import com.budcom.android.feature.sync.domain.repository.SyncRepository
import com.budcom.android.feature.sync.domain.usecase.CancelTargetSyncUseCase
import com.budcom.android.feature.sync.domain.usecase.ObserveSyncProgressUseCase
import com.budcom.android.feature.sync.domain.usecase.RefreshSyncOverviewUseCase
import com.budcom.android.feature.sync.domain.usecase.RunAvailableSyncsUseCase
import com.budcom.android.feature.sync.domain.usecase.StartTargetSyncUseCase
import com.budcom.android.feature.voucher.domain.model.VoucherDetails
import com.budcom.android.feature.voucher.domain.model.VoucherPage
import com.budcom.android.feature.voucher.domain.model.VoucherQuery
import com.budcom.android.feature.voucher.domain.model.VoucherSummary
import com.budcom.android.feature.voucher.domain.repository.VoucherRepository
import com.budcom.android.feature.voucher.domain.usecase.RefreshVouchersUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
class SyncViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeSyncRepository
    private lateinit var company: SyncVmFakeCompany
    private lateinit var connectivity: SyncVmFakeConnectivity
    private lateinit var statusPort: FakeObserveSyncStatus
    private lateinit var voucherRepository: SyncVmFakeVoucherRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeSyncRepository()
        company = SyncVmFakeCompany("estimation")
        connectivity = SyncVmFakeConnectivity(true)
        statusPort = FakeObserveSyncStatus()
        voucherRepository = SyncVmFakeVoucherRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun startTargetSync() =
        StartTargetSyncUseCase(repository, company, RefreshVouchersUseCase(voucherRepository))

    private fun createVm() = SyncViewModel(
        refreshOverview = RefreshSyncOverviewUseCase(repository, company),
        startTargetSync = startTargetSync(),
        cancelTargetSync = CancelTargetSyncUseCase(repository),
        runAvailableSyncs = RunAvailableSyncsUseCase(startTargetSync()),
        observeProgress = ObserveSyncProgressUseCase(repository),
        syncStatusPort = statusPort,
        companySession = company,
        connectivityObserver = connectivity,
    )

    @Test
    fun doesNotAutoStartSync() = runTest(dispatcher) {
        createVm()
        advanceUntilIdle()
        assertEquals(0, repository.startCalls)
    }

    @Test
    fun missingCompanyBlocksStart() = runTest(dispatcher) {
        company.selected.value = null
        val vm = createVm()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.canStart)
        vm.onEvent(SyncEvent.StartTarget(SyncTarget.Ledgers))
        advanceUntilIdle()
        assertEquals(0, repository.startCalls)
    }

    @Test
    fun startSuccess() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        vm.onEvent(SyncEvent.StartTarget(SyncTarget.Ledgers))
        advanceUntilIdle()
        assertEquals(1, repository.startCalls)
        assertEquals(SyncPhase.Success, vm.uiState.value.phase)
        assertFalse(vm.uiState.value.isBusy)
    }

    @Test
    fun `voucher start reaches the repository and does not poll an unsupported status route`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        repository.startedTargets.clear()
        repository.statusTargets.clear()

        vm.onEvent(SyncEvent.StartTarget(SyncTarget.Vouchers))
        advanceUntilIdle()

        assertEquals(listOf(SyncTarget.Vouchers), repository.startedTargets)
        assertFalse(repository.statusTargets.contains(SyncTarget.Vouchers))
        assertEquals(SyncPhase.Success, vm.uiState.value.phase)
        assertFalse(vm.uiState.value.isBusy)
    }

    @Test
    fun `run available syncs reaches vouchers after ledgers and stock items`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        repository.startedTargets.clear()

        vm.onEvent(SyncEvent.RunAvailableSyncs)
        advanceUntilIdle()

        assertEquals(
            listOf(SyncTarget.Ledgers, SyncTarget.StockItems, SyncTarget.Vouchers),
            repository.startedTargets,
        )
        assertTrue(vm.uiState.value.aggregateMessage.orEmpty().contains("3 target(s) attempted"))
        assertFalse(vm.uiState.value.isBusy)
    }

    @Test
    fun offlineDisablesStart() = runTest(dispatcher) {
        connectivity.online.value = false
        val vm = createVm()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.canStart)
    }
}

private class FakeSyncRepository : SyncRepository {
    var startCalls = 0
    val startedTargets = mutableListOf<SyncTarget>()
    val statusTargets = mutableListOf<SyncTarget>()
    override fun bindCompany(companyId: String?) = Unit
    override suspend fun startSync(target: SyncTarget, mode: SyncMode): AppResult<SyncOutcome> {
        startCalls++
        startedTargets += target
        return AppResult.Success(
            SyncOutcome.Succeeded(
                target = target,
                syncRunId = "r1",
                status = SyncRunStatus.Completed,
                progress = SyncProgress(
                    "r1",
                    SyncRunStatus.Completed,
                    SyncCounts(1, 1, 0, 0, 0, 1),
                    "t0",
                    "t1",
                    1,
                    null,
                    false,
                ),
                validationIssueCount = 0,
                extractionCompleteness = null,
                statistics = SyncStatisticsSummary("t1", 1),
                warningMessage = null,
            ),
        )
    }

    override suspend fun cancelSync(target: SyncTarget) =
        AppResult.Success(
            SyncProgress("r1", SyncRunStatus.Cancelled, SyncCounts(0, 0, 0, 0, 0, null), null, null, null, null, true),
        )

    override suspend fun getStatus(target: SyncTarget): AppResult<SyncProgress> {
        statusTargets += target
        return AppResult.Success(
            SyncProgress(null, SyncRunStatus.Idle, SyncCounts(0, 0, 0, 0, 0, null), null, null, null, null, false),
        )
    }

    override suspend fun getStatistics(target: SyncTarget) =
        AppResult.Success(SyncStatisticsSummary(null, 0))

    override suspend fun listRecentRuns(target: SyncTarget, limit: Int) =
        AppResult.Success(emptyList<SyncRunSummary>())
}

private class FakeObserveSyncStatus : ObserveSyncStatusPort {
    override val summary: StateFlow<SyncStatusSummary> = MutableStateFlow(
        SyncStatusSummary(
            companyId = "estimation",
            isAnySyncActive = false,
            activeTarget = null,
            activeStatus = null,
            latestSuccessfulAt = null,
            latestFailedMessage = null,
            targets = listOf(
                SyncTargetSnapshot(SyncTarget.Ledgers, true),
                SyncTargetSnapshot(SyncTarget.StockItems, true),
                SyncTargetSnapshot(SyncTarget.Vouchers, true),
            ),
            lastUpdatedEpochMillis = 0L,
        ),
    )
}

private class SyncVmFakeCompany(initial: String?) : CompanySessionPort {
    val selected = MutableStateFlow(initial)
    override fun observeSelectedCompanyId(): Flow<String?> = selected
    override suspend fun readSelectedCompany(): AppResult<SelectedCompanyStatus> =
        AppResult.Success(SelectedCompanyStatus(selected.value, selected.value))
    override suspend fun validateSessionStatus(): AppResult<SessionValidationStatus> =
        error("unused")
}

/** Always succeeds with an empty page — the Sync-tab Voucher tests here care about the
 * Connector-extraction outcome, not the follow-up window fetch itself (that has its own
 * dedicated coverage in SyncUseCasesTest). */
private class SyncVmFakeVoucherRepository : VoucherRepository {
    override suspend fun listVouchers(query: VoucherQuery): AppResult<VoucherPage> = error("unused")
    override suspend fun refreshVouchers(query: VoucherQuery): AppResult<VoucherPage> =
        AppResult.Success(
            VoucherPage(companyId = "estimation", items = emptyList(), page = 1, pageSize = 50, totalItems = 0, totalPages = 1),
        )
    override suspend fun getVoucherDetails(companyId: String, voucherId: String): AppResult<VoucherDetails> =
        error("unused")
    override suspend fun refreshVoucherDetails(companyId: String, voucherId: String): AppResult<VoucherDetails> =
        error("unused")
    override suspend fun getCachedVoucherSummary(companyId: String, voucherId: String): VoucherSummary? = null
}

private class SyncVmFakeConnectivity(initial: Boolean) : NetworkConnectivityObserver {
    val online = MutableStateFlow(initial)
    override val isOnline: Flow<Boolean> = online
    override fun current(): Boolean = online.value
}
