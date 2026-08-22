package com.budcom.android.feature.sync.domain.usecase

import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.feature.company.domain.port.CompanySessionPort
import com.budcom.android.feature.company.domain.port.SelectedCompanyStatus
import com.budcom.android.feature.company.domain.port.SessionValidationStatus
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerPage
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerQuery
import com.budcom.android.feature.masterdata.ledger.domain.repository.LedgerRepository
import com.budcom.android.feature.masterdata.ledger.domain.usecase.RefreshLedgersUseCase
import com.budcom.android.feature.sync.domain.model.SyncCounts
import com.budcom.android.feature.sync.domain.model.SyncMode
import com.budcom.android.feature.sync.domain.model.SyncOutcome
import com.budcom.android.feature.sync.domain.model.SyncProgress
import com.budcom.android.feature.sync.domain.model.SyncRunStatus
import com.budcom.android.feature.sync.domain.model.SyncRunSummary
import com.budcom.android.feature.sync.domain.model.SyncStatisticsSummary
import com.budcom.android.feature.sync.domain.model.SyncTarget
import com.budcom.android.feature.sync.domain.repository.SyncRepository
import com.budcom.android.feature.voucher.domain.model.VoucherPage
import com.budcom.android.feature.voucher.domain.model.VoucherQuery
import com.budcom.android.feature.voucher.domain.repository.VoucherRepository
import com.budcom.android.feature.voucher.domain.usecase.RefreshVouchersUseCase
import com.budcom.android.feature.voucher.domain.model.VoucherDetails
import com.budcom.android.feature.voucher.domain.model.VoucherSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncUseCasesTest {

    private fun succeeded(target: SyncTarget) = SyncOutcome.Succeeded(
        target = target,
        syncRunId = "r1",
        status = SyncRunStatus.Completed,
        progress = SyncProgress(
            "r1",
            SyncRunStatus.Completed,
            SyncCounts(1, 1, 0, 0, 0, 1),
            "t0",
            "t1",
            1,
            null,
            false,
        ),
        validationIssueCount = 0,
        extractionCompleteness = null,
        statistics = SyncStatisticsSummary("t1", 1),
        warningMessage = null,
    )

    private fun emptyPage() = VoucherPage(
        companyId = "estimation",
        items = emptyList(),
        page = 1,
        pageSize = 50,
        totalItems = 0,
        totalPages = 1,
    )

    private fun emptyLedgerPage() = LedgerPage(
        items = emptyList(),
        page = 1,
        pageSize = 50,
        totalItems = 0,
        totalPages = 1,
        dataFreshnessAt = null,
    )

    private fun useCase(
        syncRepo: FakeSyncRepo,
        company: FakeCompany = FakeCompany("estimation"),
        voucherRepo: FakeVoucherRepo = FakeVoucherRepo(refreshResult = AppResult.Success(emptyPage())),
        ledgerRepo: FakeLedgerRepo = FakeLedgerRepo(refreshResult = AppResult.Success(emptyLedgerPage())),
    ) = StartTargetSyncUseCase(
        syncRepo,
        company,
        RefreshVouchersUseCase(voucherRepo),
        RefreshLedgersUseCase(ledgerRepo),
    )

    @Test
    fun `voucher sync success followed by a successful window fetch reports the original success`() = runTest {
        val syncRepo = FakeSyncRepo(AppResult.Success(succeeded(SyncTarget.Vouchers)))
        val voucherRepo = FakeVoucherRepo(refreshResult = AppResult.Success(emptyPage()))
        val result = useCase(syncRepo, voucherRepo = voucherRepo)(SyncTarget.Vouchers)

        assertTrue(result is AppResult.Success)
        assertEquals(SyncTarget.Vouchers, (result as AppResult.Success).value.target)
        assertEquals(1, voucherRepo.refreshCalls)
        assertEquals("estimation", voucherRepo.lastQuery?.companyId)
    }

    @Test
    fun `voucher sync success followed by a failed window fetch is reported as a failure, not Completed`() = runTest {
        val syncRepo = FakeSyncRepo(AppResult.Success(succeeded(SyncTarget.Vouchers)))
        val voucherRepo = FakeVoucherRepo(
            refreshResult = AppResult.Failure(AppError.Message("Room persistence failed")),
        )
        val result = useCase(syncRepo, voucherRepo = voucherRepo)(SyncTarget.Vouchers)

        assertTrue(result is AppResult.Failure)
        assertEquals(1, voucherRepo.refreshCalls)
    }

    @Test
    fun `voucher extraction failure never attempts a window fetch`() = runTest {
        val syncRepo = FakeSyncRepo(
            AppResult.Failure(AppError.Message("Connector extraction failed")),
        )
        val voucherRepo = FakeVoucherRepo(refreshResult = AppResult.Success(emptyPage()))
        val result = useCase(syncRepo, voucherRepo = voucherRepo)(SyncTarget.Vouchers)

        assertTrue(result is AppResult.Failure)
        assertEquals(0, voucherRepo.refreshCalls)
    }

    /**
     * TD-039 fix regression test: before this fix, a Ledgers "Sync Now" left Android's Room cache
     * (`cached_ledgers`) untouched, so Connect's Customer/Supplier population (which reads Room via
     * `ReconcilePartiesFromLedgersUseCase`) silently stayed stale-or-empty after a "successful"
     * sync — physically reproduced on a real device.
     */
    @Test
    fun `ledger sync success is followed by a Room refresh, mirroring the Voucher window fetch`() = runTest {
        val syncRepo = FakeSyncRepo(AppResult.Success(succeeded(SyncTarget.Ledgers)))
        val ledgerRepo = FakeLedgerRepo(refreshResult = AppResult.Success(emptyLedgerPage()))
        val voucherRepo = FakeVoucherRepo(refreshResult = AppResult.Success(emptyPage()))
        val result = useCase(syncRepo, voucherRepo = voucherRepo, ledgerRepo = ledgerRepo)(SyncTarget.Ledgers)

        assertTrue(result is AppResult.Success)
        assertEquals(SyncTarget.Ledgers, (result as AppResult.Success).value.target)
        assertEquals(1, ledgerRepo.refreshCalls)
        // Ledgers must never also trigger the unrelated Voucher window fetch.
        assertEquals(0, voucherRepo.refreshCalls)
    }

    @Test
    fun `ledger sync success followed by a failed Room refresh is reported as a failure, not Completed`() = runTest {
        val syncRepo = FakeSyncRepo(AppResult.Success(succeeded(SyncTarget.Ledgers)))
        val ledgerRepo = FakeLedgerRepo(
            refreshResult = AppResult.Failure(AppError.Message("Room persistence failed")),
        )
        val result = useCase(syncRepo, ledgerRepo = ledgerRepo)(SyncTarget.Ledgers)

        assertTrue(result is AppResult.Failure)
        assertEquals(1, ledgerRepo.refreshCalls)
    }

    @Test
    fun `ledger extraction failure never attempts a Room refresh`() = runTest {
        val syncRepo = FakeSyncRepo(AppResult.Failure(AppError.Message("Connector extraction failed")))
        val ledgerRepo = FakeLedgerRepo(refreshResult = AppResult.Success(emptyLedgerPage()))
        val result = useCase(syncRepo, ledgerRepo = ledgerRepo)(SyncTarget.Ledgers)

        assertTrue(result is AppResult.Failure)
        assertEquals(0, ledgerRepo.refreshCalls)
    }

    @Test
    fun `stock item sync touches neither the ledger Room refresh nor the voucher window fetch`() = runTest {
        val syncRepo = FakeSyncRepo(AppResult.Success(succeeded(SyncTarget.StockItems)))
        val ledgerRepo = FakeLedgerRepo(refreshResult = AppResult.Success(emptyLedgerPage()))
        val voucherRepo = FakeVoucherRepo(refreshResult = AppResult.Success(emptyPage()))
        val result = useCase(syncRepo, voucherRepo = voucherRepo, ledgerRepo = ledgerRepo)(SyncTarget.StockItems)

        assertTrue(result is AppResult.Success)
        assertEquals(0, ledgerRepo.refreshCalls)
        assertEquals(0, voucherRepo.refreshCalls)
        assertEquals(1, syncRepo.startCalls)
    }

    @Test
    fun `missing company blocks start before touching any repository`() = runTest {
        val syncRepo = FakeSyncRepo(AppResult.Success(succeeded(SyncTarget.Vouchers)))
        val voucherRepo = FakeVoucherRepo(refreshResult = AppResult.Success(emptyPage()))
        val ledgerRepo = FakeLedgerRepo(refreshResult = AppResult.Success(emptyLedgerPage()))
        val result = useCase(syncRepo, FakeCompany(null), voucherRepo, ledgerRepo)(SyncTarget.Vouchers)

        assertTrue(result is AppResult.Failure)
        assertEquals(0, syncRepo.startCalls)
        assertEquals(0, voucherRepo.refreshCalls)
        assertEquals(0, ledgerRepo.refreshCalls)
    }
}

private class FakeSyncRepo(private val startResult: AppResult<SyncOutcome>) : SyncRepository {
    var startCalls = 0
    override fun bindCompany(companyId: String?) = Unit
    override suspend fun startSync(target: SyncTarget, mode: SyncMode): AppResult<SyncOutcome> {
        startCalls++
        return startResult
    }
    override suspend fun cancelSync(target: SyncTarget): AppResult<SyncProgress> = error("unused")
    override suspend fun getStatus(target: SyncTarget): AppResult<SyncProgress> = error("unused")
    override suspend fun getStatistics(target: SyncTarget): AppResult<SyncStatisticsSummary> = error("unused")
    override suspend fun listRecentRuns(target: SyncTarget, limit: Int): AppResult<List<SyncRunSummary>> =
        AppResult.Success(emptyList())
}

private class FakeVoucherRepo(private val refreshResult: AppResult<VoucherPage>) : VoucherRepository {
    var refreshCalls = 0
    var lastQuery: VoucherQuery? = null
    override suspend fun listVouchers(query: VoucherQuery): AppResult<VoucherPage> = error("unused")
    override suspend fun refreshVouchers(query: VoucherQuery): AppResult<VoucherPage> {
        refreshCalls++
        lastQuery = query
        return refreshResult
    }
    override suspend fun getVoucherDetails(companyId: String, voucherId: String): AppResult<VoucherDetails> =
        error("unused")
    override suspend fun refreshVoucherDetails(companyId: String, voucherId: String): AppResult<VoucherDetails> =
        error("unused")
    override suspend fun getCachedVoucherSummary(companyId: String, voucherId: String): VoucherSummary? = null
}

private class FakeLedgerRepo(private val refreshResult: AppResult<LedgerPage>) : LedgerRepository {
    var refreshCalls = 0
    override suspend fun listLedgers(query: LedgerQuery): AppResult<LedgerPage> = error("unused")
    override suspend fun refreshLedgers(query: LedgerQuery): AppResult<LedgerPage> {
        refreshCalls++
        return refreshResult
    }
}

private class FakeCompany(initial: String?) : CompanySessionPort {
    private val selected = MutableStateFlow(initial)
    override fun observeSelectedCompanyId(): Flow<String?> = selected
    override suspend fun readSelectedCompany(): AppResult<SelectedCompanyStatus> =
        AppResult.Success(SelectedCompanyStatus(selected.value, selected.value))
    override suspend fun validateSessionStatus(): AppResult<SessionValidationStatus> = error("unused")
}
