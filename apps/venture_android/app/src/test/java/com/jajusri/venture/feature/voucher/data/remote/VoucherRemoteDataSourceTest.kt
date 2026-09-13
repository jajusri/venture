package com.jajusri.venture.feature.voucher.data.remote

import com.jajusri.venture.core.network.ApiException
import com.jajusri.venture.core.network.ApiResult
import com.jajusri.venture.core.network.ErrorMapper
import com.jajusri.venture.core.network.NetworkConnectivityObserver
import com.jajusri.venture.core.network.NetworkError
import com.jajusri.venture.feature.voucher.domain.model.VoucherDateRange
import com.jajusri.venture.feature.voucher.domain.model.VoucherQuery
import com.jajusri.venture.feature.voucher.domain.model.VoucherSort
import com.jajusri.venture.feature.voucher.domain.model.VoucherSortDirection
import com.jajusri.venture.feature.voucher.domain.model.VoucherSortField
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoucherRemoteDataSourceTest {
    private val errorMapper = object : ErrorMapper {
        override fun toNetworkError(throwable: Throwable): NetworkError = when (throwable) {
            is ApiException -> throwable.error
            else -> NetworkError.Unknown(cause = throwable)
        }
        override fun toAppError(error: NetworkError) = when (error) {
            is NetworkError.NoConnectivity -> com.jajusri.venture.core.common.AppError.Offline()
            is NetworkError.Timeout -> com.jajusri.venture.core.common.AppError.Timeout()
            is NetworkError.Http -> com.jajusri.venture.core.common.AppError.Remote(
                error.httpStatus,
                error.code,
                error.message,
            )
            is NetworkError.Serialization -> com.jajusri.venture.core.common.AppError.Serialization(error.message)
            is NetworkError.Unknown -> com.jajusri.venture.core.common.AppError.Unexpected(
                IllegalStateException(error.message),
            )
        }
    }
    private val online = object : NetworkConnectivityObserver {
        override val isOnline: Flow<Boolean> = flowOf(true)
        override fun current(): Boolean = true
    }

    @Test
    fun `coerces paging and encodes sort and filters`() = runTest {
        val api = CapturingVoucherApi()
        val remote = DefaultVoucherRemoteDataSource(api, errorMapper, online)
        val result = remote.fetchVouchers(
            VoucherQuery(
                companyId = "venture-test-01",
                dateRange = VoucherDateRange("2026-07-01", "2026-07-27"),
                searchText = "  acme  ",
                voucherType = " Sales ",
                voucherNumber = "",
                partyName = "  ".padEnd(200, 'x'),
                page = 0,
                pageSize = 500,
                sort = VoucherSort(VoucherSortField.Amount, VoucherSortDirection.Desc),
            ),
        )
        assertTrue(result is ApiResult.Success)
        assertEquals("venture-test-01", api.lastCompany)
        assertEquals("2026-07-01", api.lastFrom)
        assertEquals("2026-07-27", api.lastTo)
        assertEquals(1, api.lastPage)
        assertEquals(100, api.lastPageSize)
        assertEquals("-amount", api.lastSort)
        assertEquals("acme", api.lastQuery)
        assertEquals("Sales", api.lastVoucherType)
        assertNull(api.lastVoucherNumber)
        assertEquals(128, api.lastPartyName?.length)
        val page = (result as ApiResult.Success).data
        assertEquals(2, page.totalPages)
        assertEquals(true, page.canLoadMore)
        assertEquals(50, page.pageSize) // from envelope, not coerced request size
    }

    @Test
    fun `maps null voucher body to not found`() = runTest {
        val api = CapturingVoucherApi(detailsNull = true)
        val remote = DefaultVoucherRemoteDataSource(api, errorMapper, online)
        val result = remote.fetchVoucherDetails("venture-test-01", "missing")
        assertTrue(result is ApiResult.Failure)
        val error = (result as ApiResult.Failure).error
        assertTrue(error is NetworkError.Http)
        assertEquals(404, (error as NetworkError.Http).httpStatus)
        assertEquals("NOT_FOUND", error.code)
    }

    @Test
    fun `offline connectivity short-circuits without calling api`() = runTest {
        val api = CapturingVoucherApi()
        val offline = object : NetworkConnectivityObserver {
            override val isOnline: Flow<Boolean> = flowOf(false)
            override fun current(): Boolean = false
        }
        val remote = DefaultVoucherRemoteDataSource(api, errorMapper, offline)
        val result = remote.fetchVouchers(
            VoucherQuery(
                companyId = "venture-test-01",
                dateRange = VoucherDateRange("2026-07-01", "2026-07-27"),
            ),
        )
        assertTrue(result is ApiResult.Failure)
        assertTrue((result as ApiResult.Failure).error is NetworkError.NoConnectivity)
        assertEquals(0, api.listCalls)
    }

    @Test
    fun `a stalled connector call is bounded by the attempt timeout, retries once, then fails`() = runTest {
        val api = HangingVoucherApi()
        val policy = VoucherRefreshTimeoutPolicy(attemptTimeoutMillis = 3_000L, retryDelayMillis = 300L, maxAttempts = 2)
        val remote = DefaultVoucherRemoteDataSource(api, errorMapper, online, policy)
        val result = remote.fetchVouchers(
            VoucherQuery(companyId = "venture-test-01", dateRange = VoucherDateRange("2026-07-01", "2026-07-27")),
        )
        assertTrue(result is ApiResult.Failure)
        assertTrue((result as ApiResult.Failure).error is NetworkError.Timeout)
        assertEquals(2, api.listCalls)
        // Two bounded attempts plus one short retry delay: well under the 6-8s ceiling required for manual refresh.
        assertEquals(6_300L, currentTime)
    }

    @Test
    fun `a connector call that recovers on the retry succeeds without a third attempt`() = runTest {
        val api = HangingVoucherApi(succeedsOnAttempt = 2)
        val policy = VoucherRefreshTimeoutPolicy(attemptTimeoutMillis = 3_000L, retryDelayMillis = 300L, maxAttempts = 2)
        val remote = DefaultVoucherRemoteDataSource(api, errorMapper, online, policy)
        val result = remote.fetchVouchers(
            VoucherQuery(companyId = "venture-test-01", dateRange = VoucherDateRange("2026-07-01", "2026-07-27")),
        )
        assertTrue(result is ApiResult.Success)
        assertEquals(2, api.listCalls)
        assertEquals(3_300L, currentTime)
    }

    private class HangingVoucherApi(private val succeedsOnAttempt: Int? = null) : VoucherApi {
        var listCalls = 0
        override suspend fun listVouchers(
            company: String,
            from: String,
            to: String,
            page: Int,
            pageSize: Int,
            sort: String?,
            query: String?,
            voucherType: String?,
            voucherNumber: String?,
            partyName: String?,
            includeDetails: Boolean?,
        ): VoucherListEnvelopeDto {
            listCalls += 1
            if (succeedsOnAttempt != null && listCalls >= succeedsOnAttempt) {
                return VoucherListEnvelopeDto(
                    schemaVersion = "1.0.0",
                    data = VoucherListDataDto(
                        companyId = company,
                        items = emptyList(),
                        pagination = VoucherPaginationDto(page = page, pageSize = pageSize, totalItems = 0, totalPages = 0),
                    ),
                )
            }
            awaitCancellation()
        }

        override suspend fun getVoucher(id: String, company: String): VoucherDetailsEnvelopeDto = awaitCancellation()
    }

    private class CapturingVoucherApi(
        private val detailsNull: Boolean = false,
    ) : VoucherApi {
        var listCalls = 0
        var lastCompany: String? = null
        var lastFrom: String? = null
        var lastTo: String? = null
        var lastPage: Int? = null
        var lastPageSize: Int? = null
        var lastSort: String? = null
        var lastQuery: String? = null
        var lastVoucherType: String? = null
        var lastVoucherNumber: String? = null
        var lastPartyName: String? = null
        var lastIncludeDetails: Boolean? = null

        override suspend fun listVouchers(
            company: String,
            from: String,
            to: String,
            page: Int,
            pageSize: Int,
            sort: String?,
            query: String?,
            voucherType: String?,
            voucherNumber: String?,
            partyName: String?,
            includeDetails: Boolean?,
        ): VoucherListEnvelopeDto {
            listCalls += 1
            lastCompany = company
            lastFrom = from
            lastTo = to
            lastPage = page
            lastPageSize = pageSize
            lastSort = sort
            lastQuery = query
            lastVoucherType = voucherType
            lastVoucherNumber = voucherNumber
            lastPartyName = partyName
            lastIncludeDetails = includeDetails
            return VoucherListEnvelopeDto(
                schemaVersion = "1.0.0",
                data = VoucherListDataDto(
                    companyId = company,
                    items = emptyList(),
                    pagination = VoucherPaginationDto(
                        page = page,
                        pageSize = 50,
                        totalItems = 75,
                        totalPages = 2,
                    ),
                ),
            )
        }

        override suspend fun getVoucher(id: String, company: String): VoucherDetailsEnvelopeDto =
            VoucherDetailsEnvelopeDto(
                schemaVersion = "1.0.0",
                data = VoucherDetailsDataDto(
                    companyId = company,
                    voucher = if (detailsNull) {
                        null
                    } else {
                        VoucherPublicDetailsDto(
                            id = id,
                            date = "2026-07-27",
                            type = "Sales",
                            status = "active",
                            dataQuality = "complete",
                        )
                    },
                ),
            )
    }
}
