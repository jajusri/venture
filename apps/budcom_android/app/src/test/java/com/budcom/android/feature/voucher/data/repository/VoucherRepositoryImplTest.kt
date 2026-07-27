package com.budcom.android.feature.voucher.data.repository

import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.network.ApiResult
import com.budcom.android.core.network.ErrorMapper
import com.budcom.android.core.network.NetworkError
import com.budcom.android.core.util.DispatcherProvider
import com.budcom.android.feature.voucher.data.remote.VoucherRemoteDataSource
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
        val repo = VoucherRepositoryImpl(remote, errorMapper, dispatchers)
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
        val repo = VoucherRepositoryImpl(remote, errorMapper, dispatchers)
        val result = repo.listVouchers(
            VoucherQuery(
                companyId = "estimation",
                dateRange = VoucherDateRange("2026-07-01", "2026-07-27"),
            ),
        ) as AppResult.Failure
        assertTrue(result.error is AppError.Offline)
    }

    private class FakeRemote(
        private val listResult: ApiResult<VoucherPage>,
        private val detailsResult: ApiResult<VoucherDetails> = ApiResult.Failure(NetworkError.Unknown()),
    ) : VoucherRemoteDataSource {
        override suspend fun fetchVouchers(query: VoucherQuery) = listResult
        override suspend fun fetchVoucherDetails(companyId: String, voucherId: String) = detailsResult
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
