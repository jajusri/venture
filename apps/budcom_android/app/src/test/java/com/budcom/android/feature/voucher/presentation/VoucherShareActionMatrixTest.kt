package com.budcom.android.feature.voucher.presentation

import androidx.lifecycle.SavedStateHandle
import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.network.NetworkConnectivityObserver
import com.budcom.android.feature.company.domain.port.CompanySessionPort
import com.budcom.android.feature.company.domain.port.SelectedCompanyStatus
import com.budcom.android.feature.company.domain.port.SessionValidationStatus
import com.budcom.android.feature.voucher.domain.model.VoucherDataQuality
import com.budcom.android.feature.voucher.domain.model.VoucherDetails
import com.budcom.android.feature.voucher.domain.model.VoucherIdentity
import com.budcom.android.feature.voucher.domain.model.VoucherMoney
import com.budcom.android.feature.voucher.domain.model.VoucherMoneySide
import com.budcom.android.feature.voucher.domain.model.VoucherPage
import com.budcom.android.feature.voucher.domain.model.VoucherQuery
import com.budcom.android.feature.voucher.domain.model.VoucherStatus
import com.budcom.android.feature.voucher.domain.model.VoucherSummary
import com.budcom.android.feature.voucher.domain.repository.VoucherRepository
import com.budcom.android.feature.voucher.sharing.InvoiceShareCoordinator
import com.budcom.android.feature.voucher.sharing.InvoiceShareResult
import com.budcom.android.feature.voucher.sharing.PreparedInvoicePdf
import android.content.Intent
import android.net.Uri
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

/**
 * Empirical regression coverage for the physically-reported "Sales Voucher loses Share Invoice
 * when the same party also has another transaction type" symptom. Exercises the real
 * [VoucherDetailsViewModel] against a repository keyed per (companyId, voucherId) — unlike
 * [VoucherDetailsViewModelTest]'s single-slot fake, this one can hold multiple distinct vouchers
 * simultaneously, which is required to prove (or disprove) cross-voucher/cross-party leakage.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoucherShareActionMatrixTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: MultiVoucherRepository
    private lateinit var company: MatrixFakeCompany
    private lateinit var connectivity: MatrixFakeConnectivity
    private lateinit var shareCoordinator: MatrixFakeShareCoordinator

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = MultiVoucherRepository()
        company = MatrixFakeCompany("estimation")
        connectivity = MatrixFakeConnectivity()
        shareCoordinator = MatrixFakeShareCoordinator()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun open(voucherId: String) = VoucherDetailsViewModel(
        savedStateHandle = SavedStateHandle(mapOf(VoucherDetailsViewModel.VOUCHER_ID_ARG to voucherId)),
        getVoucherDetails = com.budcom.android.feature.voucher.domain.usecase.GetVoucherDetailsUseCase(repository),
        refreshVoucherDetails = com.budcom.android.feature.voucher.domain.usecase.RefreshVoucherDetailsUseCase(repository),
        getCachedVoucherSummary = com.budcom.android.feature.voucher.domain.usecase.GetCachedVoucherSummaryUseCase(repository),
        companySession = company,
        connectivityObserver = connectivity,
        invoiceShareCoordinator = shareCoordinator,
    )

    @Test
    fun `A - party with sales only has share invoice available`() = runTest(dispatcher) {
        repository.put(sales("s-1", party = "XYZ"))
        val vm = open("s-1")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.canShareInvoice)
    }

    @Test
    fun `B - party with sales plus cash receipt keeps sales share invoice available`() = runTest(dispatcher) {
        repository.put(sales("s-1", party = "XYZ"))
        repository.put(receipt("r-1", party = "XYZ"))
        val vm = open("s-1")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.canShareInvoice)
    }

    @Test
    fun `C - party with sales receipt and payment keeps sales share invoice available`() = runTest(dispatcher) {
        repository.put(sales("s-1", party = "XYZ"))
        repository.put(receipt("r-1", party = "XYZ"))
        repository.put(payment("p-1", party = "XYZ"))
        val vm = open("s-1")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.canShareInvoice)
    }

    @Test
    fun `D - cash receipt does not inherit the sales invoice action`() = runTest(dispatcher) {
        repository.put(sales("s-1", party = "XYZ"))
        repository.put(receipt("r-1", party = "XYZ"))
        val vm = open("r-1")
        advanceUntilIdle()
        assertFalse(vm.uiState.value.canShareInvoice)
    }

    @Test
    fun `E - two sales vouchers for the same party are each independently eligible`() = runTest(dispatcher) {
        repository.put(sales("s-1", party = "XYZ"))
        repository.put(sales("s-2", party = "XYZ").let {
            it.copy(summary = it.summary.copy(number = "S-2"))
        })
        val first = open("s-1")
        val second = open("s-2")
        advanceUntilIdle()
        assertTrue(first.uiState.value.canShareInvoice)
        assertTrue(second.uiState.value.canShareInvoice)
    }

    @Test
    fun `F - navigate sales then receipt then sales yields correct actions each time with no state leakage`() = runTest(dispatcher) {
        repository.put(sales("s-1", party = "XYZ"))
        repository.put(receipt("r-1", party = "XYZ"))

        val firstSalesVisit = open("s-1")
        advanceUntilIdle()
        assertTrue(firstSalesVisit.uiState.value.canShareInvoice)

        val receiptVisit = open("r-1")
        advanceUntilIdle()
        assertFalse(receiptVisit.uiState.value.canShareInvoice)

        val secondSalesVisit = open("s-1")
        advanceUntilIdle()
        assertTrue(secondSalesVisit.uiState.value.canShareInvoice)
    }

    @Test
    fun `G - offline cached sales voucher keeps share available without another network download`() = runTest(dispatcher) {
        repository.put(sales("s-1", party = "XYZ"))
        connectivity.set(false)
        val vm = open("s-1")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.canShareInvoice)
        assertEquals(0, repository.refreshCalls)
    }

    @Test
    fun `H - different parties transaction mixes never leak actions across records`() = runTest(dispatcher) {
        repository.put(sales("s-1", party = "XYZ"))
        repository.put(receipt("r-1", party = "XYZ"))
        repository.put(sales("s-2", party = "ABC").let { it.copy(summary = it.summary.copy(number = "S-2")) })
        repository.put(payment("p-1", party = "ABC"))

        val xyzSales = open("s-1")
        val xyzReceipt = open("r-1")
        val abcSales = open("s-2")
        val abcPayment = open("p-1")
        advanceUntilIdle()

        assertTrue(xyzSales.uiState.value.canShareInvoice)
        assertFalse(xyzReceipt.uiState.value.canShareInvoice)
        assertTrue(abcSales.uiState.value.canShareInvoice)
        assertFalse(abcPayment.uiState.value.canShareInvoice)
    }

    private fun sales(id: String, party: String) = VoucherDetails(
        summary = VoucherSummary(
            identity = VoucherIdentity(id),
            date = "2026-08-11",
            type = "Sales",
            number = "S-1",
            partyName = party,
            referenceNumber = null,
            amount = VoucherMoney("100.00", VoucherMoneySide.Debit),
            status = VoucherStatus.Active,
            dataQuality = VoucherDataQuality.Complete,
        ),
        effectiveDate = null,
        narration = null,
        ledgerEntries = emptyList(),
        inventoryEntries = emptyList(),
    )

    private fun receipt(id: String, party: String) = sales(id, party).let {
        it.copy(summary = it.summary.copy(identity = VoucherIdentity(id), type = "Receipt", number = "R-1"))
    }

    private fun payment(id: String, party: String) = sales(id, party).let {
        it.copy(summary = it.summary.copy(identity = VoucherIdentity(id), type = "Payment", number = "P-1"))
    }
}

private class MultiVoucherRepository : VoucherRepository {
    private val store = mutableMapOf<String, VoucherDetails>()
    var refreshCalls = 0

    fun put(details: VoucherDetails) {
        store[details.summary.identity.id] = details
    }

    override suspend fun listVouchers(query: VoucherQuery): AppResult<VoucherPage> =
        AppResult.Failure(AppError.Message("unused"))

    override suspend fun refreshVouchers(query: VoucherQuery): AppResult<VoucherPage> =
        AppResult.Failure(AppError.Message("unused"))

    override suspend fun getVoucherDetails(companyId: String, voucherId: String): AppResult<VoucherDetails> =
        store[voucherId]?.let { AppResult.Success(it) }
            ?: AppResult.Failure(AppError.Message("No offline data available for this voucher."))

    override suspend fun refreshVoucherDetails(companyId: String, voucherId: String): AppResult<VoucherDetails> {
        refreshCalls += 1
        return getVoucherDetails(companyId, voucherId)
    }

    override suspend fun getCachedVoucherSummary(companyId: String, voucherId: String): VoucherSummary? =
        store[voucherId]?.summary
}

private class MatrixFakeCompany(initial: String?) : CompanySessionPort {
    private val selected = MutableStateFlow(initial)
    override fun observeSelectedCompanyId(): Flow<String?> = selected
    override suspend fun readSelectedCompany(): AppResult<SelectedCompanyStatus> =
        AppResult.Success(SelectedCompanyStatus(selected.value, selected.value))
    override suspend fun validateSessionStatus(): AppResult<SessionValidationStatus> =
        AppResult.Failure(AppError.Message("unused"))
}

private class MatrixFakeConnectivity : NetworkConnectivityObserver {
    private val flow = MutableStateFlow(true)
    override val isOnline: Flow<Boolean> = flow
    override fun current(): Boolean = flow.value
    fun set(online: Boolean) { flow.value = online }
}

private class MatrixFakeShareCoordinator : InvoiceShareCoordinator {
    override suspend fun preparePdf(details: VoucherDetails): InvoiceShareResult<PreparedInvoicePdf> =
        InvoiceShareResult.Failure("unused")
    override fun createPdfShareIntent(pdf: PreparedInvoicePdf): InvoiceShareResult<Intent> =
        InvoiceShareResult.Failure("unused")
    override fun createSummaryShareIntent(details: VoucherDetails, companyName: String?): InvoiceShareResult<Intent> =
        InvoiceShareResult.Failure("unused")
    override suspend fun savePdf(pdf: PreparedInvoicePdf, destination: Uri): InvoiceShareResult<Unit> =
        InvoiceShareResult.Failure("unused")
    override fun releasePdf(pdf: PreparedInvoicePdf) = Unit
}
