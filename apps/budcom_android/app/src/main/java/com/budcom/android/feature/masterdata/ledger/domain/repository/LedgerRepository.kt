package com.budcom.android.feature.masterdata.ledger.domain.repository

import com.budcom.android.core.common.AppResult
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerPage
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerQuery

/**
 * Typed ledger read port backed by Connector `GET /ledgers`.
 */
interface LedgerRepository {
    suspend fun loadLedgers(query: LedgerQuery): AppResult<LedgerPage>
}
