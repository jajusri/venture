package com.budcom.android.feature.voucher.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
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
import com.budcom.android.feature.voucher.sharing.InvoiceShareCoordinator
import com.budcom.android.feature.voucher.sharing.InvoiceShareCacheBoundary
import com.budcom.android.feature.voucher.sharing.InvoiceShareCachePolicy
import com.budcom.android.feature.voucher.sharing.InvoiceShareFileOperations
import com.budcom.android.feature.voucher.sharing.InvoiceShareResult
import com.budcom.android.feature.voucher.sharing.PreparedInvoicePdf
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class VoucherDetailsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeDetailsRepository
    private lateinit var companySession: DetailsFakeCompanySession
    private lateinit var connectivity: DetailsFakeConnectivity
    private lateinit var shareCoordinator: FakeInvoiceShareCoordinator

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeDetailsRepository()
        companySession = DetailsFakeCompanySession("estimation")
        connectivity = DetailsFakeConnectivity(true)
        shareCoordinator = FakeInvoiceShareCoordinator()
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
        invoiceShareCoordinator = shareCoordinator,
    )

    @Test
    fun `loads details on start`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isInitialLoading)
        assertEquals("Sales", vm.uiState.value.details?.typeLabel)
        assertEquals("Sales invoice", vm.uiState.value.details?.documentTitle)
        assertEquals("Bill to", vm.uiState.value.details?.partyHeading)
        assertEquals("S-1", vm.uiState.value.details?.numberLabel)
        assertEquals(1, vm.uiState.value.details?.ledgerLines?.size)
        assertEquals("50.00/PCS", vm.uiState.value.details?.inventoryLines?.single()?.rateLabel)
    }

    @Test
    fun `uses document labels appropriate to voucher type`() {
        val purchase = sampleDetails().copy(
            summary = sampleDetails().summary.copy(type = "Purchase"),
        ).toContentUi()
        val payment = sampleDetails().copy(
            summary = sampleDetails().summary.copy(type = "Payment"),
        ).toContentUi()

        assertEquals("Purchase invoice", purchase.documentTitle)
        assertEquals("Supplier", purchase.partyHeading)
        assertEquals("Payment voucher", payment.documentTitle)
        assertEquals("Account / party", payment.partyHeading)
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

    @Test
    fun `eligible sales invoice exposes sharing and unsupported voucher does not`() = runTest(dispatcher) {
        val eligible = createVm()
        advanceUntilIdle()
        assertTrue(eligible.uiState.value.canShareInvoice)

        repository.result = AppResult.Success(sampleDetails().copy(summary = sampleDetails().summary.copy(type = "Payment")))
        val unsupported = createVm("v-2")
        advanceUntilIdle()
        assertFalse(unsupported.uiState.value.canShareInvoice)
    }

    @Test
    fun `PDF generation failure is recoverable and duplicate rapid taps start one job`() = runTest(dispatcher) {
        shareCoordinator.prepareResult = InvoiceShareResult.Failure("recoverable")
        val vm = createVm()
        advanceUntilIdle()
        vm.onEvent(VoucherDetailsEvent.SharePdf)
        vm.onEvent(VoucherDetailsEvent.SharePdf)
        advanceUntilIdle()
        assertEquals(1, shareCoordinator.prepareCalls)
        assertEquals("recoverable", vm.uiState.value.shareError)
        assertFalse(vm.uiState.value.isShareBusy)
    }

    @Test
    fun `save cancellation after route recreation resolves ViewModel operation and releases lease`() = runTest(dispatcher) {
        val root = Files.createTempDirectory("pending-save").toFile()
        val directory = File(root, InvoiceShareCachePolicy.CACHE_DIRECTORY).apply { mkdirs() }
        val operations = InvoiceShareFileOperations()
        val policy = InvoiceShareCachePolicy(InvoiceShareCacheBoundary(root, operations), operations)
        val file = File(directory, "first.pdf").apply { writeText("pdf") }
        policy.acquire(file)
        shareCoordinator.onRelease = { policy.release(file) }
        shareCoordinator.prepareResult = InvoiceShareResult.Success(prepared("first", file.absolutePath))
        val vm = createVm()
        advanceUntilIdle()
        val effect = async { vm.shareEffects.first() }
        runCurrent()
        vm.onEvent(VoucherDetailsEvent.SavePdf)
        advanceUntilIdle()
        effect.await() as VoucherDetailsShareEffect.CreatePdfDocument
        vm.onEvent(VoucherDetailsEvent.SaveDestinationSelected(null))
        advanceUntilIdle()
        assertEquals("Save cancelled.", vm.uiState.value.shareMessage)
        assertEquals(null, vm.uiState.value.shareError)
        assertEquals(0, shareCoordinator.saveCalls)
        assertEquals(1, shareCoordinator.releaseCalls)
        assertFalse(policy.isActive(file))
        val createdAt = file.lastModified()
        policy.cleanup(directory, createdAt + InvoiceShareCachePolicy.MINIMUM_RETENTION_MILLIS - 1)
        assertTrue(file.exists())
        policy.cleanup(directory, createdAt + InvoiceShareCachePolicy.EXPIRY_MILLIS + 1)
        assertFalse(file.exists())
        root.deleteRecursively()
    }

    @Test
    fun `save success after route recreation completes the retained ViewModel operation once`() = runTest(dispatcher) {
        shareCoordinator.prepareResult = InvoiceShareResult.Success(prepared("recreated-save"))
        val vm = createVm()
        advanceUntilIdle()
        val launched = async { vm.shareEffects.first() }
        runCurrent()
        vm.onEvent(VoucherDetailsEvent.SavePdf)
        advanceUntilIdle()
        launched.await()

        vm.onEvent(VoucherDetailsEvent.SaveDestinationSelected(android.net.TestUri.create()))
        advanceUntilIdle()
        assertEquals(1, shareCoordinator.saveCalls)
        assertEquals("Invoice PDF saved.", vm.uiState.value.shareMessage)
        vm.onEvent(VoucherDetailsEvent.SaveDestinationSelected(android.net.TestUri.create()))
        advanceUntilIdle()
        assertEquals(1, shareCoordinator.saveCalls)
    }

    @Test
    fun `share result after route recreation resolves retained operation without effect replay`() = runTest(dispatcher) {
        shareCoordinator.pdfShareResult = InvoiceShareResult.Success(Intent())
        shareCoordinator.prepareResult = InvoiceShareResult.Success(prepared("recreated-share"))
        val vm = createVm()
        advanceUntilIdle()
        val launched = async { vm.shareEffects.first() }
        runCurrent()
        vm.onEvent(VoucherDetailsEvent.SharePdf)
        advanceUntilIdle()
        launched.await()

        vm.onEvent(VoucherDetailsEvent.ShareActivityFinished(cancelled = true))
        assertEquals("Sharing cancelled.", vm.uiState.value.shareMessage)
        vm.onEvent(VoucherDetailsEvent.ShareActivityFinished(cancelled = false))
        assertEquals("Sharing cancelled.", vm.uiState.value.shareMessage)
        assertTrue(vm.shareEffects.replayCache.isEmpty())
    }

    @Test
    fun `viewModel teardown releases pending save without replaying effect`() = runTest(dispatcher) {
        val root = Files.createTempDirectory("teardown-save").toFile()
        val directory = File(root, InvoiceShareCachePolicy.CACHE_DIRECTORY).apply { mkdirs() }
        val operations = InvoiceShareFileOperations()
        val policy = InvoiceShareCachePolicy(InvoiceShareCacheBoundary(root, operations), operations)
        val file = File(directory, "teardown.pdf").apply { writeText("pdf") }
        policy.acquire(file)
        shareCoordinator.onRelease = { policy.release(file) }
        shareCoordinator.prepareResult = InvoiceShareResult.Success(prepared("teardown", file.absolutePath))
        val vm = createVm()
        advanceUntilIdle()
        val effect = async { vm.shareEffects.first() }
        runCurrent()
        vm.onEvent(VoucherDetailsEvent.SavePdf)
        advanceUntilIdle()
        effect.await()
        ViewModelStore().apply { put("voucher", vm); clear() }
        assertEquals(1, shareCoordinator.releaseCalls)
        assertFalse(policy.isActive(file))
        policy.cleanup(directory, file.lastModified() + InvoiceShareCachePolicy.MINIMUM_RETENTION_MILLIS - 1)
        assertTrue(file.exists())
        val recreated = createVm()
        advanceUntilIdle()
        assertEquals(0, recreated.shareEffects.replayCache.size)
        root.deleteRecursively()
    }

    @Test
    fun `late older save result cannot overwrite newer operation`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        shareCoordinator.prepareResult = InvoiceShareResult.Success(prepared("older"))
        val olderEffect = async { vm.shareEffects.first() }
        runCurrent()
        vm.onEvent(VoucherDetailsEvent.SavePdf)
        advanceUntilIdle()
        val olderId = (olderEffect.await() as VoucherDetailsShareEffect.CreatePdfDocument).operationId

        shareCoordinator.prepareResult = InvoiceShareResult.Success(prepared("newer"))
        val newerEffect = async { vm.shareEffects.first() }
        runCurrent()
        vm.onEvent(VoucherDetailsEvent.SavePdf)
        advanceUntilIdle()
        val newerId = (newerEffect.await() as VoucherDetailsShareEffect.CreatePdfDocument).operationId
        assertNotEquals(olderId, newerId)

        vm.onEvent(VoucherDetailsEvent.SaveDestinationSelected(null))
        assertEquals(null, vm.uiState.value.shareMessage)
        assertEquals(listOf("older.pdf"), shareCoordinator.releasedPdfs.map { it.suggestedFilename })
        vm.onEvent(VoucherDetailsEvent.SaveDestinationSelected(null))
        assertEquals("Save cancelled.", vm.uiState.value.shareMessage)
        assertEquals(2, shareCoordinator.releaseCalls)
        assertEquals(listOf("older.pdf", "newer.pdf"), shareCoordinator.releasedPdfs.map { it.suggestedFilename })
        vm.onEvent(VoucherDetailsEvent.SaveDestinationSelected(null))
        assertEquals(2, shareCoordinator.releaseCalls)
    }
}

private fun prepared(name: String, path: String = "/cache/invoice-share/$name.pdf") = PreparedInvoicePdf(
    contentUri = "content://invoice/$name",
    cacheFilePath = path,
    suggestedFilename = "$name.pdf",
)

private class FakeInvoiceShareCoordinator : InvoiceShareCoordinator {
    var prepareCalls = 0
    var saveCalls = 0
    var releaseCalls = 0
    val releasedPdfs = mutableListOf<PreparedInvoicePdf>()
    var onRelease: (PreparedInvoicePdf) -> Unit = {}
    var prepareResult: InvoiceShareResult<PreparedInvoicePdf> = InvoiceShareResult.Failure("unused")
    var pdfShareResult: InvoiceShareResult<Intent> = InvoiceShareResult.Failure("unused")
    override suspend fun preparePdf(details: VoucherDetails): InvoiceShareResult<PreparedInvoicePdf> {
        prepareCalls++
        return prepareResult
    }
    override fun createPdfShareIntent(pdf: PreparedInvoicePdf): InvoiceShareResult<Intent> =
        pdfShareResult
    override fun createSummaryShareIntent(details: VoucherDetails, companyName: String?): InvoiceShareResult<Intent> =
        InvoiceShareResult.Failure("unused")
    override suspend fun savePdf(pdf: PreparedInvoicePdf, destination: Uri): InvoiceShareResult<Unit> {
        saveCalls++
        return InvoiceShareResult.Success(Unit)
    }
    override fun releasePdf(pdf: PreparedInvoicePdf) {
        releaseCalls++
        releasedPdfs += pdf
        onRelease(pdf)
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
