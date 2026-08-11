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
import com.budcom.android.feature.masterdata.ledger.domain.model.AmountSide
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatement
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatementAmount
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatementCoverage
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatementDateRange
import com.budcom.android.feature.masterdata.ledger.domain.repository.LedgerStatementRepository
import com.budcom.android.feature.masterdata.ledger.domain.usecase.GetLedgerStatementUseCase
import com.budcom.android.feature.masterdata.ledger.domain.usecase.RefreshLedgerStatementUseCase
import com.budcom.android.feature.masterdata.ledger.sharing.LedgerStatementShareCoordinator
import com.budcom.android.feature.masterdata.ledger.sharing.LedgerStatementShareResult
import com.budcom.android.feature.masterdata.ledger.sharing.PreparedLedgerStatementPdf
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LedgerStatementViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeRepository
    private lateinit var companySession: FakeCompanySession
    private lateinit var connectivity: StatementFakeConnectivity
    private lateinit var shareCoordinator: FakeShareCoordinator

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeRepository()
        companySession = FakeCompanySession("estimation")
        connectivity = StatementFakeConnectivity(true)
        shareCoordinator = FakeShareCoordinator()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createVm(ledgerId: String = "ledger-1") = LedgerStatementViewModel(
        savedStateHandle = SavedStateHandle(mapOf(LedgerStatementViewModel.LEDGER_ID_ARG to ledgerId)),
        getLedgerStatement = GetLedgerStatementUseCase(repository),
        refreshLedgerStatement = RefreshLedgerStatementUseCase(repository),
        companySession = companySession,
        connectivityObserver = connectivity,
        shareCoordinator = shareCoordinator,
    )

    @Test
    fun `loads the cached statement on start`() = runTest(dispatcher) {
        repository.cached = sampleStatement()
        val vm = createVm()
        advanceUntilIdle()
        assertEquals("Acme Traders", vm.uiState.value.content?.ledgerName)
        assertEquals(0, repository.refreshCalls)
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
        repository.cached = sampleStatement()
        val vm = createVm()
        advanceUntilIdle()
        repository.refreshResult = AppResult.Failure(AppError.Timeout())
        vm.onEvent(LedgerStatementEvent.Refresh)
        advanceUntilIdle()
        assertEquals("Acme Traders", vm.uiState.value.content?.ledgerName)
        assertTrue(vm.uiState.value.refreshError != null)
    }

    @Test
    fun `changing the period triggers a refresh with the new range`() = runTest(dispatcher) {
        repository.cached = sampleStatement()
        val vm = createVm()
        advanceUntilIdle()
        vm.onEvent(LedgerStatementEvent.PeriodChanged("2026-01-01", "2026-01-31"))
        advanceUntilIdle()
        assertEquals("2026-01-01", repository.lastRange?.from)
        assertEquals("2026-01-31", repository.lastRange?.to)
        assertEquals(1, repository.refreshCalls)
    }

    @Test
    fun `tapping a transaction with a real voucherId emits an open-voucher-details effect`() = runTest(dispatcher) {
        repository.cached = sampleStatement()
        val vm = createVm()
        advanceUntilIdle()
        vm.effects.test {
            vm.onEvent(LedgerStatementEvent.TransactionTapped("stable-guid-42"))
            assertEquals(LedgerStatementEffect.OpenVoucherDetails("stable-guid-42"), awaitItem())
        }
    }

    @Test
    fun `tapping a transaction with a blank voucherId never emits a navigation effect`() = runTest(dispatcher) {
        repository.cached = sampleStatement()
        val vm = createVm()
        advanceUntilIdle()
        vm.effects.test {
            vm.onEvent(LedgerStatementEvent.TransactionTapped(""))
            expectNoEvents()
        }
    }

    @Test
    fun `sharing the PDF after a successful prepare launches a share intent`() = runTest(dispatcher) {
        repository.cached = sampleStatement()
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
        repository.cached = sampleStatement()
        val vm = createVm()
        advanceUntilIdle()
        shareCoordinator.prepareResult = LedgerStatementShareResult.Failure("boom")
        vm.onEvent(LedgerStatementEvent.SharePdf)
        advanceUntilIdle()
        assertEquals("boom", vm.uiState.value.shareError)
    }
}

private fun sampleStatement() = LedgerStatement(
    ledgerId = "ledger-1",
    ledgerName = "Acme Traders",
    parentGroup = "Sundry Debtors",
    period = LedgerStatementDateRange("2026-07-01", "2026-07-31"),
    openingBalance = LedgerStatementAmount("0", AmountSide.Dr),
    closingBalance = LedgerStatementAmount("500", AmountSide.Dr),
    transactions = emptyList(),
    coverage = LedgerStatementCoverage(true, true, "2026-07-01", "2026-08-10", null),
)

private class FakeRepository : LedgerStatementRepository {
    var cached: LedgerStatement? = null
    var refreshResult: AppResult<LedgerStatement>? = null
    var refreshCalls = 0
        private set
    var lastRange: LedgerStatementDateRange? = null

    override suspend fun getLedgerStatement(
        companyId: String,
        ledgerId: String,
        range: LedgerStatementDateRange,
    ): AppResult<LedgerStatement> {
        lastRange = range
        return cached?.let { AppResult.Success(it) } ?: AppResult.Failure(AppError.Message("no cache"))
    }

    override suspend fun refreshLedgerStatement(
        companyId: String,
        ledgerId: String,
        range: LedgerStatementDateRange,
    ): AppResult<LedgerStatement> {
        refreshCalls++
        lastRange = range
        return refreshResult ?: cached?.let { AppResult.Success(it) } ?: AppResult.Failure(AppError.Message("no cache"))
    }
}

private class FakeShareCoordinator : LedgerStatementShareCoordinator {
    var prepareResult: LedgerStatementShareResult<PreparedLedgerStatementPdf> = LedgerStatementShareResult.Failure("unused")
    var shareIntentResult: LedgerStatementShareResult<Intent> = LedgerStatementShareResult.Failure("unused")

    override suspend fun preparePdf(
        statement: LedgerStatement,
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
