package com.budcom.android.feature.masterdata.ledger.data.repository

import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.connectorauth.domain.ConnectorTransportSelection
import com.budcom.android.core.connectorauth.domain.ConnectorTransportSelectionGate
import com.budcom.android.core.network.ApiResult
import com.budcom.android.core.network.ErrorMapper
import com.budcom.android.core.network.NetworkError
import com.budcom.android.core.util.DispatcherProvider
import com.budcom.android.core.util.TimeProvider
import com.budcom.android.feature.masterdata.ledger.data.local.LedgerStatementLocalDataSource
import com.budcom.android.feature.masterdata.ledger.data.remote.AuthenticatedLedgerStatementRemoteDataSource
import com.budcom.android.feature.masterdata.ledger.data.remote.LedgerRemoteDataSource
import com.budcom.android.feature.masterdata.ledger.domain.model.AmountSide
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatement
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatementAmount
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatementCoverage
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatementDateRange
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerStatementRepositoryImplTest {
    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = dispatcher
        override val io: CoroutineDispatcher = dispatcher
        override val default: CoroutineDispatcher = dispatcher
    }
    private val errorMapper = object : ErrorMapper {
        override fun toNetworkError(throwable: Throwable): NetworkError = NetworkError.Unknown()
        override fun toAppError(error: NetworkError): AppError = when (error) {
            is NetworkError.NoConnectivity -> AppError.Offline()
            is NetworkError.Timeout -> AppError.Timeout()
            is NetworkError.Http -> AppError.Remote(error.httpStatus, error.code, error.message)
            is NetworkError.Serialization -> AppError.Serialization(error.message)
            is NetworkError.Unknown -> AppError.Unexpected(IllegalStateException(error.message))
        }
    }
    private val timeProvider = TimeProvider { 1_000L }
    private val range = LedgerStatementDateRange("2026-07-01", "2026-07-31")

    private fun repository(
        remote: LedgerRemoteDataSource = StatementUnreachableRemote,
        authenticatedRemote: AuthenticatedLedgerStatementRemoteDataSource = StatementUnreachableAuthenticatedRemote,
        local: StatementFakeLocal = StatementFakeLocal(),
        transportGate: ConnectorTransportSelectionGate = StatementFakeTransportGate(ConnectorTransportSelection.LEGACY),
    ): LedgerStatementRepositoryImpl =
        LedgerStatementRepositoryImpl(remote, authenticatedRemote, local, transportGate, errorMapper, dispatchers, timeProvider)

    // ---------------------------------- getLedgerStatement: cache-only ----------------------------------

    @Test
    fun `getLedgerStatement never touches the network and returns the cached statement`() = runTest(dispatcher) {
        val local = StatementFakeLocal().apply { stored[Triple("co-1", "ledger-1", range)] = sampleStatement() }
        val repo = repository(local = local)
        val result = repo.getLedgerStatement("co-1", "ledger-1", range) as AppResult.Success
        assertEquals("Acme Traders", result.value.ledgerName)
    }

    @Test
    fun `getLedgerStatement fails honestly when there is no cache, never fabricating a statement`() = runTest(dispatcher) {
        val repo = repository()
        val result = repo.getLedgerStatement("co-1", "ledger-1", range) as AppResult.Failure
        assertTrue(result.error is AppError.Message)
    }

    // ---------------------------------- refreshLedgerStatement: LEGACY ----------------------------------

    @Test
    fun `refreshLedgerStatement on LEGACY fetches via the legacy remote and persists on success`() = runTest(dispatcher) {
        val local = StatementFakeLocal()
        val remote = StatementFakeRemote(ApiResult.Success(sampleStatement()))
        val repo = repository(remote = remote, local = local, transportGate = StatementFakeTransportGate(ConnectorTransportSelection.LEGACY))

        val result = repo.refreshLedgerStatement("co-1", "ledger-1", range) as AppResult.Success

        assertEquals("Acme Traders", result.value.ledgerName)
        assertEquals(1, remote.callCount)
        assertEquals(1, local.storeCount)
    }

    @Test
    fun `refreshLedgerStatement on LEGACY never calls the authenticated remote`() = runTest(dispatcher) {
        val repo = repository(
            remote = StatementFakeRemote(ApiResult.Success(sampleStatement())),
            authenticatedRemote = StatementUnreachableAuthenticatedRemote,
            transportGate = StatementFakeTransportGate(ConnectorTransportSelection.LEGACY),
        )
        repo.refreshLedgerStatement("co-1", "ledger-1", range)
        // StatementUnreachableAuthenticatedRemote throws if invoked — reaching here proves it wasn't.
    }

    @Test
    fun `refreshLedgerStatement on LEGACY failure never persists and never falls back to cache`() = runTest(dispatcher) {
        val local = StatementFakeLocal().apply { stored[Triple("co-1", "ledger-1", range)] = sampleStatement() }
        val repo = repository(remote = StatementFakeRemote(ApiResult.Failure(NetworkError.NoConnectivity)), local = local)

        val result = repo.refreshLedgerStatement("co-1", "ledger-1", range) as AppResult.Failure

        assertTrue(result.error is AppError.Offline)
        assertEquals(0, local.storeCount)
    }

    // ------------------------------- refreshLedgerStatement: AUTHENTICATED -------------------------------

    @Test
    fun `refreshLedgerStatement on AUTHENTICATED fetches via the authenticated remote and persists on success`() = runTest(dispatcher) {
        val local = StatementFakeLocal()
        val authenticatedRemote = StatementFakeAuthenticatedRemote(AppResult.Success(sampleStatement()))
        val repo = repository(
            authenticatedRemote = authenticatedRemote,
            local = local,
            transportGate = StatementFakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
        )

        val result = repo.refreshLedgerStatement("co-1", "ledger-1", range) as AppResult.Success

        assertEquals("Acme Traders", result.value.ledgerName)
        assertEquals(1, authenticatedRemote.callCount)
        assertEquals(1, local.storeCount)
    }

    @Test
    fun `refreshLedgerStatement on AUTHENTICATED never calls the legacy remote`() = runTest(dispatcher) {
        val repo = repository(
            remote = StatementUnreachableRemote,
            authenticatedRemote = StatementFakeAuthenticatedRemote(AppResult.Success(sampleStatement())),
            transportGate = StatementFakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
        )
        repo.refreshLedgerStatement("co-1", "ledger-1", range)
        // StatementUnreachableRemote throws if invoked — reaching here proves it wasn't.
    }

    @Test
    fun `refreshLedgerStatement on AUTHENTICATED failure never persists`() = runTest(dispatcher) {
        val local = StatementFakeLocal()
        val repo = repository(
            authenticatedRemote = StatementFakeAuthenticatedRemote(AppResult.Failure(AppError.Offline())),
            local = local,
            transportGate = StatementFakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
        )

        val result = repo.refreshLedgerStatement("co-1", "ledger-1", range) as AppResult.Failure

        assertTrue(result.error is AppError.Offline)
        assertEquals(0, local.storeCount)
    }

    // ----------------------------------------- company isolation -----------------------------------------

    @Test
    fun `two companies' cached statements for the same ledgerId and period never leak into each other`() = runTest(dispatcher) {
        val local = StatementFakeLocal().apply {
            stored[Triple("co-a", "ledger-1", range)] = sampleStatement(closing = "100")
            stored[Triple("co-b", "ledger-1", range)] = sampleStatement(closing = "999")
        }
        val repo = repository(local = local)
        val a = (repo.getLedgerStatement("co-a", "ledger-1", range) as AppResult.Success).value
        val b = (repo.getLedgerStatement("co-b", "ledger-1", range) as AppResult.Success).value
        assertEquals("100", a.closingBalance?.amount)
        assertEquals("999", b.closingBalance?.amount)
    }
}

private fun sampleStatement(closing: String = "500"): LedgerStatement = LedgerStatement(
    ledgerId = "ledger-1",
    ledgerName = "Acme Traders",
    ledgerAlias = null,
    parentGroup = "Sundry Debtors",
    period = LedgerStatementDateRange("2026-07-01", "2026-07-31"),
    openingBalance = LedgerStatementAmount("0", AmountSide.Dr),
    closingBalance = LedgerStatementAmount(closing, AmountSide.Dr),
    transactions = emptyList(),
    coverage = LedgerStatementCoverage(true, true, "2026-07-01", "2026-08-10", null),
)

private class StatementFakeLocal : LedgerStatementLocalDataSource {
    val stored = mutableMapOf<Triple<String, String, LedgerStatementDateRange>, LedgerStatement>()
    var storeCount = 0
        private set

    override suspend fun statement(companyId: String, ledgerId: String, range: LedgerStatementDateRange): LedgerStatement? =
        stored[Triple(companyId, ledgerId, range)]

    override suspend fun store(companyId: String, statement: LedgerStatement, syncedAt: Long) {
        storeCount++
        stored[Triple(companyId, statement.ledgerId, statement.period)] = statement
    }
}

private class StatementFakeRemote(private val result: ApiResult<LedgerStatement>) : LedgerRemoteDataSource {
    var callCount = 0
        private set

    override suspend fun fetchLedgers(
        query: com.budcom.android.feature.masterdata.ledger.domain.model.LedgerQuery,
    ): ApiResult<com.budcom.android.feature.masterdata.ledger.domain.model.LedgerPage> =
        error("fetchLedgers is not exercised by ledger-statement tests")

    override suspend fun fetchLedgerStatement(ledgerId: String, range: LedgerStatementDateRange): ApiResult<LedgerStatement> {
        callCount++
        return result
    }

    override suspend fun fetchLedgerContactDetails(
        ledgerId: String,
    ): ApiResult<com.budcom.android.feature.masterdata.ledger.domain.model.LedgerContactDetails> =
        error("fetchLedgerContactDetails is not exercised by ledger-statement tests")
}

private object StatementUnreachableRemote : LedgerRemoteDataSource {
    override suspend fun fetchLedgers(
        query: com.budcom.android.feature.masterdata.ledger.domain.model.LedgerQuery,
    ): ApiResult<com.budcom.android.feature.masterdata.ledger.domain.model.LedgerPage> =
        error("fetchLedgers is not exercised by ledger-statement tests")

    override suspend fun fetchLedgerStatement(ledgerId: String, range: LedgerStatementDateRange): ApiResult<LedgerStatement> =
        error("StatementUnreachableRemote must never be called on the AUTHENTICATED path")

    override suspend fun fetchLedgerContactDetails(
        ledgerId: String,
    ): ApiResult<com.budcom.android.feature.masterdata.ledger.domain.model.LedgerContactDetails> =
        error("fetchLedgerContactDetails is not exercised by ledger-statement tests")
}

private class StatementFakeAuthenticatedRemote(private val result: AppResult<LedgerStatement>) : AuthenticatedLedgerStatementRemoteDataSource {
    var callCount = 0
        private set

    override suspend fun fetchLedgerStatement(ledgerId: String, range: LedgerStatementDateRange): AppResult<LedgerStatement> {
        callCount++
        return result
    }
}

private object StatementUnreachableAuthenticatedRemote : AuthenticatedLedgerStatementRemoteDataSource {
    override suspend fun fetchLedgerStatement(ledgerId: String, range: LedgerStatementDateRange): AppResult<LedgerStatement> =
        error("StatementUnreachableAuthenticatedRemote must never be called on the LEGACY path")
}

private class StatementFakeTransportGate(private val selection: ConnectorTransportSelection) : ConnectorTransportSelectionGate {
    override suspend fun resolve(): ConnectorTransportSelection = selection
}
