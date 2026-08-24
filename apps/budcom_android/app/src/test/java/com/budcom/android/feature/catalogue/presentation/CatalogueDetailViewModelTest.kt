package com.budcom.android.feature.catalogue.presentation

import androidx.lifecycle.SavedStateHandle
import com.budcom.android.feature.catalogue.domain.model.CatalogueLifecycleAction
import com.budcom.android.feature.catalogue.domain.model.CatalogueLifecycleState
import com.budcom.android.feature.catalogue.domain.model.PriceDisplayMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogueDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(repository: FakeCatalogueRepository, productId: String = "p1", companyId: String? = "co-1") =
        CatalogueDetailViewModel(
            SavedStateHandle(mapOf(CatalogueDetailViewModel.PRODUCT_ID_ARG to productId)),
            repository,
            FakeCompanySessionPort(companyId),
            FakeCatalogueClock(),
        )

    @Test
    fun `loads the product and seeds edit drafts from it`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        repository.products["co-1"] = mutableListOf(
            sampleProduct(productId = "p1", displayName = "Widget").copy(description = "A fine widget"),
        )
        val vm = viewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Widget", vm.uiState.value.product?.displayName)
        assertEquals("A fine widget", vm.uiState.value.descriptionDraft)
        assertTrue(!vm.uiState.value.isLoading)
    }

    @Test
    fun `an unknown product id surfaces notFound`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        val vm = viewModel(repository, productId = "missing")
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.notFound)
    }

    @Test
    fun `no selected company surfaces notFound rather than crashing`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        val vm = viewModel(repository, companyId = null)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.notFound)
    }

    @Test
    fun `saving enrichment persists the draft fields and clears dirty state`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        repository.products["co-1"] = mutableListOf(sampleProduct(productId = "p1"))
        val vm = viewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(CatalogueDetailEvent.DescriptionChanged("Hand-crafted"))
        vm.onEvent(CatalogueDetailEvent.PriceDisplayModeChanged(PriceDisplayMode.Open))
        vm.onEvent(CatalogueDetailEvent.ManualPriceChanged("499"))
        assertTrue(vm.uiState.value.isDirty)

        vm.onEvent(CatalogueDetailEvent.SaveEnrichment)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Hand-crafted", repository.products["co-1"]!!.single().description)
        assertEquals("499", repository.products["co-1"]!!.single().manualPriceAmount)
        assertTrue(!vm.uiState.value.isDirty)
        assertEquals("Saved", vm.uiState.value.message)
    }

    @Test
    fun `Publish transitions the product and refreshes the shown state`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        repository.products["co-1"] = mutableListOf(sampleProduct(productId = "p1"))
        val vm = viewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(CatalogueDetailEvent.Transition(CatalogueLifecycleAction.Publish))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(CatalogueLifecycleState.Published, vm.uiState.value.product?.lifecycleState)
        assertTrue(vm.uiState.value.availableActions.contains(CatalogueLifecycleAction.Archive))
    }

    @Test
    fun `a rejected transition surfaces a message and leaves state unchanged`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        repository.products["co-1"] = mutableListOf(sampleProduct(productId = "p1", lifecycleState = CatalogueLifecycleState.Published))
        val vm = viewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(CatalogueDetailEvent.Transition(CatalogueLifecycleAction.SubmitForReview))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(CatalogueLifecycleState.Published, vm.uiState.value.product?.lifecycleState)
        assertEquals("That action isn't allowed right now", vm.uiState.value.message)
    }

    @Test
    fun `editing is disabled once Published, matching canEdit`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        repository.products["co-1"] = mutableListOf(sampleProduct(productId = "p1", lifecycleState = CatalogueLifecycleState.Published))
        val vm = viewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(!vm.uiState.value.canEdit)
    }
}
