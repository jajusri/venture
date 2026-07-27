package com.budcom.android.feature.masterdata.ledger.domain.usecase

import com.budcom.android.core.common.AppResult
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerPage
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerQuery
import com.budcom.android.feature.masterdata.ledger.domain.repository.LedgerRepository
import javax.inject.Inject

class LoadLedgersUseCase @Inject constructor(
    private val repository: LedgerRepository,
) {
    suspend operator fun invoke(query: LedgerQuery): AppResult<LedgerPage> =
        repository.loadLedgers(query)
}

class RefreshLedgersUseCase @Inject constructor(
    private val repository: LedgerRepository,
) {
    suspend operator fun invoke(query: LedgerQuery): AppResult<LedgerPage> =
        repository.loadLedgers(query)
}
