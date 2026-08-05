package com.budcom.android.feature.voucher.presentation

import androidx.lifecycle.SavedStateHandle
import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.network.NetworkConnectivityObserver
import com.budcom.android.feature.company.domain.port.CompanySessionPort
import com.budcom.android.feature.company.domain.port.SelectedCompanyStatus
import com.budcom.android.feature.company.domain.port.SessionValidationStatus
import com.budcom.android.feature.masterdata.presentation.MasterDataUiError
import com.budcom.android.feature.voucher.domain.model.VoucherCacheState
import com.budcom.android.feature.voucher.domain.model.VoucherDataQuality
import com.budcom.android.feature.voucher.domain.model.VoucherIdentity
import com.budcom.android.feature.voucher.domain.model.VoucherPage
import com.budcom.android.feature.voucher.domain.model.VoucherQuery
import com.budcom.android.feature.voucher.domain.model.VoucherStatus
import com.budcom.android.feature.voucher.domain.model.VoucherSummary
import com.budcom.android.feature.voucher.domain.repository.VoucherRepository
import com.budcom.android.feature.voucher.domain.usecase.LoadVouchersUseCase
import com.budcom.android.feature.voucher.domain.usecase.RefreshVouchersUseCase
import com.budcom.android.navigation.Routes
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

    private fun createVm(query: String = "") = VoucherBrowserViewModel(
        savedStateHandle = SavedStateHandle(mapOf(Routes.QUERY_ARG to query)),
        loadVouchers = LoadVouchersUseCase(repository),
        refreshVouchers = RefreshVouchersUseCase(repository),
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

    @Test
    fun `requires company before loading`() = runTest(dispatcher) {
        companySession.selected.value = null
        val vm = createVm()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.error is MasterDataUiError.Message)
        assertEquals(0, repository.listCalls)
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
            AppError.Message("No offline data available. Connect to BUDCOM Desktop and synchronize once."),
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
