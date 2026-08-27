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
import com.budcom.android.feature.transaction.data.local.OrderDeliveryEnvelopeEntity
import com.budcom.android.feature.transaction.data.local.OrderOutboxDao
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
import com.budcom.android.feature.transaction.domain.model.CanonicalOrder
import com.budcom.android.feature.transaction.domain.model.CanonicalOrderLine
import com.budcom.android.feature.transaction.domain.model.OrderRevisionLineChange
import com.budcom.android.feature.transaction.domain.model.CommercialTransactionState
import com.budcom.android.feature.transaction.domain.model.CanonicalOrderState
import com.budcom.android.feature.transaction.domain.model.OrderRevisionAcceptEvidence
import com.budcom.android.feature.transaction.domain.model.OrderVersionLineSnapshot
import com.budcom.android.feature.transaction.domain.model.OrderVersionSnapshot
import com.budcom.android.feature.transaction.domain.model.OrderTransportState
import com.budcom.android.feature.transaction.data.local.OrderCommercialEventDao
import com.budcom.android.feature.transaction.data.local.OrderCommercialEventEntity
import com.budcom.android.feature.transaction.data.local.OrderVersionArchiveDao
import com.budcom.android.feature.transaction.data.local.OrderVersionArchiveEntity
import com.budcom.android.feature.transaction.data.local.OrderVersionLineArchiveEntity
import com.budcom.android.feature.transaction.data.local.StructuredRecipientInboxDao
import com.budcom.android.feature.transaction.data.local.StructuredRecipientInboxEntity
import com.budcom.android.feature.transaction.domain.model.OrderCommercialEventType
import com.budcom.android.feature.transaction.domain.model.OrderConfirmAuthority
import com.budcom.android.feature.transaction.domain.model.OrderConfirmEvidence
import com.budcom.android.feature.transaction.domain.model.OrderSeenEvidence
import com.budcom.android.feature.transaction.domain.model.OrderStructuredOpenEvent
import com.budcom.android.feature.transaction.domain.model.RelayAcceptanceEvidence
import com.budcom.android.feature.transaction.domain.model.RecipientInboxTransportState
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
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
    private val orderOutboxDao = FakeOrderOutboxDao()
    private val recipientInboxDao = FakeTransactionRecipientInboxDao()
    private val orderCommercialEventDao = FakeOrderCommercialEventDao()
    private val orderVersionArchiveDao = FakeOrderVersionArchiveDao()
    private val submissionPort = FakeTransactionSubmissionPort(sellerInboxEntryDao)
    private val reminderScheduler = FakeTransactionReminderScheduler()
    private val partyRepository = FakePartyRepository()

    @Before
    fun resetFakes() {
        estimatePoDao.store.clear()
        lineItemDao.store.clear()
        sellerInboxEntryDao.store.clear()
        transactionDao.store.clear()
        termsDao.store.clear()
        paymentEventDao.store.clear()
        ledgerIntentDao.store.clear()
        accessGrantDao.store.clear()
        canonicalOrderDao.orders.clear()
        canonicalOrderDao.clearLines()
        orderOutboxDao.envelopes.clear()
        recipientInboxDao.entries.clear()
        orderCommercialEventDao.events.clear()
        orderVersionArchiveDao.orders.clear()
        orderVersionArchiveDao.lines.clear()
    }

    private fun repository() = TransactionRepositoryImpl(
        estimatePoDao, lineItemDao, sellerInboxEntryDao, transactionDao, termsDao, paymentEventDao,
        ledgerIntentDao, accessGrantDao, submissionPort, reminderScheduler, partyRepository, dispatchers,
        canonicalOrderDao, orderOutboxDao, recipientInboxDao, orderCommercialEventDao, orderVersionArchiveDao,
        com.budcom.android.feature.transaction.data.local.PassthroughCommercialDbTransaction,
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

    @Test
    fun `order delivery enqueue is durable idempotent and does not change order state`() = runTest(dispatcher) {
        val repo = repository()
        var draft = TransactionDraftOperations.empty("co-1", "buyer-1", TransactionSubmissionType.Estimate)
        draft = TransactionDraftOperations.addOrIncrementLine(draft, "product-1", "Widget", "Nos", "SKU-1", TransactionDraftPriceState.ContactForPrice)
        val order = repo.createDraftOrder(draft, "review-queue-1", timestamp = ts(100))

        val first = repo.enqueueOrderDelivery(order, ts(200))
        val retry = repo.enqueueOrderDelivery(order, ts(300))

        assertEquals(first.envelopeId, retry.envelopeId)
        assertEquals(OrderTransportState.Queued, first.state)
        assertEquals(order.orderId, first.orderId)
        assertEquals(order.version, first.orderVersion)
        assertEquals("co-1", first.senderCompanyId)
        assertEquals("buyer-1", first.recipientPartyId)
        assertEquals(1, orderOutboxDao.envelopes.size)
        assertEquals(CanonicalOrderState.Draft, order.state)
        assertTrue(estimatePoDao.store.isEmpty())
        assertTrue(transactionDao.store.isEmpty())
    }

    @Test
    fun `matching relay acceptance marks draft order sent without claiming seen or delivered`() = runTest(dispatcher) {
        val repo = repository()
        var draft = TransactionDraftOperations.empty("co-1", "buyer-1", TransactionSubmissionType.Estimate)
        draft = TransactionDraftOperations.addOrIncrementLine(draft, "product-1", "Widget", "Nos", "SKU-1", TransactionDraftPriceState.ContactForPrice)
        val order = repo.createDraftOrder(draft, "sent-evidence-1", timestamp = ts(100))
        val envelope = repo.enqueueOrderDelivery(order, ts(200))
        val evidence = RelayAcceptanceEvidence(
            acceptanceId = "accept-1", envelopeId = envelope.envelopeId, objectType = envelope.objectType,
            objectId = order.orderId, objectVersion = order.version, senderBusinessId = order.sellerCompanyId,
            recipientBusinessId = "buyer-1", acceptedAtEpochMillis = 250, status = "relay_accepted",
        )
        val sent = repo.markOrderSentFromRelayEvidence("co-1", envelope, evidence)!!
        val retry = repo.markOrderSentFromRelayEvidence("co-1", envelope, evidence)!!
        assertEquals(CanonicalOrderState.Sent, sent.state)
        assertEquals(CanonicalOrderState.Sent, retry.state)
        assertEquals("SENT", canonicalOrderDao.orders.single().state)
        assertNull(repo.markOrderSentFromRelayEvidence("co-1", envelope, evidence.copy(objectVersion = 99)))
        assertNull(repo.markOrderSentFromRelayEvidence("co-1", envelope, evidence.copy(status = "delivered")))
        assertEquals("SENT", canonicalOrderDao.orders.single().state)
        assertEquals(OrderTransportState.Queued, envelope.state)
    }

    @Test
    fun `delivered transport alone leaves order sent and opening creates one seen event`() = runTest(dispatcher) {
        val repo = repository()
        canonicalOrderDao.orders += CanonicalOrderEntity(
            companyId = "buyer-co", orderId = "order-1", creationKey = "k", sellerCompanyId = "buyer-co",
            buyerPartyId = "seller-co", state = CanonicalOrderState.Sent.columnValue,
            source = TransactionEntryPointType.Catalogue.columnValue,
            submissionType = TransactionSubmissionType.Estimate.columnValue,
            note = null, createdAt = 100, createdAtSource = TransactionTimestampSource.DeviceLocalProvisional.name, version = 1,
        )
        recipientInboxDao.entries += StructuredRecipientInboxEntity(
            companyId = "seller-co", envelopeId = "env-1", idempotencyKey = "inbox-1",
            objectType = "CANONICAL_ORDER", objectId = "order-1", objectVersion = 1,
            senderBusinessId = "buyer-co", senderActorId = "actor-b", senderDeviceId = "device-b",
            mailboxId = "orders", mailboxSequence = 1, acceptanceId = "accept-1",
            acceptedAt = 200, acceptedAtSource = TransactionTimestampSource.DeviceLocalProvisional.name,
            ingestedAt = 210, ingestedAtSource = TransactionTimestampSource.DeviceLocalProvisional.name,
            transportState = RecipientInboxTransportState.Received.columnValue,
        )
        val open = OrderStructuredOpenEvent(
            eventId = "seen-1", idempotencyKey = "seen:order-1:v1:seller-co",
            orderId = "order-1", orderVersion = 1, objectType = "CANONICAL_ORDER",
            viewerBusinessId = "seller-co", viewerActorId = "actor-s", viewerDeviceId = "device-s",
            senderBusinessId = "buyer-co", openedAt = ts(300),
        )
        val recorded = repo.recordOrderSeenFromOpenEvent("seller-co", "env-1", open)!!
        val retry = repo.recordOrderSeenFromOpenEvent("seller-co", "env-1", open)!!
        assertEquals(recorded.eventId, retry.eventId)
        assertEquals(OrderCommercialEventType.Seen, recorded.eventType)
        assertEquals(1, orderCommercialEventDao.events.size)
        assertEquals("SENT", canonicalOrderDao.orders.single { it.companyId == "buyer-co" }.state)
        val evidence = OrderSeenEvidence(
            eventId = recorded.eventId, orderId = "order-1", orderVersion = 1,
            viewerBusinessId = "seller-co", viewerActorId = "actor-s", viewerDeviceId = "device-s",
            senderBusinessId = "buyer-co", seenAt = ts(300),
        )
        val seen = repo.applyOrderSeenEvidence("buyer-co", evidence)!!
        assertEquals(CanonicalOrderState.Seen, seen.state)
        assertEquals(CanonicalOrderState.Seen, repo.applyOrderSeenEvidence("buyer-co", evidence)!!.state)
        assertNull(repo.recordOrderSeenFromOpenEvent(
            "seller-co", "env-1",
            open.copy(viewerBusinessId = "wrong", idempotencyKey = "seen:wrong"),
        ))
        assertNull(repo.applyOrderSeenEvidence("buyer-co", evidence.copy(orderVersion = 2)))
    }

    @Test
    fun `seen and confirm retries stay idempotent`() = runTest(dispatcher) {
        val repo = repository()
        seedSentOrderFixture()
        val open = seenOpenEvent()
        val seen1 = repo.recordOrderSeenFromOpenEvent("seller-co", "env-1", open)!!
        val seen2 = repo.recordOrderSeenFromOpenEvent("seller-co", "env-1", open)!!
        assertEquals(seen1.eventId, seen2.eventId)
        val authority = OrderConfirmAuthority("seller-co", "actor-s", "device-s", setOf("confirm_orders"), 1)
        val confirm1 = repo.recordOrderConfirmFromSellerAction("seller-co", "env-1", authority, "confirm-1", "confirm:key", ts(400))!!
        val confirm2 = repo.recordOrderConfirmFromSellerAction("seller-co", "env-1", authority, "confirm-1", "confirm:key", ts(401))!!
        assertEquals(confirm1.eventId, confirm2.eventId)
        val seenEvidence = OrderSeenEvidence(
            eventId = seen1.eventId, orderId = "order-1", orderVersion = 1, viewerBusinessId = "seller-co",
            viewerActorId = "actor-s", viewerDeviceId = "device-s", senderBusinessId = "buyer-co", seenAt = ts(300),
        )
        assertEquals(CanonicalOrderState.Seen, repo.applyOrderSeenEvidence("buyer-co", seenEvidence)!!.state)
        val evidence = OrderConfirmEvidence(
            eventId = confirm1.eventId, orderId = "order-1", orderVersion = 1, confirmingBusinessId = "seller-co",
            confirmingActorId = "actor-s", confirmingDeviceId = "device-s", senderBusinessId = "buyer-co",
            authorityEpoch = 1, authorityScopeFingerprint = "confirm_orders", confirmedAt = ts(400),
        )
        assertEquals(CanonicalOrderState.Confirmed, repo.applyOrderConfirmEvidence("buyer-co", evidence)!!.state)
        assertEquals(CanonicalOrderState.Confirmed, repo.applyOrderConfirmEvidence("buyer-co", evidence)!!.state)
    }

    @Test
    fun `wrong authority and forged seen fail closed`() = runTest(dispatcher) {
        val repo = repository()
        recipientInboxDao.entries += inboxFixture()
        orderCommercialEventDao.events += OrderCommercialEventEntity(
            companyId = "seller-co", eventId = "seen-1", idempotencyKey = "seen:key",
            orderId = "order-1", orderVersion = 1, eventType = OrderCommercialEventType.Seen.columnValue,
            actorBusinessId = "seller-co", actorId = "actor-s", actorDeviceId = "device-s",
            counterpartyBusinessId = "buyer-co", occurredAt = 300, occurredAtSource = TransactionTimestampSource.DeviceLocalProvisional.name,
        )
        val denied = OrderConfirmAuthority("wrong-co", "actor-s", "device-s", setOf("confirm_orders"), 1)
        assertNull(repo.recordOrderConfirmFromSellerAction("seller-co", "env-1", denied, "c-1", "confirm:bad", ts(400)))
        val forgedSeen = OrderSeenEvidence(
            eventId = "forged", orderId = "order-1", orderVersion = 1, viewerBusinessId = "buyer-co",
            viewerActorId = "actor-b", viewerDeviceId = "device-b", senderBusinessId = "buyer-co", seenAt = ts(1),
        )
        assertNull(repo.applyOrderSeenEvidence("buyer-co", forgedSeen))
    }

    @Test
    fun `canonical sent seen confirm path leaves accounting untouched`() = runTest(dispatcher) {
        val repo = repository()
        seedSentOrderFixture()
        repo.recordOrderSeenFromOpenEvent("seller-co", "env-1", seenOpenEvent())
        val seenEvidence = OrderSeenEvidence(
            eventId = "seen-1", orderId = "order-1", orderVersion = 1, viewerBusinessId = "seller-co",
            viewerActorId = "actor-s", viewerDeviceId = "device-s", senderBusinessId = "buyer-co", seenAt = ts(300),
        )
        assertEquals(CanonicalOrderState.Seen, repo.applyOrderSeenEvidence("buyer-co", seenEvidence)!!.state)
        val authority = OrderConfirmAuthority("seller-co", "actor-s", "device-s", setOf("confirm_orders"), 1)
        repo.recordOrderConfirmFromSellerAction("seller-co", "env-1", authority, "confirm-1", "confirm:key", ts(400))
        val confirmEvidence = OrderConfirmEvidence(
            eventId = "confirm-1", orderId = "order-1", orderVersion = 1, confirmingBusinessId = "seller-co",
            confirmingActorId = "actor-s", confirmingDeviceId = "device-s", senderBusinessId = "buyer-co",
            authorityEpoch = 1, authorityScopeFingerprint = "confirm_orders", confirmedAt = ts(400),
        )
        assertEquals(CanonicalOrderState.Confirmed, repo.applyOrderConfirmEvidence("buyer-co", confirmEvidence)!!.state)
        assertTrue(transactionDao.store.isEmpty())
    }

    private fun seedSentOrderFixture() {
        canonicalOrderDao.orders += CanonicalOrderEntity(
            companyId = "buyer-co", orderId = "order-1", creationKey = "k", sellerCompanyId = "buyer-co",
            buyerPartyId = "seller-co", state = CanonicalOrderState.Sent.columnValue,
            source = TransactionEntryPointType.Catalogue.columnValue,
            submissionType = TransactionSubmissionType.Estimate.columnValue,
            note = null, createdAt = 100, createdAtSource = TransactionTimestampSource.DeviceLocalProvisional.name, version = 1,
        )
        recipientInboxDao.entries += inboxFixture()
    }

    private fun inboxFixture() = StructuredRecipientInboxEntity(
        companyId = "seller-co", envelopeId = "env-1", idempotencyKey = "inbox-1",
        objectType = "CANONICAL_ORDER", objectId = "order-1", objectVersion = 1,
        senderBusinessId = "buyer-co", senderActorId = "actor-b", senderDeviceId = "device-b",
        mailboxId = "orders", mailboxSequence = 1, acceptanceId = "accept-1",
        acceptedAt = 200, acceptedAtSource = TransactionTimestampSource.DeviceLocalProvisional.name,
        ingestedAt = 210, ingestedAtSource = TransactionTimestampSource.DeviceLocalProvisional.name,
        transportState = RecipientInboxTransportState.Received.columnValue,
    )

    private fun seenOpenEvent() = OrderStructuredOpenEvent(
        eventId = "seen-1", idempotencyKey = "seen:order-1:v1:seller-co",
        orderId = "order-1", orderVersion = 1, objectType = "CANONICAL_ORDER",
        viewerBusinessId = "seller-co", viewerActorId = "actor-s", viewerDeviceId = "device-s",
        senderBusinessId = "buyer-co", openedAt = ts(300),
    )

    @Test
    fun `complete revision acceptance flow preserves archive and rejects stale or unauthorized acceptance`() = runTest(dispatcher) {
        val repo = repository()
        seedBuyerOrderWithLines(CanonicalOrderState.Sent)
        seedSellerReceivedOrder()
        recipientInboxDao.entries += inboxFixture()
        repo.recordOrderSeenFromOpenEvent("seller-co", "env-1", seenOpenEvent())
        val sellerAuthority = OrderConfirmAuthority("seller-co", "actor-s", "device-s", setOf("revise_orders"), 1)
        val sellerRevision = repo.proposeOrderRevision(
            "seller-co", "env-1", sellerBaselineOrder(), revisionLines("12"), "Need 12 units",
            sellerAuthority, ts(500), "revision:order-1:v2",
        )!!
        assertEquals(CanonicalOrderState.RevisionPending, sellerRevision.state)
        assertEquals(2, sellerRevision.version)
        val revisionEnvelope = repo.enqueueOrderDelivery(sellerRevision, ts(510))
        repo.markRevisionSent("seller-co", "order-1", revisionEnvelope)!!
        recipientInboxDao.entries += buyerRevisionInboxFixture("env-2", 2)
        val buyerRevision = sellerRevision.copy(
            companyId = "buyer-co",
            sellerCompanyId = "seller-co",
            buyerPartyId = "buyer-co",
            state = CanonicalOrderState.RevisionSent,
        )
        val received = repo.receiveOrderRevisionOnBuyer("buyer-co", "env-2", buyerRevision, ts(520))!!
        assertEquals(CanonicalOrderState.RevisionSent, received.state)
        assertEquals("10", repo.findArchivedOrderVersion("buyer-co", "order-1", 1)!!.lines.single().quantity)
        assertEquals(RecipientInboxTransportState.Received.columnValue, recipientInboxDao.entries.single { it.envelopeId == "env-2" }.transportState)
        val buyerOpen = OrderStructuredOpenEvent(
            eventId = "seen-rev-1", idempotencyKey = "seen:order-1:v2:buyer-co",
            orderId = "order-1", orderVersion = 2, objectType = "CANONICAL_ORDER",
            viewerBusinessId = "buyer-co", viewerActorId = "actor-b", viewerDeviceId = "device-b",
            senderBusinessId = "seller-co", openedAt = ts(530),
        )
        repo.recordOrderSeenFromOpenEvent("buyer-co", "env-2", buyerOpen)
        val seenEvidence = repo.findOrderSeenEvidence("buyer-co", "order-1", 2)!!
        val revisionSeen = repo.applyOrderSeenEvidence("buyer-co", seenEvidence)!!
        assertEquals(CanonicalOrderState.RevisionSeen, revisionSeen.state)
        assertNotEquals(CanonicalOrderState.Seen, received.state)
        val buyerAuthority = OrderConfirmAuthority("buyer-co", "actor-b", "device-b", setOf("accept_order_revisions"), 1)
        val acceptedEvent = repo.recordOrderRevisionAcceptFromBuyerAction(
            "buyer-co", "env-2", buyerAuthority, "accept-2", "accept:order-1:v2:buyer-co", ts(600),
        )!!
        val retryAccept = repo.recordOrderRevisionAcceptFromBuyerAction(
            "buyer-co", "env-2", buyerAuthority, "accept-2-retry", "accept:order-1:v2:buyer-co", ts(601),
        )!!
        assertEquals(acceptedEvent.eventId, retryAccept.eventId)
        val acceptEvidence = OrderRevisionAcceptEvidence(
            eventId = acceptedEvent.eventId, orderId = "order-1", orderVersion = 2,
            acceptingBusinessId = "buyer-co", acceptingActorId = "actor-b", acceptingDeviceId = "device-b",
            counterpartyBusinessId = "seller-co", authorityEpoch = 1, acceptedAt = ts(600),
        )
        val confirmed = repo.applyOrderRevisionAcceptEvidence("buyer-co", acceptEvidence)!!
        assertEquals(CanonicalOrderState.Confirmed, confirmed.state)
        assertEquals("12", confirmed.lines.single().quantity)
        assertEquals("10", repo.findArchivedOrderVersion("buyer-co", "order-1", 1)!!.lines.single().quantity)
        assertNull(repo.applyOrderRevisionAcceptEvidence("buyer-co", acceptEvidence.copy(orderVersion = 1)))
        assertNull(
            repo.recordOrderRevisionAcceptFromBuyerAction(
                "buyer-co", "env-2",
                OrderConfirmAuthority("buyer-co", "actor-b", "device-b", setOf("send_orders"), 1),
                "denied", "accept:denied", ts(602),
            ),
        )
        assertTrue(transactionDao.store.isEmpty())
        assertEquals(1, canonicalOrderDao.orders.count { it.companyId == "buyer-co" && it.orderId == "order-1" })
    }

    @Test
    fun `recipient materializes v1 and v2 snapshots without duplicates or wrong recipient`() = runTest(dispatcher) {
        val repo = repository()
        recipientInboxDao.entries += inboxFixture().copy(companyId = "seller-co", senderBusinessId = "buyer-co")
        val v1 = OrderVersionSnapshot(
            1, "order-1", 1, "buyer-co", "seller-co", 100, null, "CATALOGUE", "ESTIMATE", "env-1",
            listOf(OrderVersionLineSnapshot("line-1", "p1", "Widget", "Nos", "SKU-1", "10", "100", "INR", "ACTUAL", "1000")),
        )
        val first = repo.materializeReceivedOrderVersion("seller-co", "env-1", v1, ts(200))!!
        val duplicate = repo.materializeReceivedOrderVersion("seller-co", "env-1", v1, ts(201))!!
        assertEquals(first.orderId, duplicate.orderId)
        assertEquals(CanonicalOrderState.Sent, first.state)
        assertEquals("10", first.lines.single().quantity)
        assertEquals(1, canonicalOrderDao.orders.count { it.companyId == "seller-co" && it.orderId == "order-1" })
        assertNull(repo.materializeReceivedOrderVersion("other-co", "env-1", v1, ts(202)))
        recipientInboxDao.entries += buyerRevisionInboxFixture("env-2", 2)
        seedBuyerOrderWithLines(CanonicalOrderState.Sent)
        val v2 = v1.copy(orderVersion = 2, envelopeId = "env-2", recipientBusinessId = "buyer-co", senderBusinessId = "seller-co",
            lines = listOf(v1.lines.single().copy(quantity = "12")))
        val revised = repo.materializeReceivedOrderVersion("buyer-co", "env-2", v2, ts(520))!!
        assertEquals(2, revised.version)
        assertEquals("12", revised.lines.single().quantity)
        assertEquals("10", repo.findArchivedOrderVersion("buyer-co", "order-1", 1)!!.lines.single().quantity)
        assertTrue(transactionDao.store.isEmpty())
        val hidden = v1.copy(lines = listOf(v1.lines.single().copy(priceState = "HIDDEN", unitPriceAmount = null, lineTotalAmount = null)))
        assertFalse(hidden.deterministicEncoding().contains("unitPrice:100"))
    }

    private suspend fun seedBuyerOrderWithLines(state: CanonicalOrderState) {
        canonicalOrderDao.orders += CanonicalOrderEntity(
            companyId = "buyer-co", orderId = "order-1", creationKey = "k", sellerCompanyId = "buyer-co",
            buyerPartyId = "seller-co", state = state.columnValue,
            source = TransactionEntryPointType.Catalogue.columnValue,
            submissionType = TransactionSubmissionType.Estimate.columnValue,
            note = null, createdAt = 100, createdAtSource = TransactionTimestampSource.DeviceLocalProvisional.name, version = 1,
        )
        canonicalOrderDao.upsertLines(
            listOf(
                CanonicalOrderLineEntity(
                    companyId = "buyer-co", orderId = "order-1", lineId = "line-1", linkedProductId = "p1",
                    snapshotProductName = "Widget", snapshotUnit = "Nos", snapshotSku = "SKU-1", quantity = "10",
                    unitPriceAmount = "100", unitPriceCurrencyCode = "INR", priceState = "ACTUAL", lineTotalAmount = "1000",
                ),
            ),
        )
    }

    private suspend fun seedSellerReceivedOrder() {
        canonicalOrderDao.orders += CanonicalOrderEntity(
            companyId = "seller-co", orderId = "order-1", creationKey = "seller-k", sellerCompanyId = "buyer-co",
            buyerPartyId = "seller-co", state = CanonicalOrderState.Seen.columnValue,
            source = TransactionEntryPointType.Catalogue.columnValue,
            submissionType = TransactionSubmissionType.Estimate.columnValue,
            note = null, createdAt = 100, createdAtSource = TransactionTimestampSource.DeviceLocalProvisional.name, version = 1,
        )
        canonicalOrderDao.upsertLines(
            listOf(
                CanonicalOrderLineEntity(
                    companyId = "seller-co", orderId = "order-1", lineId = "line-1", linkedProductId = "p1",
                    snapshotProductName = "Widget", snapshotUnit = "Nos", snapshotSku = "SKU-1", quantity = "10",
                    unitPriceAmount = "100", unitPriceCurrencyCode = "INR", priceState = "ACTUAL", lineTotalAmount = "1000",
                ),
            ),
        )
    }

    private fun sellerBaselineOrder() = CanonicalOrder(
        companyId = "seller-co", orderId = "order-1", creationKey = "k", sellerCompanyId = "buyer-co", buyerPartyId = "seller-co",
        state = CanonicalOrderState.Seen, source = TransactionEntryPointType.Catalogue, submissionType = TransactionSubmissionType.Estimate,
        note = null, createdAt = ts(100), version = 1,
        lines = listOf(
            CanonicalOrderLine(
                orderId = "order-1", lineId = "line-1", linkedProductId = "p1", snapshotProductName = "Widget",
                snapshotUnit = "Nos", snapshotSku = "SKU-1", quantity = "10", unitPriceAmount = "100",
                unitPriceCurrencyCode = "INR", priceState = TransactionDraftPriceState.ActualPrice("100", "INR"), lineTotalAmount = "1000",
            ),
        ),
    )

    private fun revisionLines(quantity: String) = listOf(
        OrderRevisionLineChange(
            lineId = "line-1", linkedProductId = "p1", snapshotProductName = "Widget", snapshotUnit = "Nos", snapshotSku = "SKU-1",
            quantity = quantity, unitPriceAmount = "100", unitPriceCurrencyCode = "INR",
            priceState = TransactionDraftPriceState.ActualPrice("100", "INR"), lineTotalAmount = "1200",
        ),
    )

    private fun buyerRevisionInboxFixture(envelopeId: String, version: Int) = StructuredRecipientInboxEntity(
        companyId = "buyer-co", envelopeId = envelopeId, idempotencyKey = "inbox-$envelopeId",
        objectType = "CANONICAL_ORDER", objectId = "order-1", objectVersion = version,
        senderBusinessId = "seller-co", senderActorId = "actor-s", senderDeviceId = "device-s",
        mailboxId = "orders", mailboxSequence = 2, acceptanceId = "accept-2",
        acceptedAt = 520, acceptedAtSource = TransactionTimestampSource.DeviceLocalProvisional.name,
        ingestedAt = 525, ingestedAtSource = TransactionTimestampSource.DeviceLocalProvisional.name,
        transportState = RecipientInboxTransportState.Received.columnValue,
    )

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

    override suspend fun findById(companyId: String, orderId: String) =
        orders.firstOrNull { it.companyId == companyId && it.orderId == orderId }

    override suspend fun updateState(companyId: String, orderId: String, state: String) {
        val index = orders.indexOfFirst { it.companyId == companyId && it.orderId == orderId }
        if (index >= 0) orders[index] = orders[index].copy(state = state)
    }

    override suspend fun updateVersionStateAndNote(companyId: String, orderId: String, version: Int, state: String, note: String?) {
        val index = orders.indexOfFirst { it.companyId == companyId && it.orderId == orderId }
        if (index >= 0) orders[index] = orders[index].copy(version = version, state = state, note = note)
    }

    override suspend fun deleteLines(companyId: String, orderId: String) {
        lines.removeAll { it.companyId == companyId && it.orderId == orderId }
    }

    override suspend fun findLines(companyId: String, orderId: String) =
        lines.filter { it.companyId == companyId && it.orderId == orderId }.sortedBy { it.lineId }

    fun clearLines() {
        lines.clear()
    }
}

class FakeOrderOutboxDao : OrderOutboxDao {
    val envelopes = mutableListOf<OrderDeliveryEnvelopeEntity>()

    override suspend fun insert(entity: OrderDeliveryEnvelopeEntity) {
        if (envelopes.any { it.companyId == entity.companyId && it.idempotencyKey == entity.idempotencyKey }) {
            throw android.database.SQLException("duplicate idempotency key")
        }
        envelopes += entity
    }

    override suspend fun findByIdempotencyKey(companyId: String, idempotencyKey: String) =
        envelopes.firstOrNull { it.companyId == companyId && it.idempotencyKey == idempotencyKey }

    override suspend fun findPending(companyId: String) =
        envelopes.filter { it.companyId == companyId && it.state in setOf("QUEUED", "RETRYING") }.sortedBy { it.createdAt }

    override suspend fun findPendingBatch(companyId: String, limit: Int) = findPending(companyId).take(limit)

    override suspend fun updateTransportAttempt(
        companyId: String,
        envelopeId: String,
        state: String,
        attemptCount: Int,
        lastAttemptAt: Long,
        lastAttemptAtSource: String,
        lastError: String?,
    ) {
        val index = envelopes.indexOfFirst { it.companyId == companyId && it.envelopeId == envelopeId }
        if (index >= 0) {
            envelopes[index] = envelopes[index].copy(
                state = state,
                attemptCount = attemptCount,
                lastAttemptAt = lastAttemptAt,
                lastAttemptAtSource = lastAttemptAtSource,
                lastError = lastError,
            )
        }
    }
}

class FakeTransactionRecipientInboxDao : StructuredRecipientInboxDao {
    val entries = mutableListOf<StructuredRecipientInboxEntity>()
    override suspend fun insert(entity: StructuredRecipientInboxEntity) { entries += entity }
    override suspend fun findByEnvelopeId(companyId: String, envelopeId: String) =
        entries.firstOrNull { it.companyId == companyId && it.envelopeId == envelopeId }
    override suspend fun findAll(companyId: String) = findPage(companyId, 50, 0)
    override suspend fun findPage(companyId: String, limit: Int, offset: Int) =
        entries.filter { it.companyId == companyId }.sortedBy { it.mailboxSequence }.drop(offset).take(limit)
}

class FakeOrderCommercialEventDao : OrderCommercialEventDao {
    val events = mutableListOf<OrderCommercialEventEntity>()
    override suspend fun insert(entity: OrderCommercialEventEntity) {
        if (events.any { it.companyId == entity.companyId && it.idempotencyKey == entity.idempotencyKey }) {
            throw android.database.SQLException("duplicate idempotency key")
        }
        events += entity
    }
    override suspend fun findByIdempotencyKey(companyId: String, idempotencyKey: String) =
        events.firstOrNull { it.companyId == companyId && it.idempotencyKey == idempotencyKey }
    override suspend fun findByOrderVersionAndType(companyId: String, orderId: String, orderVersion: Int, eventType: String) =
        events.firstOrNull { it.companyId == companyId && it.orderId == orderId && it.orderVersion == orderVersion && it.eventType == eventType }
    override suspend fun findAllForOrderVersion(companyId: String, orderId: String, orderVersion: Int) =
        events.filter { it.companyId == companyId && it.orderId == orderId && it.orderVersion == orderVersion }
}

class FakeOrderVersionArchiveDao : OrderVersionArchiveDao {
    val orders = mutableListOf<OrderVersionArchiveEntity>()
    val lines = mutableListOf<OrderVersionLineArchiveEntity>()
    override suspend fun insertOrder(entity: OrderVersionArchiveEntity) { orders += entity }
    override suspend fun insertLines(entities: List<OrderVersionLineArchiveEntity>) { lines += entities }
    override suspend fun findOrder(companyId: String, orderId: String, version: Int) =
        orders.firstOrNull { it.companyId == companyId && it.orderId == orderId && it.version == version }
    override suspend fun findLines(companyId: String, orderId: String, version: Int) =
        lines.filter { it.companyId == companyId && it.orderId == orderId && it.version == version }.sortedBy { it.lineId }
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
