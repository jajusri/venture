package com.budcom.android.feature.voucher.domain.usecase

import com.budcom.android.core.common.AppResult
import com.budcom.android.feature.voucher.domain.model.VoucherDataQuality
import com.budcom.android.feature.voucher.domain.model.VoucherDateRange
import com.budcom.android.feature.voucher.domain.model.VoucherDetails
import com.budcom.android.feature.voucher.domain.model.VoucherIdentity
import com.budcom.android.feature.voucher.domain.model.VoucherPage
import com.budcom.android.feature.voucher.domain.model.VoucherQuery
import com.budcom.android.feature.voucher.domain.model.VoucherStatus
import com.budcom.android.feature.voucher.domain.model.VoucherSummary
import com.budcom.android.feature.voucher.domain.repository.VoucherRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class VoucherUseCasesTest {
    @Test
    fun `load refresh and details delegate to repository`() = runTest {
        val page = VoucherPage(
            companyId = "estimation",
            items = listOf(sampleSummary()),
            page = 1,
            pageSize = 50,
            totalItems = 1,
            totalPages = 1,
        )
        val details = VoucherDetails(
            summary = sampleSummary(),
            effectiveDate = null,
            narration = null,
            ledgerEntries = emptyList(),
            inventoryEntries = emptyList(),
        )
        val repo = object : VoucherRepository {
            var listCalls = 0
            var detailCalls = 0
            override suspend fun listVouchers(query: VoucherQuery): AppResult<VoucherPage> {
                listCalls += 1
                return AppResult.Success(page)
            }

            override suspend fun getVoucherDetails(
                companyId: String,
                voucherId: String,
            ): AppResult<VoucherDetails> {
                detailCalls += 1
                return AppResult.Success(details)
            }
        }
        val query = VoucherQuery(
            companyId = "estimation",
            dateRange = VoucherDateRange("2026-07-01", "2026-07-27"),
        )
        assertEquals(page, (LoadVouchersUseCase(repo)(query) as AppResult.Success).value)
        assertEquals(page, (RefreshVouchersUseCase(repo)(query) as AppResult.Success).value)
        assertEquals(details, (GetVoucherDetailsUseCase(repo)("estimation", "v-1") as AppResult.Success).value)
        assertEquals(2, repo.listCalls)
        assertEquals(1, repo.detailCalls)
    }

    private fun sampleSummary() = VoucherSummary(
        identity = VoucherIdentity("v-1"),
        date = "2026-07-27",
        type = "Sales",
        number = "S-1",
        partyName = null,
        referenceNumber = null,
        amount = null,
        status = VoucherStatus.Active,
        dataQuality = VoucherDataQuality.Complete,
    )
}
