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
import com.budcom.android.feature.voucher.domain.model.VoucherCacheState
import com.budcom.android.feature.voucher.domain.model.VoucherStatus
import com.budcom.android.feature.voucher.domain.model.VoucherSummary
import com.budcom.android.feature.voucher.domain.repository.VoucherRepository
import com.budcom.android.feature.voucher.domain.usecase.GetCachedVoucherSummaryUseCase
import com.budcom.android.feature.voucher.domain.usecase.GetVoucherDetailsUseCase
import com.budcom.android.feature.voucher.domain.usecase.RefreshVoucherDetailsUseCase
import com.budcom.android.core.pdf.PdfPageRenderer
import com.budcom.android.core.pdf.PdfPreviewDocument
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
    private lateinit var pdfPageRenderer: FakePdfPageRenderer

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeDetailsRepository()
        companySession = DetailsFakeCompanySession("estimation")
        connectivity = DetailsFakeConnectivity(true)
        shareCoordinator = FakeInvoiceShareCoordinator()
        pdfPageRenderer = FakePdfPageRenderer()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createVm(voucherId: String = "v-1") = VoucherDetailsViewModel(
        savedStateHandle = SavedStateHandle(mapOf(VoucherDetailsViewModel.VOUCHER_ID_ARG to voucherId)),
        getVoucherDetails = GetVoucherDetailsUseCase(repository),
        refreshVoucherDetails = RefreshVoucherDetailsUseCase(repository),
        getCachedVoucherSummary = GetCachedVoucherSummaryUseCase(repository),
        companySession = companySession,
        connectivityObserver = connectivity,
        invoiceShareCoordinator = shareCoordinator,
        pdfPageRenderer = pdfPageRenderer,
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
    fun `a cache-only load failure always means details are not stored locally, never a generic blocking error`() =
        runTest(dispatcher) {
            // getVoucherDetails() is Room-only; post Phase 3E its only failure mode (once id/company
            // guards pass) is "not cached yet" — regardless of what AppError a stale caller might return.
            repository.result = AppResult.Failure(AppError.Remote(404, "NOT_FOUND", "Voucher was not found."))
            val vm = createVm()
            advanceUntilIdle()
            assertEquals(null, vm.uiState.value.error)
            assertTrue(vm.uiState.value.detailsNotStored)
        }

    @Test
    fun `refresh retains content on failure and sets a distinct refresh error, not the blocking error`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        repository.refreshResult = AppResult.Failure(AppError.Timeout())
        vm.onEvent(VoucherDetailsEvent.Refresh)
        advanceUntilIdle()
        assertEquals("S-1", vm.uiState.value.details?.numberLabel)
        assertEquals(null, vm.uiState.value.error)
        assertTrue(vm.uiState.value.refreshError?.startsWith("Could not refresh") == true)
    }

    @Test
    fun `Load never calls refreshVoucherDetails`() = runTest(dispatcher) {
        createVm()
        advanceUntilIdle()
        assertEquals(1, repository.calls)
        assertEquals(0, repository.refreshCalls)
    }

    @Test
    fun `cached voucher details open via the cache-only path even when refresh would fail`() = runTest(dispatcher) {
        repository.refreshResult = AppResult.Failure(AppError.Offline())
        val vm = createVm()
        advanceUntilIdle()
        assertEquals("S-1", vm.uiState.value.details?.numberLabel)
        assertEquals(0, repository.refreshCalls)
    }

    @Test
    fun `a later successful refresh clears the previous refresh error`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        repository.refreshResult = AppResult.Failure(AppError.Timeout())
        vm.onEvent(VoucherDetailsEvent.Refresh)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.refreshError != null)

        repository.refreshResult = AppResult.Success(sampleDetails().copy(cacheState = VoucherCacheState.Live, lastSyncedAt = 555L))
        vm.onEvent(VoucherDetailsEvent.Refresh)
        advanceUntilIdle()
        assertEquals(null, vm.uiState.value.refreshError)
        assertEquals(VoucherCacheState.Live, vm.uiState.value.cacheState)
    }

    @Test
    fun `reopening voucher details ten times returns the same deterministic cached content`() = runTest(dispatcher) {
        repeat(10) {
            val vm = createVm()
            advanceUntilIdle()
            assertEquals("S-1", vm.uiState.value.details?.numberLabel)
        }
        assertEquals(10, repository.calls)
        assertEquals(0, repository.refreshCalls)
    }

    @Test
    fun `ineligible voucher exposes a concise unavailable reason instead of silently hiding the action`() =
        runTest(dispatcher) {
            repository.result = AppResult.Success(sampleDetails().copy(summary = sampleDetails().summary.copy(type = "Payment")))
            val vm = createVm()
            advanceUntilIdle()
            assertFalse(vm.uiState.value.canShareInvoice)
            assertTrue(vm.uiState.value.shareUnavailableReason != null)
        }

    @Test
    fun `eligible voucher has no unavailable reason`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.canShareInvoice)
        assertEquals(null, vm.uiState.value.shareUnavailableReason)
    }

    @Test
    fun `offline without content still resolves to the not-stored state, not a generic offline error`() =
        runTest(dispatcher) {
            repository.result = AppResult.Failure(AppError.Offline())
            val vm = createVm()
            advanceUntilIdle()
            assertEquals(null, vm.uiState.value.error)
            assertTrue(vm.uiState.value.detailsNotStored)
        }

    @Test
    fun `not-stored state exposes the locally known summary and explains why sharing is unavailable, never silent`() =
        runTest(dispatcher) {
            repository.result = AppResult.Failure(AppError.Message("unused"))
            repository.summaryResult = sampleDetails().summary
            val vm = createVm()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.detailsNotStored)
            assertEquals("S-1", vm.uiState.value.knownSummary?.primaryLabel)
            assertFalse(vm.uiState.value.canShareInvoice)
            // The cached summary already looks like an eligible Sales invoice — only its full
            // details (ledger/inventory lines) are missing, so the reason must say so, not go silent.
            assertEquals(
                "Download this voucher's details to share it as an invoice.",
                vm.uiState.value.shareUnavailableReason,
            )
        }

    @Test
    fun `not-stored state for a genuinely ineligible voucher type gives the same reason as the full-details path`() =
        runTest(dispatcher) {
            repository.result = AppResult.Failure(AppError.Message("unused"))
            repository.summaryResult = sampleDetails().summary.copy(type = "Payment")
            val vm = createVm()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.detailsNotStored)
            assertFalse(vm.uiState.value.canShareInvoice)
            assertEquals(
                "Only sales vouchers can be shared as an invoice.",
                vm.uiState.value.shareUnavailableReason,
            )
        }

    @Test
    fun `not-stored state with no locally known summary at all still has no reason to give`() = runTest(dispatcher) {
        repository.result = AppResult.Failure(AppError.Message("unused"))
        repository.summaryResult = null
        val vm = createVm()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.detailsNotStored)
        assertEquals(null, vm.uiState.value.knownSummary)
        assertFalse(vm.uiState.value.canShareInvoice)
        assertEquals(null, vm.uiState.value.shareUnavailableReason)
    }

    @Test
    fun `DownloadDetails calls the network-backed refresh, never the cache-only path again`() = runTest(dispatcher) {
        repository.result = AppResult.Failure(AppError.Message("unused"))
        val vm = createVm()
        advanceUntilIdle()
        val cacheCallsBefore = repository.calls
        vm.onEvent(VoucherDetailsEvent.DownloadDetails)
        advanceUntilIdle()
        assertEquals(cacheCallsBefore, repository.calls)
        assertEquals(1, repository.refreshCalls)
    }

    @Test
    fun `duplicate download taps cannot start a second concurrent download`() = runTest(dispatcher) {
        repository.result = AppResult.Failure(AppError.Message("unused"))
        val vm = createVm()
        advanceUntilIdle()
        vm.onEvent(VoucherDetailsEvent.DownloadDetails)
        vm.onEvent(VoucherDetailsEvent.DownloadDetails)
        advanceUntilIdle()
        assertEquals(1, repository.refreshCalls)
    }

    @Test
    fun `known-offline download attempt never calls the Connector and shows guidance immediately`() =
        runTest(dispatcher) {
            repository.result = AppResult.Failure(AppError.Message("unused"))
            connectivity.set(false)
            val vm = createVm()
            advanceUntilIdle()
            vm.onEvent(VoucherDetailsEvent.DownloadDetails)
            advanceUntilIdle()
            assertEquals(0, repository.refreshCalls)
            assertTrue(vm.uiState.value.downloadError?.contains("Connect to BUDCOM Desktop") == true)
            assertFalse(vm.uiState.value.isDownloadingDetails)
        }

    @Test
    fun `successful download persists, clears not-stored, and shows the downloaded details`() = runTest(dispatcher) {
        repository.result = AppResult.Failure(AppError.Message("unused"))
        val vm = createVm()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.detailsNotStored)

        repository.refreshResult = AppResult.Success(sampleDetails())
        vm.onEvent(VoucherDetailsEvent.DownloadDetails)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.detailsNotStored)
        assertEquals("S-1", vm.uiState.value.details?.numberLabel)
        assertEquals(null, vm.uiState.value.downloadError)
    }

    @Test
    fun `successful download makes eligible sharing available immediately`() = runTest(dispatcher) {
        repository.result = AppResult.Failure(AppError.Message("unused"))
        val vm = createVm()
        advanceUntilIdle()

        repository.refreshResult = AppResult.Success(sampleDetails())
        vm.onEvent(VoucherDetailsEvent.DownloadDetails)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.canShareInvoice)
    }

    @Test
    fun `failed download keeps the not-stored state and known summary, without inventing partial details`() =
        runTest(dispatcher) {
            repository.result = AppResult.Failure(AppError.Message("unused"))
            repository.summaryResult = sampleDetails().summary
            val vm = createVm()
            advanceUntilIdle()

            repository.refreshResult = AppResult.Failure(AppError.Timeout())
            vm.onEvent(VoucherDetailsEvent.DownloadDetails)
            advanceUntilIdle()

            assertTrue(vm.uiState.value.detailsNotStored)
            assertEquals(null, vm.uiState.value.details)
            assertEquals("S-1", vm.uiState.value.knownSummary?.primaryLabel)
            assertTrue(vm.uiState.value.downloadError != null)
        }

    @Test
    fun `a later successful download clears the previous download failure`() = runTest(dispatcher) {
        repository.result = AppResult.Failure(AppError.Message("unused"))
        val vm = createVm()
        advanceUntilIdle()

        repository.refreshResult = AppResult.Failure(AppError.Timeout())
        vm.onEvent(VoucherDetailsEvent.DownloadDetails)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.downloadError != null)

        repository.refreshResult = AppResult.Success(sampleDetails())
        vm.onEvent(VoucherDetailsEvent.DownloadDetails)
        advanceUntilIdle()
        assertEquals(null, vm.uiState.value.downloadError)
        assertFalse(vm.uiState.value.detailsNotStored)
    }

    @Test
    fun `download retry can be attempted again after a failure`() = runTest(dispatcher) {
        repository.result = AppResult.Failure(AppError.Message("unused"))
        val vm = createVm()
        advanceUntilIdle()

        repository.refreshResult = AppResult.Failure(AppError.Timeout())
        vm.onEvent(VoucherDetailsEvent.DownloadDetails)
        advanceUntilIdle()
        vm.onEvent(VoucherDetailsEvent.DownloadDetails)
        advanceUntilIdle()

        assertEquals(2, repository.refreshCalls)
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
    fun `sharing never calls the Connector at share time`() = runTest(dispatcher) {
        shareCoordinator.prepareResult = InvoiceShareResult.Success(prepared("share-no-network"))
        val vm = createVm()
        advanceUntilIdle()
        val callsBefore = repository.calls
        val refreshCallsBefore = repository.refreshCalls
        vm.onEvent(VoucherDetailsEvent.SharePdf)
        advanceUntilIdle()
        assertEquals(callsBefore, repository.calls)
        assertEquals(refreshCallsBefore, repository.refreshCalls)
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

    // TD-028 focused coverage ------------------------------------------------------------------

    @Test
    fun `A - PreviewPdf opens the in-app preview with the prepared pdf`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        shareCoordinator.prepareResult = InvoiceShareResult.Success(prepared("s-1"))

        vm.onEvent(VoucherDetailsEvent.PreviewPdf)
        advanceUntilIdle()

        assertEquals("s-1.pdf", vm.uiState.value.previewPdf?.suggestedFilename)
    }

    @Test
    fun `C - Save and Share from preview act on the exact prepared pdf, never re-preparing`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        val pdf = prepared("s-1")
        shareCoordinator.prepareResult = InvoiceShareResult.Success(pdf)
        vm.onEvent(VoucherDetailsEvent.PreviewPdf)
        advanceUntilIdle()
        assertEquals(1, shareCoordinator.prepareCalls)

        vm.onEvent(VoucherDetailsEvent.ShareFromPreview)
        advanceUntilIdle()

        // Still exactly one prepare call — Share from preview reused the already-held pdf.
        assertEquals(1, shareCoordinator.prepareCalls)
    }

    @Test
    fun `D - dismissing preview clears preview state but leaves the loaded voucher content untouched`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        shareCoordinator.prepareResult = InvoiceShareResult.Success(prepared("s-1"))
        vm.onEvent(VoucherDetailsEvent.PreviewPdf)
        advanceUntilIdle()
        val detailsBefore = vm.uiState.value.details

        vm.onEvent(VoucherDetailsEvent.DismissPreview)

        assertEquals(null, vm.uiState.value.previewPdf)
        assertEquals(detailsBefore, vm.uiState.value.details)
    }

    @Test
    fun `E - Save from preview emits CreatePdfDocument for the prepared pdf's filename`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        shareCoordinator.prepareResult = InvoiceShareResult.Success(prepared("s-1"))
        vm.onEvent(VoucherDetailsEvent.PreviewPdf)
        advanceUntilIdle()

        val effect = async { vm.shareEffects.first() }
        runCurrent()
        vm.onEvent(VoucherDetailsEvent.SaveFromPreview)
        advanceUntilIdle()

        val result = effect.await() as VoucherDetailsShareEffect.CreatePdfDocument
        assertEquals("s-1.pdf", result.suggestedFilename)
        assertEquals(null, vm.uiState.value.previewPdf)
    }

    @Test
    fun `F - Share from preview emits LaunchShare built from the prepared pdf`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        shareCoordinator.prepareResult = InvoiceShareResult.Success(prepared("s-1"))
        shareCoordinator.pdfShareResult = InvoiceShareResult.Success(Intent())
        vm.onEvent(VoucherDetailsEvent.PreviewPdf)
        advanceUntilIdle()

        val effect = async { vm.shareEffects.first() }
        runCurrent()
        vm.onEvent(VoucherDetailsEvent.ShareFromPreview)
        advanceUntilIdle()

        assertTrue(effect.await() is VoucherDetailsShareEffect.LaunchShare)
        assertEquals(null, vm.uiState.value.previewPdf)
    }

    @Test
    fun `H - preview never regenerates a separate document -- it is the exact object preparePdf returned`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        val pdf = prepared("s-1")
        shareCoordinator.prepareResult = InvoiceShareResult.Success(pdf)

        vm.onEvent(VoucherDetailsEvent.PreviewPdf)
        advanceUntilIdle()

        // Reference/value equality to the coordinator's own return value proves this is the same
        // generated artifact Save/Share operate on, not a second, separately rendered copy — the
        // same guarantee that keeps the "ESTIMATE" framing (or any other rendering detail)
        // automatically consistent between preview and the eventual saved/shared file.
        assertEquals(pdf, vm.uiState.value.previewPdf)
    }

    @Test
    fun `J - a failed prepare does not open a broken preview`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        shareCoordinator.prepareResult = InvoiceShareResult.Failure("Invoice PDF could not be generated. Please try again.")

        vm.onEvent(VoucherDetailsEvent.PreviewPdf)
        advanceUntilIdle()

        assertEquals(null, vm.uiState.value.previewPdf)
        assertEquals("Invoice PDF could not be generated. Please try again.", vm.uiState.value.shareError)
    }

    @Test
    fun `L - repeated Preview open does not prepare a second pdf while one is already open`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        shareCoordinator.prepareResult = InvoiceShareResult.Success(prepared("s-1"))

        vm.onEvent(VoucherDetailsEvent.PreviewPdf)
        advanceUntilIdle()
        vm.onEvent(VoucherDetailsEvent.PreviewPdf)
        advanceUntilIdle()

        assertEquals(1, shareCoordinator.prepareCalls)
    }

    @Test
    fun `L - Preview then Back then Preview regenerates deterministically and releases the first file`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        shareCoordinator.prepareResult = InvoiceShareResult.Success(prepared("s-1"))

        vm.onEvent(VoucherDetailsEvent.PreviewPdf)
        advanceUntilIdle()
        vm.onEvent(VoucherDetailsEvent.DismissPreview)
        vm.onEvent(VoucherDetailsEvent.PreviewPdf)
        advanceUntilIdle()

        assertEquals(2, shareCoordinator.prepareCalls)
        assertEquals(1, shareCoordinator.releaseCalls)
        assertEquals("s-1.pdf", vm.uiState.value.previewPdf?.suggestedFilename)
    }

    @Test
    fun `M and N - opening and dismissing preview never calls refresh or mutates repository state`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        shareCoordinator.prepareResult = InvoiceShareResult.Success(prepared("s-1"))
        val refreshCallsBefore = repository.refreshCalls

        vm.onEvent(VoucherDetailsEvent.PreviewPdf)
        advanceUntilIdle()
        vm.onEvent(VoucherDetailsEvent.DismissPreview)

        assertEquals(refreshCallsBefore, repository.refreshCalls)
    }

    @Test
    fun `viewModel teardown while preview is open releases the held preview pdf`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        shareCoordinator.prepareResult = InvoiceShareResult.Success(prepared("s-1"))
        vm.onEvent(VoucherDetailsEvent.PreviewPdf)
        advanceUntilIdle()

        ViewModelStore().apply { put("voucher", vm); clear() }

        assertEquals(1, shareCoordinator.releaseCalls)
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

private class FakePdfPageRenderer : PdfPageRenderer {
    var openResult: PdfPreviewDocument? = FakePdfPreviewDocument()
    var lastOpenedPath: String? = null
    var openCalls = 0
    override suspend fun open(filePath: String): PdfPreviewDocument? {
        openCalls++
        lastOpenedPath = filePath
        return openResult
    }
}

private class FakePdfPreviewDocument(override val pageCount: Int = 1) : PdfPreviewDocument {
    var closeCalls = 0
    override suspend fun renderPage(index: Int, targetWidthPx: Int): android.graphics.Bitmap? = null
    override fun close() {
        closeCalls++
    }
}

private class FakeDetailsRepository : VoucherRepository {
    var result: AppResult<VoucherDetails> = AppResult.Success(sampleDetails())
    var refreshResult: AppResult<VoucherDetails> = result
    var summaryResult: VoucherSummary? = sampleDetails().summary
    var calls = 0
    var refreshCalls = 0
    var summaryCalls = 0

    override suspend fun listVouchers(query: VoucherQuery): AppResult<VoucherPage> =
        AppResult.Failure(AppError.Message("unused"))

    override suspend fun refreshVouchers(query: VoucherQuery): AppResult<VoucherPage> =
        AppResult.Failure(AppError.Message("unused"))

    override suspend fun getVoucherDetails(companyId: String, voucherId: String): AppResult<VoucherDetails> {
        calls += 1
        return result
    }

    override suspend fun refreshVoucherDetails(companyId: String, voucherId: String): AppResult<VoucherDetails> {
        refreshCalls += 1
        return refreshResult
    }

    override suspend fun getCachedVoucherSummary(companyId: String, voucherId: String): VoucherSummary? {
        summaryCalls += 1
        return summaryResult
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
    fun set(online: Boolean) { flow.value = online }
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
