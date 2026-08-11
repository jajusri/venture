package com.budcom.android.feature.masterdata.ledger.domain.usecase

import com.budcom.android.core.common.AppResult
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatement
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatementDateRange
import com.budcom.android.feature.masterdata.ledger.domain.repository.LedgerStatementRepository
import javax.inject.Inject

/** Reads the cached ledger statement immediately. Never contacts the Connector. */
class GetLedgerStatementUseCase @Inject constructor(
    private val repository: LedgerStatementRepository,
) {
    suspend operator fun invoke(
        companyId: String,
        ledgerId: String,
        range: LedgerStatementDateRange,
    ): AppResult<LedgerStatement> = repository.getLedgerStatement(companyId, ledgerId, range)
}

/** Explicit, bounded Connector refresh for one ledger's statement over one period. */
class RefreshLedgerStatementUseCase @Inject constructor(
    private val repository: LedgerStatementRepository,
) {
    suspend operator fun invoke(
        companyId: String,
        ledgerId: String,
        range: LedgerStatementDateRange,
    ): AppResult<LedgerStatement> = repository.refreshLedgerStatement(companyId, ledgerId, range)
}
