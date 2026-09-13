package com.jajusri.venture.feature.masterdata.ledger.domain.usecase

import com.jajusri.venture.core.common.AppResult
import com.jajusri.venture.feature.masterdata.ledger.domain.model.LedgerPage
import com.jajusri.venture.feature.masterdata.ledger.domain.model.LedgerQuery
import com.jajusri.venture.feature.masterdata.ledger.domain.repository.LedgerRepository
import javax.inject.Inject

/** Room-only: used on every normal screen open, search keystroke, and pagination fetch. Never
 * reaches the network — see [LedgerRepository.listLedgers]. */
class LoadLedgersUseCase @Inject constructor(
    private val repository: LedgerRepository,
) {
    suspend operator fun invoke(query: LedgerQuery): AppResult<LedgerPage> =
        repository.listLedgers(query)
}

/** The Ledger Browser's only network-reaching action (explicit pull-to-refresh) — see
 * [LedgerRepository.refreshLedgers]. */
class RefreshLedgersUseCase @Inject constructor(
    private val repository: LedgerRepository,
) {
    suspend operator fun invoke(query: LedgerQuery): AppResult<LedgerPage> =
        repository.refreshLedgers(query)
}
