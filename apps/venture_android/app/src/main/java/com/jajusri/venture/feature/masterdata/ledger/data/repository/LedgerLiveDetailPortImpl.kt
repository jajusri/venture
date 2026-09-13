package com.jajusri.venture.feature.masterdata.ledger.data.repository

import com.jajusri.venture.core.common.AppResult
import com.jajusri.venture.core.network.ApiResult
import com.jajusri.venture.core.network.ErrorMapper
import com.jajusri.venture.feature.masterdata.ledger.data.remote.LedgerRemoteDataSource
import com.jajusri.venture.feature.masterdata.ledger.domain.model.LedgerContactDetails
import com.jajusri.venture.feature.masterdata.ledger.domain.port.LedgerLiveDetailPort
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LedgerLiveDetailPortImpl @Inject constructor(
    private val remoteDataSource: LedgerRemoteDataSource,
    private val errorMapper: ErrorMapper,
) : LedgerLiveDetailPort {
    override suspend fun fetchContactDetails(ledgerId: String): AppResult<LedgerContactDetails> =
        when (val result = remoteDataSource.fetchLedgerContactDetails(ledgerId)) {
            is ApiResult.Success -> AppResult.Success(result.data)
            is ApiResult.Failure -> AppResult.Failure(errorMapper.toAppError(result.error))
        }
}
