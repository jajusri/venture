package com.budcom.android.feature.masterdata.ledger.data.repository

import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.connectorauth.domain.ConnectorTransportSelection
import com.budcom.android.core.connectorauth.domain.ConnectorTransportSelectionGate
import com.budcom.android.core.network.ApiResult
import com.budcom.android.core.network.ErrorMapper
import com.budcom.android.core.util.DispatcherProvider
import com.budcom.android.core.util.TimeProvider
import com.budcom.android.feature.masterdata.ledger.data.local.LedgerStatementLocalDataSource
import com.budcom.android.feature.masterdata.ledger.data.remote.AuthenticatedLedgerStatementRemoteDataSource
import com.budcom.android.feature.masterdata.ledger.data.remote.LedgerRemoteDataSource
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatement
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatementDateRange
import com.budcom.android.feature.masterdata.ledger.domain.repository.LedgerStatementRepository
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room is the sole source of truth for [getLedgerStatement]: it reads the local cache
 * immediately and never contacts the Connector. [refreshLedgerStatement] is the only operation
 * that reaches the network; it persists a successful response and otherwise leaves the existing
 * cache untouched — matching [com.budcom.android.feature.voucher.data.repository.VoucherRepositoryImpl]'s
 * established cache-only-vs-explicit-refresh split exactly (see that class's own doc comment for
 * why this shape, not Ledger-list's warm-full-snapshot shape, is correct here: a statement is
 * scoped to one ledger + one date window, not "the whole unfiltered set").
 *
 * [refreshLedgerStatement] resolves [ConnectorTransportSelectionGate] once per call and, on
 * AUTHENTICATED, routes through [AuthenticatedLedgerStatementRemoteDataSource] instead of the
 * legacy [LedgerRemoteDataSource] — never both, never falling back to legacy once AUTHENTICATED
 * is selected.
 */
@Singleton
class LedgerStatementRepositoryImpl @Inject constructor(
    private val remoteDataSource: LedgerRemoteDataSource,
    private val authenticatedRemoteDataSource: AuthenticatedLedgerStatementRemoteDataSource,
    private val localDataSource: LedgerStatementLocalDataSource,
    private val transportGate: ConnectorTransportSelectionGate,
    private val errorMapper: ErrorMapper,
    private val dispatchers: DispatcherProvider,
    private val timeProvider: TimeProvider,
) : LedgerStatementRepository {

    override suspend fun getLedgerStatement(
        companyId: String,
        ledgerId: String,
        range: LedgerStatementDateRange,
    ): AppResult<LedgerStatement> = withContext(dispatchers.io) {
        localDataSource.statement(companyId, ledgerId, range)?.let { AppResult.Success(it) }
            ?: AppResult.Failure(AppError.Message(NO_CACHE_MESSAGE))
    }

    override suspend fun refreshLedgerStatement(
        companyId: String,
        ledgerId: String,
        range: LedgerStatementDateRange,
    ): AppResult<LedgerStatement> = withContext(dispatchers.io) {
        val result = when (transportGate.resolve()) {
            ConnectorTransportSelection.LEGACY -> when (val api = remoteDataSource.fetchLedgerStatement(ledgerId, range)) {
                is ApiResult.Success -> AppResult.Success(api.data)
                is ApiResult.Failure -> AppResult.Failure(errorMapper.toAppError(api.error))
            }
            ConnectorTransportSelection.AUTHENTICATED -> authenticatedRemoteDataSource.fetchLedgerStatement(ledgerId, range)
        }
        when (result) {
            is AppResult.Success -> {
                localDataSource.store(companyId, result.value, timeProvider.nowEpochMillis())
                result
            }
            is AppResult.Failure -> result
        }
    }

    companion object {
        const val NO_CACHE_MESSAGE =
            "No offline ledger statement available for this period. Connect to BUDCOM Desktop and refresh once."
    }
}
