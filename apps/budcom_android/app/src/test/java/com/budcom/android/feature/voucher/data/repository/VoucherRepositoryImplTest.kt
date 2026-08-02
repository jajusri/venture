package com.budcom.android.feature.voucher.data.repository

import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.network.ApiResult
import com.budcom.android.core.network.ErrorMapper
import com.budcom.android.core.network.NetworkError
import com.budcom.android.core.util.DispatcherProvider
import com.budcom.android.feature.voucher.data.remote.VoucherRemoteDataSource
import com.budcom.android.feature.voucher.data.local.VoucherLocalDataSource
import com.budcom.android.feature.voucher.domain.model.VoucherDataQuality
import com.budcom.android.feature.voucher.domain.model.VoucherDateRange
import com.budcom.android.feature.voucher.domain.model.VoucherDetails
import com.budcom.android.feature.voucher.domain.model.VoucherIdentity
import com.budcom.android.feature.voucher.domain.model.VoucherPage
import com.budcom.android.feature.voucher.domain.model.VoucherQuery
import com.budcom.android.feature.voucher.domain.model.VoucherStatus
import com.budcom.android.feature.voucher.domain.model.VoucherSummary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoucherRepositoryImplTest {
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
    fun `maps list success`() = runTest(dispatcher) {
        val remote = FakeRemote(
            listResult = ApiResult.Success(
                VoucherPage(
                    companyId = "estimation",
                    items = listOf(sampleSummary()),
                    page = 1,
                    pageSize = 50,
                    totalItems = 1,
                    totalPages = 1,
                ),
            ),
        )
        val repo = repo(remote)
        val result = repo.listVouchers(
            VoucherQuery(
                companyId = "estimation",
                dateRange = VoucherDateRange("2026-07-01", "2026-07-27"),
            ),
        ) as AppResult.Success
        assertEquals("v-1", result.value.items[0].identity.id)
    }

    @Test
    fun `maps offline failure`() = runTest(dispatcher) {
        val remote = FakeRemote(listResult = ApiResult.Failure(NetworkError.NoConnectivity))
        val repo = repo(remote)
        val result = repo.listVouchers(
            VoucherQuery(
                companyId = "estimation",
                dateRange = VoucherDateRange("2026-07-01", "2026-07-27"),
            ),
        ) as AppResult.Failure
        assertEquals(VoucherRepositoryImpl.NO_CACHE_MESSAGE, (result.error as AppError.Message).message)
    }

    @Test
    fun `maps details success`() = runTest(dispatcher) {
        val details = VoucherDetails(
            summary = sampleSummary(),
            effectiveDate = "2026-07-27",
            narration = "Paid",
            ledgerEntries = emptyList(),
            inventoryEntries = emptyList(),
        )
        val remote = FakeRemote(
            listResult = ApiResult.Failure(NetworkError.Unknown()),
            detailsResult = ApiResult.Success(details),
        )
        val repo = repo(remote)
        val result = repo.getVoucherDetails("estimation", "v-1") as AppResult.Success
        assertEquals("v-1", result.value.summary.identity.id)
        assertEquals("Paid", result.value.narration)
    }

    @Test
    fun `maps details not found`() = runTest(dispatcher) {
        val remote = FakeRemote(
            listResult = ApiResult.Failure(NetworkError.Unknown()),
            detailsResult = ApiResult.Failure(
                NetworkError.Http(404, "NOT_FOUND", "Voucher was not found."),
            ),
        )
        val repo = repo(remote)
        val result = repo.getVoucherDetails("estimation", "missing") as AppResult.Failure
        assertTrue(result.error is AppError.Remote)
        assertEquals(404, (result.error as AppError.Remote).httpStatus)
    }

    @Test
    fun `maps list http validation failure`() = runTest(dispatcher) {
        val remote = FakeRemote(
            listResult = ApiResult.Failure(
                NetworkError.Http(400, "VALIDATION_ERROR", "Invalid voucher query."),
            ),
        )
        val repo = repo(remote)
        val result = repo.listVouchers(
            VoucherQuery(
                companyId = "estimation",
                dateRange = VoucherDateRange("2026-07-01", "2026-07-27"),
            ),
        ) as AppResult.Failure
        assertTrue(result.error is AppError.Remote)
        assertEquals(400, (result.error as AppError.Remote).httpStatus)
        assertEquals("VALIDATION_ERROR", result.error.code)
    }

    @Test
    fun `maps empty list page success`() = runTest(dispatcher) {
        val remote = FakeRemote(
            listResult = ApiResult.Success(
                VoucherPage(
                    companyId = "estimation",
                    items = emptyList(),
                    page = 1,
                    pageSize = 50,
                    totalItems = 0,
                    totalPages = 0,
                ),
            ),
        )
        val repo = repo(remote)
        val result = repo.listVouchers(
            VoucherQuery(
                companyId = "estimation",
                dateRange = VoucherDateRange("2026-07-01", "2026-07-27"),
            ),
        ) as AppResult.Success
        assertTrue(result.value.items.isEmpty())
        assertEquals(false, result.value.canLoadMore)
    }

    @Test
    fun `connector failure returns company scoped cached list`() = runTest(dispatcher) {
        val cached = VoucherPage("company-a", listOf(sampleSummary()), 1, 50, 1, 1,
            com.budcom.android.feature.voucher.domain.model.VoucherCacheState.Offline, 123L)
        val local = FakeLocal(listValue = cached)
        val result = repo(FakeRemote(ApiResult.Failure(NetworkError.NoConnectivity)), local).listVouchers(
            VoucherQuery("company-a", VoucherDateRange("2026-07-01", "2026-07-27")),
        ) as AppResult.Success
        assertEquals(cached, result.value)
        assertEquals("company-a", local.lastListCompany)
    }

    @Test
    fun `parser failure returns cached details with inventory`() = runTest(dispatcher) {
        val cached = VoucherDetails(sampleSummary(), "2026-07-27", "cached", emptyList(),
            listOf(com.budcom.android.feature.voucher.domain.model.VoucherInventoryLine(1, "Item", "2 pcs", "10", null)),
            com.budcom.android.feature.voucher.domain.model.VoucherCacheState.Offline, 321L)
        val result = repo(
            FakeRemote(ApiResult.Failure(NetworkError.Unknown()), ApiResult.Failure(NetworkError.Serialization("bad response"))),
            FakeLocal(detailsValue = cached),
        ).getVoucherDetails("company-a", "v-1") as AppResult.Success
        assertEquals("cached", result.value.narration)
        assertEquals("Item", result.value.inventoryEntries.single().itemName)
    }

    @Test
    fun `successful fetch is persisted and failed refresh cannot replace it`() = runTest(dispatcher) {
        val local = FakeLocal()
        repo(FakeRemote(ApiResult.Success(VoucherPage("company-a", listOf(sampleSummary()), 1, 50, 1, 1))), local)
            .listVouchers(VoucherQuery("company-a", VoucherDateRange("2026-07-01", "2026-07-27")))
        assertEquals(1, local.storedItems.size)
        repo(FakeRemote(ApiResult.Failure(NetworkError.Timeout())), local)
            .listVouchers(VoucherQuery("company-a", VoucherDateRange("2026-07-01", "2026-07-27")))
        assertEquals("v-1", local.storedItems.single().identity.id)
    }

    private class FakeRemote(
        private val listResult: ApiResult<VoucherPage>,
        private val detailsResult: ApiResult<VoucherDetails> = ApiResult.Failure(NetworkError.Unknown()),
    ) : VoucherRemoteDataSource {
        override suspend fun fetchVouchers(query: VoucherQuery) = listResult
        override suspend fun fetchVoucherDetails(companyId: String, voucherId: String) = detailsResult
    }

    private fun repo(remote: VoucherRemoteDataSource, local: VoucherLocalDataSource = FakeLocal()) =
        VoucherRepositoryImpl(remote, errorMapper, dispatchers, local)

    private class FakeLocal(
        private val listValue: VoucherPage? = null,
        private val detailsValue: VoucherDetails? = null,
    ) : VoucherLocalDataSource {
        var storedItems: List<VoucherSummary> = emptyList()
        var lastListCompany: String? = null
        override suspend fun storeList(companyId: String, items: List<VoucherSummary>, syncedAt: Long) { storedItems = items }
        override suspend fun storeDetails(companyId: String, details: VoucherDetails, syncedAt: Long) = Unit
        override suspend fun list(query: VoucherQuery): VoucherPage? { lastListCompany = query.companyId; return listValue?.takeIf { it.companyId == query.companyId } }
        override suspend fun details(companyId: String, voucherId: String): VoucherDetails? = detailsValue
    }

    private fun sampleSummary() = VoucherSummary(
        identity = VoucherIdentity("v-1"),
        date = "2026-07-27",
        type = "Sales",
        number = "S-1",
        partyName = "Acme",
        referenceNumber = null,
        amount = null,
        status = VoucherStatus.Active,
        dataQuality = VoucherDataQuality.Complete,
    )
}
