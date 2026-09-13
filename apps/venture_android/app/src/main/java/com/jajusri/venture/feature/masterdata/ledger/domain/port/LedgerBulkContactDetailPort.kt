package com.jajusri.venture.feature.masterdata.ledger.domain.port

import com.jajusri.venture.core.common.AppResult
import com.jajusri.venture.feature.masterdata.ledger.domain.model.LedgerContactDetailsBulkResult

/**
 * Manually-triggered, bulk, all-ledgers-in-one-Tally-round-trip read of contact-compatible fields
 * (Connect address/email/GSTIN auto-population) — distinct from [LedgerLiveDetailPort] (single-
 * ledger, per-party "Check Tally" re-sync). Like that port, this is never a scheduled/automatic
 * path: it exists only for an explicit, occasional user action on the Connect screen, deliberately
 * decoupled from the routine Ledgers/StockItems/Vouchers sync cycle.
 */
interface LedgerBulkContactDetailPort {
    suspend fun fetchContactDetailsBulk(): AppResult<LedgerContactDetailsBulkResult>
}
