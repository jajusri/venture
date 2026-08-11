package com.budcom.android.feature.masterdata.ledger.domain.repository

import com.budcom.android.core.common.AppResult
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatement
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatementDateRange

interface LedgerStatementRepository {
    /** Reads the locally cached statement for this exact (ledger, period) immediately. Never contacts the Connector. */
    suspend fun getLedgerStatement(
        companyId: String,
        ledgerId: String,
        range: LedgerStatementDateRange,
    ): AppResult<LedgerStatement>

    /** Attempts a bounded Connector fetch and persists it on success. Never falls back to cache on failure. */
    suspend fun refreshLedgerStatement(
        companyId: String,
        ledgerId: String,
        range: LedgerStatementDateRange,
    ): AppResult<LedgerStatement>
}
