package com.budcom.android.feature.masterdata.ledger.presentation

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.network.NetworkConnectivityObserver
import com.budcom.android.feature.company.domain.port.CompanySessionPort
import com.budcom.android.feature.company.domain.port.SelectedCompanyStatus
import com.budcom.android.feature.company.domain.port.SessionValidationStatus
import com.budcom.android.feature.masterdata.ledger.data.local.LedgerDao
import com.budcom.android.feature.masterdata.ledger.data.local.LedgerEntity
import com.budcom.android.feature.masterdata.ledger.data.local.LedgerMovementDao
import com.budcom.android.feature.masterdata.ledger.data.local.LedgerMovementInventoryRow
import com.budcom.android.feature.masterdata.ledger.data.local.LedgerMovementRow
import com.budcom.android.feature.masterdata.ledger.data.local.VoucherNarrationRow
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerPeriodSelection
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatementMode
import com.budcom.android.feature.masterdata.ledger.domain.usecase.GetLocalLedgerStatementUseCase
import com.budcom.android.feature.masterdata.ledger.domain.usecase.RefreshLedgerCoverageUseCase
import com.budcom.android.feature.masterdata.ledger.sharing.LedgerShareDefaultDestination
import com.budcom.android.feature.masterdata.ledger.sharing.LedgerShareDestination
import com.budcom.android.feature.masterdata.ledger.sharing.LedgerSharingDefaultPeriod
import com.budcom.android.feature.masterdata.ledger.sharing.LedgerSharingPreferences
import com.budcom.android.feature.masterdata.ledger.sharing.LedgerSharingPreferencesStore
import com.budcom.android.feature.masterdata.ledger.sharing.LedgerStatementShareCoordinator
import com.budcom.android.feature.masterdata.ledger.sharing.LedgerStatementShareResult
import com.budcom.android.feature.masterdata.ledger.sharing.PreparedLedgerStatementPdf
import com.budcom.android.feature.voucher.domain.model.VoucherDetails
import com.budcom.android.feature.voucher.domain.model.VoucherPage
import com.budcom.android.feature.voucher.domain.model.VoucherQuery
import com.budcom.android.feature.voucher.domain.model.VoucherSummary
import com.budcom.android.feature.voucher.domain.repository.VoucherRepository
import com.budcom.android.feature.voucher.domain.usecase.RefreshVouchersUseCase
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
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

@OptIn(ExperimentalCoroutinesApi::class)
class LedgerStatementViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var ledgerDao: FakeLedgerDao
    private lateinit var movementDao: FakeLedgerMovementDao
    private lateinit var voucherRepository: FakeVoucherRepository
    private lateinit var companySession: FakeCompanySession
    private lateinit var connectivity: StatementFakeConnectivity
    private lateinit var shareCoordinator: FakeShareCoordinator
    private lateinit var sharingPreferencesStore: FakeLedgerSharingPreferencesStore
    private lateinit var pdfPageRenderer: FakeLedgerPdfPageRenderer

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        ledgerDao = FakeLedgerDao()
        movementDao = FakeLedgerMovementDao()
        voucherRepository = FakeVoucherRepository()
        companySession = FakeCompanySession("estimation")
        connectivity = StatementFakeConnectivity(true)
        shareCoordinator = FakeShareCoordinator()
        sharingPreferencesStore = FakeLedgerSharingPreferencesStore()
        pdfPageRenderer = FakeLedgerPdfPageRenderer()
        // A ledger with no synced balance and no local movements is a benign default; individual
        // tests override via [ledgerDao.entity]/[movementDao] as needed.
        ledgerDao.entity = LedgerEntity(
            companyId = "estimation", id = "ledger-1", name = "Acme Traders", alias = null,
            parentGroup = "Sundry Debtors", status = "active", closingAmount = null,
            closingCurrencyCode = null, closingSide = null, dataQuality = "complete",
            syncedAt = "2026-08-01T00:00:00Z", dataFreshnessAt = null,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val fixedClock: Clock = Clock.fixed(Instant.parse("2026-08-12T04:00:00Z"), ZoneId.of("Asia/Kolkata"))

    private fun createVm(ledgerId: String = "ledger-1", clock: Clock = fixedClock) = LedgerStatementViewModel(
        savedStateHandle = SavedStateHandle(mapOf(LedgerStatementViewModel.LEDGER_ID_ARG to ledgerId)),
        getLocalStatement = GetLocalLedgerStatementUseCase(ledgerDao, movementDao, clock),
        refreshCoverage = RefreshLedgerCoverageUseCase(RefreshVouchersUseCase(voucherRepository)),
        companySession = companySession,
        connectivityObserver = connectivity,
        shareCoordinator = shareCoordinator,
        sharingPreferencesStore = sharingPreferencesStore,
        pdfPageRenderer = pdfPageRenderer,
    )

    @Test
    fun `loads the local statement on start with zero network calls`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        assertEquals("Acme Traders", vm.uiState.value.content?.ledgerName)
        assertEquals(0, voucherRepository.refreshCalls)
    }

    @Test
    fun `shows an error when no company is selected`() = runTest(dispatcher) {
        companySession.selected.value = null
        val vm = createVm()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.error != null)
    }

    @Test
    fun `a failed refresh preserves the existing content and surfaces a refresh error`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        voucherRepository.refreshResult = AppResult.Failure(AppError.Timeout())
        vm.onEvent(LedgerStatementEvent.Refresh)
        advanceUntilIdle()
        assertEquals("Acme Traders", vm.uiState.value.content?.ledgerName)
        assertTrue(vm.uiState.value.refreshError != null)
    }

    @Test
    fun `selecting a period reads Room only and never triggers a network call`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        vm.onEvent(LedgerStatementEvent.PeriodSelected(LedgerPeriodSelection.ThisMonth))
        advanceUntilIdle()
        assertEquals(LedgerPeriodSelection.ThisMonth, vm.uiState.value.periodSelection)
        assertEquals("no period change may call the Connector", 0, voucherRepository.refreshCalls)
    }

    @Test
    fun `a custom period change reads Room only and never triggers a network call`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        vm.onEvent(LedgerStatementEvent.PeriodChanged("2026-01-01", "2026-01-31"))
        advanceUntilIdle()
        assertEquals("2026-01-01", vm.uiState.value.fromDate)
        assertEquals("2026-01-31", vm.uiState.value.toDate)
        assertEquals(0, voucherRepository.refreshCalls)
    }

    @Test
    fun `explicit Refresh scopes the Connector call to the currently displayed window`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        val shownFrom = vm.uiState.value.fromDate
        val shownTo = vm.uiState.value.toDate
        vm.onEvent(LedgerStatementEvent.Refresh)
        advanceUntilIdle()
        assertEquals(shownFrom, voucherRepository.lastQuery?.dateRange?.from)
        assertEquals(shownTo, voucherRepository.lastQuery?.dateRange?.to)
        assertEquals(1, voucherRepository.refreshCalls)
    }

    @Test
    fun `tapping a transaction with a real voucherId emits an open-voucher-details effect`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        vm.effects.test {
            vm.onEvent(LedgerStatementEvent.TransactionTapped("stable-guid-42"))
            assertEquals(LedgerStatementEffect.OpenVoucherDetails("stable-guid-42"), awaitItem())
        }
    }

    @Test
    fun `tapping a transaction with a blank voucherId never emits a navigation effect`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        vm.effects.test {
            vm.onEvent(LedgerStatementEvent.TransactionTapped(""))
            expectNoEvents()
        }
    }

    @Test
    fun `sharing the PDF after a successful prepare launches a share intent`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        shareCoordinator.prepareResult = LedgerStatementShareResult.Success(
            PreparedLedgerStatementPdf("content://x", "/cache/x.pdf", "x.pdf"),
        )
        shareCoordinator.shareIntentResult = LedgerStatementShareResult.Success(Intent())
        vm.shareEffects.test {
            vm.onEvent(LedgerStatementEvent.ShareLedgerFast)
            advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is LedgerStatementShareEffect.LaunchShare)
        }
    }

    @Test
    fun `switching to Previous Financial Year updates the displayed period and the PDF is prepared from that same period`() = runTest(dispatcher) {
        // Locked period/PDF/Share contract: whatever period is currently selected must be the
        // single source of truth both for what's displayed AND for what SharePdf hands to the
        // share coordinator — never a stale or default (Last-7-Sales) period.
        val vm = createVm()
        advanceUntilIdle()

        vm.onEvent(LedgerStatementEvent.PeriodSelected(LedgerPeriodSelection.PreviousFinancialYear))
        advanceUntilIdle()
        assertEquals("2025-04-01", vm.uiState.value.fromDate)
        assertEquals("2026-03-31", vm.uiState.value.toDate)
        assertEquals(0, voucherRepository.refreshCalls)

        shareCoordinator.prepareResult = LedgerStatementShareResult.Success(
            PreparedLedgerStatementPdf("content://x", "/cache/x.pdf", "x.pdf"),
        )
        shareCoordinator.shareIntentResult = LedgerStatementShareResult.Success(Intent())
        vm.onEvent(LedgerStatementEvent.ShareLedgerFast)
        advanceUntilIdle()

        assertEquals("2025-04-01", shareCoordinator.lastPreparedStatement?.period?.from)
        assertEquals("2026-03-31", shareCoordinator.lastPreparedStatement?.period?.to)
    }

    @Test
    fun `switching to Current Financial Year after Previous Financial Year re-anchors the PDF to the new period, not a stale one`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()

        vm.onEvent(LedgerStatementEvent.PeriodSelected(LedgerPeriodSelection.PreviousFinancialYear))
        advanceUntilIdle()
        vm.onEvent(LedgerStatementEvent.PeriodSelected(LedgerPeriodSelection.CurrentFinancialYear))
        advanceUntilIdle()
        assertEquals("2026-04-01", vm.uiState.value.fromDate)
        assertEquals("2026-08-12", vm.uiState.value.toDate)

        shareCoordinator.prepareResult = LedgerStatementShareResult.Success(
            PreparedLedgerStatementPdf("content://x", "/cache/x.pdf", "x.pdf"),
        )
        vm.onEvent(LedgerStatementEvent.ShareLedgerFast)
        advanceUntilIdle()

        assertEquals("2026-04-01", shareCoordinator.lastPreparedStatement?.period?.from)
        assertEquals("2026-08-12", shareCoordinator.lastPreparedStatement?.period?.to)
    }

    @Test
    fun `a failed PDF prepare surfaces a share error instead of a silent failure`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        shareCoordinator.prepareResult = LedgerStatementShareResult.Failure("boom")
        vm.onEvent(LedgerStatementEvent.ShareLedgerFast)
        advanceUntilIdle()
        assertEquals("boom", vm.uiState.value.shareError)
    }

    @Test
    fun `an unsynced ledger shows an error rather than a fabricated empty statement`() = runTest(dispatcher) {
        ledgerDao.entity = null
        val vm = createVm()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.error != null)
        assertNull(vm.uiState.value.content)
    }

    @Test
    fun `the initial period reflects the persisted default, not the hardcoded fallback`() = runTest(dispatcher) {
        sharingPreferencesStore = FakeLedgerSharingPreferencesStore(
            LedgerSharingPreferences(defaultPeriod = LedgerSharingDefaultPeriod.ThisMonth),
        )
        val vm = createVm()
        advanceUntilIdle()
        assertEquals(LedgerPeriodSelection.ThisMonth, vm.uiState.value.periodSelection)
    }

    @Test
    fun `ShareLedgerFast routes to the persisted default destination with no options screen shown`() = runTest(dispatcher) {
        sharingPreferencesStore = FakeLedgerSharingPreferencesStore(
            LedgerSharingPreferences(defaultDestination = LedgerShareDefaultDestination.WhatsAppSelect),
        )
        val vm = createVm()
        advanceUntilIdle()
        shareCoordinator.prepareResult = LedgerStatementShareResult.Success(
            PreparedLedgerStatementPdf("content://x", "/cache/x.pdf", "x.pdf"),
        )
        shareCoordinator.whatsAppIntentResult = LedgerStatementShareResult.Success(Intent())
        vm.shareEffects.test {
            vm.onEvent(LedgerStatementEvent.ShareLedgerFast)
            advanceUntilIdle()
            assertTrue(awaitItem() is LedgerStatementShareEffect.LaunchShare)
        }
        assertFalse("fast share must never open the advanced options sheet", vm.uiState.value.showShareOptions)
    }

    @Test
    fun `ShareLedgerFast uses the persisted Detailed statement mode`() = runTest(dispatcher) {
        sharingPreferencesStore = FakeLedgerSharingPreferencesStore(
            LedgerSharingPreferences(statementMode = LedgerStatementMode.Detailed),
        )
        val movement = LedgerMovementRow("v1", "2026-08-10", "Sales", "v-1", null, 1, "100", "dr")
        movementDao.lastSales = listOf(movement)
        movementDao.movements = listOf(movement)
        val vm = createVm()
        advanceUntilIdle()
        shareCoordinator.prepareResult = LedgerStatementShareResult.Success(
            PreparedLedgerStatementPdf("content://x", "/cache/x.pdf", "x.pdf"),
        )
        shareCoordinator.shareIntentResult = LedgerStatementShareResult.Success(Intent())
        vm.onEvent(LedgerStatementEvent.ShareLedgerFast)
        advanceUntilIdle()
        assertEquals("Detailed mode must batch-query inventory lines once", 1, movementDao.inventoryQueryCalls)
    }

    @Test
    fun `ShareLedgerWithMode Detailed shares in Detailed mode using the remembered default destination, with no options screen shown`() = runTest(dispatcher) {
        sharingPreferencesStore = FakeLedgerSharingPreferencesStore(
            LedgerSharingPreferences(defaultDestination = LedgerShareDefaultDestination.WhatsAppSelect),
        )
        val movement = LedgerMovementRow("v1", "2026-08-10", "Sales", "v-1", null, 1, "100", "dr")
        movementDao.lastSales = listOf(movement)
        movementDao.movements = listOf(movement)
        val vm = createVm()
        advanceUntilIdle()
        shareCoordinator.prepareResult = LedgerStatementShareResult.Success(
            PreparedLedgerStatementPdf("content://x", "/cache/x.pdf", "x.pdf"),
        )
        shareCoordinator.whatsAppIntentResult = LedgerStatementShareResult.Success(Intent())
        vm.shareEffects.test {
            vm.onEvent(LedgerStatementEvent.ShareLedgerWithMode(LedgerStatementMode.Detailed))
            advanceUntilIdle()
            assertTrue(awaitItem() is LedgerStatementShareEffect.LaunchShare)
        }
        assertEquals("Detailed mode must batch-query inventory lines once", 1, movementDao.inventoryQueryCalls)
        assertFalse("tapping a share-mode choice must never open the advanced options sheet", vm.uiState.value.showShareOptions)
    }

    @Test
    fun `ShareLedgerWithMode Summary shares in Summary mode without querying inventory lines`() = runTest(dispatcher) {
        val movement = LedgerMovementRow("v1", "2026-08-10", "Sales", "v-1", null, 1, "100", "dr")
        movementDao.lastSales = listOf(movement)
        movementDao.movements = listOf(movement)
        val vm = createVm()
        advanceUntilIdle()
        shareCoordinator.prepareResult = LedgerStatementShareResult.Success(
            PreparedLedgerStatementPdf("content://x", "/cache/x.pdf", "x.pdf"),
        )
        shareCoordinator.shareIntentResult = LedgerStatementShareResult.Success(Intent())
        vm.onEvent(LedgerStatementEvent.ShareLedgerWithMode(LedgerStatementMode.Summary))
        advanceUntilIdle()
        assertEquals(0, movementDao.inventoryQueryCalls)
    }

    @Test
    fun `ShareLedgerWithMode uses the currently displayed period, not a stale one`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        vm.onEvent(LedgerStatementEvent.PeriodSelected(LedgerPeriodSelection.PreviousFinancialYear))
        advanceUntilIdle()
        shareCoordinator.prepareResult = LedgerStatementShareResult.Success(
            PreparedLedgerStatementPdf("content://x", "/cache/x.pdf", "x.pdf"),
        )
        vm.onEvent(LedgerStatementEvent.ShareLedgerWithMode(LedgerStatementMode.Detailed))
        advanceUntilIdle()
        assertEquals("2025-04-01", shareCoordinator.lastPreparedStatement?.period?.from)
        assertEquals("2026-03-31", shareCoordinator.lastPreparedStatement?.period?.to)
    }

    @Test
    fun `AdvancedShare overrides period, mode, and destination for exactly one share without persisting a new default`() = runTest(dispatcher) {
        movementDao.movements = listOf(LedgerMovementRow("v1", "2026-08-05", "Sales", "v-1", null, 1, "100", "dr"))
        val vm = createVm()
        advanceUntilIdle()
        val originalPeriod = vm.uiState.value.periodSelection
        shareCoordinator.prepareResult = LedgerStatementShareResult.Success(
            PreparedLedgerStatementPdf("content://x", "/cache/x.pdf", "x.pdf"),
        )

        vm.onEvent(LedgerStatementEvent.AdvancedPeriodChanged(LedgerPeriodSelection.ThisMonth))
        vm.onEvent(LedgerStatementEvent.AdvancedStatementModeChanged(LedgerStatementMode.Detailed))
        vm.onEvent(LedgerStatementEvent.AdvancedShare(LedgerShareDestination.PreviewPdf))
        advanceUntilIdle()

        assertEquals("2026-08-01", shareCoordinator.lastPreparedStatement?.period?.from)
        assertEquals(1, movementDao.inventoryQueryCalls)
        assertEquals("a one-time override must never be persisted as the new default", 0, sharingPreferencesStore.saveCalls)
        assertFalse(vm.uiState.value.showShareOptions)
        // TD-028: Preview now opens in-app rather than launching an external intent.
        assertEquals("x.pdf", vm.uiState.value.previewPdf?.suggestedFilename)

        // The next fast share must fall back to the original default, not the one-time override.
        shareCoordinator.shareIntentResult = LedgerStatementShareResult.Success(Intent())
        vm.onEvent(LedgerStatementEvent.ShareLedgerFast)
        advanceUntilIdle()
        assertEquals(originalPeriod, vm.uiState.value.periodSelection)
    }

    @Test
    fun `WhatsApp to Party surfaces a disabled error and releases the PDF when no recipient resolves`() = runTest(dispatcher) {
        // Default fixture ledger has alias = null: no explicit mobile field and no valid alias
        // fallback, so this must never fabricate a recipient or launch a send.
        val vm = createVm()
        advanceUntilIdle()
        shareCoordinator.prepareResult = LedgerStatementShareResult.Success(
            PreparedLedgerStatementPdf("content://x", "/cache/x.pdf", "x.pdf"),
        )
        vm.shareEffects.test {
            vm.onEvent(LedgerStatementEvent.AdvancedShare(LedgerShareDestination.WhatsAppToParty))
            advanceUntilIdle()
            expectNoEvents()
        }
        assertEquals(1, shareCoordinator.releaseCalls)
        assertTrue(vm.uiState.value.shareError.orEmpty().contains("no phone number is linked"))
    }

    @Test
    fun `WhatsApp to Party opens a direct-targeted intent when a valid 10-digit alias resolves`() = runTest(dispatcher) {
        ledgerDao.entity = ledgerDao.entity!!.copy(alias = "9876543210")
        val vm = createVm()
        advanceUntilIdle()
        shareCoordinator.prepareResult = LedgerStatementShareResult.Success(
            PreparedLedgerStatementPdf("content://x", "/cache/x.pdf", "x.pdf"),
        )
        shareCoordinator.whatsAppDirectIntentResult = LedgerStatementShareResult.Success(Intent())
        vm.shareEffects.test {
            vm.onEvent(LedgerStatementEvent.AdvancedShare(LedgerShareDestination.WhatsAppToParty))
            advanceUntilIdle()
            assertTrue(awaitItem() is LedgerStatementShareEffect.LaunchShare)
        }
        assertEquals("+919876543210", shareCoordinator.lastWhatsAppDirectNumber)
        assertEquals(0, shareCoordinator.releaseCalls)
    }

    @Test
    fun `WhatsApp to Party never sends automatically - it only ever hands the caller an intent to launch`() = runTest(dispatcher) {
        // The ViewModel/coordinator layer has no send/dispatch API at all: success can only ever
        // surface as a LaunchShare effect (an Intent for the Activity to start), which requires
        // the user to complete the Send themselves inside WhatsApp.
        ledgerDao.entity = ledgerDao.entity!!.copy(alias = "9876543210")
        val vm = createVm()
        advanceUntilIdle()
        shareCoordinator.prepareResult = LedgerStatementShareResult.Success(
            PreparedLedgerStatementPdf("content://x", "/cache/x.pdf", "x.pdf"),
        )
        shareCoordinator.whatsAppDirectIntentResult = LedgerStatementShareResult.Success(Intent())
        vm.shareEffects.test {
            vm.onEvent(LedgerStatementEvent.AdvancedShare(LedgerShareDestination.WhatsAppToParty))
            advanceUntilIdle()
            assertTrue(awaitItem() is LedgerStatementShareEffect.LaunchShare)
            expectNoEvents()
        }
    }

    @Test
    fun `WhatsApp to Party recipient resolution never triggers a Connector or Tally network call`() = runTest(dispatcher) {
        ledgerDao.entity = ledgerDao.entity!!.copy(alias = "9876543210")
        val vm = createVm()
        advanceUntilIdle()
        shareCoordinator.prepareResult = LedgerStatementShareResult.Success(
            PreparedLedgerStatementPdf("content://x", "/cache/x.pdf", "x.pdf"),
        )
        shareCoordinator.whatsAppDirectIntentResult = LedgerStatementShareResult.Success(Intent())
        vm.onEvent(LedgerStatementEvent.AdvancedShare(LedgerShareDestination.WhatsAppToParty))
        advanceUntilIdle()
        assertEquals(0, voucherRepository.refreshCalls)
    }

    @Test
    fun `Save PDF via the advanced options path emits the SAF create-document effect`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        shareCoordinator.prepareResult = LedgerStatementShareResult.Success(
            PreparedLedgerStatementPdf("content://x", "/cache/x.pdf", "x.pdf"),
        )
        vm.shareEffects.test {
            vm.onEvent(LedgerStatementEvent.AdvancedShare(LedgerShareDestination.SavePdf))
            advanceUntilIdle()
            assertTrue(awaitItem() is LedgerStatementShareEffect.CreatePdfDocument)
        }
    }

    @Test
    fun `PreviewLedgerFast opens the in-app preview directly, without the advanced options sheet`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        val prepared = PreparedLedgerStatementPdf("content://x", "/cache/x.pdf", "x.pdf")
        shareCoordinator.prepareResult = LedgerStatementShareResult.Success(prepared)
        vm.shareEffects.test {
            vm.onEvent(LedgerStatementEvent.PreviewLedgerFast)
            advanceUntilIdle()
            expectNoEvents()
        }
        assertEquals(prepared, vm.uiState.value.previewPdf)
        assertFalse("the direct Preview tap must never open the advanced options sheet", vm.uiState.value.showShareOptions)
    }

    @Test
    fun `PreviewLedgerFast uses the currently displayed period, not a stale one`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        vm.onEvent(LedgerStatementEvent.PeriodSelected(LedgerPeriodSelection.PreviousFinancialYear))
        advanceUntilIdle()
        shareCoordinator.prepareResult = LedgerStatementShareResult.Success(
            PreparedLedgerStatementPdf("content://x", "/cache/x.pdf", "x.pdf"),
        )
        vm.onEvent(LedgerStatementEvent.PreviewLedgerFast)
        advanceUntilIdle()
        assertEquals("2025-04-01", shareCoordinator.lastPreparedStatement?.period?.from)
        assertEquals("2026-03-31", shareCoordinator.lastPreparedStatement?.period?.to)
    }

    @Test
    fun `Save from a direct PreviewLedgerFast acts on the exact same prepared pdf, without re-preparing`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        shareCoordinator.prepareResult = LedgerStatementShareResult.Success(
            PreparedLedgerStatementPdf("content://x", "/cache/x.pdf", "x.pdf"),
        )
        vm.onEvent(LedgerStatementEvent.PreviewLedgerFast)
        advanceUntilIdle()
        val prepareCallsBeforeSave = shareCoordinator.prepareCalls
        vm.shareEffects.test {
            vm.onEvent(LedgerStatementEvent.SaveFromPreview)
            advanceUntilIdle()
            assertTrue(awaitItem() is LedgerStatementShareEffect.CreatePdfDocument)
        }
        assertEquals(prepareCallsBeforeSave, shareCoordinator.prepareCalls)
    }

    @Test
    fun `Preview PDF via the advanced options path opens the in-app preview with the exact prepared pdf`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        val prepared = PreparedLedgerStatementPdf("content://x", "/cache/x.pdf", "x.pdf")
        shareCoordinator.prepareResult = LedgerStatementShareResult.Success(prepared)
        vm.shareEffects.test {
            vm.onEvent(LedgerStatementEvent.AdvancedShare(LedgerShareDestination.PreviewPdf))
            advanceUntilIdle()
            expectNoEvents()
        }
        assertEquals(prepared, vm.uiState.value.previewPdf)
    }

    @Test
    fun `Save from preview emits the SAF create-document effect using the already-prepared pdf, without re-preparing`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        shareCoordinator.prepareResult = LedgerStatementShareResult.Success(
            PreparedLedgerStatementPdf("content://x", "/cache/x.pdf", "x.pdf"),
        )
        vm.onEvent(LedgerStatementEvent.AdvancedShare(LedgerShareDestination.PreviewPdf))
        advanceUntilIdle()
        val prepareCallsBeforeSave = shareCoordinator.prepareCalls
        vm.shareEffects.test {
            vm.onEvent(LedgerStatementEvent.SaveFromPreview)
            advanceUntilIdle()
            assertTrue(awaitItem() is LedgerStatementShareEffect.CreatePdfDocument)
        }
        assertEquals(prepareCallsBeforeSave, shareCoordinator.prepareCalls)
        assertEquals(null, vm.uiState.value.previewPdf)
    }

    @Test
    fun `Share from preview emits LaunchShare using the already-prepared pdf, without re-preparing`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        shareCoordinator.prepareResult = LedgerStatementShareResult.Success(
            PreparedLedgerStatementPdf("content://x", "/cache/x.pdf", "x.pdf"),
        )
        shareCoordinator.shareIntentResult = LedgerStatementShareResult.Success(Intent())
        vm.onEvent(LedgerStatementEvent.AdvancedShare(LedgerShareDestination.PreviewPdf))
        advanceUntilIdle()
        val prepareCallsBeforeShare = shareCoordinator.prepareCalls
        vm.shareEffects.test {
            vm.onEvent(LedgerStatementEvent.ShareFromPreview)
            advanceUntilIdle()
            assertTrue(awaitItem() is LedgerStatementShareEffect.LaunchShare)
        }
        assertEquals(prepareCallsBeforeShare, shareCoordinator.prepareCalls)
        assertEquals(null, vm.uiState.value.previewPdf)
    }

    @Test
    fun `Dismissing preview without saving or sharing releases the held pdf`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        shareCoordinator.prepareResult = LedgerStatementShareResult.Success(
            PreparedLedgerStatementPdf("content://x", "/cache/x.pdf", "x.pdf"),
        )
        vm.onEvent(LedgerStatementEvent.AdvancedShare(LedgerShareDestination.PreviewPdf))
        advanceUntilIdle()
        assertEquals(0, shareCoordinator.releaseCalls)

        vm.onEvent(LedgerStatementEvent.DismissPreview)

        assertEquals(1, shareCoordinator.releaseCalls)
        assertEquals(null, vm.uiState.value.previewPdf)
    }

    @Test
    fun `sharing never triggers a Connector or Tally network call`() = runTest(dispatcher) {
        // shareStatement never consults isOnline at all — sharing is unconditionally local-only,
        // so PDF generation must work identically whether the device is online or offline.
        val vm = createVm()
        advanceUntilIdle()
        shareCoordinator.prepareResult = LedgerStatementShareResult.Success(
            PreparedLedgerStatementPdf("content://x", "/cache/x.pdf", "x.pdf"),
        )
        shareCoordinator.shareIntentResult = LedgerStatementShareResult.Success(Intent())
        vm.onEvent(LedgerStatementEvent.ShareLedgerFast)
        advanceUntilIdle()
        assertEquals(0, voucherRepository.refreshCalls)
    }
}

private class FakeLedgerDao : LedgerDao {
    var entity: LedgerEntity? = null

    override suspend fun countForCompany(companyId: String): Int = if (entity != null) 1 else 0
    override suspend fun findById(companyId: String, ledgerId: String): LedgerEntity? =
        entity?.takeIf { it.companyId == companyId && it.id == ledgerId }
    override suspend fun getAllForCompany(companyId: String): List<LedgerEntity> = error("not used by this test")
    override suspend fun upsertAll(entities: List<LedgerEntity>) = error("not used by this test")
    override suspend fun deleteForCompany(companyId: String) = error("not used by this test")
    override suspend fun queryPage(
        companyId: String, query: String?, sortBy: String, ascending: Int, limit: Int, offset: Int, exactAliasFirst: Int,
    ): List<LedgerEntity> = error("not used by this test")
    override suspend fun countMatching(companyId: String, query: String?): Int = error("not used by this test")
}

private class FakeLedgerMovementDao : LedgerMovementDao {
    var lastSales: List<LedgerMovementRow> = emptyList()
    var movements: List<LedgerMovementRow> = emptyList()
    var earliestDate: String? = null
    var inventoryLines: List<LedgerMovementInventoryRow> = emptyList()
    var inventoryQueryCalls = 0
        private set

    override suspend fun lastSalesMovements(companyId: String, ledgerName: String, limit: Int): List<LedgerMovementRow> =
        lastSales.take(limit)
    override suspend fun movementsInRange(companyId: String, ledgerName: String, from: String, to: String): List<LedgerMovementRow> =
        movements.filter { it.date in from..to }
    override suspend fun narrations(companyId: String, voucherIds: List<String>): List<VoucherNarrationRow> = emptyList()
    override suspend fun earliestSyncedDate(companyId: String): String? = earliestDate
    override suspend fun inventoryLinesForVouchers(companyId: String, voucherIds: List<String>): List<LedgerMovementInventoryRow> {
        inventoryQueryCalls++
        return inventoryLines.filter { it.voucherId in voucherIds }
    }
}

private class FakeVoucherRepository : VoucherRepository {
    var refreshResult: AppResult<VoucherPage>? = null
    var refreshCalls = 0
        private set
    var lastQuery: VoucherQuery? = null

    override suspend fun listVouchers(query: VoucherQuery): AppResult<VoucherPage> = error("not used by this test")

    override suspend fun refreshVouchers(query: VoucherQuery): AppResult<VoucherPage> {
        refreshCalls++
        lastQuery = query
        return refreshResult ?: AppResult.Success(
            VoucherPage(query.companyId, emptyList(), 1, query.pageSize, 0, 0),
        )
    }

    override suspend fun getVoucherDetails(companyId: String, voucherId: String): AppResult<VoucherDetails> =
        error("not used by this test")
    override suspend fun refreshVoucherDetails(companyId: String, voucherId: String): AppResult<VoucherDetails> =
        error("not used by this test")
    override suspend fun getCachedVoucherSummary(companyId: String, voucherId: String): VoucherSummary? = null
}

private class FakeShareCoordinator : LedgerStatementShareCoordinator {
    var prepareResult: LedgerStatementShareResult<PreparedLedgerStatementPdf> = LedgerStatementShareResult.Failure("unused")
    var shareIntentResult: LedgerStatementShareResult<Intent> = LedgerStatementShareResult.Failure("unused")
    var whatsAppIntentResult: LedgerStatementShareResult<Intent> = LedgerStatementShareResult.Failure("unused")
    var whatsAppDirectIntentResult: LedgerStatementShareResult<Intent> = LedgerStatementShareResult.Failure("unused")
    var lastWhatsAppDirectNumber: String? = null
        private set
    var lastPreparedStatement: com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatement? = null
        private set
    var releaseCalls = 0
        private set
    var prepareCalls = 0
        private set

    override suspend fun preparePdf(
        statement: com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatement,
        companyName: String?,
    ): LedgerStatementShareResult<PreparedLedgerStatementPdf> {
        prepareCalls++
        lastPreparedStatement = statement
        return prepareResult
    }

    override fun createPdfShareIntent(pdf: PreparedLedgerStatementPdf): LedgerStatementShareResult<Intent> = shareIntentResult
    override fun createWhatsAppShareIntent(pdf: PreparedLedgerStatementPdf): LedgerStatementShareResult<Intent> = whatsAppIntentResult
    override fun createWhatsAppDirectIntent(pdf: PreparedLedgerStatementPdf, e164Number: String): LedgerStatementShareResult<Intent> {
        lastWhatsAppDirectNumber = e164Number
        return whatsAppDirectIntentResult
    }
    override suspend fun savePdf(pdf: PreparedLedgerStatementPdf, destination: Uri): LedgerStatementShareResult<Unit> =
        LedgerStatementShareResult.Success(Unit)

    override fun releasePdf(pdf: PreparedLedgerStatementPdf) {
        releaseCalls++
    }
}

private class FakeLedgerPdfPageRenderer : com.budcom.android.core.pdf.PdfPageRenderer {
    override suspend fun open(filePath: String): com.budcom.android.core.pdf.PdfPreviewDocument? = null
}

private class FakeCompanySession(initial: String?) : CompanySessionPort {
    val selected = MutableStateFlow(initial)
    override fun observeSelectedCompanyId(): Flow<String?> = selected
    override suspend fun readSelectedCompany(): AppResult<SelectedCompanyStatus> =
        AppResult.Success(SelectedCompanyStatus(selected.value, selected.value))
    override suspend fun validateSessionStatus(): AppResult<SessionValidationStatus> =
        AppResult.Failure(AppError.Message("unused"))
}

private class StatementFakeConnectivity(online: Boolean) : NetworkConnectivityObserver {
    private val flow = MutableStateFlow(online)
    override val isOnline: Flow<Boolean> = flow
    override fun current(): Boolean = flow.value
}

private class FakeLedgerSharingPreferencesStore(
    initial: LedgerSharingPreferences = LedgerSharingPreferences(),
) : LedgerSharingPreferencesStore {
    private val flow = MutableStateFlow(initial)
    override val observation: Flow<LedgerSharingPreferences> = flow
    var saveCalls = 0
        private set

    override suspend fun save(preferences: LedgerSharingPreferences) {
        saveCalls++
        flow.value = preferences
    }
}
