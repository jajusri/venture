package com.budcom.android.feature.transaction.presentation

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.budcom.android.core.common.AppResult
import com.budcom.android.feature.catalogue.domain.model.Branch
import com.budcom.android.feature.catalogue.domain.model.CatalogueAsset
import com.budcom.android.feature.catalogue.domain.model.CatalogueLifecycleAction
import com.budcom.android.feature.catalogue.domain.model.CatalogueLifecycleState
import com.budcom.android.feature.catalogue.domain.model.CatalogueOverrideAttribute
import com.budcom.android.feature.catalogue.domain.model.CatalogueOverrideRow
import com.budcom.android.feature.catalogue.domain.model.CatalogueOverrideScope
import com.budcom.android.feature.catalogue.domain.model.CatalogueProduct
import com.budcom.android.feature.catalogue.domain.model.CataloguePublishedSnapshot
import com.budcom.android.feature.catalogue.domain.model.CatalogueTimestamp
import com.budcom.android.feature.catalogue.domain.model.PriceDisplayMode
import com.budcom.android.feature.catalogue.domain.repository.CatalogueEnrichmentUpdate
import com.budcom.android.feature.catalogue.domain.repository.CatalogueLifecycleResult
import com.budcom.android.feature.catalogue.domain.repository.CatalogueReconciliationResult
import com.budcom.android.feature.catalogue.domain.repository.CatalogueRepository
import com.budcom.android.feature.catalogue.storage.CatalogueAssetResult
import com.budcom.android.feature.masterdata.stockitem.domain.model.StockItem
import com.budcom.android.feature.company.domain.port.CompanySessionPort
import com.budcom.android.feature.company.domain.port.SelectedCompanyStatus
import com.budcom.android.feature.company.domain.port.SessionValidationStatus
import com.budcom.android.feature.transaction.domain.model.CatalogueAccessGrant
import com.budcom.android.feature.transaction.domain.model.CommercialTransaction
import com.budcom.android.feature.transaction.domain.model.EstimatePo
import com.budcom.android.feature.transaction.domain.model.EstimatePoStatus
import com.budcom.android.feature.transaction.domain.model.LedgerGroupChoice
import com.budcom.android.feature.transaction.domain.model.PaymentClaimStatus
import com.budcom.android.feature.transaction.domain.model.PaymentEvent
import com.budcom.android.feature.transaction.domain.model.SellerInboxEntry
import com.budcom.android.feature.transaction.domain.model.TermsAcknowledgment
import com.budcom.android.feature.transaction.domain.model.TransactionClock
import com.budcom.android.feature.transaction.domain.model.TransactionDeliveryChannel
import com.budcom.android.feature.transaction.domain.model.TransactionDraftPriceState
import com.budcom.android.feature.transaction.domain.model.TransactionEntryPointType
import com.budcom.android.feature.transaction.domain.model.TransactionLineItem
import com.budcom.android.feature.transaction.domain.model.TransactionSubmissionType
import com.budcom.android.feature.transaction.domain.model.TransactionTimestamp
import com.budcom.android.feature.transaction.domain.model.TransactionTimestampSource
import com.budcom.android.feature.transaction.domain.repository.AcceptSellerInboxEntryResult
import com.budcom.android.feature.transaction.domain.repository.NewLineItem
import com.budcom.android.feature.transaction.domain.repository.ProposedTerms
import com.budcom.android.feature.transaction.domain.repository.SellerInboxActionResult
import com.budcom.android.feature.transaction.domain.repository.TransactionRepository
import com.budcom.android.feature.transaction.domain.repository.TransactionSnapshot
import com.budcom.android.feature.transaction.sharing.PreparedTransactionShare
import com.budcom.android.feature.transaction.sharing.TransactionShareCoordinator
import com.budcom.android.feature.transaction.sharing.TransactionSharePriceVisibility
import com.budcom.android.feature.transaction.sharing.TransactionShareResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionComposerViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun viewModel(
        repository: FakeTransactionRepository = FakeTransactionRepository(),
        catalogueRepository: FakeCatalogueRepository = FakeCatalogueRepository(),
        shareCoordinator: FakeTransactionShareCoordinator = FakeTransactionShareCoordinator(),
        buyerPartyId: String? = "buyer-1",
        companyId: String? = "co-1",
    ) = TransactionComposerViewModel(
        SavedStateHandle(buildMap { buyerPartyId?.let { put(TransactionComposerViewModel.BUYER_PARTY_ID_ARG, it) } }),
        repository, catalogueRepository, shareCoordinator, FakeCompanySessionPort(companyId), FakeTransactionClock(),
    )

    private fun line(estimatePoId: String, productId: String, name: String, quantity: String) = TransactionLineItem(
        estimatePoId = estimatePoId, lineItemId = "$estimatePoId-l", linkedProductId = productId,
        snapshotProductName = name, snapshotUnit = "Nos", snapshotSku = null, quantity = quantity,
        unitPriceAmount = "10", unitPriceCurrencyCode = "INR", lineTotalAmount = "10", isContactForPrice = false,
    )

    // ============================== init / loading ==============================

    @Test
    fun `no selected company surfaces a not-loading state rather than crashing`() = runTest(dispatcher) {
        val vm = viewModel(companyId = null)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(!vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.draft)
    }

    @Test
    fun `on load, an empty Estimate draft is seeded for the resolved company and buyer`() = runTest(dispatcher) {
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        val draft = vm.uiState.value.draft!!
        assertEquals("co-1", draft.companyId)
        assertEquals("buyer-1", draft.buyerPartyId)
        assertTrue(draft.isEmpty)
        assertTrue(!vm.uiState.value.isLoading)
    }

    @Test
    fun `Buy Again list is populated from completed purchase history, deduplicated`() = runTest(dispatcher) {
        val repository = FakeTransactionRepository()
        repository.completedHistory["co-1|buyer-1"] = listOf(line("e1", "p1", "Widget", "2"), line("e2", "p1", "Widget", "5"))
        val vm = viewModel(repository = repository)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, vm.uiState.value.buyAgainEntries.size)
        assertEquals("5", vm.uiState.value.buyAgainEntries.single().mostRecentQuantity)
    }

    // ============================== inline selection editing ==============================

    @Test
    fun `adding a product places it in the draft immediately - no navigation away`() = runTest(dispatcher) {
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(TransactionComposerEvent.AddOrIncrementProduct("p1", "Widget", "Nos", "SKU-1", TransactionDraftPriceState.ActualPrice("100", "INR")))
        assertEquals(1, vm.uiState.value.draft!!.lines.size)
    }

    @Test
    fun `adding the same product twice increments quantity rather than duplicating`() = runTest(dispatcher) {
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(TransactionComposerEvent.AddOrIncrementProduct("p1", "Widget", "Nos", "SKU-1", TransactionDraftPriceState.ActualPrice("100", "INR")))
        vm.onEvent(TransactionComposerEvent.AddOrIncrementProduct("p1", "Widget", "Nos", "SKU-1", TransactionDraftPriceState.ActualPrice("100", "INR")))
        assertEquals(1, vm.uiState.value.draft!!.lines.size)
        assertEquals("2", vm.uiState.value.draft!!.lines.single().quantity)
    }

    @Test
    fun `selecting a Buy Again item immediately populates it into the selected-products area`() = runTest(dispatcher) {
        val repository = FakeTransactionRepository()
        repository.completedHistory["co-1|buyer-1"] = listOf(line("e1", "p1", "Widget", "3"))
        val vm = viewModel(repository = repository)
        dispatcher.scheduler.advanceUntilIdle()

        val entry = vm.uiState.value.buyAgainEntries.single()
        vm.onEvent(TransactionComposerEvent.SelectBuyAgainItem(entry, TransactionDraftPriceState.ActualPrice("10", "INR")))
        assertEquals(1, vm.uiState.value.draft!!.lines.size)
        assertEquals("Widget", vm.uiState.value.draft!!.lines.single().snapshotProductName)
    }

    @Test
    fun `quantity can be increased, decreased, and a product removed`() = runTest(dispatcher) {
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(TransactionComposerEvent.AddOrIncrementProduct("p1", "Widget", "Nos", "SKU-1", TransactionDraftPriceState.ActualPrice("100", "INR")))
        vm.onEvent(TransactionComposerEvent.SetQuantity("p1", "9"))
        assertEquals("9", vm.uiState.value.draft!!.lines.single().quantity)
        vm.onEvent(TransactionComposerEvent.SetQuantity("p1", "2"))
        assertEquals("2", vm.uiState.value.draft!!.lines.single().quantity)
        vm.onEvent(TransactionComposerEvent.RemoveProduct("p1"))
        assertTrue(vm.uiState.value.draft!!.isEmpty)
    }

    // ============================== submission / WhatsApp ==============================

    @Test
    fun `sharing an empty draft surfaces a message and never calls the share coordinator`() = runTest(dispatcher) {
        val shareCoordinator = FakeTransactionShareCoordinator()
        val vm = viewModel(shareCoordinator = shareCoordinator)
        dispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(TransactionComposerEvent.ShareViaWhatsApp)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.message != null)
        assertEquals(0, shareCoordinator.prepareCallCount)
    }

    @Test
    fun `sharing a valid draft creates the EstimatePo, prepares a share, and resets the draft`() = runTest(dispatcher) {
        val repository = FakeTransactionRepository()
        val shareCoordinator = FakeTransactionShareCoordinator()
        val vm = viewModel(repository = repository, shareCoordinator = shareCoordinator)
        dispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(TransactionComposerEvent.AddOrIncrementProduct("p1", "Widget", "Nos", "SKU-1", TransactionDraftPriceState.ActualPrice("100", "INR")))

        vm.onEvent(TransactionComposerEvent.ShareViaWhatsApp)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repository.createdEstimatePos.size)
        assertEquals(TransactionDeliveryChannel.WhatsAppShared, repository.createdEstimatePos.single().deliveryChannel)
        assertEquals(1, shareCoordinator.prepareCallCount)
        assertTrue("draft must reset to empty after a successful share", vm.uiState.value.draft!!.isEmpty)
    }

    @Test
    fun `a draft with a Hidden-price line is refused, never reaches createEstimatePo or the share coordinator`() = runTest(dispatcher) {
        val repository = FakeTransactionRepository()
        val shareCoordinator = FakeTransactionShareCoordinator()
        val vm = viewModel(repository = repository, shareCoordinator = shareCoordinator)
        dispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(TransactionComposerEvent.AddOrIncrementProduct("p1", "Widget", "Nos", "SKU-1", TransactionDraftPriceState.Hidden))

        vm.onEvent(TransactionComposerEvent.ShareViaWhatsApp)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.message != null)
        assertEquals(0, repository.createdEstimatePos.size)
        assertEquals(0, shareCoordinator.prepareCallCount)
    }

    @Test
    fun `SubmitInApp uses the IN_APP_SUBMITTED channel and never touches the share coordinator`() = runTest(dispatcher) {
        val repository = FakeTransactionRepository()
        val shareCoordinator = FakeTransactionShareCoordinator()
        val vm = viewModel(repository = repository, shareCoordinator = shareCoordinator)
        dispatcher.scheduler.advanceUntilIdle()
        vm.onEvent(TransactionComposerEvent.AddOrIncrementProduct("p1", "Widget", "Nos", "SKU-1", TransactionDraftPriceState.ActualPrice("100", "INR")))

        vm.onEvent(TransactionComposerEvent.SubmitInApp)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(TransactionDeliveryChannel.InAppSubmitted, repository.createdEstimatePos.single().deliveryChannel)
        assertEquals(0, shareCoordinator.prepareCallCount)
    }
}

// ============================== Phase A: real Catalogue data ==============================

class TransactionComposerRealCatalogueTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun snapshot(
        productId: String, displayName: String,
        priceDisplayMode: PriceDisplayMode = PriceDisplayMode.Open, amount: String? = "100",
    ) = CataloguePublishedSnapshot(
        companyId = "co-1", productId = productId, displayName = displayName, description = null,
        specifications = null, customerFacingCategory = null, priceDisplayMode = priceDisplayMode,
        resolvedPriceAmount = amount, resolvedPriceCurrencyCode = if (amount != null) "INR" else null,
        primaryAssetId = null, publishedAt = CatalogueTimestamp(1_000L, com.budcom.android.feature.catalogue.domain.model.CatalogueTimestampSource.DeviceLocalProvisional),
    )

    @Test
    fun `real published Catalogue products reach the composer as New SKUs`() = runTest(dispatcher) {
        val catalogueRepository = FakeCatalogueRepository()
        catalogueRepository.published["co-1"] = listOf(snapshot("p1", "Real Widget"))
        val vm = TransactionComposerViewModel(
            SavedStateHandle(mapOf(TransactionComposerViewModel.BUYER_PARTY_ID_ARG to "buyer-1")),
            FakeTransactionRepository(), catalogueRepository, FakeTransactionShareCoordinator(),
            FakeCompanySessionPort("co-1"), FakeTransactionClock(),
        )
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, vm.uiState.value.newSkus.size)
        assertEquals("Real Widget", vm.uiState.value.newSkus.single().displayName)
    }

    @Test
    fun `an Open-priced New SKU shows its actual price, never Contact-for-price`() = runTest(dispatcher) {
        val catalogueRepository = FakeCatalogueRepository()
        catalogueRepository.published["co-1"] = listOf(snapshot("p1", "Widget", PriceDisplayMode.Open, "250"))
        val vm = TransactionComposerViewModel(
            SavedStateHandle(mapOf(TransactionComposerViewModel.BUYER_PARTY_ID_ARG to "buyer-1")),
            FakeTransactionRepository(), catalogueRepository, FakeTransactionShareCoordinator(),
            FakeCompanySessionPort("co-1"), FakeTransactionClock(),
        )
        dispatcher.scheduler.advanceUntilIdle()
        val priceState = vm.uiState.value.newSkus.single().priceState as TransactionDraftPriceState.ActualPrice
        assertEquals("250", priceState.unitAmount)
    }

    @Test
    fun `an Open-priced product with no amount yet is No price supplied, never Contact-for-price`() = runTest(dispatcher) {
        val catalogueRepository = FakeCatalogueRepository()
        catalogueRepository.published["co-1"] = listOf(snapshot("p1", "Widget", PriceDisplayMode.Open, amount = null))
        val vm = TransactionComposerViewModel(
            SavedStateHandle(mapOf(TransactionComposerViewModel.BUYER_PARTY_ID_ARG to "buyer-1")),
            FakeTransactionRepository(), catalogueRepository, FakeTransactionShareCoordinator(),
            FakeCompanySessionPort("co-1"), FakeTransactionClock(),
        )
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(TransactionDraftPriceState.NoPriceSupplied, vm.uiState.value.newSkus.single().priceState)
    }

    @Test
    fun `a Contact-for-price product is rendered as Contact for price`() = runTest(dispatcher) {
        val catalogueRepository = FakeCatalogueRepository()
        catalogueRepository.published["co-1"] = listOf(snapshot("p1", "Widget", PriceDisplayMode.ContactForPrice, amount = null))
        val vm = TransactionComposerViewModel(
            SavedStateHandle(mapOf(TransactionComposerViewModel.BUYER_PARTY_ID_ARG to "buyer-1")),
            FakeTransactionRepository(), catalogueRepository, FakeTransactionShareCoordinator(),
            FakeCompanySessionPort("co-1"), FakeTransactionClock(),
        )
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(TransactionDraftPriceState.ContactForPrice, vm.uiState.value.newSkus.single().priceState)
    }

    @Test
    fun `selecting a real New SKU adds it to the draft's selected products immediately`() = runTest(dispatcher) {
        val catalogueRepository = FakeCatalogueRepository()
        catalogueRepository.published["co-1"] = listOf(snapshot("p1", "Real Widget"))
        val vm = TransactionComposerViewModel(
            SavedStateHandle(mapOf(TransactionComposerViewModel.BUYER_PARTY_ID_ARG to "buyer-1")),
            FakeTransactionRepository(), catalogueRepository, FakeTransactionShareCoordinator(),
            FakeCompanySessionPort("co-1"), FakeTransactionClock(),
        )
        dispatcher.scheduler.advanceUntilIdle()
        val row = vm.uiState.value.newSkus.single()
        vm.onEvent(TransactionComposerEvent.AddOrIncrementProduct(row.linkedProductId, row.displayName, row.unit, row.sku, row.priceState))
        assertEquals(1, vm.uiState.value.draft!!.lines.size)
        assertEquals("Real Widget", vm.uiState.value.draft!!.lines.single().snapshotProductName)
    }
}

// ============================== fakes ==============================

/** Only [listAllPublished] is meaningfully implemented — the only method
 * [TransactionComposerViewModel] actually calls; every other member of this large, unrelated
 * interface is intentionally unsupported here, matching every other large-interface fake this
 * session already established the same discipline for. */
private class FakeCatalogueRepository : CatalogueRepository {
    val published = mutableMapOf<String, List<CataloguePublishedSnapshot>>()
    override suspend fun listAllPublished(companyId: String): List<CataloguePublishedSnapshot> = published[companyId].orEmpty()

    private fun unsupported(): Nothing = throw UnsupportedOperationException("not used by TransactionComposerViewModelTest")
    override suspend fun createDraftFromStockItem(companyId: String, stockItemId: String, timestamp: CatalogueTimestamp): CatalogueProduct? = unsupported()
    override suspend fun createManualDraft(companyId: String, displayName: String, timestamp: CatalogueTimestamp): CatalogueProduct = unsupported()
    override suspend fun createDraftsFromStockItems(companyId: String, stockItemIds: List<String>, timestamp: CatalogueTimestamp, onProgress: suspend (linked: Int, total: Int) -> Unit): Int = unsupported()
    override suspend fun warmStockItemCache(companyId: String): Unit = unsupported()
    override suspend fun listUnlinkedStockItems(companyId: String): List<StockItem> = unsupported()
    override suspend fun findProduct(companyId: String, productId: String): CatalogueProduct? = unsupported()
    override suspend fun listProducts(companyId: String): List<CatalogueProduct> = unsupported()
    override suspend fun listProductsByState(companyId: String, state: CatalogueLifecycleState): List<CatalogueProduct> = unsupported()
    override suspend fun updateEnrichment(companyId: String, productId: String, update: CatalogueEnrichmentUpdate, timestamp: CatalogueTimestamp): CatalogueProduct? = unsupported()
    override suspend fun transitionLifecycle(companyId: String, productId: String, action: CatalogueLifecycleAction, isOwner: Boolean, timestamp: CatalogueTimestamp): CatalogueLifecycleResult = unsupported()
    override suspend fun reconcileStockItemLinks(companyId: String, timestamp: CatalogueTimestamp): CatalogueReconciliationResult = unsupported()
    override suspend fun upsertBranch(companyId: String, branchId: String, name: String, isActive: Boolean, timestamp: CatalogueTimestamp): Branch = unsupported()
    override suspend fun listBranches(companyId: String): List<Branch> = unsupported()
    override suspend fun setOverride(companyId: String, scope: CatalogueOverrideScope, attribute: CatalogueOverrideAttribute, value: String, timestamp: CatalogueTimestamp): Unit = unsupported()
    override suspend fun clearOverride(companyId: String, scope: CatalogueOverrideScope, attribute: CatalogueOverrideAttribute): Unit = unsupported()
    override suspend fun resolveOverride(companyId: String, productId: String, branchId: String?, attribute: CatalogueOverrideAttribute): CatalogueOverrideRow? = unsupported()
    override suspend fun upsertCustomFields(companyId: String, productId: String, values: Map<String, String?>, timestamp: CatalogueTimestamp): Unit = unsupported()
    override suspend fun listCustomFields(companyId: String, productId: String): Map<String, String?> = unsupported()
    override suspend fun listAllCustomFieldColumnNames(companyId: String): List<String> = unsupported()
    override suspend fun listPublishedForCategory(companyId: String, category: String): List<CataloguePublishedSnapshot> = unsupported()
    override suspend fun isPublic(companyId: String): Boolean = unsupported()
    override suspend fun setPublic(companyId: String, isPublic: Boolean, timestamp: CatalogueTimestamp): Unit = unsupported()
    override suspend fun addAsset(companyId: String, productId: String, sourceUri: Uri, timestamp: CatalogueTimestamp): CatalogueAssetResult = unsupported()
    override suspend fun listAssets(companyId: String, productId: String): List<CatalogueAsset> = unsupported()
    override suspend fun setPrimaryAsset(companyId: String, productId: String, assetId: String, timestamp: CatalogueTimestamp): Unit = unsupported()
    override suspend fun deleteAsset(companyId: String, productId: String, assetId: String): Unit = unsupported()
    override fun resolveAssetFile(companyId: String, productId: String, filePath: String): java.io.File? = unsupported()
}

private class FakeTransactionClock : TransactionClock {
    override suspend fun now() = TransactionTimestamp(1_000L, TransactionTimestampSource.DeviceLocalProvisional)
}

private class FakeCompanySessionPort(initial: String?) : CompanySessionPort {
    private val state = MutableStateFlow(initial)
    override fun observeSelectedCompanyId() = state
    override suspend fun readSelectedCompany(): AppResult<SelectedCompanyStatus> = AppResult.Success(SelectedCompanyStatus(state.value, state.value))
    override suspend fun validateSessionStatus(): AppResult<SessionValidationStatus> = error("not used in these tests")
}

private class FakeTransactionShareCoordinator : TransactionShareCoordinator {
    var prepareCallCount = 0
    override suspend fun prepareShare(
        companyId: String, estimatePo: EstimatePo, priceVisibility: TransactionSharePriceVisibility,
        businessName: String?, buyerDisplayName: String?, terms: TermsAcknowledgment?,
    ): TransactionShareResult<PreparedTransactionShare> {
        prepareCallCount++
        return TransactionShareResult.Success(PreparedTransactionShare("uri", "path", "file.txt"))
    }

    override fun createShareIntent(prepared: PreparedTransactionShare): TransactionShareResult<Intent> =
        TransactionShareResult.Success(Intent(Intent.ACTION_SEND))

    override fun releaseShare(prepared: PreparedTransactionShare) = Unit
}

/** Only [createEstimatePo]/[findCompletedPurchaseHistory] are meaningfully implemented — the only
 * two this ViewModel actually calls; every other member of this large interface is intentionally
 * unsupported here, mirroring the same scoping discipline
 * [com.budcom.android.feature.transaction.data.repository.TransactionRepositoryImplTest]'s own
 * `FakePartyRepository` already established for an unrelated large interface. */
private class FakeTransactionRepository : TransactionRepository {
    val createdEstimatePos = mutableListOf<EstimatePo>()
    val completedHistory = mutableMapOf<String, List<TransactionLineItem>>()

    override suspend fun createEstimatePo(
        companyId: String, entryPointType: TransactionEntryPointType, submissionType: TransactionSubmissionType,
        deliveryChannel: TransactionDeliveryChannel, buyerPartyId: String?, lineItems: List<NewLineItem>,
        timestamp: TransactionTimestamp,
    ): EstimatePo {
        val estimatePo = EstimatePo(
            companyId = companyId, estimatePoId = "e-${createdEstimatePos.size + 1}", entryPointType = entryPointType,
            submissionType = submissionType, deliveryChannel = deliveryChannel, buyerPartyId = buyerPartyId,
            totalAmount = "0", currencyCode = null,
            status = if (deliveryChannel == TransactionDeliveryChannel.WhatsAppShared) EstimatePoStatus.Shared else EstimatePoStatus.Submitted,
            submittedAt = timestamp, lineItems = emptyList(),
        )
        createdEstimatePos += estimatePo
        return estimatePo
    }

    override suspend fun findCompletedPurchaseHistory(companyId: String, buyerPartyId: String): List<TransactionLineItem> =
        completedHistory["$companyId|$buyerPartyId"].orEmpty()

    private fun unsupported(): Nothing = throw UnsupportedOperationException("not used by TransactionComposerViewModelTest")
    override suspend fun findEstimatePoById(companyId: String, estimatePoId: String): EstimatePo? = unsupported()
    override suspend fun findSellerInboxEntry(companyId: String, inboxEntryId: String): SellerInboxEntry? = unsupported()
    override suspend fun findAllSellerInboxEntries(companyId: String): List<SellerInboxEntry> = unsupported()
    override suspend fun acknowledgeSellerInboxEntry(companyId: String, inboxEntryId: String, timestamp: TransactionTimestamp): SellerInboxActionResult = unsupported()
    override suspend fun requestChangesOnSellerInboxEntry(companyId: String, inboxEntryId: String, note: String, timestamp: TransactionTimestamp): SellerInboxActionResult = unsupported()
    override suspend fun acceptSellerInboxEntry(companyId: String, inboxEntryId: String, ledgerGroupChoice: LedgerGroupChoice, proposedTerms: ProposedTerms, timestamp: TransactionTimestamp): AcceptSellerInboxEntryResult = unsupported()
    override suspend fun findTransactionById(companyId: String, transactionId: String): CommercialTransaction? = unsupported()
    override suspend fun findTermsForTransaction(companyId: String, transactionId: String): TermsAcknowledgment? = unsupported()
    override suspend fun confirmTermsAsBuyer(companyId: String, transactionId: String, timestamp: TransactionTimestamp): TermsAcknowledgment? = unsupported()
    override suspend fun confirmTermsAsSeller(companyId: String, transactionId: String, timestamp: TransactionTimestamp): TermsAcknowledgment? = unsupported()
    override suspend fun findPaymentEventsForTransaction(companyId: String, transactionId: String): List<PaymentEvent> = unsupported()
    override suspend fun recordPaymentClaim(companyId: String, transactionId: String, claimedAmount: String, currencyCode: String?, isFinalOrPartial: PaymentClaimStatus, timestamp: TransactionTimestamp): PaymentEvent? = unsupported()
    override suspend fun confirmPaymentReceived(companyId: String, transactionId: String, paymentEventId: String, timestamp: TransactionTimestamp, discrepancyNote: String?): CommercialTransaction? = unsupported()
    override suspend fun findTransactionsForCounterparty(companyId: String, buyerPartyId: String): List<CommercialTransaction> = unsupported()
    override suspend fun findAllTransactionsForCompany(companyId: String): List<CommercialTransaction> = unsupported()
    override suspend fun findTransactionSnapshot(companyId: String, transactionId: String): TransactionSnapshot? = unsupported()
    override suspend fun grantCatalogueAccess(companyId: String, buyerPartyId: String, expiresAt: TransactionTimestamp?, timestamp: TransactionTimestamp): CatalogueAccessGrant = unsupported()
    override suspend fun revokeCatalogueAccess(companyId: String, grantId: String, timestamp: TransactionTimestamp): Unit = unsupported()
    override suspend fun findActiveCatalogueAccessGrant(companyId: String, buyerPartyId: String, nowEpochMillis: Long): CatalogueAccessGrant? = unsupported()
}
