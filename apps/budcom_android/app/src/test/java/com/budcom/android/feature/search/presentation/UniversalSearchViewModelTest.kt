package com.budcom.android.feature.search.presentation

import androidx.lifecycle.SavedStateHandle
import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.network.NetworkConnectivityObserver
import com.budcom.android.feature.company.domain.port.CompanySessionPort
import com.budcom.android.feature.company.domain.port.SelectedCompanyStatus
import com.budcom.android.feature.company.domain.port.SessionValidationStatus
import com.budcom.android.feature.masterdata.domain.model.MasterDataPagination
import com.budcom.android.feature.masterdata.ledger.domain.model.Ledger
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerDataQuality
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerPage
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerQuery
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatus
import com.budcom.android.feature.masterdata.ledger.domain.port.SearchLedgersPort
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItem
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItemDataQuality
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItemPage
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItemQuery
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItemStatus
import com.budcom.android.feature.masterdata.stockitem.domain.port.SearchStockItemsPort
import com.budcom.android.feature.search.domain.UniversalSearchDefaults
import com.budcom.android.feature.search.domain.model.SearchSection
import com.budcom.android.feature.search.domain.usecase.ExecuteUniversalSearchUseCase
import com.budcom.android.feature.voucher.domain.model.VoucherDataQuality
import com.budcom.android.feature.voucher.domain.model.VoucherIdentity
import com.budcom.android.feature.voucher.domain.model.VoucherPage
import com.budcom.android.feature.voucher.domain.model.VoucherQuery
import com.budcom.android.feature.voucher.domain.model.VoucherStatus
import com.budcom.android.feature.voucher.domain.model.VoucherSummary
import com.budcom.android.feature.voucher.domain.port.SearchVouchersPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class UniversalSearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = Clock.fixed(Instant.parse("2026-07-27T12:00:00Z"), ZoneOffset.UTC)
    private lateinit var ledgers: FakeLedgers
    private lateinit var stock: FakeStock
    private lateinit var vouchers: FakeVouchers
    private lateinit var companySession: SearchFakeCompanySession
    private lateinit var connectivity: SearchFakeConnectivity

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        ledgers = FakeLedgers()
        stock = FakeStock()
        vouchers = FakeVouchers()
        companySession = SearchFakeCompanySession("estimation")
        connectivity = SearchFakeConnectivity(true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createVm(handle: SavedStateHandle = SavedStateHandle()) = UniversalSearchViewModel(
        savedStateHandle = handle,
        executeSearch = ExecuteUniversalSearchUseCase(ledgers, stock, vouchers, clock),
        companySession = companySession,
        connectivityObserver = connectivity,
    )

    @Test
    fun initialIdleState() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.showIdleHint)
        assertEquals(0, ledgers.calls)
    }

    @Test
    fun blankQueryDoesNotSearch() = runTest(dispatcher) {
        val vm = createVm()
        vm.onEvent(UniversalSearchEvent.QueryChanged("   "))
        advanceTimeBy(UniversalSearchDefaults.SEARCH_DEBOUNCE_MS)
        advanceUntilIdle()
        assertEquals(0, ledgers.calls)
        assertTrue(vm.uiState.value.showIdleHint)
    }

    @Test
    fun debouncedSearchLoadsGroupedContent() = runTest(dispatcher) {
        val vm = createVm()
        vm.onEvent(UniversalSearchEvent.QueryChanged("cash"))
        advanceTimeBy(UniversalSearchDefaults.SEARCH_DEBOUNCE_MS - 1)
        assertEquals(0, ledgers.calls)
        advanceTimeBy(1)
        advanceUntilIdle()
        assertEquals(1, ledgers.calls)
        assertEquals(1, stock.calls)
        assertEquals(1, vouchers.calls)
        assertFalse(vm.uiState.value.isSearching)
        assertEquals(3, vm.uiState.value.sections.size)
        assertEquals("2026-06-27", vm.uiState.value.voucherDateFrom)
        assertTrue(vm.uiState.value.sections[0] is SearchSectionUi.Success)
    }

    @Test
    fun rapidQueryReplacementCancelsObsoleteSearch() = runTest(dispatcher) {
        val vm = createVm()
        vm.onEvent(UniversalSearchEvent.QueryChanged("one"))
        advanceTimeBy(100)
        vm.onEvent(UniversalSearchEvent.QueryChanged("two"))
        advanceTimeBy(UniversalSearchDefaults.SEARCH_DEBOUNCE_MS)
        advanceUntilIdle()
        assertEquals(1, ledgers.calls)
        assertEquals("two", ledgers.lastQuery!!.text)
        assertEquals("two", vm.uiState.value.activeQuery)
    }

    @Test
    fun clearQueryResetsState() = runTest(dispatcher) {
        val vm = createVm()
        vm.onEvent(UniversalSearchEvent.QueryChanged("cash"))
        advanceTimeBy(UniversalSearchDefaults.SEARCH_DEBOUNCE_MS)
        advanceUntilIdle()
        vm.onEvent(UniversalSearchEvent.ClearQuery)
        advanceUntilIdle()
        assertEquals("", vm.uiState.value.query)
        assertNull(vm.uiState.value.activeQuery)
        assertTrue(vm.uiState.value.showIdleHint)
    }

    @Test
    fun partialFailureState() = runTest(dispatcher) {
        stock.result = AppResult.Failure(AppError.Timeout())
        val vm = createVm()
        vm.onEvent(UniversalSearchEvent.QueryChanged("x"))
        advanceTimeBy(UniversalSearchDefaults.SEARCH_DEBOUNCE_MS)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isPartialFailure)
        assertTrue(vm.uiState.value.sections[0] is SearchSectionUi.Success)
        assertTrue(vm.uiState.value.sections[1] is SearchSectionUi.Failure)
    }

    @Test
    fun emptyResults() = runTest(dispatcher) {
        ledgers.result = AppResult.Success(LedgerPage(emptyList(), 1, 5, 0, 0, null))
        stock.result = AppResult.Success(StockItemPage(emptyList(), MasterDataPagination(1, 5, 0, 0), null))
        vouchers.result = AppResult.Success(VoucherPage("estimation", emptyList(), 1, 5, 0, 0))
        val vm = createVm()
        vm.onEvent(UniversalSearchEvent.QueryChanged("zzz"))
        advanceTimeBy(UniversalSearchDefaults.SEARCH_DEBOUNCE_MS)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.showEmpty)
    }

    @Test
    fun retryReplaysActiveQuery() = runTest(dispatcher) {
        val vm = createVm()
        vm.onEvent(UniversalSearchEvent.QueryChanged("cash"))
        advanceTimeBy(UniversalSearchDefaults.SEARCH_DEBOUNCE_MS)
        advanceUntilIdle()
        assertEquals(1, ledgers.calls)
        vm.onEvent(UniversalSearchEvent.Retry)
        advanceUntilIdle()
        assertEquals(2, ledgers.calls)
    }

    @Test
    fun seeAllAndResultNavigation() = runTest(dispatcher) {
        ledgers.result = AppResult.Success(
            LedgerPage(listOf(sampleLedger()), 1, 5, 20, 4, null),
        )
        val vm = createVm()
        val nav = mutableListOf<UniversalSearchNavigation>()
        val job = launch { vm.navigation.collect { nav.add(it) } }
        vm.onEvent(UniversalSearchEvent.QueryChanged("cash"))
        advanceTimeBy(UniversalSearchDefaults.SEARCH_DEBOUNCE_MS)
        advanceUntilIdle()

        vm.onEvent(UniversalSearchEvent.SeeAll(SearchSection.Ledgers))
        vm.onEvent(
            UniversalSearchEvent.ResultClicked(
                SearchResultRowUi("v1", SearchSection.Vouchers, "Sales · S-1", null),
            ),
        )
        advanceUntilIdle()
        assertTrue(nav.any { it is UniversalSearchNavigation.LedgerBrowser && it.query == "cash" })
        assertTrue(nav.any { it is UniversalSearchNavigation.VoucherDetails && it.voucherId == "v1" })
        job.cancel()
    }

    @Test
    fun offlineFlagTracked() = runTest(dispatcher) {
        val vm = createVm()
        connectivity.online.value = false
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isOnline)
    }

    @Test
    fun queryIsWrittenToSavedState() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val vm = createVm(handle)
        vm.onEvent(UniversalSearchEvent.QueryChanged("cash"))
        assertEquals("cash", handle.get<String>(com.budcom.android.navigation.Routes.QUERY_ARG))
        vm.onEvent(UniversalSearchEvent.ClearQuery)
        assertEquals("", handle.get<String>(com.budcom.android.navigation.Routes.QUERY_ARG))
    }
}

private fun sampleLedger() = Ledger(
    id = "l1",
    name = "Cash",
    alias = null,
    parentGroup = "Assets",
    status = LedgerStatus.Active,
    closingBalance = null,
    dataQuality = LedgerDataQuality.Complete,
    syncedAt = "t",
)

private fun sampleStock() = StockItem(
    id = "s1",
    name = "Widget",
    alias = null,
    parentGroup = "Goods",
    category = null,
    baseUnit = "Nos",
    partNumber = null,
    hsnCode = null,
    gstRate = null,
    status = StockItemStatus.Active,
    closingBalance = null,
    dataQuality = StockItemDataQuality.Complete,
    syncedAt = "t",
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

private class FakeLedgers : SearchLedgersPort {
    var calls = 0
    var lastQuery: LedgerQuery? = null
    var result: AppResult<LedgerPage> = AppResult.Success(
        LedgerPage(listOf(sampleLedger()), 1, 5, 1, 1, null),
    )
    override suspend fun search(query: LedgerQuery): AppResult<LedgerPage> {
        calls++
        lastQuery = query
        return result
    }
}

private class FakeStock : SearchStockItemsPort {
    var calls = 0
    var result: AppResult<StockItemPage> = AppResult.Success(
        StockItemPage(listOf(sampleStock()), MasterDataPagination(1, 5, 1, 1), null),
    )
    override suspend fun search(query: StockItemQuery): AppResult<StockItemPage> {
        calls++
        return result
    }
}

private class FakeVouchers : SearchVouchersPort {
    var calls = 0
    var result: AppResult<VoucherPage> = AppResult.Success(
        VoucherPage("estimation", listOf(sampleVoucher()), 1, 5, 1, 1),
    )
    override suspend fun search(query: VoucherQuery): AppResult<VoucherPage> {
        calls++
        return result
    }
}

private class SearchFakeCompanySession(
    initial: String?,
) : CompanySessionPort {
    val selected = MutableStateFlow(initial)
    override fun observeSelectedCompanyId(): Flow<String?> = selected
    override suspend fun readSelectedCompany(): AppResult<SelectedCompanyStatus> =
        AppResult.Success(SelectedCompanyStatus(selected.value, selected.value))
    override suspend fun validateSessionStatus(): AppResult<SessionValidationStatus> =
        error("unused")
}

private class SearchFakeConnectivity(
    initial: Boolean,
) : NetworkConnectivityObserver {
    val online = MutableStateFlow(initial)
    override val isOnline: Flow<Boolean> = online
    override fun current(): Boolean = online.value
}
