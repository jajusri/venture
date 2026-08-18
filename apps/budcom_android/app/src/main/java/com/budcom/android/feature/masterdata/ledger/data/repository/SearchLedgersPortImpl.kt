package com.budcom.android.feature.masterdata.ledger.data.repository

import com.budcom.android.core.common.AppResult
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerPage
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerQuery
import com.budcom.android.feature.masterdata.ledger.domain.port.SearchLedgersPort
import com.budcom.android.feature.masterdata.ledger.domain.repository.LedgerRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchLedgersPortImpl @Inject constructor(
    private val repository: LedgerRepository,
) : SearchLedgersPort {
    // Room-only: Universal Search must never fire a live network/Tally request on every
    // keystroke — matches the Ledger Browser's own cache-only read path.
    override suspend fun search(query: LedgerQuery): AppResult<LedgerPage> =
        repository.listLedgers(query)
}
