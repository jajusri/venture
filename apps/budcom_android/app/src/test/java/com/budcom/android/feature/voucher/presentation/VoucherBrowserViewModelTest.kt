package com.budcom.android.feature.voucher.presentation

import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.network.NetworkConnectivityObserver
import com.budcom.android.feature.company.domain.port.CompanySessionPort
import com.budcom.android.feature.company.domain.port.SelectedCompanyStatus
import com.budcom.android.feature.company.domain.port.SessionValidationStatus
import com.budcom.android.feature.masterdata.presentation.MasterDataUiError
import com.budcom.android.feature.voucher.domain.model.VoucherDataQuality
import com.budcom.android.feature.voucher.domain.model.VoucherIdentity
import com.budcom.android.feature.voucher.domain.model.VoucherPage
import com.budcom.android.feature.voucher.domain.model.VoucherQuery
import com.budcom.android.feature.voucher.domain.model.VoucherStatus
import com.budcom.android.feature.voucher.domain.model.VoucherSummary
import com.budcom.android.feature.voucher.domain.repository.VoucherRepository
import com.budcom.android.feature.voucher.domain.usecase.LoadVouchersUseCase
import com.budcom.android.feature.voucher.domain.usecase.RefreshVouchersUseCase
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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

    private fun createVm() = VoucherBrowserViewModel(
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
        assertEquals("estimation", repository.lastQuery?.companyId)
    }

    @Test
    fun `requires company before loading`() = runTest(dispatcher) {
        companySession.selected.value = null
        val vm = createVm()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.error is MasterDataUiError.Message)
        assertEquals(0, repository.calls)
    }

    @Test
    fun `empty result`() = runTest(dispatcher) {
        repository.result = AppResult.Success(
            VoucherPage("estimation", emptyList(), 1, 50, 0, 1),
        )
        val vm = createVm()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.vouchers.isEmpty())
        assertEquals(null, vm.uiState.value.error)
    }

    @Test
    fun `offline without content`() = runTest(dispatcher) {
        repository.result = AppResult.Failure(AppError.Offline())
        val vm = createVm()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.error is MasterDataUiError.Offline)
    }

    @Test
    fun `refresh retains content on failure`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        repository.result = AppResult.Failure(AppError.Timeout())
        vm.onEvent(VoucherBrowserEvent.Refresh)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.vouchers.size)
        assertTrue(vm.uiState.value.error is MasterDataUiError.Timeout)
    }

    @Test
    fun `search passes q to repository`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        vm.onEvent(VoucherBrowserEvent.SearchChanged("acme"))
        advanceUntilIdle()
        assertEquals("acme", repository.lastQuery?.searchText)
    }
}

private class FakeVoucherRepository : VoucherRepository {
    var result: AppResult<VoucherPage> = AppResult.Success(
        VoucherPage(
            companyId = "estimation",
            items = listOf(sampleSummary()),
            page = 1,
            pageSize = 50,
            totalItems = 1,
            totalPages = 1,
        ),
    )
    var lastQuery: VoucherQuery? = null
    var calls = 0

    override suspend fun listVouchers(query: VoucherQuery): AppResult<VoucherPage> {
        calls += 1
        lastQuery = query
        return result
    }

    override suspend fun getVoucherDetails(companyId: String, voucherId: String) =
        AppResult.Failure(AppError.Message("unused"))
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
