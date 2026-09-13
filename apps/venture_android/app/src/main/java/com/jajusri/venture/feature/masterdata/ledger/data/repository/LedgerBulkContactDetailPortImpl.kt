package com.jajusri.venture.feature.masterdata.ledger.data.repository

import com.jajusri.venture.core.common.AppResult
import com.jajusri.venture.core.network.ApiResult
import com.jajusri.venture.core.network.ErrorMapper
import com.jajusri.venture.feature.masterdata.ledger.data.remote.LedgerRemoteDataSource
import com.jajusri.venture.feature.masterdata.ledger.domain.model.LedgerContactDetailsBulkResult
import com.jajusri.venture.feature.masterdata.ledger.domain.port.LedgerBulkContactDetailPort
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LedgerBulkContactDetailPortImpl @Inject constructor(
    private val remoteDataSource: LedgerRemoteDataSource,
    private val errorMapper: ErrorMapper,
) : LedgerBulkContactDetailPort {
    override suspend fun fetchContactDetailsBulk(): AppResult<LedgerContactDetailsBulkResult> =
        when (val result = remoteDataSource.fetchLedgerContactDetailsBulk()) {
            is ApiResult.Success -> AppResult.Success(result.data)
            is ApiResult.Failure -> AppResult.Failure(errorMapper.toAppError(result.error))
        }
}
