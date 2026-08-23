package com.budcom.android.feature.masterdata.ledger.data.repository

import com.budcom.android.core.common.AppResult
import com.budcom.android.core.network.ApiResult
import com.budcom.android.core.network.ErrorMapper
import com.budcom.android.feature.masterdata.ledger.data.remote.LedgerRemoteDataSource
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerContactDetailsBulkResult
import com.budcom.android.feature.masterdata.ledger.domain.port.LedgerBulkContactDetailPort
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
