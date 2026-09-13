package com.jajusri.venture.feature.voucher.presentation

import androidx.lifecycle.SavedStateHandle
import com.jajusri.venture.core.common.AppError
import com.jajusri.venture.core.common.AppResult
import com.jajusri.venture.core.network.NetworkConnectivityObserver
import com.jajusri.venture.feature.company.domain.model.CompanyDiscoverySnapshot
import com.jajusri.venture.feature.company.domain.model.ConnectorSessionSnapshot
import com.jajusri.venture.feature.company.domain.model.SessionValidationOutcome
import com.jajusri.venture.feature.company.domain.port.CompanySessionPort
import com.jajusri.venture.feature.company.domain.port.SelectedCompanyStatus
import com.jajusri.venture.feature.company.domain.port.SessionValidationStatus
import com.jajusri.venture.feature.company.domain.repository.CompanyRepository
import com.jajusri.venture.feature.masterdata.presentation.MasterDataUiError
import com.jajusri.venture.feature.voucher.domain.model.VoucherCacheState
import com.jajusri.venture.feature.voucher.domain.model.VoucherDataQuality
import com.jajusri.venture.feature.voucher.domain.model.VoucherIdentity
import com.jajusri.venture.feature.voucher.domain.model.VoucherPage
import com.jajusri.venture.feature.voucher.domain.model.VoucherQuery
import com.jajusri.venture.feature.voucher.domain.model.VoucherStatus
import com.jajusri.venture.feature.voucher.domain.model.VoucherSummary
import com.jajusri.venture.feature.voucher.domain.repository.VoucherRepository
import com.jajusri.venture.feature.voucher.domain.usecase.LoadVouchersUseCase
import com.jajusri.venture.feature.voucher.domain.usecase.ReconcileVoucherWindowsUseCase
import com.jajusri.venture.feature.voucher.domain.usecase.RefreshVouchersUseCase
import com.jajusri.venture.navigation.Routes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers the Phase 3E offline-voucher-reliability contract: Load must read the cache
 * immediately without ever calling refresh, a failed Refresh must never clear or hide
 * valid cached rows, and repeated navigation must return the same deterministic rows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoucherBrowserViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeVoucherRepository
    private lateinit var companySession: FakeCompanySession
    private lateinit var connectivity: FakeConnectivity

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeVoucherRepository()
        companySession = FakeCompanySession("estimation")
        connectivity = FakeConnectivity(true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Background reconciliation is wired to its OWN throwaway repository, decoupled from
    // [repository], for every test in this file except the "background reconciliation wiring"
    // group below — this file is about VoucherBrowserViewModel's foreground behavior, which
    // predates and is independent of reconciliation (covered thoroughly in its own
    // ReconcileVoucherWindowsUseCaseTest); sharing [repository] here would make every existing
    // refreshCalls/listCalls assertion also count the background walk's own calls.
    private fun createVm(query: String = "") = VoucherBrowserViewModel(
        savedStateHandle = SavedStateHandle(mapOf(Routes.QUERY_ARG to query)),
        loadVouchers = LoadVouchersUseCase(repository),
        refreshVouchers = RefreshVouchersUseCase(repository),
        reconcileVoucherWindows = ReconcileVoucherWindowsUseCase(
            RefreshVouchersUseCase(FakeVoucherRepository()),
            FakeCompanyRepository(),
        ),
        companySession = companySession,
        connectivityObserver = connectivity,
    )

    @Test
    fun `loads vouchers when company selected`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isInitialLoading)
        assertEquals(1, vm.uiState.value.vouchers.size)
        assertEquals("S-1", vm.uiState.value.vouchers[0].primaryLabel)
        assertEquals("estimation", repository.lastListQuery?.companyId)
    }

    @Test
    fun `Load never calls the refresh path`() = runTest(dispatcher) {
        createVm()
        advanceUntilIdle()
        assertEquals(0, repository.refreshCalls)
        assertEquals(1, repository.listCalls)
    }

    @Test
    fun `Refresh never bypasses the cache read on the next Load`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        vm.onEvent(VoucherBrowserEvent.Refresh)
        advanceUntilIdle()
        assertEquals(1, repository.refreshCalls)
        assertEquals(1, repository.listCalls) // Refresh itself never calls listVouchers
    }

    /**
     * A company that has never synced has no cache for `listVouchers` to fall back to, so it
     * fails every time with the same message — the Retry button shown in that exact state
     * (`state.error != null && !state.hasContent`) must escalate to a real network refresh, or
     * it is a permanent dead end. Physically reproduced on a real never-synced company
     * (2026-08-22 real-device validation) before this fix.
     */
    @Test
    fun `retry with no cached content escalates to network refresh`() = runTest(dispatcher) {
        repository.listResult = AppResult.Failure(AppError.Message("No offline data available."))
        repository.refreshResult = repository.listResult
        val vm = createVm()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.vouchers.isEmpty())
        assertEquals(0, repository.refreshCalls)

        vm.onEvent(VoucherBrowserEvent.Retry)
        advanceUntilIdle()
        assertEquals(1, repository.refreshCalls)
    }

    @Test
    fun `requires company before loading`() = runTest(dispatcher) {
        companySession.selected.value = null
        val vm = createVm()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.error is MasterDataUiError.Message)
        assertEquals(0, repository.listCalls)
    }

    @Test
    fun `TypeFilterChanged updates selection without refetching`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        val callsBefore = repository.listCalls + repository.refreshCalls
        vm.onEvent(VoucherBrowserEvent.TypeFilterChanged("Sales"))
        advanceUntilIdle()
        assertEquals("Sales", vm.uiState.value.selectedTypeFilter)
        assertEquals(callsBefore, repository.listCalls + repository.refreshCalls)
    }

    @Test
    fun `a new selected company resets the type filter back to All`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        vm.onEvent(VoucherBrowserEvent.TypeFilterChanged("Sales"))
        advanceUntilIdle()
        companySession.selected.value = "other-company"
        advanceUntilIdle()
        assertNull(vm.uiState.value.selectedTypeFilter)
    }

    @Test
    fun `empty result`() = runTest(dispatcher) {
        repository.listResult = AppResult.Success(
            VoucherPage("estimation", emptyList(), 1, 50, 0, 1),
        )
        val vm = createVm()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.vouchers.isEmpty())
        assertEquals(null, vm.uiState.value.error)
    }

    @Test
    fun `true no-cache load failure surfaces the first-sync message, not a generic error`() = runTest(dispatcher) {
        repository.listResult = AppResult.Failure(
            AppError.Message("No offline data available. Connect to VENTURE Desktop and synchronize once."),
        )
        val vm = createVm()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.error is MasterDataUiError.Message)
        assertTrue(vm.uiState.value.vouchers.isEmpty())
    }

    @Test
    fun `offline without content`() = runTest(dispatcher) {
        repository.listResult = AppResult.Failure(AppError.Offline())
        val vm = createVm()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.error is MasterDataUiError.Offline)
    }

    @Test
    fun `a slow or failing refresh can never replace valid cached rows with an empty state`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        repository.refreshResult = AppResult.Failure(AppError.Timeout())
        vm.onEvent(VoucherBrowserEvent.Refresh)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.vouchers.size)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `refresh failure with content sets a distinct non-blocking refresh error using the real last-synced time`() =
        runTest(dispatcher) {
            val vm = createVm()
            advanceUntilIdle()
            repository.listResult = AppResult.Success(
                VoucherPage("estimation", listOf(sampleSummary()), 1, 50, 1, 1, VoucherCacheState.Offline, 1_700_000_000_000L),
            )
            vm.onEvent(VoucherBrowserEvent.Load)
            advanceUntilIdle()
            repository.refreshResult = AppResult.Failure(AppError.Timeout())
            vm.onEvent(VoucherBrowserEvent.Refresh)
            advanceUntilIdle()
            val message = vm.uiState.value.refreshError
            assertTrue(message != null && message.startsWith("Could not refresh"))
            assertEquals(1, vm.uiState.value.vouchers.size)
        }

    @Test
    fun `a later successful refresh clears the previous refresh error`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        repository.refreshResult = AppResult.Failure(AppError.Timeout())
        vm.onEvent(VoucherBrowserEvent.Refresh)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.refreshError != null)

        repository.refreshResult = AppResult.Success(
            VoucherPage("estimation", listOf(sampleSummary()), 1, 50, 1, 1, VoucherCacheState.Live, 123L),
        )
        vm.onEvent(VoucherBrowserEvent.Refresh)
        advanceUntilIdle()
        assertNull(vm.uiState.value.refreshError)
        assertEquals(VoucherCacheState.Live, vm.uiState.value.cacheState)
    }

    @Test
    fun `revisiting the voucher screen ten times returns the same deterministic cached rows`() = runTest(dispatcher) {
        repeat(10) {
            val vm = createVm()
            advanceUntilIdle()
            assertEquals(1, vm.uiState.value.vouchers.size)
            assertEquals("S-1", vm.uiState.value.vouchers[0].primaryLabel)
        }
        assertEquals(10, repository.listCalls)
        assertEquals(0, repository.refreshCalls)
    }

    @Test
    fun `search passes q to repository`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        vm.onEvent(VoucherBrowserEvent.SearchChanged("acme"))
        advanceUntilIdle()
        assertEquals("acme", repository.lastListQuery?.searchText)
    }

    // ====================== Background reconciliation wiring (VENTURE MVP-1 Section 3/4) ======================

    @Test
    fun `an explicit Refresh also starts background historical reconciliation, reaching Completed`() = runTest(dispatcher) {
        val vm = VoucherBrowserViewModel(
            savedStateHandle = SavedStateHandle(mapOf(Routes.QUERY_ARG to "")),
            loadVouchers = LoadVouchersUseCase(repository),
            refreshVouchers = RefreshVouchersUseCase(repository),
            reconcileVoucherWindows = ReconcileVoucherWindowsUseCase(RefreshVouchersUseCase(repository), FakeCompanyRepository()),
            companySession = companySession,
            connectivityObserver = connectivity,
        )
        advanceUntilIdle()
        assertEquals(VoucherHistoryReconciliationStatus.NotStarted, vm.uiState.value.historyReconciliationStatus)

        vm.onEvent(VoucherBrowserEvent.Refresh)
        advanceUntilIdle()

        assertEquals(VoucherHistoryReconciliationStatus.Completed, vm.uiState.value.historyReconciliationStatus)
        // FakeCompanyRepository has no booksFrom for this company — the completed walk only
        // covered the fallback window, so it must NOT be reported as authoritative full history.
        assertEquals(false, vm.uiState.value.historyReconciliationScopeIsAuthoritative)
    }

    @Test
    fun `a passive Load never starts background reconciliation`() = runTest(dispatcher) {
        val vm = VoucherBrowserViewModel(
            savedStateHandle = SavedStateHandle(mapOf(Routes.QUERY_ARG to "")),
            loadVouchers = LoadVouchersUseCase(repository),
            refreshVouchers = RefreshVouchersUseCase(repository),
            reconcileVoucherWindows = ReconcileVoucherWindowsUseCase(RefreshVouchersUseCase(repository), FakeCompanyRepository()),
            companySession = companySession,
            connectivityObserver = connectivity,
        )
        advanceUntilIdle()

        assertEquals(VoucherHistoryReconciliationStatus.NotStarted, vm.uiState.value.historyReconciliationStatus)
    }

    @Test
    fun `a failed background reconciliation window is reflected as Failed, never silently treated as complete`() = runTest(dispatcher) {
        val flaky = object : VoucherRepository by repository {
            var calls = 0
            override suspend fun refreshVouchers(query: VoucherQuery): AppResult<VoucherPage> {
                calls += 1
                return if (calls >= 2) AppResult.Failure(AppError.Offline()) else repository.refreshVouchers(query)
            }
        }
        val vm = VoucherBrowserViewModel(
            savedStateHandle = SavedStateHandle(mapOf(Routes.QUERY_ARG to "")),
            loadVouchers = LoadVouchersUseCase(repository),
            refreshVouchers = RefreshVouchersUseCase(repository),
            reconcileVoucherWindows = ReconcileVoucherWindowsUseCase(RefreshVouchersUseCase(flaky), FakeCompanyRepository()),
            companySession = companySession,
            connectivityObserver = connectivity,
        )
        advanceUntilIdle()

        vm.onEvent(VoucherBrowserEvent.Refresh)
        advanceUntilIdle()

        assertEquals(VoucherHistoryReconciliationStatus.Failed, vm.uiState.value.historyReconciliationStatus)
        // The foreground refresh (this file's core contract) must be unaffected by the
        // background walk's later failure — cached rows remain visible, no foreground error.
        assertEquals(1, vm.uiState.value.vouchers.size)
        assertNull(vm.uiState.value.error)
    }
}

private class FakeVoucherRepository : VoucherRepository {
    var listResult: AppResult<VoucherPage> = AppResult.Success(
        VoucherPage(
            companyId = "estimation",
            items = listOf(sampleSummary()),
            page = 1,
            pageSize = 50,
            totalItems = 1,
            totalPages = 1,
        ),
    )
    var refreshResult: AppResult<VoucherPage> = listResult
    var lastListQuery: VoucherQuery? = null
    var listCalls = 0
    var refreshCalls = 0

    override suspend fun listVouchers(query: VoucherQuery): AppResult<VoucherPage> {
        listCalls += 1
        lastListQuery = query
        return listResult
    }

    override suspend fun refreshVouchers(query: VoucherQuery): AppResult<VoucherPage> {
        refreshCalls += 1
        lastListQuery = query
        return refreshResult
    }

    override suspend fun getVoucherDetails(companyId: String, voucherId: String) =
        AppResult.Failure(AppError.Message("unused"))

    override suspend fun refreshVoucherDetails(companyId: String, voucherId: String) =
        AppResult.Failure(AppError.Message("unused"))

    override suspend fun getCachedVoucherSummary(companyId: String, voucherId: String) = null
}

private class FakeCompanySession(initial: String?) : CompanySessionPort {
    val selected = MutableStateFlow(initial)
    override fun observeSelectedCompanyId(): Flow<String?> = selected
    override suspend fun readSelectedCompany(): AppResult<SelectedCompanyStatus> =
        AppResult.Success(SelectedCompanyStatus(selected.value, selected.value))
    override suspend fun validateSessionStatus(): AppResult<SessionValidationStatus> =
        AppResult.Failure(AppError.Message("unused"))
}

private class FakeConnectivity(online: Boolean) : NetworkConnectivityObserver {
    private val flow = MutableStateFlow(online)
    override val isOnline: Flow<Boolean> = flow
    override fun current(): Boolean = flow.value
}

/** booksFrom-less by design: these tests exercise the browser, not reconciliation-scope resolution. */
private class FakeCompanyRepository : CompanyRepository {
    override fun observeSelectedCompanyId(): Flow<String?> = MutableStateFlow(null)
    override suspend fun loadCompanies(): AppResult<CompanyDiscoverySnapshot> =
        AppResult.Success(
            CompanyDiscoverySnapshot(
                items = emptyList(),
                schemaVersion = "1.0.0",
                dataFreshnessAt = "2026-07-27T00:00:00Z",
                contractVersion = "1",
                status = "SUCCESS",
                tallyReachable = true,
                dataQualityStatus = null,
                dataQualityReason = null,
                reason = null,
            ),
        )
    override suspend fun refreshCompanies(): AppResult<CompanyDiscoverySnapshot> = loadCompanies()
    override suspend fun getSession(): AppResult<ConnectorSessionSnapshot> = AppResult.Failure(AppError.Message("unused"))
    override suspend fun restoreSelection(): AppResult<SessionValidationOutcome?> = AppResult.Failure(AppError.Message("unused"))
    override suspend fun selectCompany(companyId: String): AppResult<SessionValidationOutcome> = AppResult.Failure(AppError.Message("unused"))
    override suspend fun validateSession(): AppResult<SessionValidationOutcome> = AppResult.Failure(AppError.Message("unused"))
    override suspend fun clearSelection(): AppResult<Unit> = AppResult.Failure(AppError.Message("unused"))
}

private fun sampleSummary() = VoucherSummary(
    identity = VoucherIdentity("v-1"),
    date = "2026-07-27",
    type = "Sales",
    number = "S-1",
    partyName = "Acme",
    referenceNumber = null,
    amount = null,
    status = VoucherStatus.Active,
    dataQuality = VoucherDataQuality.Complete,
)
