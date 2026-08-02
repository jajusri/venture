package com.budcom.android.feature.voucher.data.remote

import com.budcom.android.core.network.ApiException
import com.budcom.android.core.network.ApiResult
import com.budcom.android.core.network.ErrorMapper
import com.budcom.android.core.network.NetworkConnectivityObserver
import com.budcom.android.core.network.NetworkError
import com.budcom.android.core.network.RetryPolicy
import com.budcom.android.feature.voucher.domain.model.VoucherDateRange
import com.budcom.android.feature.voucher.domain.model.VoucherQuery
import com.budcom.android.feature.voucher.domain.model.VoucherSort
import com.budcom.android.feature.voucher.domain.model.VoucherSortDirection
import com.budcom.android.feature.voucher.domain.model.VoucherSortField
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoucherRemoteDataSourceTest {
    private val errorMapper = object : ErrorMapper {
        override fun toNetworkError(throwable: Throwable): NetworkError = when (throwable) {
            is ApiException -> throwable.error
            else -> NetworkError.Unknown(cause = throwable)
        }
        override fun toAppError(error: NetworkError) = when (error) {
            is NetworkError.NoConnectivity -> com.budcom.android.core.common.AppError.Offline()
            is NetworkError.Timeout -> com.budcom.android.core.common.AppError.Timeout()
            is NetworkError.Http -> com.budcom.android.core.common.AppError.Remote(
                error.httpStatus,
                error.code,
                error.message,
            )
            is NetworkError.Serialization -> com.budcom.android.core.common.AppError.Serialization(error.message)
            is NetworkError.Unknown -> com.budcom.android.core.common.AppError.Unexpected(
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
        val remote = DefaultVoucherRemoteDataSource(api, errorMapper, online, RetryPolicy.None)
        val result = remote.fetchVouchers(
            VoucherQuery(
                companyId = "budcom-test-01",
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
        assertEquals("budcom-test-01", api.lastCompany)
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
        val remote = DefaultVoucherRemoteDataSource(api, errorMapper, online, RetryPolicy.None)
        val result = remote.fetchVoucherDetails("budcom-test-01", "missing")
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
        val remote = DefaultVoucherRemoteDataSource(api, errorMapper, offline, RetryPolicy.None)
        val result = remote.fetchVouchers(
            VoucherQuery(
                companyId = "budcom-test-01",
                dateRange = VoucherDateRange("2026-07-01", "2026-07-27"),
            ),
        )
        assertTrue(result is ApiResult.Failure)
        assertTrue((result as ApiResult.Failure).error is NetworkError.NoConnectivity)
        assertEquals(0, api.listCalls)
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
