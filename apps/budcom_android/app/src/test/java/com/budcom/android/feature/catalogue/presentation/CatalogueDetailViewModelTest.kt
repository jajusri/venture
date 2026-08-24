package com.budcom.android.feature.catalogue.presentation

import androidx.lifecycle.SavedStateHandle
import com.budcom.android.feature.catalogue.domain.model.CatalogueLifecycleAction
import com.budcom.android.feature.catalogue.domain.model.CatalogueLifecycleState
import com.budcom.android.feature.catalogue.domain.model.PriceDisplayMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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

    // ============================== Photos ==============================

    @Test
    fun `loading a product also loads its existing photos`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        repository.products["co-1"] = mutableListOf(sampleProduct(productId = "p1"))
        repository.addAsset("co-1", "p1", android.net.TestUri.create(), com.budcom.android.feature.catalogue.domain.model.CatalogueTimestamp(1L, com.budcom.android.feature.catalogue.domain.model.CatalogueTimestampSource.DeviceLocalProvisional))

        val vm = viewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, vm.uiState.value.assets.size)
        assertTrue(vm.uiState.value.assets.single().isPrimary)
    }

    @Test
    fun `PhotoSelected adds a new photo and it becomes primary if it is the first`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        repository.products["co-1"] = mutableListOf(sampleProduct(productId = "p1"))
        val vm = viewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(CatalogueDetailEvent.PhotoSelected(android.net.TestUri.create()))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, vm.uiState.value.assets.size)
        assertTrue(vm.uiState.value.assets.single().isPrimary)
        assertEquals("Photo added", vm.uiState.value.message)
    }

    @Test
    fun `a second photo does not become primary until explicitly set`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        repository.products["co-1"] = mutableListOf(sampleProduct(productId = "p1"))
        val vm = viewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(CatalogueDetailEvent.PhotoSelected(android.net.TestUri.create()))
        dispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(CatalogueDetailEvent.PhotoSelected(android.net.TestUri.create()))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, vm.uiState.value.assets.count { it.isPrimary })
    }

    @Test
    fun `SetPrimaryAsset promotes the chosen photo and demotes the rest`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        repository.products["co-1"] = mutableListOf(sampleProduct(productId = "p1"))
        val vm = viewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(CatalogueDetailEvent.PhotoSelected(android.net.TestUri.create()))
        dispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(CatalogueDetailEvent.PhotoSelected(android.net.TestUri.create()))
        dispatcher.scheduler.advanceUntilIdle()
        val second = vm.uiState.value.assets.last().assetId

        vm.onEvent(CatalogueDetailEvent.SetPrimaryAsset(second))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(second, vm.uiState.value.assets.single { it.isPrimary }.assetId)
    }

    @Test
    fun `DeleteAsset removes the photo and surfaces a confirmation message`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        repository.products["co-1"] = mutableListOf(sampleProduct(productId = "p1"))
        val vm = viewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(CatalogueDetailEvent.PhotoSelected(android.net.TestUri.create()))
        dispatcher.scheduler.advanceUntilIdle()
        val assetId = vm.uiState.value.assets.single().assetId

        vm.onEvent(CatalogueDetailEvent.DeleteAsset(assetId))
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.assets.isEmpty())
        assertEquals("Photo removed", vm.uiState.value.message)
    }

    @Test
    fun `a rejected photo surfaces a specific, honest failure message`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        repository.products["co-1"] = mutableListOf(sampleProduct(productId = "p1"))
        repository.addAssetFailure = com.budcom.android.feature.catalogue.storage.CatalogueAssetFailureReason.FileTooLarge
        val vm = viewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(CatalogueDetailEvent.PhotoSelected(android.net.TestUri.create()))
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.assets.isEmpty())
        assertEquals("That photo is too large", vm.uiState.value.message)
    }

    @Test
    fun `TakePhoto and PickPhotoFromGallery emit the correct one-shot effects`() = runTest(dispatcher) {
        val repository = FakeCatalogueRepository()
        repository.products["co-1"] = mutableListOf(sampleProduct(productId = "p1"))
        val vm = viewModel(repository)
        dispatcher.scheduler.advanceUntilIdle()

        val effects = mutableListOf<CatalogueDetailEffect>()
        val job = launch(Dispatchers.Unconfined) { vm.effects.collect { effects.add(it) } }
        vm.onEvent(CatalogueDetailEvent.TakePhoto)
        vm.onEvent(CatalogueDetailEvent.PickPhotoFromGallery)
        dispatcher.scheduler.advanceUntilIdle()
        job.cancel()

        assertEquals(listOf(CatalogueDetailEffect.RequestCameraCapture, CatalogueDetailEffect.RequestGalleryPick), effects)
    }
}
