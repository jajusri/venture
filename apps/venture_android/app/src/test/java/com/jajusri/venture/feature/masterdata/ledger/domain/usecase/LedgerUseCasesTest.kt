package com.jajusri.venture.feature.masterdata.ledger.domain.usecase

import com.jajusri.venture.core.common.AppResult
import com.jajusri.venture.feature.masterdata.ledger.domain.model.Ledger
import com.jajusri.venture.feature.masterdata.ledger.domain.model.LedgerDataQuality
import com.jajusri.venture.feature.masterdata.ledger.domain.model.LedgerPage
import com.jajusri.venture.feature.masterdata.ledger.domain.model.LedgerQuery
import com.jajusri.venture.feature.masterdata.ledger.domain.model.LedgerStatus
import com.jajusri.venture.feature.masterdata.ledger.domain.repository.LedgerRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LedgerUseCasesTest {

    private fun samplePage() = LedgerPage(
        items = listOf(
            Ledger(
                id = "guid:cash",
                name = "Cash",
                alias = null,
                parentGroup = null,
                status = LedgerStatus.Active,
                closingBalance = null,
                dataQuality = LedgerDataQuality.Complete,
                syncedAt = "t",
            ),
        ),
        page = 1,
        pageSize = 50,
        totalItems = 1,
        totalPages = 1,
        dataFreshnessAt = null,
    )

    /**
     * Locked contract (this exact bug shape previously affected the Voucher screens too, see
     * `VoucherRepositoryImpl`): Load must be Room-only and Refresh must be the only network path
     * — the two must delegate to DIFFERENT repository methods, never the same one, or every
     * normal screen open silently pays for a live Connector/Tally round trip.
     */
    @Test
    fun `Load delegates to listLedgers and Refresh delegates to refreshLedgers, never the other's method`() = runTest {
        val repo = CountingFakeRepository()
        val load = LoadLedgersUseCase(repo)
        val refresh = RefreshLedgersUseCase(repo)
        val query = LedgerQuery(text = "cash")

        assertEquals(samplePage(), (load(query) as AppResult.Success).value)
        assertEquals(1, repo.listCalls)
        assertEquals(0, repo.refreshCalls)

        assertEquals(samplePage(), (refresh(query) as AppResult.Success).value)
        assertEquals(1, repo.listCalls)
        assertEquals(1, repo.refreshCalls)
    }

    private inner class CountingFakeRepository : LedgerRepository {
        var listCalls = 0
            private set
        var refreshCalls = 0
            private set

        override suspend fun listLedgers(query: LedgerQuery): AppResult<LedgerPage> {
            listCalls += 1
            return AppResult.Success(samplePage())
        }

        override suspend fun refreshLedgers(query: LedgerQuery): AppResult<LedgerPage> {
            refreshCalls += 1
            return AppResult.Success(samplePage())
        }
    }
}
