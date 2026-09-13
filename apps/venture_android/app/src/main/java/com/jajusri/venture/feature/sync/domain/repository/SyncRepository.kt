package com.jajusri.venture.feature.sync.domain.repository

import com.jajusri.venture.core.common.AppResult
import com.jajusri.venture.feature.sync.domain.model.SyncMode
import com.jajusri.venture.feature.sync.domain.model.SyncOutcome
import com.jajusri.venture.feature.sync.domain.model.SyncProgress
import com.jajusri.venture.feature.sync.domain.model.SyncRunSummary
import com.jajusri.venture.feature.sync.domain.model.SyncStatisticsSummary
import com.jajusri.venture.feature.sync.domain.model.SyncTarget

interface SyncRepository {
    fun bindCompany(companyId: String?)
    suspend fun startSync(target: SyncTarget, mode: SyncMode = SyncMode.Full): AppResult<SyncOutcome>
    suspend fun cancelSync(target: SyncTarget): AppResult<SyncProgress>
    suspend fun getStatus(target: SyncTarget): AppResult<SyncProgress>
    suspend fun getStatistics(target: SyncTarget): AppResult<SyncStatisticsSummary>
    suspend fun listRecentRuns(target: SyncTarget, limit: Int): AppResult<List<SyncRunSummary>>
}
