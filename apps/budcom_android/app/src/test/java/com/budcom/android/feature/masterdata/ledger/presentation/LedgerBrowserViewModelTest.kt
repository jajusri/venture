package com.budcom.android.feature.masterdata.ledger.presentation

import androidx.lifecycle.SavedStateHandle
import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.core.network.NetworkConnectivityObserver
import com.budcom.android.feature.masterdata.presentation.MasterDataUiError
import com.budcom.android.feature.masterdata.ledger.domain.model.Ledger
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerDataQuality
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerPage
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerQuery
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerStatus
import com.budcom.android.feature.masterdata.ledger.domain.repository.LedgerRepository
import com.budcom.android.feature.masterdata.ledger.domain.usecase.LoadLedgersUseCase
import com.budcom.android.feature.masterdata.ledger.domain.usecase.RefreshLedgersUseCase
import com.budcom.android.navigation.Routes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import app.cash.turbine.test

@OptIn(ExperimentalCoroutinesApi::class)
class LedgerBrowserViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeLedgerRepository
    private lateinit var connectivity: FakeConnectivity

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeLedgerRepository()
        connectivity = FakeConnectivity(true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createVm(query: String = "") = LedgerBrowserViewModel(
        savedStateHandle = SavedStateHandle(mapOf(Routes.QUERY_ARG to query)),
        loadLedgers = LoadLedgersUseCase(repository),
        refreshLedgers = RefreshLedgersUseCase(repository),
        connectivityObserver = connectivity,
    )

    @Test
    fun `loads ledgers on start`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isInitialLoading)
        assertEquals(1, vm.uiState.value.ledgers.size)
        assertEquals("Cash", vm.uiState.value.ledgers[0].primaryLabel)
    }

    @Test
    fun `empty result`() = runTest(dispatcher) {
        repository.result = AppResult.Success(
            LedgerPage(emptyList(), 1, 50, 0, 0, null),
        )
        val vm = createVm()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.ledgers.isEmpty())
        assertEquals(null, vm.uiState.value.error)
    }

    @Test
    fun `offline error without content`() = runTest(dispatcher) {
        repository.result = AppResult.Failure(AppError.Offline())
        val vm = createVm()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.error is MasterDataUiError.Offline)
    }

    @Test
    fun `refresh retains content on failure`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.ledgers.size)
        repository.result = AppResult.Failure(AppError.Timeout())
        vm.onEvent(LedgerBrowserEvent.Refresh)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.ledgers.size)
        assertTrue(vm.uiState.value.error is MasterDataUiError.Timeout)
    }

    @Test
    fun `search passes query to repository`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        vm.onEvent(LedgerBrowserEvent.SearchChanged("cash"))
        advanceUntilIdle()
        assertEquals("cash", repository.lastQuery?.text)
    }

    @Test
    fun `tapping a resolvable ledger row navigates to its statement instead of a dead tap`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        assertEquals(null, vm.uiState.value.selectedLedgerNotice)
        vm.effects.test {
            vm.onEvent(LedgerBrowserEvent.LedgerTapped("guid:cash"))
            assertEquals(LedgerBrowserEffect.OpenLedgerStatement("guid:cash"), awaitItem())
        }
        assertEquals(null, vm.uiState.value.selectedLedgerNotice)
    }

    @Test
    fun `tapping a ledger with a missing identifier does not fail silently`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        vm.onEvent(LedgerBrowserEvent.LedgerTapped(""))
        assertTrue(vm.uiState.value.selectedLedgerNotice?.contains("identifier") == true)
    }

    @Test
    fun `tapping a ledger no longer in the list does not fail silently`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        vm.onEvent(LedgerBrowserEvent.LedgerTapped("guid:stale"))
        assertTrue(vm.uiState.value.selectedLedgerNotice?.contains("no longer") == true)
    }

    @Test
    fun `dismissing the ledger notice clears it`() = runTest(dispatcher) {
        val vm = createVm()
        advanceUntilIdle()
        vm.onEvent(LedgerBrowserEvent.LedgerTapped(""))
        assertTrue(vm.uiState.value.selectedLedgerNotice != null)
        vm.onEvent(LedgerBrowserEvent.DismissLedgerNotice)
        assertEquals(null, vm.uiState.value.selectedLedgerNotice)
    }

    @Test
    fun `load next page appends`() = runTest(dispatcher) {
        repository.result = AppResult.Success(
            LedgerPage(
                items = listOf(sampleLedger("1", "A")),
                page = 1,
                pageSize = 1,
                totalItems = 2,
                totalPages = 2,
                dataFreshnessAt = null,
            ),
        )
        val vm = createVm()
        advanceUntilIdle()
        repository.result = AppResult.Success(
            LedgerPage(
                items = listOf(sampleLedger("2", "B")),
                page = 2,
                pageSize = 1,
                totalItems = 2,
                totalPages = 2,
                dataFreshnessAt = null,
            ),
        )
        vm.onEvent(LedgerBrowserEvent.LoadNextPage)
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.ledgers.size)
        assertFalse(vm.uiState.value.canLoadMore)
    }
}

private class FakeLedgerRepository : LedgerRepository {
    var result: AppResult<LedgerPage> = AppResult.Success(
        LedgerPage(
            items = listOf(sampleLedger("guid:cash", "Cash")),
            page = 1,
            pageSize = 50,
            totalItems = 1,
            totalPages = 1,
            dataFreshnessAt = "t",
        ),
    )
    var lastQuery: LedgerQuery? = null

    override suspend fun loadLedgers(query: LedgerQuery): AppResult<LedgerPage> {
        lastQuery = query
        return result
    }
}

private class FakeConnectivity(online: Boolean) : NetworkConnectivityObserver {
    private val flow = MutableStateFlow(online)
    override val isOnline: Flow<Boolean> = flow
    override fun current(): Boolean = flow.value
}

private fun sampleLedger(id: String, name: String) = Ledger(
    id = id,
    name = name,
    alias = null,
    parentGroup = "Cash-in-Hand",
    status = LedgerStatus.Active,
    closingBalance = null,
    dataQuality = LedgerDataQuality.Complete,
    syncedAt = "t",
)
