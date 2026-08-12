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
import com.budcom.android.feature.masterdata.ledger.data.local.LedgerMovementRow
import com.budcom.android.feature.masterdata.ledger.data.local.VoucherNarrationRow
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerPeriodSelection
import com.budcom.android.feature.masterdata.ledger.domain.usecase.GetLocalLedgerStatementUseCase
import com.budcom.android.feature.masterdata.ledger.domain.usecase.RefreshLedgerCoverageUseCase
import com.budcom.android.feature.masterdata.ledger.sharing.LedgerStatementShareCoordinator
import com.budcom.android.feature.masterdata.ledger.sharing.LedgerStatementShareResult
import com.budcom.android.feature.masterdata.ledger.sharing.PreparedLedgerStatementPdf
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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        ledgerDao = FakeLedgerDao()
        movementDao = FakeLedgerMovementDao()
        voucherRepository = FakeVoucherRepository()
        companySession = FakeCompanySession("estimation")
        connectivity = StatementFakeConnectivity(true)
        shareCoordinator = FakeShareCoordinator()
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

    private fun createVm(ledgerId: String = "ledger-1") = LedgerStatementViewModel(
        savedStateHandle = SavedStateHandle(mapOf(LedgerStatementViewModel.LEDGER_ID_ARG to ledgerId)),
        getLocalStatement = GetLocalLedgerStatementUseCase(ledgerDao, movementDao),
        refreshCoverage = RefreshLedgerCoverageUseCase(RefreshVouchersUseCase(voucherRepository)),
        companySession = companySession,
        connectivityObserver = connectivity,
        shareCoordinator = shareCoordinator,
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
            vm.onEvent(LedgerStatementEvent.SharePdf)
            advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is LedgerStatementShareEffect.LaunchShare)
        }
    }

    @Test
    fun `a failed PDF prepare surfaces a share error instead of a silent failure`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        shareCoordinator.prepareResult = LedgerStatementShareResult.Failure("boom")
        vm.onEvent(LedgerStatementEvent.SharePdf)
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
}

private class FakeLedgerDao : LedgerDao {
    var entity: LedgerEntity? = null

    override suspend fun countForCompany(companyId: String): Int = if (entity != null) 1 else 0
    override suspend fun findById(companyId: String, ledgerId: String): LedgerEntity? =
        entity?.takeIf { it.companyId == companyId && it.id == ledgerId }
    override suspend fun upsertAll(entities: List<LedgerEntity>) = error("not used by this test")
    override suspend fun deleteForCompany(companyId: String) = error("not used by this test")
    override suspend fun queryPage(
        companyId: String, query: String?, sortBy: String, ascending: Int, limit: Int, offset: Int,
    ): List<LedgerEntity> = error("not used by this test")
    override suspend fun countMatching(companyId: String, query: String?): Int = error("not used by this test")
}

private class FakeLedgerMovementDao : LedgerMovementDao {
    var lastSales: List<LedgerMovementRow> = emptyList()
    var movements: List<LedgerMovementRow> = emptyList()
    var earliestDate: String? = null

    override suspend fun lastSalesMovements(companyId: String, ledgerName: String, limit: Int): List<LedgerMovementRow> =
        lastSales.take(limit)
    override suspend fun movementsInRange(companyId: String, ledgerName: String, from: String, to: String): List<LedgerMovementRow> =
        movements.filter { it.date in from..to }
    override suspend fun narrations(companyId: String, voucherIds: List<String>): List<VoucherNarrationRow> = emptyList()
    override suspend fun earliestSyncedDate(companyId: String): String? = earliestDate
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

    override suspend fun preparePdf(
        statement: com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatement,
        companyName: String?,
    ): LedgerStatementShareResult<PreparedLedgerStatementPdf> = prepareResult

    override fun createPdfShareIntent(pdf: PreparedLedgerStatementPdf): LedgerStatementShareResult<Intent> = shareIntentResult

    override suspend fun savePdf(pdf: PreparedLedgerStatementPdf, destination: Uri): LedgerStatementShareResult<Unit> =
        LedgerStatementShareResult.Success(Unit)

    override fun releasePdf(pdf: PreparedLedgerStatementPdf) = Unit
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
