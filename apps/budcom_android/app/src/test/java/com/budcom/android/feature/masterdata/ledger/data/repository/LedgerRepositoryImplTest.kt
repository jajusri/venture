package com.budcom.android.feature.masterdata.ledger.data.repository

import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.network.ApiResult
import com.budcom.android.core.network.ErrorMapper
import com.budcom.android.core.network.NetworkError
import com.budcom.android.core.util.DispatcherProvider
import com.budcom.android.feature.company.data.repository.SelectedCompanyStore
import com.budcom.android.feature.masterdata.ledger.data.local.LedgerLocalDataSource
import com.budcom.android.feature.masterdata.ledger.data.remote.LedgerRemoteDataSource
import com.budcom.android.feature.masterdata.ledger.domain.model.Ledger
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerDataQuality
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerPage
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerQuery
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
    fun `maps success page and replaces cache`() = runTest(dispatcher) {
        val local = FakeLedgerLocal()
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
        val repo = LedgerRepositoryImpl(
            remote,
            local,
            FakeSelectedCompanyStore("co-1"),
            errorMapper,
            dispatchers,
        )
        val result = repo.loadLedgers(LedgerQuery()) as AppResult.Success
        assertEquals(1, result.value.items.size)
        assertEquals(1, local.replaceCount)
        assertEquals(1, local.stored.size)
    }

    @Test
    fun `offline without cache maps failure`() = runTest(dispatcher) {
        val repo = LedgerRepositoryImpl(
            FakeRemote(ApiResult.Failure(NetworkError.NoConnectivity)),
            FakeLedgerLocal(),
            FakeSelectedCompanyStore("co-1"),
            errorMapper,
            dispatchers,
        )
        val result = repo.loadLedgers(LedgerQuery()) as AppResult.Failure
        assertTrue(result.error is AppError.Offline)
    }

    @Test
    fun `offline with cache returns cached page`() = runTest(dispatcher) {
        val local = FakeLedgerLocal().apply {
            stored["co-1"] = mutableListOf(sampleLedger())
        }
        val repo = LedgerRepositoryImpl(
            FakeRemote(ApiResult.Failure(NetworkError.NoConnectivity)),
            local,
            FakeSelectedCompanyStore("co-1"),
            errorMapper,
            dispatchers,
        )
        val result = repo.loadLedgers(LedgerQuery()) as AppResult.Success
        assertEquals("guid:cash", result.value.items.single().id)
    }

    @Test
    fun `failed snapshot warm does not clear existing cache`() = runTest(dispatcher) {
        val local = FakeLedgerLocal().apply {
            stored["co-1"] = mutableListOf(sampleLedger(id = "guid:old", name = "Old"))
        }
        val remote = object : LedgerRemoteDataSource {
            override suspend fun fetchLedgers(query: LedgerQuery): ApiResult<LedgerPage> {
                return if (query.page == 1) {
                    ApiResult.Success(
                        LedgerPage(
                            items = listOf(sampleLedger(id = "guid:new", name = "New")),
                            page = 1,
                            pageSize = 50,
                            totalItems = 100,
                            totalPages = 2,
                            dataFreshnessAt = "t2",
                        ),
                    )
                } else {
                    ApiResult.Failure(NetworkError.Timeout())
                }
            }
        }
        val repo = LedgerRepositoryImpl(
            remote,
            local,
            FakeSelectedCompanyStore("co-1"),
            errorMapper,
            dispatchers,
        )
        repo.loadLedgers(LedgerQuery())
        assertEquals(0, local.replaceCount)
        assertTrue(local.stored["co-1"]!!.any { it.id == "guid:old" })
        assertTrue(local.stored["co-1"]!!.any { it.id == "guid:new" })
    }

    private class FakeRemote(
        private val result: ApiResult<LedgerPage>,
    ) : LedgerRemoteDataSource {
        override suspend fun fetchLedgers(query: LedgerQuery): ApiResult<LedgerPage> = result
    }

    private class FakeLedgerLocal : LedgerLocalDataSource {
        val stored = mutableMapOf<String, MutableList<Ledger>>()
        var replaceCount = 0

        override suspend fun hasCache(companyId: String): Boolean = (stored[companyId]?.isNotEmpty() == true)

        override suspend fun upsert(companyId: String, items: List<Ledger>, dataFreshnessAt: String?) {
            val bucket = stored.getOrPut(companyId) { mutableListOf() }
            items.forEach { item ->
                bucket.removeAll { it.id == item.id }
                bucket.add(item)
            }
        }

        override suspend fun replaceAll(companyId: String, items: List<Ledger>, dataFreshnessAt: String?) {
            replaceCount++
            stored[companyId] = items.toMutableList()
        }

        override suspend fun query(companyId: String, query: LedgerQuery): LedgerPage? {
            val items = stored[companyId] ?: return null
            if (items.isEmpty()) return null
            return LedgerPage(
                items = items,
                page = 1,
                pageSize = query.pageSize,
                totalItems = items.size,
                totalPages = 1,
                dataFreshnessAt = "cached",
            )
        }
    }

    private class FakeSelectedCompanyStore(
        initial: String?,
    ) : SelectedCompanyStore {
        private val state = MutableStateFlow(initial)
        override fun observeSelectedCompanyId(): Flow<String?> = state
        override suspend fun getSelectedCompanyId(): String? = state.value
        override suspend fun saveSelectedCompanyId(companyId: String) {
            state.value = companyId
        }
        override suspend fun clearSelectedCompanyId() {
            state.value = null
        }
    }

    private fun sampleLedger(
        id: String = "guid:cash",
        name: String = "Cash",
    ) = Ledger(
        id = id,
        name = name,
        alias = null,
        parentGroup = "Cash-in-Hand",
        status = LedgerStatus.Active,
        closingBalance = null,
        dataQuality = LedgerDataQuality.Complete,
        syncedAt = "t",
    )
}
