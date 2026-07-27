package com.budcom.android.feature.masterdata.ledger.data.repository

import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.network.ApiResult
import com.budcom.android.core.network.ErrorMapper
import com.budcom.android.core.network.NetworkError
import com.budcom.android.core.util.DispatcherProvider
import com.budcom.android.feature.masterdata.ledger.data.remote.LedgerRemoteDataSource
import com.budcom.android.feature.masterdata.ledger.domain.model.Ledger
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerDataQuality
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerPage
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerQuery
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerRepositoryImplTest {
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

    @Test
    fun `maps success page`() = runTest(dispatcher) {
        val remote = FakeRemote(
            ApiResult.Success(
                LedgerPage(
                    items = listOf(sampleLedger()),
                    page = 1,
                    pageSize = 50,
                    totalItems = 1,
                    totalPages = 1,
                    dataFreshnessAt = "t",
                ),
            ),
        )
        val repo = LedgerRepositoryImpl(remote, errorMapper, dispatchers)
        val result = repo.loadLedgers(LedgerQuery()) as AppResult.Success
        assertEquals(1, result.value.items.size)
        assertEquals("guid:cash", result.value.items[0].id)
    }

    @Test
    fun `maps offline failure`() = runTest(dispatcher) {
        val remote = FakeRemote(ApiResult.Failure(NetworkError.NoConnectivity))
        val repo = LedgerRepositoryImpl(remote, errorMapper, dispatchers)
        val result = repo.loadLedgers(LedgerQuery()) as AppResult.Failure
        assertTrue(result.error is AppError.Offline)
    }

    private class FakeRemote(
        private val result: ApiResult<LedgerPage>,
    ) : LedgerRemoteDataSource {
        override suspend fun fetchLedgers(query: LedgerQuery): ApiResult<LedgerPage> = result
    }

    private fun sampleLedger() = Ledger(
        id = "guid:cash",
        name = "Cash",
        alias = null,
        parentGroup = "Cash-in-Hand",
        status = LedgerStatus.Active,
        closingBalance = null,
        dataQuality = LedgerDataQuality.Complete,
        syncedAt = "t",
    )
}
