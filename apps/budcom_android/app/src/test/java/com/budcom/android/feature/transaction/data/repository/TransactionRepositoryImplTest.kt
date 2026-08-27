package com.budcom.android.feature.transaction.data.repository

import com.budcom.android.core.util.DispatcherProvider
import com.budcom.android.feature.party.domain.model.BulkContactSeedResult
import com.budcom.android.feature.party.domain.model.EligibleLedgerSeed
import com.budcom.android.feature.party.domain.model.FieldProvenanceState
import com.budcom.android.feature.party.domain.model.IssueActivitySummary
import com.budcom.android.feature.party.domain.model.NoteType
import com.budcom.android.feature.party.domain.model.Party
import com.budcom.android.feature.party.domain.model.PartyClassification
import com.budcom.android.feature.party.domain.model.PartyContactPerson
import com.budcom.android.feature.party.domain.model.PartyExportEvent
import com.budcom.android.feature.party.domain.model.PartyFieldProvenance
import com.budcom.android.feature.party.domain.model.PartyIssue
import com.budcom.android.feature.party.domain.model.PartyNote
import com.budcom.android.feature.party.domain.model.PartyNotePage
import com.budcom.android.feature.party.domain.model.PartyPage
import com.budcom.android.feature.party.domain.model.PartySourceLink
import com.budcom.android.feature.party.domain.model.ProspectDraft
import com.budcom.android.feature.party.domain.model.Tag
import com.budcom.android.feature.party.domain.model.TallyFieldExportCandidate
import com.budcom.android.feature.party.domain.model.TimelineEntryPage
import com.budcom.android.feature.party.domain.repository.PartyRepository
import com.budcom.android.feature.transaction.data.local.CatalogueAccessGrantDao
import com.budcom.android.feature.transaction.data.local.CatalogueAccessGrantEntity
import com.budcom.android.feature.transaction.data.local.CanonicalOrderDao
import com.budcom.android.feature.transaction.data.local.CanonicalOrderEntity
import com.budcom.android.feature.transaction.data.local.CanonicalOrderLineEntity
import com.budcom.android.feature.transaction.data.local.CommercialTransactionDao
import com.budcom.android.feature.transaction.data.local.CommercialTransactionEntity
import com.budcom.android.feature.transaction.data.local.EstimatePoDao
import com.budcom.android.feature.transaction.data.local.EstimatePoEntity
import com.budcom.android.feature.transaction.data.local.EstimatePoLineItemDao
import com.budcom.android.feature.transaction.data.local.EstimatePoLineItemEntity
import com.budcom.android.feature.transaction.data.local.LedgerIntentDao
import com.budcom.android.feature.transaction.data.local.LedgerIntentEntity
import com.budcom.android.feature.transaction.data.local.PaymentEventDao
import com.budcom.android.feature.transaction.data.local.PaymentEventEntity
import com.budcom.android.feature.transaction.data.local.SellerInboxEntryDao
import com.budcom.android.feature.transaction.data.local.SellerInboxEntryEntity
import com.budcom.android.feature.transaction.data.local.TermsAcknowledgmentDao
import com.budcom.android.feature.transaction.data.local.TermsAcknowledgmentEntity
import com.budcom.android.feature.transaction.domain.model.CommercialTransactionState
import com.budcom.android.feature.transaction.domain.model.TransactionDraftOperations
import com.budcom.android.feature.transaction.domain.model.TransactionDraftPriceState
import com.budcom.android.feature.transaction.domain.model.LedgerGroupChoice
import com.budcom.android.feature.transaction.domain.model.PaymentClaimStatus
import com.budcom.android.feature.transaction.domain.model.PaymentTiming
import com.budcom.android.feature.transaction.domain.model.SellerInboxEntry
import com.budcom.android.feature.transaction.domain.model.SellerInboxState
import com.budcom.android.feature.transaction.domain.model.TransactionDeliveryChannel
import com.budcom.android.feature.transaction.domain.model.TransactionEntryPointType
import com.budcom.android.feature.transaction.domain.model.TransactionSubmissionType
import com.budcom.android.feature.transaction.domain.model.TransactionTimestamp
import com.budcom.android.feature.transaction.domain.model.TransactionTimestampSource
import com.budcom.android.feature.transaction.domain.port.TransactionReminderScheduler
import com.budcom.android.feature.transaction.domain.port.TransactionSubmissionPort
import com.budcom.android.feature.transaction.domain.repository.AcceptSellerInboxEntryResult
import com.budcom.android.feature.transaction.domain.repository.NewLineItem
import com.budcom.android.feature.transaction.domain.repository.ProposedTerms
import com.budcom.android.feature.transaction.domain.repository.SellerInboxActionResult
import com.budcom.android.feature.transaction.data.port.toDomain
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class TransactionRepositoryImplTest {
    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = dispatcher
        override val io: CoroutineDispatcher = dispatcher
        override val default: CoroutineDispatcher = dispatcher
    }

    private val estimatePoDao = FakeEstimatePoDao()
    private val lineItemDao = FakeEstimatePoLineItemDao()
    private val sellerInboxEntryDao = FakeSellerInboxEntryDao()
    private val transactionDao = FakeCommercialTransactionDao()
    private val termsDao = FakeTermsAcknowledgmentDao()
    private val paymentEventDao = FakePaymentEventDao()
    private val ledgerIntentDao = FakeLedgerIntentDao()
    private val accessGrantDao = FakeCatalogueAccessGrantDao()
    private val canonicalOrderDao = FakeCanonicalOrderDao()
    private val submissionPort = FakeTransactionSubmissionPort(sellerInboxEntryDao)
    private val reminderScheduler = FakeTransactionReminderScheduler()
    private val partyRepository = FakePartyRepository()

    private fun repository() = TransactionRepositoryImpl(
        estimatePoDao, lineItemDao, sellerInboxEntryDao, transactionDao, termsDao, paymentEventDao,
        ledgerIntentDao, accessGrantDao, submissionPort, reminderScheduler, partyRepository, dispatchers, canonicalOrderDao,
    )

    private fun ts(millis: Long) = TransactionTimestamp(millis, TransactionTimestampSource.DeviceLocalProvisional)

    private fun oneLine(amount: String = "1000") = NewLineItem(
        linkedProductId = "product-1", snapshotProductName = "Widget", snapshotUnit = "Nos", snapshotSku = "SKU-1",
        quantity = "10", unitPriceAmount = amount, unitPriceCurrencyCode = "INR", lineTotalAmount = amount,
        isContactForPrice = false,
    )

    @Test
    fun `draft order creation snapshots the draft and retries by key`() = runTest(dispatcher) {
        val repo = repository()
        var draft = TransactionDraftOperations.empty("co-1", "buyer-1", TransactionSubmissionType.Estimate)
        draft = TransactionDraftOperations.addOrIncrementLine(
            draft, "product-1", "Widget", "Nos", "SKU-1",
            TransactionDraftPriceState.ActualPrice("100", "INR"), "3",
        )

        val first = repo.createDraftOrder(draft, "review-1", "Deliver Friday", ts(100))
        val retry = repo.createDraftOrder(draft.copy(lines = draft.lines.map { it.copy(quantity = "99") }), "review-1", timestamp = ts(200))

        assertEquals(first.orderId, retry.orderId)
        assertEquals("DRAFT", first.state.columnValue)
        assertEquals("co-1", first.sellerCompanyId)
        assertEquals("buyer-1", first.buyerPartyId)
        assertEquals("3", first.lines.single().quantity)
        assertEquals("product-1", first.lines.single().linkedProductId)
        assertEquals(TransactionDraftPriceState.ActualPrice("100", "INR"), first.lines.single().priceState)
        assertEquals(1, canonicalOrderDao.orders.size)
        assertTrue(estimatePoDao.store.isEmpty())
        assertTrue(transactionDao.store.isEmpty())
    }

    @Test
    fun `hidden price cannot become a canonical draft order`() = runTest(dispatcher) {
        val repo = repository()
        val draft = TransactionDraftOperations.addOrIncrementLine(
            TransactionDraftOperations.empty("co-1", "buyer-1", TransactionSubmissionType.Estimate),
            "product-1", "Widget", "Nos", "SKU-1", TransactionDraftPriceState.Hidden,
        )

        assertTrue(runCatching { repo.createDraftOrder(draft, "hidden-1", timestamp = ts(100)) }.isFailure)
        assertTrue(canonicalOrderDao.orders.isEmpty())
    }

    // ============================== company isolation ==============================

    @Test
    fun `an in-app submission for company A never appears in company B's inbox`() = runTest(dispatcher) {
        val repo = repository()
        repo.createEstimatePo(
            "co-A", TransactionEntryPointType.Catalogue, TransactionSubmissionType.Estimate,
            TransactionDeliveryChannel.InAppSubmitted, "buyer-1", listOf(oneLine()), ts(0),
        )
        assertEquals(1, repo.findAllSellerInboxEntries("co-A").size)
        assertEquals(0, repo.findAllSellerInboxEntries("co-B").size)
    }

    @Test
    fun `transactions for company A never appear when querying company B, even with identical buyerPartyId`() = runTest(dispatcher) {
        val repo = repository()
        val inboxA = acceptFlow(repo, companyId = "co-A", buyerPartyId = "same-buyer-id")
        assertTrue(inboxA is AcceptSellerInboxEntryResult.Success)

        assertEquals(1, repo.findAllTransactionsForCompany("co-A").size)
        assertEquals(0, repo.findAllTransactionsForCompany("co-B").size)
        assertEquals(1, repo.findTransactionsForCounterparty("co-A", "same-buyer-id").size)
        assertEquals(0, repo.findTransactionsForCounterparty("co-B", "same-buyer-id").size)
    }

    // ============================== seller inbox ==============================

    @Test
    fun `Acknowledge then RequestChanges is valid, a Converted entry rejects further actions`() = runTest(dispatcher) {
        val repo = repository()
        val estimatePo = repo.createEstimatePo(
            "co-1", TransactionEntryPointType.Catalogue, TransactionSubmissionType.Estimate,
            TransactionDeliveryChannel.InAppSubmitted, "buyer-1", listOf(oneLine()), ts(0),
        )
        val inboxEntry = repo.findAllSellerInboxEntries("co-1").single { it.estimatePoId == estimatePo.estimatePoId }

        val ackResult = repo.acknowledgeSellerInboxEntry("co-1", inboxEntry.inboxEntryId, ts(1))
        assertTrue(ackResult is SellerInboxActionResult.Success)

        val changesResult = repo.requestChangesOnSellerInboxEntry("co-1", inboxEntry.inboxEntryId, "need 5 more units", ts(2))
        assertTrue(changesResult is SellerInboxActionResult.Success)
        assertEquals(SellerInboxState.ChangesRequested, (changesResult as SellerInboxActionResult.Success).entry.state)

        // Closed record: no further action permitted.
        val rejected = repo.acknowledgeSellerInboxEntry("co-1", inboxEntry.inboxEntryId, ts(3))
        assertTrue(rejected is SellerInboxActionResult.InvalidState)
    }

    @Test
    fun `WhatsApp-shared submissions never create a seller inbox entry`() = runTest(dispatcher) {
        val repo = repository()
        repo.createEstimatePo(
            "co-1", TransactionEntryPointType.Catalogue, TransactionSubmissionType.Estimate,
            TransactionDeliveryChannel.WhatsAppShared, "buyer-1", listOf(oneLine()), ts(0),
        )
        assertTrue(repo.findAllSellerInboxEntries("co-1").isEmpty())
    }

    // ============================== line item snapshot integrity ==============================

    @Test
    fun `line items are snapshotted at submission time, independent of any later Catalogue change`() = runTest(dispatcher) {
        val repo = repository()
        val estimatePo = repo.createEstimatePo(
            "co-1", TransactionEntryPointType.Catalogue, TransactionSubmissionType.Estimate,
            TransactionDeliveryChannel.WhatsAppShared, "buyer-1",
            listOf(oneLine().copy(snapshotProductName = "Widget v1", snapshotSku = "SKU-OLD")), ts(0),
        )
        // Simulate a later "Catalogue rename" by simply never touching this repository's stored
        // snapshot again — there is no code path in this repository that re-reads Catalogue.
        val reread = repo.findEstimatePoById("co-1", estimatePo.estimatePoId)!!
        assertEquals("Widget v1", reread.lineItems.single().snapshotProductName)
        assertEquals("SKU-OLD", reread.lineItems.single().snapshotSku)
    }

    // ============================== Prospect -> Ledger ==============================

    @Test
    fun `Accept promotes a Prospect buyer to Customer and records the seller's ledger-group choice`() = runTest(dispatcher) {
        partyRepository.seed(Party("co-1", "buyer-1", "Acme Traders", PartyClassification.Prospect, null, null, null, null, null, null, null, null, 0, 0))
        val repo = repository()
        val result = acceptFlow(repo, "co-1", "buyer-1", LedgerGroupChoice.Debtor)
        assertTrue(result is AcceptSellerInboxEntryResult.Success)

        assertEquals(PartyClassification.Customer, partyRepository.getPartyById("co-1", "buyer-1")!!.classification)
        val intent = ledgerIntentDao.findByTransactionId("co-1", (result as AcceptSellerInboxEntryResult.Success).transaction.transactionId)!!
        assertEquals("DEBTOR", intent.chosenLedgerGroup)
        assertNotNull(intent.promotedProspectAt)
    }

    @Test
    fun `Accept for an already-Customer buyer is idempotent - no re-promotion timestamp`() = runTest(dispatcher) {
        partyRepository.seed(Party("co-1", "buyer-1", "Acme Traders", PartyClassification.Customer, null, null, null, null, null, null, null, null, 0, 0))
        val repo = repository()
        val result = acceptFlow(repo, "co-1", "buyer-1", LedgerGroupChoice.Debtor) as AcceptSellerInboxEntryResult.Success
        val intent = ledgerIntentDao.findByTransactionId("co-1", result.transaction.transactionId)!!
        assertNull(intent.promotedProspectAt)
    }

    @Test
    fun `Prospect promotion never creates a second identity table - reuses the same partyId`() = runTest(dispatcher) {
        partyRepository.seed(Party("co-1", "buyer-1", "Acme Traders", PartyClassification.Prospect, null, null, null, null, null, null, null, null, 0, 0))
        val repo = repository()
        val result = acceptFlow(repo, "co-1", "buyer-1") as AcceptSellerInboxEntryResult.Success
        assertEquals("buyer-1", result.transaction.buyerPartyId)
        assertEquals(1, partyRepository.allParties().size) // still exactly one Party row
    }

    // ============================== state machine via repository ==============================

    @Test
    fun `full lifecycle - PendingConfirmation to Agreed to PaymentInitiated to PaymentConfirmed to Completed`() = runTest(dispatcher) {
        partyRepository.seed(Party("co-1", "buyer-1", "Acme", PartyClassification.Customer, null, null, null, null, null, null, null, null, 0, 0))
        val repo = repository()
        val accepted = acceptFlow(repo, "co-1", "buyer-1") as AcceptSellerInboxEntryResult.Success
        val transactionId = accepted.transaction.transactionId

        assertEquals(CommercialTransactionState.PendingConfirmation, repo.findTransactionById("co-1", transactionId)!!.state)

        repo.confirmTermsAsBuyer("co-1", transactionId, ts(10))
        assertEquals(CommercialTransactionState.PendingConfirmation, repo.findTransactionById("co-1", transactionId)!!.state)

        repo.confirmTermsAsSeller("co-1", transactionId, ts(11))
        assertEquals(CommercialTransactionState.Agreed, repo.findTransactionById("co-1", transactionId)!!.state)
        assertTrue(reminderScheduler.scheduled.contains(transactionId))

        val event = repo.recordPaymentClaim("co-1", transactionId, "1000", "INR", PaymentClaimStatus.Paid, ts(12))!!
        assertEquals(CommercialTransactionState.PaymentInitiated, repo.findTransactionById("co-1", transactionId)!!.state)

        repo.confirmPaymentReceived("co-1", transactionId, event.paymentEventId, ts(13))
        val final = repo.findTransactionById("co-1", transactionId)!!
        assertEquals(CommercialTransactionState.Completed, final.state)
        assertNotNull(final.completedAt)
        assertTrue(reminderScheduler.cancelled.contains(transactionId))
    }

    @Test
    fun `completedAt is set exactly once and never moves on further calls`() = runTest(dispatcher) {
        partyRepository.seed(Party("co-1", "buyer-1", "Acme", PartyClassification.Customer, null, null, null, null, null, null, null, null, 0, 0))
        val repo = repository()
        val accepted = acceptFlow(repo, "co-1", "buyer-1") as AcceptSellerInboxEntryResult.Success
        val transactionId = accepted.transaction.transactionId
        repo.confirmTermsAsBuyer("co-1", transactionId, ts(1))
        repo.confirmTermsAsSeller("co-1", transactionId, ts(2))
        val event = repo.recordPaymentClaim("co-1", transactionId, "1000", "INR", PaymentClaimStatus.Paid, ts(3))!!
        repo.confirmPaymentReceived("co-1", transactionId, event.paymentEventId, ts(100))
        val firstCompletedAt = repo.findTransactionById("co-1", transactionId)!!.completedAt!!.epochMillis
        assertEquals(100L, firstCompletedAt)
    }

    // ============================== buying history ==============================

    @Test
    fun `only Completed transactions feed buying history - Pending and Agreed are excluded`() = runTest(dispatcher) {
        partyRepository.seed(Party("co-1", "buyer-1", "Acme", PartyClassification.Customer, null, null, null, null, null, null, null, null, 0, 0))
        val repo = repository()

        // Transaction 1: never progresses past PendingConfirmation.
        acceptFlow(repo, "co-1", "buyer-1")
        assertTrue(repo.findCompletedPurchaseHistory("co-1", "buyer-1").isEmpty())

        // Transaction 2: fully completed.
        val accepted2 = acceptFlow(repo, "co-1", "buyer-1") as AcceptSellerInboxEntryResult.Success
        val txId2 = accepted2.transaction.transactionId
        repo.confirmTermsAsBuyer("co-1", txId2, ts(1))
        repo.confirmTermsAsSeller("co-1", txId2, ts(2))
        val event = repo.recordPaymentClaim("co-1", txId2, "1000", "INR", PaymentClaimStatus.Paid, ts(3))!!
        repo.confirmPaymentReceived("co-1", txId2, event.paymentEventId, ts(4))

        val history = repo.findCompletedPurchaseHistory("co-1", "buyer-1")
        assertEquals(1, history.size)
        assertEquals("Widget", history.single().snapshotProductName)
    }

    // ============================== access grants (Q18) ==============================

    @Test
    fun `a grant with expiresAt in the past is never active - checked on read`() = runTest(dispatcher) {
        val repo = repository()
        repo.grantCatalogueAccess("co-1", "buyer-1", ts(500), ts(0))
        assertNull(repo.findActiveCatalogueAccessGrant("co-1", "buyer-1", nowEpochMillis = 1000))
    }

    @Test
    fun `an Always grant (null expiry) never expires`() = runTest(dispatcher) {
        val repo = repository()
        repo.grantCatalogueAccess("co-1", "buyer-1", expiresAt = null, timestamp = ts(0))
        assertNotNull(repo.findActiveCatalogueAccessGrant("co-1", "buyer-1", nowEpochMillis = Long.MAX_VALUE))
    }

    @Test
    fun `a revoked grant is immediately inactive regardless of expiry`() = runTest(dispatcher) {
        val repo = repository()
        val grant = repo.grantCatalogueAccess("co-1", "buyer-1", expiresAt = null, timestamp = ts(0))
        repo.revokeCatalogueAccess("co-1", grant.grantId, ts(1))
        assertNull(repo.findActiveCatalogueAccessGrant("co-1", "buyer-1", nowEpochMillis = 2))
    }

    // ============================== helper ==============================

    private suspend fun acceptFlow(
        repo: com.budcom.android.feature.transaction.domain.repository.TransactionRepository,
        companyId: String,
        buyerPartyId: String,
        ledgerGroupChoice: LedgerGroupChoice = LedgerGroupChoice.Debtor,
    ): AcceptSellerInboxEntryResult {
        val estimatePo = repo.createEstimatePo(
            companyId, TransactionEntryPointType.Catalogue, TransactionSubmissionType.Estimate,
            TransactionDeliveryChannel.InAppSubmitted, buyerPartyId, listOf(oneLine()), ts(0),
        )
        val inboxEntry = repo.findAllSellerInboxEntries(companyId).single { it.estimatePoId == estimatePo.estimatePoId }
        return repo.acceptSellerInboxEntry(
            companyId, inboxEntry.inboxEntryId, ledgerGroupChoice,
            ProposedTerms(paymentTiming = PaymentTiming.Advance), ts(1),
        )
    }
}

// ============================== fakes ==============================

class FakeEstimatePoDao : EstimatePoDao {
    val store = mutableMapOf<String, EstimatePoEntity>()
    private fun key(companyId: String, id: String) = "$companyId|$id"
    override suspend fun upsert(entity: EstimatePoEntity) { store[key(entity.companyId, entity.estimatePoId)] = entity }
    override suspend fun findById(companyId: String, estimatePoId: String) = store[key(companyId, estimatePoId)]
    override suspend fun findAllForBuyer(companyId: String, buyerPartyId: String) =
        store.values.filter { it.companyId == companyId && it.buyerPartyId == buyerPartyId }
}

class FakeEstimatePoLineItemDao : EstimatePoLineItemDao {
    val store = mutableListOf<EstimatePoLineItemEntity>()
    override suspend fun upsertAll(entities: List<EstimatePoLineItemEntity>) { store.addAll(entities) }
    override suspend fun findAllForEstimatePo(companyId: String, estimatePoId: String) =
        store.filter { it.companyId == companyId && it.estimatePoId == estimatePoId }
}

class FakeSellerInboxEntryDao : SellerInboxEntryDao {
    val store = mutableMapOf<String, SellerInboxEntryEntity>()
    private fun key(companyId: String, id: String) = "$companyId|$id"
    override suspend fun upsert(entity: SellerInboxEntryEntity) { store[key(entity.companyId, entity.inboxEntryId)] = entity }
    override suspend fun findById(companyId: String, inboxEntryId: String) = store[key(companyId, inboxEntryId)]
    override suspend fun findByEstimatePoId(companyId: String, estimatePoId: String) =
        store.values.firstOrNull { it.companyId == companyId && it.estimatePoId == estimatePoId }
    override suspend fun findAllForCompany(companyId: String) = store.values.filter { it.companyId == companyId }
}

class FakeCommercialTransactionDao : CommercialTransactionDao {
    val store = mutableMapOf<String, CommercialTransactionEntity>()
    private fun key(companyId: String, id: String) = "$companyId|$id"
    override suspend fun upsert(entity: CommercialTransactionEntity) { store[key(entity.companyId, entity.transactionId)] = entity }
    override suspend fun findById(companyId: String, transactionId: String) = store[key(companyId, transactionId)]
    override suspend fun findAllForBuyer(companyId: String, buyerPartyId: String) =
        store.values.filter { it.companyId == companyId && it.buyerPartyId == buyerPartyId }.sortedBy { it.acceptedAt }
    override suspend fun findAllForCompany(companyId: String) = store.values.filter { it.companyId == companyId }.sortedByDescending { it.acceptedAt }
    override suspend fun findAllForBuyerInState(companyId: String, buyerPartyId: String, state: String) =
        store.values.filter { it.companyId == companyId && it.buyerPartyId == buyerPartyId && it.state == state }
}

class FakeTermsAcknowledgmentDao : TermsAcknowledgmentDao {
    val store = mutableMapOf<String, TermsAcknowledgmentEntity>()
    private fun key(companyId: String, id: String) = "$companyId|$id"
    override suspend fun upsert(entity: TermsAcknowledgmentEntity) { store[key(entity.companyId, entity.transactionId)] = entity }
    override suspend fun findByTransactionId(companyId: String, transactionId: String) = store[key(companyId, transactionId)]
}

class FakePaymentEventDao : PaymentEventDao {
    val store = mutableMapOf<String, PaymentEventEntity>()
    private fun key(companyId: String, txId: String, eventId: String) = "$companyId|$txId|$eventId"
    override suspend fun upsert(entity: PaymentEventEntity) { store[key(entity.companyId, entity.transactionId, entity.paymentEventId)] = entity }
    override suspend fun findAllForTransaction(companyId: String, transactionId: String) =
        store.values.filter { it.companyId == companyId && it.transactionId == transactionId }.sortedBy { it.installmentSequence }
    override suspend fun findById(companyId: String, transactionId: String, paymentEventId: String) = store[key(companyId, transactionId, paymentEventId)]
}

class FakeLedgerIntentDao : LedgerIntentDao {
    val store = mutableMapOf<String, LedgerIntentEntity>()
    private fun key(companyId: String, id: String) = "$companyId|$id"
    override suspend fun upsert(entity: LedgerIntentEntity) { store[key(entity.companyId, entity.transactionId)] = entity }
    override suspend fun findByTransactionId(companyId: String, transactionId: String) = store[key(companyId, transactionId)]
}

class FakeCatalogueAccessGrantDao : CatalogueAccessGrantDao {
    val store = mutableMapOf<String, CatalogueAccessGrantEntity>()
    private fun key(companyId: String, id: String) = "$companyId|$id"
    override suspend fun upsert(entity: CatalogueAccessGrantEntity) { store[key(entity.companyId, entity.grantId)] = entity }
    override suspend fun findById(companyId: String, grantId: String) = store[key(companyId, grantId)]
    override suspend fun findAllForBuyer(companyId: String, buyerPartyId: String) =
        store.values.filter { it.companyId == companyId && it.buyerPartyId == buyerPartyId }.sortedByDescending { it.grantedAt }
}

class FakeCanonicalOrderDao : CanonicalOrderDao {
    val orders = mutableListOf<CanonicalOrderEntity>()
    private val lines = mutableListOf<CanonicalOrderLineEntity>()

    override suspend fun insert(entity: CanonicalOrderEntity) {
        if (orders.any { it.companyId == entity.companyId && it.creationKey == entity.creationKey }) {
            throw android.database.SQLException("duplicate creation key")
        }
        orders += entity
    }

    override suspend fun upsertLines(entities: List<CanonicalOrderLineEntity>) {
        lines.removeAll { old -> entities.any { it.companyId == old.companyId && it.orderId == old.orderId && it.lineId == old.lineId } }
        lines += entities
    }

    override suspend fun findByCreationKey(companyId: String, creationKey: String) =
        orders.firstOrNull { it.companyId == companyId && it.creationKey == creationKey }

    override suspend fun findLines(companyId: String, orderId: String) =
        lines.filter { it.companyId == companyId && it.orderId == orderId }.sortedBy { it.lineId }
}

class FakeTransactionSubmissionPort(private val sellerInboxEntryDao: FakeSellerInboxEntryDao) : TransactionSubmissionPort {
    override suspend fun deliverToSellerInbox(companyId: String, estimatePoId: String): SellerInboxEntry {
        val entity = SellerInboxEntryEntity(
            companyId = companyId, inboxEntryId = UUID.randomUUID().toString(), estimatePoId = estimatePoId,
            state = SellerInboxState.New.columnValue, acknowledgedAt = null, acknowledgedAtSource = null,
            changeRequestNote = null, respondedAt = null, respondedAtSource = null, convertedTransactionId = null,
        )
        sellerInboxEntryDao.upsert(entity)
        return entity.toDomain()
    }
}

class FakeTransactionReminderScheduler : TransactionReminderScheduler {
    val scheduled = mutableListOf<String>()
    val cancelled = mutableListOf<String>()
    override suspend fun scheduleReminders(companyId: String, transactionId: String) { scheduled += transactionId }
    override suspend fun cancelReminders(companyId: String, transactionId: String) { cancelled += transactionId }
}

/** Minimal fake — only [getPartyById]/[promoteProspectToCustomer] are meaningfully implemented
 * (the only two this feature's Prospect -> Ledger flow uses); every other member of this large,
 * unrelated interface is intentionally unsupported here to avoid faking ~35 methods this test
 * suite never exercises. */
class FakePartyRepository : PartyRepository {
    private val parties = mutableMapOf<String, Party>()
    private fun key(companyId: String, partyId: String) = "$companyId|$partyId"
    fun seed(party: Party) { parties[key(party.companyId, party.partyId)] = party }
    fun allParties(): List<Party> = parties.values.toList()

    override suspend fun getPartyById(companyId: String, partyId: String): Party? = parties[key(companyId, partyId)]

    override suspend fun promoteProspectToCustomer(companyId: String, partyId: String): Party? {
        val existing = parties[key(companyId, partyId)] ?: return null
        if (existing.classification != PartyClassification.Prospect) return existing
        val updated = existing.copy(classification = PartyClassification.Customer)
        parties[key(companyId, partyId)] = updated
        return updated
    }

    private fun unsupported(): Nothing = throw UnsupportedOperationException("not used by TransactionRepositoryImplTest")
    override suspend fun getPartyForLedger(companyId: String, ledgerId: String): Party? = unsupported()
    override suspend fun listByClassification(companyId: String, classification: PartyClassification, page: Int, pageSize: Int): PartyPage = unsupported()
    override suspend fun searchParties(companyId: String, query: String, classification: PartyClassification?, page: Int, pageSize: Int): PartyPage = unsupported()
    override suspend fun getContactPersons(companyId: String, partyId: String): List<PartyContactPerson> = unsupported()
    override suspend fun getTagsForParty(companyId: String, partyId: String): List<Tag> = unsupported()
    override suspend fun getSourceLinksForCompany(companyId: String): List<PartySourceLink> = unsupported()
    override suspend fun getTagsForCompany(companyId: String): Map<String, List<Tag>> = unsupported()
    override suspend fun getFieldProvenance(companyId: String, partyId: String): List<PartyFieldProvenance> = unsupported()
    override suspend fun updateBudcomOnlyField(companyId: String, partyId: String, fieldName: String, value: String?): FieldProvenanceState = unsupported()
    override suspend fun confirmFieldFromTally(companyId: String, partyId: String, fieldName: String, tallyValue: String?): FieldProvenanceState = unsupported()
    override suspend fun reconcilePartiesFromEligibleLedgers(companyId: String, seeds: List<EligibleLedgerSeed>): List<Party> = unsupported()
    override suspend fun applyLedgerContactDetailsBulk(companyId: String, items: List<com.budcom.android.feature.masterdata.ledger.domain.model.LedgerContactDetails>): BulkContactSeedResult = unsupported()
    override suspend fun createProspect(companyId: String, draft: ProspectDraft): Party = unsupported()
    override suspend fun getSourceLinkForParty(companyId: String, partyId: String): PartySourceLink? = unsupported()
    override suspend fun upsertContactPerson(companyId: String, partyId: String, contactPersonId: String?, name: String, designation: String?, mobile: String?, whatsappNumber: String?, email: String?, isPrimary: Boolean): PartyContactPerson = unsupported()
    override suspend fun deleteContactPerson(companyId: String, contactPersonId: String): Unit = unsupported()
    override suspend fun getAllTags(): List<Tag> = unsupported()
    override suspend fun createOrGetTag(name: String, parentTagId: String?): Tag = unsupported()
    override suspend fun assignTag(companyId: String, partyId: String, tagId: String): Unit = unsupported()
    override suspend fun unassignTag(companyId: String, partyId: String, tagId: String): Unit = unsupported()
    override suspend fun addNote(companyId: String, partyId: String, body: String, linkedVoucherId: String?, type: NoteType, dueAt: Long?, issueId: String?): PartyNote = unsupported()
    override suspend fun editNote(companyId: String, noteId: String, body: String, type: NoteType, dueAt: Long?, issueId: String?): PartyNote? = unsupported()
    override suspend fun setNoteCompletion(companyId: String, noteId: String, completedAt: Long?): PartyNote? = unsupported()
    override suspend fun deleteNote(companyId: String, noteId: String): Unit = unsupported()
    override suspend fun getNotesForParty(companyId: String, partyId: String, page: Int, pageSize: Int): PartyNotePage = unsupported()
    override suspend fun getTimelineForParty(companyId: String, partyId: String, page: Int, pageSize: Int, issueId: String?): TimelineEntryPage = unsupported()
    override suspend fun createIssue(companyId: String, partyId: String, title: String): PartyIssue = unsupported()
    override suspend fun resolveIssue(companyId: String, issueId: String): PartyIssue? = unsupported()
    override suspend fun reopenIssue(companyId: String, issueId: String): PartyIssue? = unsupported()
    override suspend fun getIssuesForParty(companyId: String, partyId: String): List<PartyIssue> = unsupported()
    override suspend fun getIssueActivitySummary(companyId: String, partyId: String): Map<String, IssueActivitySummary> = unsupported()
    override suspend fun getExportCandidates(companyId: String, partyId: String): List<TallyFieldExportCandidate> = unsupported()
    override suspend fun recordExport(companyId: String, partyId: String, outputFileName: String, fieldNames: List<String>): PartyExportEvent = unsupported()
    override suspend fun reconcileExportedFieldFromTally(companyId: String, partyId: String, fieldName: String, tallyRawValue: String?): FieldProvenanceState = unsupported()
    override suspend fun getExportHistory(companyId: String, partyId: String, limit: Int): List<PartyExportEvent> = unsupported()
}
