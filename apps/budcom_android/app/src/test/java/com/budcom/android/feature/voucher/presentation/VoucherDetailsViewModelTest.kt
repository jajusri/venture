package com.budcom.android.feature.voucher.presentation

import androidx.lifecycle.SavedStateHandle
import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.network.NetworkConnectivityObserver
import com.budcom.android.feature.company.domain.port.CompanySessionPort
import com.budcom.android.feature.company.domain.port.SelectedCompanyStatus
import com.budcom.android.feature.company.domain.port.SessionValidationStatus
import com.budcom.android.feature.masterdata.presentation.MasterDataUiError
import com.budcom.android.feature.voucher.domain.model.VoucherDataQuality
import com.budcom.android.feature.voucher.domain.model.VoucherDetails
import com.budcom.android.feature.voucher.domain.model.VoucherIdentity
import com.budcom.android.feature.voucher.domain.model.VoucherInventoryLine
import com.budcom.android.feature.voucher.domain.model.VoucherLedgerLine
import com.budcom.android.feature.voucher.domain.model.VoucherMoney
import com.budcom.android.feature.voucher.domain.model.VoucherMoneySide
import com.budcom.android.feature.voucher.domain.model.VoucherPage
import com.budcom.android.feature.voucher.domain.model.VoucherQuery
import com.budcom.android.feature.voucher.domain.model.VoucherStatus
import com.budcom.android.feature.voucher.domain.model.VoucherSummary
import com.budcom.android.feature.voucher.domain.repository.VoucherRepository
import com.budcom.android.feature.voucher.domain.usecase.GetVoucherDetailsUseCase
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
class VoucherDetailsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeDetailsRepository
    private lateinit var companySession: DetailsFakeCompanySession
    private lateinit var connectivity: DetailsFakeConnectivity

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeDetailsRepository()
        companySession = DetailsFakeCompanySession("estimation")
        connectivity = DetailsFakeConnectivity(true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createVm(voucherId: String = "v-1") = VoucherDetailsViewModel(
        savedStateHandle = SavedStateHandle(mapOf(VoucherDetailsViewModel.VOUCHER_ID_ARG to voucherId)),
        getVoucherDetails = GetVoucherDetailsUseCase(repository),
        companySession = companySession,
        connectivityObserver = connectivity,
    )

    @Test
    fun `loads details on start`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isInitialLoading)
        assertEquals("Sales", vm.uiState.value.details?.typeLabel)
        assertEquals("S-1", vm.uiState.value.details?.numberLabel)
        assertEquals(1, vm.uiState.value.details?.ledgerLines?.size)
        assertEquals("50.00/PCS", vm.uiState.value.details?.inventoryLines?.single()?.rateLabel)
    }

    @Test
    fun `requires company`() = runTest(dispatcher) {
        companySession.selected.value = null
        val vm = createVm()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.error is MasterDataUiError.Message)
        assertEquals(0, repository.calls)
    }

    @Test
    fun `not found error`() = runTest(dispatcher) {
        repository.result = AppResult.Failure(AppError.Remote(404, "NOT_FOUND", "Voucher was not found."))
        val vm = createVm()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.error is MasterDataUiError.Remote)
    }

    @Test
    fun `refresh retains content on failure`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        repository.result = AppResult.Failure(AppError.Timeout())
        vm.onEvent(VoucherDetailsEvent.Refresh)
        advanceUntilIdle()
        assertEquals("S-1", vm.uiState.value.details?.numberLabel)
        assertTrue(vm.uiState.value.error is MasterDataUiError.Timeout)
    }

    @Test
    fun `offline without content`() = runTest(dispatcher) {
        repository.result = AppResult.Failure(AppError.Offline())
        val vm = createVm()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.error is MasterDataUiError.Offline)
    }
}

private class FakeDetailsRepository : VoucherRepository {
    var result: AppResult<VoucherDetails> = AppResult.Success(sampleDetails())
    var calls = 0

    override suspend fun listVouchers(query: VoucherQuery): AppResult<VoucherPage> =
        AppResult.Failure(AppError.Message("unused"))

    override suspend fun getVoucherDetails(companyId: String, voucherId: String): AppResult<VoucherDetails> {
        calls += 1
        return result
    }
}

private class DetailsFakeCompanySession(initial: String?) : CompanySessionPort {
    val selected = MutableStateFlow(initial)
    override fun observeSelectedCompanyId(): Flow<String?> = selected
    override suspend fun readSelectedCompany(): AppResult<SelectedCompanyStatus> =
        AppResult.Success(SelectedCompanyStatus(selected.value, selected.value))
    override suspend fun validateSessionStatus(): AppResult<SessionValidationStatus> =
        AppResult.Failure(AppError.Message("unused"))
}

private class DetailsFakeConnectivity(online: Boolean) : NetworkConnectivityObserver {
    private val flow = MutableStateFlow(online)
    override val isOnline: Flow<Boolean> = flow
    override fun current(): Boolean = flow.value
}

private fun sampleDetails() = VoucherDetails(
    summary = VoucherSummary(
        identity = VoucherIdentity("v-1"),
        date = "2026-07-27",
        type = "Sales",
        number = "S-1",
        partyName = "Acme",
        referenceNumber = "R-1",
        amount = VoucherMoney("100.00", VoucherMoneySide.Debit),
        status = VoucherStatus.Active,
        dataQuality = VoucherDataQuality.Complete,
    ),
    effectiveDate = "2026-07-27",
    narration = "Narration text",
    ledgerEntries = listOf(
        VoucherLedgerLine(
            lineNumber = 1,
            ledgerName = "Cash",
            amount = VoucherMoney("100.00", VoucherMoneySide.Debit),
            isDeemedPositive = true,
        ),
    ),
    inventoryEntries = listOf(
        VoucherInventoryLine(
            lineNumber = 1,
            itemName = "Fixture item",
            quantity = "2 PCS",
            rate = "50.00/PCS",
            amount = VoucherMoney("100.00", VoucherMoneySide.Debit),
        ),
    ),
)
