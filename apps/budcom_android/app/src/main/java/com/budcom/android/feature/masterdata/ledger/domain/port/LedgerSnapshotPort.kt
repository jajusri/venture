package com.budcom.android.feature.masterdata.ledger.domain.port

import com.budcom.android.feature.masterdata.ledger.domain.model.Ledger

/**
 * Local-only read of every currently cached ledger for a company — no network call, no paging.
 * Exists so MVP-1.1 Connect's Party-seeding reconciliation can read already-synced Ledger data
 * without depending on [com.budcom.android.feature.masterdata.ledger.domain.repository.LedgerRepository]
 * (which always attempts a live Connector call) or on Ledger's internal Room types directly —
 * the same cross-feature hexagonal-port pattern already used by
 * [com.budcom.android.feature.masterdata.ledger.domain.port.SearchLedgersPort].
 */
interface LedgerSnapshotPort {
    suspend fun getCachedLedgers(companyId: String): List<Ledger>
}
