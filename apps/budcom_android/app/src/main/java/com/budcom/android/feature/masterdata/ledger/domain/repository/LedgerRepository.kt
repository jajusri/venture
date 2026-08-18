package com.budcom.android.feature.masterdata.ledger.domain.repository

import com.budcom.android.core.common.AppResult
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerPage
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerQuery

/**
 * Typed ledger read port. Room is the sole source of truth for [listLedgers]: it reads the
 * local cache immediately and never contacts the Connector — matching
 * [com.budcom.android.feature.voucher.data.repository.VoucherRepositoryImpl]'s established
 * cache-only-vs-explicit-refresh split. [refreshLedgers] is the only operation that reaches the
 * network (`GET /ledgers`); it persists a successful response and otherwise leaves the existing
 * cache untouched.
 */
interface LedgerRepository {
    suspend fun listLedgers(query: LedgerQuery): AppResult<LedgerPage>
    suspend fun refreshLedgers(query: LedgerQuery): AppResult<LedgerPage>
}
