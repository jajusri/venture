package com.budcom.android.feature.voucher.domain.usecase

import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.feature.voucher.domain.model.VoucherDateRange
import com.budcom.android.feature.voucher.domain.model.VoucherDetails
import com.budcom.android.feature.voucher.domain.model.VoucherPage
import com.budcom.android.feature.voucher.domain.model.VoucherQuery
import com.budcom.android.feature.voucher.domain.model.VoucherSummary
import com.budcom.android.feature.voucher.domain.repository.VoucherRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconcileVoucherWindowsUseCaseTest {

    @Test
    fun `refreshes the fast recent window first, then progressively older windows`() = runTest {
        val repo = RecordingRepository()
        val useCase = ReconcileVoucherWindowsUseCase(RefreshVouchersUseCase(repo))

        val outcome = useCase("company-a", totalLookbackDays = 90)

        assertTrue(outcome is VoucherReconciliationOutcome.Completed)
        assertEquals(3, (outcome as VoucherReconciliationOutcome.Completed).windowsSynced)
        // Descending: each window's "to" date is on or before the previous window's "from".
        for (index in 0 until repo.refreshedRanges.size - 1) {
            assertTrue(repo.refreshedRanges[index].from > repo.refreshedRanges[index + 1].to)
        }
    }

    @Test
    fun `stops at the first failing window and reports how many completed before it`() = runTest {
        val repo = RecordingRepository(failOnWindowIndex = 1)
        val useCase = ReconcileVoucherWindowsUseCase(RefreshVouchersUseCase(repo))

        val outcome = useCase("company-a", totalLookbackDays = 90)

        assertTrue(outcome is VoucherReconciliationOutcome.PartiallyCompleted)
        assertEquals(1, (outcome as VoucherReconciliationOutcome.PartiallyCompleted).windowsSynced)
        // Never attempted the third window once the second one failed.
        assertEquals(2, repo.refreshedRanges.size)
    }

    @Test
    fun `a second concurrent reconciliation for the same company is rejected as already running`() = runTest {
        val repo = RecordingRepository(delayEachWindow = true)
        val useCase = ReconcileVoucherWindowsUseCase(RefreshVouchersUseCase(repo))

        val first = async { useCase("company-a", totalLookbackDays = 90) }
        repo.awaitFirstCallStarted()
        val second = useCase("company-a", totalLookbackDays = 90)

        assertEquals(VoucherReconciliationOutcome.AlreadyRunning, second)
        repo.release()
        assertTrue(first.await() is VoucherReconciliationOutcome.Completed)
    }

    @Test
    fun `a different company can reconcile concurrently without being blocked`() = runTest {
        val repoA = RecordingRepository()
        val repoB = RecordingRepository()
        val useCaseA = ReconcileVoucherWindowsUseCase(RefreshVouchersUseCase(repoA))
        val useCaseB = ReconcileVoucherWindowsUseCase(RefreshVouchersUseCase(repoB))

        val results = listOf(
            async { useCaseA("company-a", totalLookbackDays = 30) },
            async { useCaseB("company-b", totalLookbackDays = 30) },
        ).awaitAll()

        assertTrue(results.all { it is VoucherReconciliationOutcome.Completed })
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
