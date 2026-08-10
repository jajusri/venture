package com.budcom.android.feature.voucher.domain.usecase

import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.feature.company.domain.model.CompanyDiscoverySnapshot
import com.budcom.android.feature.company.domain.model.ConnectorCompany
import com.budcom.android.feature.company.domain.model.ConnectorSessionSnapshot
import com.budcom.android.feature.company.domain.model.SessionSelectedCompany
import com.budcom.android.feature.company.domain.model.SessionValidationOutcome
import com.budcom.android.feature.company.domain.repository.CompanyRepository
import com.budcom.android.feature.voucher.domain.model.VoucherDateRange
import com.budcom.android.feature.voucher.domain.model.VoucherDetails
import com.budcom.android.feature.voucher.domain.model.VoucherPage
import com.budcom.android.feature.voucher.domain.model.VoucherQuery
import com.budcom.android.feature.voucher.domain.model.VoucherSummary
import com.budcom.android.feature.voucher.domain.repository.VoucherRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconcileVoucherWindowsUseCaseTest {
    private val fixedClock = Clock.fixed(Instant.parse("2026-07-27T00:00:00Z"), ZoneOffset.UTC)
    private val today = LocalDate.now(fixedClock)

    @Test
    fun `refreshes the fast recent window first, then progressively older windows`() = runTest {
        val repo = RecordingRepository()
        // booksFrom 90 days back so the walk spans exactly 3 default-size windows.
        val useCase = useCase(repo, booksFrom = today.minusDays(89).toString())

        val outcome = useCase("company-a")

        assertTrue(outcome is VoucherReconciliationOutcome.Completed)
        assertEquals(3, (outcome as VoucherReconciliationOutcome.Completed).windowsSynced)
        assertTrue("booksFrom was provided, so scope must be reported authoritative", outcome.scopeIsAuthoritative)
        for (index in 0 until repo.refreshedRanges.size - 1) {
            assertTrue(repo.refreshedRanges[index].from > repo.refreshedRanges[index + 1].to)
        }
    }

    @Test
    fun `derives Full Reconcile coverage from the company's own booksFrom, not a fixed lookback`() = runTest {
        val repo = RecordingRepository()
        // 400 days back — deliberately past the old fixed 365-day default, to prove coverage now
        // tracks the authoritative booksFrom rather than an invented boundary.
        val useCase = useCase(repo, booksFrom = today.minusDays(399).toString())

        val outcome = useCase("company-a")

        val oldestWindow = repo.refreshedRanges.last()
        assertEquals(today.minusDays(399).toString(), oldestWindow.from)
        assertTrue((outcome as VoucherReconciliationOutcome.Completed).scopeIsAuthoritative)
    }

    @Test
    fun `falls back to the default lookback when booksFrom is absent for the company, and marks the scope non-authoritative`() = runTest {
        val repo = RecordingRepository()
        val useCase = useCase(repo, booksFrom = null)

        val outcome = useCase("company-a")

        assertTrue(outcome is VoucherReconciliationOutcome.Completed)
        val expectedWindows = com.budcom.android.feature.voucher.domain.model.VoucherWindowPlanner.plan(clock = fixedClock).size
        assertEquals(expectedWindows, (outcome as VoucherReconciliationOutcome.Completed).windowsSynced)
        assertFalse(
            "booksFrom was absent — Completed must never claim authoritative full-history coverage",
            outcome.scopeIsAuthoritative,
        )
    }

    @Test
    fun `a failed fallback-scope reconciliation also reports the scope as non-authoritative`() = runTest {
        val repo = RecordingRepository(failOnWindowIndex = 0)
        val useCase = useCase(repo, booksFrom = null)

        val outcome = useCase("company-a")

        assertTrue(outcome is VoucherReconciliationOutcome.PartiallyCompleted)
        assertFalse((outcome as VoucherReconciliationOutcome.PartiallyCompleted).scopeIsAuthoritative)
    }

    @Test
    fun `falls back to the default lookback when booksFrom does not parse as a plain date`() = runTest {
        val repo = RecordingRepository()
        val useCase = useCase(repo, booksFrom = "not-a-date")

        val outcome = useCase("company-a")

        assertTrue(outcome is VoucherReconciliationOutcome.Completed)
        val expectedWindows = com.budcom.android.feature.voucher.domain.model.VoucherWindowPlanner.plan(clock = fixedClock).size
        assertEquals(expectedWindows, (outcome as VoucherReconciliationOutcome.Completed).windowsSynced)
    }

    @Test
    fun `stops at the first failing window and reports how many completed before it`() = runTest {
        val repo = RecordingRepository(failOnWindowIndex = 1)
        val useCase = useCase(repo, booksFrom = today.minusDays(89).toString())

        val outcome = useCase("company-a")

        assertTrue(outcome is VoucherReconciliationOutcome.PartiallyCompleted)
        assertEquals(1, (outcome as VoucherReconciliationOutcome.PartiallyCompleted).windowsSynced)
        assertEquals(2, repo.refreshedRanges.size)
    }

    @Test
    fun `a second concurrent reconciliation for the same company is rejected as already running`() = runTest {
        val repo = RecordingRepository(delayEachWindow = true)
        val useCase = useCase(repo, booksFrom = today.minusDays(89).toString())

        val first = async { useCase("company-a") }
        repo.awaitFirstCallStarted()
        val second = useCase("company-a")

        assertEquals(VoucherReconciliationOutcome.AlreadyRunning, second)
        repo.release()
        assertTrue(first.await() is VoucherReconciliationOutcome.Completed)
    }

    @Test
    fun `a different company can reconcile concurrently without being blocked`() = runTest {
        val repoA = RecordingRepository()
        val repoB = RecordingRepository()
        val useCaseA = useCase(repoA, booksFrom = today.minusDays(29).toString())
        val useCaseB = useCase(repoB, booksFrom = today.minusDays(29).toString())

        val results = listOf(
            async { useCaseA("company-a") },
            async { useCaseB("company-b") },
        ).awaitAll()

        assertTrue(results.all { it is VoucherReconciliationOutcome.Completed })
    }

    private fun useCase(
        repo: VoucherRepository,
        booksFrom: String?,
        companyId: String = "company-a",
    ) = ReconcileVoucherWindowsUseCase(
        RefreshVouchersUseCase(repo),
        FakeCompanyRepository(companyId, booksFrom),
        fixedClock,
    )

    private class FakeCompanyRepository(
        private val companyId: String,
        private val booksFrom: String?,
    ) : CompanyRepository {
        override fun observeSelectedCompanyId(): Flow<String?> = flowOf(companyId)

        override suspend fun loadCompanies(): AppResult<CompanyDiscoverySnapshot> = AppResult.Success(
            CompanyDiscoverySnapshot(
                items = listOf(ConnectorCompany(companyId, "Company", null, booksFrom, null)),
                schemaVersion = "1.0.0",
                dataFreshnessAt = "2026-07-27T00:00:00Z",
                contractVersion = "1",
                status = "SUCCESS",
                tallyReachable = true,
                dataQualityStatus = null,
                dataQualityReason = null,
                reason = null,
            ),
        )

        override suspend fun refreshCompanies(): AppResult<CompanyDiscoverySnapshot> = loadCompanies()
        override suspend fun getSession(): AppResult<ConnectorSessionSnapshot> =
            AppResult.Failure(AppError.Message("unused"))
        override suspend fun restoreSelection(): AppResult<SessionValidationOutcome?> =
            AppResult.Failure(AppError.Message("unused"))
        override suspend fun selectCompany(companyId: String): AppResult<SessionValidationOutcome> =
            AppResult.Failure(AppError.Message("unused"))
        override suspend fun validateSession(): AppResult<SessionValidationOutcome> =
            AppResult.Failure(AppError.Message("unused"))
        override suspend fun clearSelection(): AppResult<Unit> = AppResult.Failure(AppError.Message("unused"))
    }

    private class RecordingRepository(
        private val failOnWindowIndex: Int? = null,
        private val delayEachWindow: Boolean = false,
    ) : VoucherRepository {
        val refreshedRanges = mutableListOf<VoucherDateRange>()
        private var startedSignal: kotlinx.coroutines.CompletableDeferred<Unit>? =
            if (delayEachWindow) kotlinx.coroutines.CompletableDeferred() else null
        private var releaseGate: kotlinx.coroutines.CompletableDeferred<Unit>? =
            if (delayEachWindow) kotlinx.coroutines.CompletableDeferred() else null

        suspend fun awaitFirstCallStarted() {
            startedSignal?.await()
        }

        fun release() {
            releaseGate?.complete(Unit)
        }

        override suspend fun listVouchers(query: VoucherQuery): AppResult<VoucherPage> =
            AppResult.Failure(AppError.Message("unused"))

        override suspend fun refreshVouchers(query: VoucherQuery): AppResult<VoucherPage> {
            if (delayEachWindow) {
                startedSignal?.complete(Unit)
                releaseGate?.await()
            }
            val index = refreshedRanges.size
            refreshedRanges += query.dateRange
            if (failOnWindowIndex == index) return AppResult.Failure(AppError.Offline())
            return AppResult.Success(VoucherPage(query.companyId, emptyList(), 1, 100, 0, 1))
        }

        override suspend fun getVoucherDetails(companyId: String, voucherId: String): AppResult<VoucherDetails> =
            AppResult.Failure(AppError.Message("unused"))

        override suspend fun refreshVoucherDetails(companyId: String, voucherId: String): AppResult<VoucherDetails> =
            AppResult.Failure(AppError.Message("unused"))

        override suspend fun getCachedVoucherSummary(companyId: String, voucherId: String): VoucherSummary? = null
    }
}
