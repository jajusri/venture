package com.jajusri.venture.feature.voucher.domain.usecase

import com.jajusri.venture.core.common.AppResult
import com.jajusri.venture.feature.voucher.domain.model.VoucherDataQuality
import com.jajusri.venture.feature.voucher.domain.model.VoucherDateRange
import com.jajusri.venture.feature.voucher.domain.model.VoucherDetails
import com.jajusri.venture.feature.voucher.domain.model.VoucherIdentity
import com.jajusri.venture.feature.voucher.domain.model.VoucherPage
import com.jajusri.venture.feature.voucher.domain.model.VoucherQuery
import com.jajusri.venture.feature.voucher.domain.model.VoucherStatus
import com.jajusri.venture.feature.voucher.domain.model.VoucherSummary
import com.jajusri.venture.feature.voucher.domain.repository.VoucherRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class VoucherUseCasesTest {
    @Test
    fun `load and refresh delegate to their own dedicated repository methods`() = runTest {
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
            var refreshListCalls = 0
            var detailCalls = 0
            var refreshDetailCalls = 0
            var summaryCalls = 0
            override suspend fun listVouchers(query: VoucherQuery): AppResult<VoucherPage> {
                listCalls += 1
                return AppResult.Success(page)
            }

            override suspend fun refreshVouchers(query: VoucherQuery): AppResult<VoucherPage> {
                refreshListCalls += 1
                return AppResult.Success(page)
            }

            override suspend fun getVoucherDetails(
                companyId: String,
                voucherId: String,
            ): AppResult<VoucherDetails> {
                detailCalls += 1
                return AppResult.Success(details)
            }

            override suspend fun refreshVoucherDetails(
                companyId: String,
                voucherId: String,
            ): AppResult<VoucherDetails> {
                refreshDetailCalls += 1
                return AppResult.Success(details)
            }

            override suspend fun getCachedVoucherSummary(companyId: String, voucherId: String): VoucherSummary? {
                summaryCalls += 1
                return sampleSummary()
            }
        }
        val query = VoucherQuery(
            companyId = "estimation",
            dateRange = VoucherDateRange("2026-07-01", "2026-07-27"),
        )
        assertEquals(page, (LoadVouchersUseCase(repo)(query) as AppResult.Success).value)
        assertEquals(page, (RefreshVouchersUseCase(repo)(query) as AppResult.Success).value)
        assertEquals(details, (GetVoucherDetailsUseCase(repo)("estimation", "v-1") as AppResult.Success).value)
        assertEquals(details, (RefreshVoucherDetailsUseCase(repo)("estimation", "v-1") as AppResult.Success).value)
        assertEquals(sampleSummary(), GetCachedVoucherSummaryUseCase(repo)("estimation", "v-1"))

        // Load must never touch the refresh path, and refresh must never touch the cache-read path.
        assertEquals(1, repo.listCalls)
        assertEquals(1, repo.refreshListCalls)
        assertEquals(1, repo.detailCalls)
        assertEquals(1, repo.refreshDetailCalls)
        assertEquals(1, repo.summaryCalls)
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
