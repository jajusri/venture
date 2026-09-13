package com.jajusri.venture.feature.search.domain.usecase

import com.jajusri.venture.core.common.AppError
import com.jajusri.venture.core.common.AppResult
import com.jajusri.venture.feature.masterdata.domain.model.MasterDataPagination
import com.jajusri.venture.feature.masterdata.ledger.domain.model.Ledger
import com.jajusri.venture.feature.masterdata.ledger.domain.model.LedgerDataQuality
import com.jajusri.venture.feature.masterdata.ledger.domain.model.LedgerPage
import com.jajusri.venture.feature.masterdata.ledger.domain.model.LedgerQuery
import com.jajusri.venture.feature.masterdata.ledger.domain.model.LedgerStatus
import com.jajusri.venture.feature.masterdata.ledger.domain.port.SearchLedgersPort
import com.jajusri.venture.feature.masterdata.stockitem.domain.model.StockItem
import com.jajusri.venture.feature.masterdata.stockitem.domain.model.StockItemDataQuality
import com.jajusri.venture.feature.masterdata.stockitem.domain.model.StockItemPage
import com.jajusri.venture.feature.masterdata.stockitem.domain.model.StockItemQuery
import com.jajusri.venture.feature.masterdata.stockitem.domain.model.StockItemStatus
import com.jajusri.venture.feature.masterdata.stockitem.domain.port.SearchStockItemsPort
import com.jajusri.venture.feature.search.domain.UniversalSearchDefaults
import com.jajusri.venture.feature.search.domain.model.SearchQuery
import com.jajusri.venture.feature.search.domain.model.SearchSection
import com.jajusri.venture.feature.search.domain.model.SearchSectionState
import com.jajusri.venture.feature.voucher.domain.model.VoucherDataQuality
import com.jajusri.venture.feature.voucher.domain.model.VoucherIdentity
import com.jajusri.venture.feature.voucher.domain.model.VoucherPage
import com.jajusri.venture.feature.voucher.domain.model.VoucherQuery
import com.jajusri.venture.feature.voucher.domain.model.VoucherStatus
import com.jajusri.venture.feature.voucher.domain.model.VoucherSummary
import com.jajusri.venture.feature.voucher.domain.port.SearchVouchersPort
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ExecuteUniversalSearchUseCaseTest {
    private val clock = Clock.fixed(Instant.parse("2026-07-27T12:00:00Z"), ZoneOffset.UTC)

    @Test
    fun blankQueryDoesNotSearch() = runTest {
        val ledgers = RecordingLedgersPort()
        val stock = RecordingStockPort()
        val vouchers = RecordingVouchersPort()
        val useCase = ExecuteUniversalSearchUseCase(ledgers, stock, vouchers, clock)
        assertNull(useCase(SearchQuery("  "), companyId = "c1"))
        assertEquals(0, ledgers.calls)
        assertEquals(0, stock.calls)
        assertEquals(0, vouchers.calls)
    }

    @Test
    fun allSuccessWithDeterministicOrderAndBoundedLimit() = runTest {
        val ledgers = RecordingLedgersPort(successPage(total = 12))
        val stock = RecordingStockPort(successStock(total = 1))
        val vouchers = RecordingVouchersPort(successVouchers(total = 8))
        val useCase = ExecuteUniversalSearchUseCase(ledgers, stock, vouchers, clock)

        val result = useCase(SearchQuery("cash"), companyId = "estimation")!!
        assertEquals("cash", result.query)
        assertEquals(
            listOf(SearchSection.Ledgers, SearchSection.StockItems, SearchSection.Vouchers),
            result.sections.map { it.section },
        )
        assertEquals(UniversalSearchDefaults.PREVIEW_PAGE_SIZE, ledgers.lastQuery!!.pageSize)
        assertEquals("cash", ledgers.lastQuery!!.text)
        assertEquals("cash", stock.lastQuery!!.text)
        assertEquals("estimation", vouchers.lastQuery!!.companyId)
        assertEquals("cash", vouchers.lastQuery!!.searchText)
        assertEquals("2026-06-27", vouchers.lastQuery!!.dateRange.from)
        assertEquals("2026-07-27", vouchers.lastQuery!!.dateRange.to)
        assertEquals("2026-06-27", result.voucherDateRange.from)
        assertEquals("2026-07-27", result.voucherDateRange.to)

        val ledgerSection = result.sections[0] as SearchSectionState.Success
        assertTrue(ledgerSection.showSeeAll)
        assertEquals(12, ledgerSection.totalItems)

        val stockSection = result.sections[1] as SearchSectionState.Success
        assertFalse(stockSection.showSeeAll)

        val voucherSection = result.sections[2] as SearchSectionState.Success
        assertTrue(voucherSection.showSeeAll)
    }

    @Test
    fun allEmpty() = runTest {
        val useCase = ExecuteUniversalSearchUseCase(
            RecordingLedgersPort(emptyLedgerPage()),
            RecordingStockPort(emptyStockPage()),
            RecordingVouchersPort(emptyVoucherPage()),
            clock,
        )
        val result = useCase(SearchQuery("zzz"), companyId = "c1")!!
        assertTrue(result.allEmpty)
        assertTrue(result.sections.all { it is SearchSectionState.Empty })
    }

    @Test
    fun partialSuccessPreservesSuccessfulSections() = runTest {
        val useCase = ExecuteUniversalSearchUseCase(
            RecordingLedgersPort(successPage(total = 1)),
            RecordingStockPort(AppResult.Failure(AppError.Timeout())),
            RecordingVouchersPort(successVouchers(total = 1)),
            clock,
        )
        val result = useCase(SearchQuery("acme"), companyId = "c1")!!
        assertTrue(result.isPartialSuccess)
        assertTrue(result.sections[0] is SearchSectionState.Success)
        assertTrue(result.sections[1] is SearchSectionState.Failure)
        assertTrue(result.sections[2] is SearchSectionState.Success)
    }

    @Test
    fun allFailure() = runTest {
        val useCase = ExecuteUniversalSearchUseCase(
            RecordingLedgersPort(AppResult.Failure(AppError.Offline())),
            RecordingStockPort(AppResult.Failure(AppError.Offline())),
            RecordingVouchersPort(AppResult.Failure(AppError.Offline())),
            clock,
        )
        val result = useCase(SearchQuery("x"), companyId = "c1")!!
        assertTrue(result.sections.all { it is SearchSectionState.Failure })
        assertFalse(result.hasAnyHits)
    }

    @Test
    fun missingCompanyFailsOnlyVoucherSection() = runTest {
        val vouchers = RecordingVouchersPort()
        val useCase = ExecuteUniversalSearchUseCase(
            RecordingLedgersPort(successPage(total = 1)),
            RecordingStockPort(successStock(total = 1)),
            vouchers,
            clock,
        )
        val result = useCase(SearchQuery("x"), companyId = null)!!
        assertEquals(0, vouchers.calls)
        assertTrue(result.sections[2] is SearchSectionState.Failure)
        assertTrue(result.sections[0] is SearchSectionState.Success)
    }
}

private fun successPage(total: Int) = AppResult.Success(
    LedgerPage(
        items = listOf(sampleLedger()),
        page = 1,
        pageSize = UniversalSearchDefaults.PREVIEW_PAGE_SIZE,
        totalItems = total,
        totalPages = (total + 4) / 5,
        dataFreshnessAt = null,
    ),
)

private fun emptyLedgerPage() = AppResult.Success(
    LedgerPage(emptyList(), 1, 5, 0, 0, null),
)

private fun successStock(total: Int) = AppResult.Success(
    StockItemPage(
        items = listOf(sampleStock()),
        pagination = MasterDataPagination(1, 5, total, (total + 4) / 5),
        dataFreshnessAt = null,
    ),
)

private fun emptyStockPage() = AppResult.Success(
    StockItemPage(emptyList(), MasterDataPagination(1, 5, 0, 0), null),
)

private fun successVouchers(total: Int) = AppResult.Success(
    VoucherPage(
        companyId = "estimation",
        items = listOf(sampleVoucher()),
        page = 1,
        pageSize = 5,
        totalItems = total,
        totalPages = (total + 4) / 5,
    ),
)

private fun emptyVoucherPage() = AppResult.Success(
    VoucherPage("estimation", emptyList(), 1, 5, 0, 0),
)

private fun sampleLedger() = Ledger(
    id = "l1",
    name = "Cash",
    alias = null,
    parentGroup = "Current Assets",
    status = LedgerStatus.Active,
    closingBalance = null,
    dataQuality = LedgerDataQuality.Complete,
    syncedAt = "2026-07-01T00:00:00Z",
)

private fun sampleStock() = StockItem(
    id = "s1",
    name = "Widget",
    alias = null,
    parentGroup = "Goods",
    category = "Primary",
    baseUnit = "Nos",
    partNumber = null,
    hsnCode = null,
    gstRate = null,
    status = StockItemStatus.Active,
    closingBalance = null,
    dataQuality = StockItemDataQuality.Complete,
    syncedAt = "2026-07-01T00:00:00Z",
)

private fun sampleVoucher() = VoucherSummary(
    identity = VoucherIdentity("v1"),
    date = "2026-07-20",
    type = "Sales",
    number = "S-1",
    partyName = "Acme",
    referenceNumber = null,
    amount = null,
    status = VoucherStatus.Active,
    dataQuality = VoucherDataQuality.Complete,
)

private class RecordingLedgersPort(
    private val result: AppResult<LedgerPage> = successPage(1),
) : SearchLedgersPort {
    var calls = 0
    var lastQuery: LedgerQuery? = null
    override suspend fun search(query: LedgerQuery): AppResult<LedgerPage> {
        calls++
        lastQuery = query
        return result
    }
}

private class RecordingStockPort(
    private val result: AppResult<StockItemPage> = successStock(1),
) : SearchStockItemsPort {
    var calls = 0
    var lastQuery: StockItemQuery? = null
    override suspend fun search(query: StockItemQuery): AppResult<StockItemPage> {
        calls++
        lastQuery = query
        return result
    }
}

private class RecordingVouchersPort(
    private val result: AppResult<VoucherPage> = successVouchers(1),
) : SearchVouchersPort {
    var calls = 0
    var lastQuery: VoucherQuery? = null
    override suspend fun search(query: VoucherQuery): AppResult<VoucherPage> {
        calls++
        lastQuery = query
        return result
    }
}
