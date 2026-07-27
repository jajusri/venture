package com.budcom.android.feature.masterdata.ledger.data.repository

import com.budcom.android.core.common.AppResult
import com.budcom.android.core.network.ApiResult
import com.budcom.android.core.network.ErrorMapper
import com.budcom.android.core.util.DispatcherProvider
import com.budcom.android.feature.masterdata.ledger.data.remote.LedgerRemoteDataSource
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerPage
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerQuery
import com.budcom.android.feature.masterdata.ledger.domain.repository.LedgerRepository
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LedgerRepositoryImpl @Inject constructor(
    private val remoteDataSource: LedgerRemoteDataSource,
    private val errorMapper: ErrorMapper,
    private val dispatchers: DispatcherProvider,
) : LedgerRepository {

    override suspend fun loadLedgers(query: LedgerQuery): AppResult<LedgerPage> =
        withContext(dispatchers.io) {
            when (val result = remoteDataSource.fetchLedgers(query)) {
                is ApiResult.Success -> AppResult.Success(result.data)
                is ApiResult.Failure -> AppResult.Failure(errorMapper.toAppError(result.error))
            }
        }
}
