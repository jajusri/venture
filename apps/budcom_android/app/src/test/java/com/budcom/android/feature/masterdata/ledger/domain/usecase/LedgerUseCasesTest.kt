package com.budcom.android.feature.masterdata.ledger.domain.usecase

import com.budcom.android.core.common.AppResult
import com.budcom.android.feature.masterdata.ledger.domain.model.Ledger
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerDataQuality
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerPage
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerQuery
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatus
import com.budcom.android.feature.masterdata.ledger.domain.repository.LedgerRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LedgerUseCasesTest {
    @Test
    fun `load and refresh both delegate to repository`() = runTest {
        val page = LedgerPage(
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
        val repo = object : LedgerRepository {
            var calls = 0
            override suspend fun loadLedgers(query: LedgerQuery): AppResult<LedgerPage> {
                calls += 1
                return AppResult.Success(page)
            }
        }
        val load = LoadLedgersUseCase(repo)
        val refresh = RefreshLedgersUseCase(repo)
        val query = LedgerQuery(text = "cash")
        assertEquals(page, (load(query) as AppResult.Success).value)
        assertEquals(page, (refresh(query) as AppResult.Success).value)
        assertEquals(2, repo.calls)
    }
}
