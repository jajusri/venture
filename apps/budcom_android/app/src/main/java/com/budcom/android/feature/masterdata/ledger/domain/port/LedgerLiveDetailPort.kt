package com.budcom.android.feature.masterdata.ledger.domain.port

import com.budcom.android.core.common.AppResult
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerContactDetails

/**
 * Bounded, single-ledger, on-demand read of a Ledger's contact-compatible fields (MVP-1.1-D
 * re-sync confirmation only — never a bulk/automatic path). Unlike [LedgerSnapshotPort] (local
 * Room cache only), this genuinely contacts the Connector, which itself returns its own
 * already-synced local snapshot rather than a live Tally query — see `LedgerApi.getLedgerDetail`.
 */
interface LedgerLiveDetailPort {
    /** No `companyId` parameter — the paired Connector's own session is already scoped to one
     * company (same as the existing [LedgerApi][com.budcom.android.feature.masterdata.ledger.data.remote.LedgerApi]
     * list call), so there is nothing to pass. */
    suspend fun fetchContactDetails(ledgerId: String): AppResult<LedgerContactDetails>
}
