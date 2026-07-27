package com.budcom.android.feature.masterdata.ledger.domain.port

import com.budcom.android.core.common.AppResult
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerPage
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerQuery

/**
 * Stable public read-only search port for Ledgers.
 *
 * Cross-feature consumers (e.g. Universal Search) must use this port rather than
 * the Ledger repository implementation.
 */
interface SearchLedgersPort {
    suspend fun search(query: LedgerQuery): AppResult<LedgerPage>
}
