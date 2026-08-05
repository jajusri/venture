package com.budcom.android.feature.voucher.data.repository

import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.network.ApiResult
import com.budcom.android.core.network.ErrorMapper
import com.budcom.android.core.network.NetworkError
import com.budcom.android.core.util.DispatcherProvider
import com.budcom.android.core.util.TimeProvider
import com.budcom.android.feature.voucher.data.remote.VoucherRemoteDataSource
import com.budcom.android.feature.voucher.data.local.VoucherLocalDataSource
import com.budcom.android.feature.voucher.domain.model.VoucherCacheState
import com.budcom.android.feature.voucher.domain.model.VoucherDataQuality
import com.budcom.android.feature.voucher.domain.model.VoucherDateRange
import com.budcom.android.feature.voucher.domain.model.VoucherDetails
import com.budcom.android.feature.voucher.domain.model.VoucherIdentity
import com.budcom.android.feature.voucher.domain.model.VoucherInventoryLine
import com.budcom.android.feature.voucher.domain.model.VoucherPage
import com.budcom.android.feature.voucher.domain.model.VoucherQuery
import com.budcom.android.feature.voucher.domain.model.VoucherStatus
import com.budcom.android.feature.voucher.domain.model.VoucherSummary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [VoucherRepositoryImpl] must keep Room as the sole source for [VoucherRepositoryImpl.listVouchers]
 * / [VoucherRepositoryImpl.getVoucherDetails] (never contacting the Connector), and must never let a
 * failed [VoucherRepositoryImpl.refreshVouchers] / [VoucherRepositoryImpl.refreshVoucherDetails] replace
 * or hide valid cached rows. See Phase 3E offline-voucher-reliability root cause notes.
 */
class VoucherRepositoryImplTest {
    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = dispatcher
        override val io: CoroutineDispatcher = dispatcher
        override val default: CoroutineDispatcher = dispatcher
    }
    private val timeProvider = TimeProvider { 999_000L }
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
    fun `listVouchers never calls the remote data source`() = runTest(dispatcher) {
        val remote = FakeRemote(listResult = ApiResult.Success(samplePage()))
        val local = FakeLocal(listValue = samplePage())
        repo(remote, local).listVouchers(query())
        assertEquals(0, remote.listCalls)
    }

    @Test
    fun `getVoucherDetails never calls the remote data source`() = runTest(dispatcher) {
        val remote = FakeRemote(detailsResult = ApiResult.Success(sampleDetails()))
        val local = FakeLocal(detailsValue = sampleDetails())
        repo(remote, local).getVoucherDetails("company-a", "v-1")
        assertEquals(0, remote.detailCalls)
    }

    @Test
    fun `listVouchers returns matching cached rows immediately`() = runTest(dispatcher) {
        val local = FakeLocal(listValue = samplePage())
        val result = repo(FakeRemote(), local).listVouchers(query()) as AppResult.Success
        assertEquals("v-1", result.value.items[0].identity.id)
        assertEquals("company-a", local.lastListCompany)
    }

    @Test
    fun `listVouchers reports no-cache only when Room has never synced this company`() = runTest(dispatcher) {
        val local = FakeLocal(listValue = null)
        val result = repo(FakeRemote(), local).listVouchers(query()) as AppResult.Failure
        assertEquals(VoucherRepositoryImpl.NO_CACHE_MESSAGE, (result.error as AppError.Message).message)
    }

    @Test
    fun `listVouchers returns a true empty page distinctly from no-cache`() = runTest(dispatcher) {
        val emptyButSynced = samplePage().copy(items = emptyList(), totalItems = 0, totalPages = 0)
        val local = FakeLocal(listValue = emptyButSynced)
        val result = repo(FakeRemote(), local).listVouchers(query()) as AppResult.Success
        assertTrue(result.value.items.isEmpty())
    }

    @Test
    fun `refresh success persists and returns fresh live rows`() = runTest(dispatcher) {
        val remote = FakeRemote(listResult = ApiResult.Success(samplePage()))
        val local = FakeLocal(listValue = samplePage().copy(cacheState = VoucherCacheState.Live, lastSyncedAt = 999_000L))
        val result = repo(remote, local).refreshVouchers(query()) as AppResult.Success
        assertEquals(1, local.storedItems.size)
        assertEquals(VoucherCacheState.Live, result.value.cacheState)
        assertEquals(999_000L, result.value.lastSyncedAt)
    }

    @Test
    fun `refresh failure never maps to cached-data absence and does not touch Room`() = runTest(dispatcher) {
        val remote = FakeRemote(listResult = ApiResult.Failure(NetworkError.Timeout()))
        val local = FakeLocal(listValue = samplePage())
        val result = repo(remote, local).refreshVouchers(query()) as AppResult.Failure
        assertTrue(result.error is AppError.Timeout)
        assertEquals(0, local.storeListCalls)
    }

    @Test
    fun `refresh failure on http 4xx still never touches cache`() = runTest(dispatcher) {
        val remote = FakeRemote(listResult = ApiResult.Failure(NetworkError.Http(400, "VALIDATION_ERROR", "Invalid query.")))
        val local = FakeLocal(listValue = samplePage())
        val result = repo(remote, local).refreshVouchers(query()) as AppResult.Failure
        assertTrue(result.error is AppError.Remote)
        assertEquals(400, (result.error as AppError.Remote).httpStatus)
        assertEquals(0, local.storeListCalls)
    }

    @Test
    fun `a caller can still read Room after a failed refresh and see the same cached rows`() = runTest(dispatcher) {
        val remote = FakeRemote(listResult = ApiResult.Failure(NetworkError.NoConnectivity))
        val local = FakeLocal(listValue = samplePage())
        val repository = repo(remote, local)
        repository.refreshVouchers(query())
        val stillCached = repository.listVouchers(query()) as AppResult.Success
        assertEquals("v-1", stillCached.value.items[0].identity.id)
    }

    @Test
    fun `refreshVoucherDetails success persists and returns live details`() = runTest(dispatcher) {
        val remote = FakeRemote(detailsResult = ApiResult.Success(sampleDetails()))
        val local = FakeLocal()
        val result = repo(remote, local).refreshVoucherDetails("company-a", "v-1") as AppResult.Success
        assertEquals(999_000L, result.value.lastSyncedAt)
        assertEquals(VoucherCacheState.Live, result.value.cacheState)
    }

    @Test
    fun `refreshVoucherDetails failure never falls back to cache`() = runTest(dispatcher) {
        val remote = FakeRemote(detailsResult = ApiResult.Failure(NetworkError.Serialization("bad response")))
        val local = FakeLocal(detailsValue = sampleDetails())
        val result = repo(remote, local).refreshVoucherDetails("company-a", "v-1") as AppResult.Failure
        assertTrue(result.error is AppError.Serialization)
    }

    @Test
    fun `getVoucherDetails reports no-cache-details when never synced`() = runTest(dispatcher) {
        val local = FakeLocal(detailsValue = null)
        val result = repo(FakeRemote(), local).getVoucherDetails("company-a", "missing") as AppResult.Failure
        assertEquals(VoucherRepositoryImpl.NO_CACHE_DETAILS_MESSAGE, (result.error as AppError.Message).message)
    }

    @Test
    fun `getVoucherDetails returns cached inventory lines`() = runTest(dispatcher) {
        val local = FakeLocal(detailsValue = sampleDetails())
        val result = repo(FakeRemote(), local).getVoucherDetails("company-a", "v-1") as AppResult.Success
        assertFalse(result.value.inventoryEntries.isEmpty())
        assertEquals("Item", result.value.inventoryEntries.single().itemName)
    }

    private class FakeRemote(
        private val listResult: ApiResult<VoucherPage> = ApiResult.Failure(NetworkError.Unknown()),
        private val detailsResult: ApiResult<VoucherDetails> = ApiResult.Failure(NetworkError.Unknown()),
    ) : VoucherRemoteDataSource {
        var listCalls = 0
        var detailCalls = 0
        override suspend fun fetchVouchers(query: VoucherQuery): ApiResult<VoucherPage> {
            listCalls += 1
            return listResult
        }
        override suspend fun fetchVoucherDetails(companyId: String, voucherId: String): ApiResult<VoucherDetails> {
            detailCalls += 1
            return detailsResult
        }
    }

    private fun repo(remote: VoucherRemoteDataSource, local: VoucherLocalDataSource = FakeLocal()) =
        VoucherRepositoryImpl(remote, errorMapper, dispatchers, local, timeProvider)

    private class FakeLocal(
        private val listValue: VoucherPage? = null,
        private val detailsValue: VoucherDetails? = null,
    ) : VoucherLocalDataSource {
        var storedItems: List<VoucherSummary> = emptyList()
        var storeListCalls = 0
        var lastListCompany: String? = null
        override suspend fun storeList(companyId: String, items: List<VoucherSummary>, syncedAt: Long) {
            storeListCalls += 1
            storedItems = items
        }
        override suspend fun storeDetails(companyId: String, details: VoucherDetails, syncedAt: Long) = Unit
        override suspend fun list(query: VoucherQuery): VoucherPage? { lastListCompany = query.companyId; return listValue?.takeIf { it.companyId == query.companyId } }
        override suspend fun details(companyId: String, voucherId: String): VoucherDetails? = detailsValue
    }

    private fun query() = VoucherQuery("company-a", VoucherDateRange("2026-07-01", "2026-07-27"))

    private fun samplePage() = VoucherPage("company-a", listOf(sampleSummary()), 1, 50, 1, 1)

    private fun sampleDetails() = VoucherDetails(
        sampleSummary(), "2026-07-27", "cached", emptyList(),
        listOf(VoucherInventoryLine(1, "Item", "2 pcs", "10", null)),
    )

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
