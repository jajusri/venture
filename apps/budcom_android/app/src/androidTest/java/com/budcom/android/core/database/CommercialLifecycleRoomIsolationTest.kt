package com.budcom.android.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.budcom.android.core.util.DefaultDispatcherProvider
import com.budcom.android.feature.masterdata.ledger.domain.model.LedgerContactDetails
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
import com.budcom.android.feature.transaction.data.local.RoomCommercialDbTransaction
import com.budcom.android.feature.transaction.data.repository.TransactionRepositoryImpl
import com.budcom.android.feature.transaction.domain.model.AuthenticatedCounterpartyBinding
import com.budcom.android.feature.transaction.domain.model.AuthenticatedCounterpartyBindingRepository
import com.budcom.android.feature.transaction.domain.model.CanonicalOrder
import com.budcom.android.feature.transaction.domain.model.CanonicalOrderState
import com.budcom.android.feature.transaction.domain.model.CommercialAction
import com.budcom.android.feature.transaction.domain.model.CommercialActionAuthorityContext
import com.budcom.android.feature.transaction.domain.model.CommercialActionAuthorityOutcome
import com.budcom.android.feature.transaction.domain.model.CommercialActionAuthorityPolicy
import com.budcom.android.feature.transaction.domain.model.CommercialActionAuthorityRequest
import com.budcom.android.feature.transaction.domain.model.CommercialActionAuthorityResolver
import com.budcom.android.feature.transaction.domain.model.CommercialReturnEvent
import com.budcom.android.feature.transaction.domain.model.COMMERCIAL_EVENT_CONTENT_TYPE
import com.budcom.android.feature.transaction.domain.model.COMMERCIAL_EVENT_CONTENT_VERSION
import com.budcom.android.feature.transaction.domain.model.CounterpartyBindingStatus
import com.budcom.android.feature.transaction.domain.model.OrderCommercialEventType
import com.budcom.android.feature.transaction.domain.model.OrderRevisionLineChange
import com.budcom.android.feature.transaction.domain.model.OrderStructuredOpenEvent
import com.budcom.android.feature.transaction.domain.model.OrderVersionSnapshot
import com.budcom.android.feature.transaction.domain.model.RelayAcceptanceEvidence
import com.budcom.android.feature.transaction.domain.model.TransactionDraftOperations
import com.budcom.android.feature.transaction.domain.model.TransactionDraftPriceState
import com.budcom.android.feature.transaction.domain.model.TransactionSubmissionType
import com.budcom.android.feature.transaction.domain.model.TransactionTimestamp
import com.budcom.android.feature.transaction.domain.model.TransactionTimestampSource
import com.budcom.android.feature.transaction.domain.port.CredentialVerificationRequest
import com.budcom.android.feature.transaction.domain.port.NoOpTransactionReminderScheduler
import com.budcom.android.feature.transaction.domain.port.RelayMailboxDeliveryItem
import com.budcom.android.feature.transaction.domain.port.TransactionSubmissionPort
import com.budcom.android.feature.transaction.domain.port.TransportUnavailableException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Task-6 Room evidence: two on-disk databases stand for two separate Budcom installations
 * (buyer device vs seller device). Production still uses one Room file per installation;
 * these filenames exist only in this harness.
 *
 * Authentication and credential establishment are **bypassed**. Authority/binding ports grant
 * verified outcomes without Trust/keystore/credential verification. This class does **not**
 * prove authentication. Production verifier/authority tests cover that path separately.
 *
 * Canonical [buyerBusinessId] / [sellerBusinessId] are never rewritten. Roles come from
 * create + binding on the buyer, then from [OrderVersionSnapshot] / [CommercialReturnEvent]
 * encode/parse on the wire.
 */
@RunWith(AndroidJUnit4::class)
class CommercialLifecycleRoomIsolationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var buyer: IsolatedBusinessPersistence
    private lateinit var seller: IsolatedBusinessPersistence

    @Before
    fun setUp() {
        context.deleteDatabase(BUYER_FILE)
        context.deleteDatabase(SELLER_FILE)
        buyer = IsolatedBusinessPersistence.open(context, BUYER_FILE, BUYER_BUSINESS)
        seller = IsolatedBusinessPersistence.open(context, SELLER_FILE, SELLER_BUSINESS)
    }

    @After
    fun tearDown() {
        if (::buyer.isInitialized) buyer.closeQuietly()
        if (::seller.isInitialized) seller.closeQuietly()
        context.deleteDatabase(BUYER_FILE)
        context.deleteDatabase(SELLER_FILE)
    }

    @Test
    fun twoInstallationsSurviveLifecycleRestartIdempotencyAndStaleReplay() = runBlocking {
        val draft = TransactionDraftOperations.addOrIncrementLine(
            TransactionDraftOperations.empty(BUYER_BUSINESS, SELLER_PARTY, TransactionSubmissionType.Estimate),
            "product-1", "Widget", "Nos", "SKU-1",
            TransactionDraftPriceState.ActualPrice("100", "INR"), "10",
        )
        val created = buyer.repo.createDraftOrder(
            draft, "task6-create", timestamp = ts(100),
            authorityRequest = creationAuthority(),
        )
        val retriedCreate = buyer.repo.createDraftOrder(
            draft, "task6-create", timestamp = ts(101),
            authorityRequest = creationAuthority(),
        )
        assertEquals(created.orderId, retriedCreate.orderId)
        assertEquals(1, created.version)
        assertRoles(created)
        assertEquals(BUYER_BUSINESS, created.companyId)
        assertNull(seller.repo.findCanonicalOrderById(SELLER_BUSINESS, created.orderId))
        assertNull(buyer.repo.findCanonicalOrderById(SELLER_BUSINESS, created.orderId))

        val queued = buyer.repo.enqueueOrderDelivery(created, ts(200))
        val retriedQueue = buyer.repo.enqueueOrderDelivery(created, ts(201))
        assertEquals(queued.envelopeId, retriedQueue.envelopeId)
        val sent = buyer.repo.markOrderSentFromRelayEvidence(
            BUYER_BUSINESS, queued,
            RelayAcceptanceEvidence(
                acceptanceId = "relay-order-v1", envelopeId = queued.envelopeId,
                objectType = queued.objectType, objectId = created.orderId, objectVersion = 1,
                senderBusinessId = created.sellerCompanyId,
                recipientBusinessId = requireNotNull(created.buyerPartyId),
                acceptedAtEpochMillis = 210, status = "relay_accepted",
            ),
        )
        assertEquals(CanonicalOrderState.Sent, sent!!.state)
        assertRoles(sent)

        val v1Encoded = OrderVersionSnapshot.fromCanonicalOrder(
            buyer.repo.findCanonicalOrderById(BUYER_BUSINESS, created.orderId)!!,
            SELLER_BUSINESS, queued.envelopeId,
        ).deterministicEncoding()
        val v1Snapshot = requireNotNull(OrderVersionSnapshot.parse(v1Encoded))
        assertEquals(BUYER_BUSINESS, v1Snapshot.buyerBusinessId)
        assertEquals(SELLER_BUSINESS, v1Snapshot.sellerBusinessId)
        val v1Item = orderMailboxItem(v1Snapshot, BUYER_ACTOR, BUYER_DEVICE, mailboxSequence = 1)
        assertTrue(seller.repo.ingestReceivedOrderVersion(SELLER_BUSINESS, v1Item, v1Snapshot, ts(220)))
        assertTrue(seller.repo.ingestReceivedOrderVersion(SELLER_BUSINESS, v1Item, v1Snapshot, ts(221)))
        val sellerV1 = seller.repo.findCanonicalOrderById(SELLER_BUSINESS, created.orderId)!!
        assertEquals(CanonicalOrderState.Sent, sellerV1.state)
        assertEquals("10", sellerV1.lines.single().quantity)
        assertRoles(sellerV1)
        assertNull(seller.repo.findCanonicalOrderById(BUYER_BUSINESS, created.orderId))
        assertTrue(
            runCatching {
                val tampered = v1Snapshot.copy(lines = listOf(v1Snapshot.lines.single().copy(quantity = "99")))
                seller.repo.ingestReceivedOrderVersion(
                    SELLER_BUSINESS,
                    v1Item.copy(commercialSnapshotCanonical = tampered.deterministicEncoding()),
                    tampered,
                    ts(222),
                )
            }.isFailure,
        )
        assertEquals("10", seller.repo.findCanonicalOrderById(SELLER_BUSINESS, created.orderId)!!.lines.single().quantity)

        val sellerOpen = OrderStructuredOpenEvent(
            eventId = "seen-v1", idempotencyKey = "seen:${created.orderId}:v1:$SELLER_BUSINESS",
            orderId = created.orderId, orderVersion = 1, objectType = "CANONICAL_ORDER",
            viewerBusinessId = SELLER_BUSINESS, viewerActorId = SELLER_ACTOR, viewerDeviceId = SELLER_DEVICE,
            senderBusinessId = BUYER_BUSINESS, openedAt = ts(300),
        )
        val seen1 = seller.repo.recordOrderSeenFromOpenEvent(
            SELLER_BUSINESS, queued.envelopeId, sellerOpen, seenAuthority(sellerOpen),
        )!!
        val seenRetry = seller.repo.recordOrderSeenFromOpenEvent(
            SELLER_BUSINESS, queued.envelopeId, sellerOpen, seenAuthority(sellerOpen),
        )!!
        assertEquals(seen1.eventId, seenRetry.eventId)
        assertEquals(1, seller.eventCount(created.orderId, 1, OrderCommercialEventType.Seen))
        assertRoles(seller.repo.findCanonicalOrderById(SELLER_BUSINESS, created.orderId)!!)

        val seenReturn = commercialMailboxItem(seller, "return:${sellerOpen.idempotencyKey}", SELLER_BUSINESS, mailboxSequence = 10)
        assertTrue(buyer.repo.ingestReceivedCommercialEvent(BUYER_BUSINESS, seenReturn.item, seenReturn.event, ts(310)))
        assertTrue(buyer.repo.ingestReceivedCommercialEvent(BUYER_BUSINESS, seenReturn.item, seenReturn.event, ts(311)))
        assertEquals(CanonicalOrderState.Seen, buyer.repo.findCanonicalOrderById(BUYER_BUSINESS, created.orderId)!!.state)
        assertEquals(1, buyer.eventCount(created.orderId, 1, OrderCommercialEventType.Seen))
        assertRoles(buyer.repo.findCanonicalOrderById(BUYER_BUSINESS, created.orderId)!!)
        assertFalse(
            buyer.repo.ingestReceivedCommercialEvent(
                BUYER_BUSINESS,
                seenReturn.item.copy(envelopeId = "commercial:seen-conflict"),
                seenReturn.event.copy(idempotencyKey = "seen:changed"),
                ts(312),
            ),
        )
        assertEquals(CanonicalOrderState.Seen, buyer.repo.findCanonicalOrderById(BUYER_BUSINESS, created.orderId)!!.state)
        assertEquals(1, buyer.eventCount(created.orderId, 1, OrderCommercialEventType.Seen))

        reopenBoth()
        val afterSeenRestartBuyer = buyer.repo.findCanonicalOrderById(BUYER_BUSINESS, created.orderId)!!
        val afterSeenRestartSeller = seller.repo.findCanonicalOrderById(SELLER_BUSINESS, created.orderId)!!
        assertEquals(CanonicalOrderState.Seen, afterSeenRestartBuyer.state)
        assertEquals(1, afterSeenRestartBuyer.version)
        assertRoles(afterSeenRestartBuyer)
        assertRoles(afterSeenRestartSeller)
        assertEquals(1, buyer.eventCount(created.orderId, 1, OrderCommercialEventType.Seen))
        assertEquals(1, seller.eventCount(created.orderId, 1, OrderCommercialEventType.Seen))
        assertNotNull(seller.repo.findCanonicalOrderById(SELLER_BUSINESS, created.orderId))
        assertNull(seller.repo.findCanonicalOrderById(BUYER_BUSINESS, created.orderId))

        val confirmAuthority = actionAuthority(
            CommercialAction.SellerConfirm, SELLER_BUSINESS, SELLER_ACTOR, SELLER_DEVICE, created.orderId, 1,
        )
        val confirm1 = seller.repo.recordOrderConfirmFromSellerAction(
            SELLER_BUSINESS, queued.envelopeId, confirmAuthority, "confirm-v1", "confirm:${created.orderId}:v1", ts(400),
        )!!
        val confirmRetry = seller.repo.recordOrderConfirmFromSellerAction(
            SELLER_BUSINESS, queued.envelopeId, confirmAuthority, "confirm-v1", "confirm:${created.orderId}:v1", ts(401),
        )!!
        assertEquals(confirm1.eventId, confirmRetry.eventId)
        assertEquals(CanonicalOrderState.Confirmed, seller.repo.findCanonicalOrderById(SELLER_BUSINESS, created.orderId)!!.state)
        assertEquals(1, seller.eventCount(created.orderId, 1, OrderCommercialEventType.Confirmed))
        val confirmReturn = commercialMailboxItem(seller, "return:confirm:${created.orderId}:v1", SELLER_BUSINESS, mailboxSequence = 11)
        assertTrue(buyer.repo.ingestReceivedCommercialEvent(BUYER_BUSINESS, confirmReturn.item, confirmReturn.event, ts(410)))
        assertTrue(buyer.repo.ingestReceivedCommercialEvent(BUYER_BUSINESS, confirmReturn.item, confirmReturn.event, ts(411)))
        assertEquals(CanonicalOrderState.Confirmed, buyer.repo.findCanonicalOrderById(BUYER_BUSINESS, created.orderId)!!.state)
        assertEquals(1, buyer.eventCount(created.orderId, 1, OrderCommercialEventType.Confirmed))
        assertRoles(buyer.repo.findCanonicalOrderById(BUYER_BUSINESS, created.orderId)!!)
        assertFalse(buyer.repo.ingestReceivedCommercialEvent(BUYER_BUSINESS, seenReturn.item, seenReturn.event, ts(412)))
        assertEquals(CanonicalOrderState.Confirmed, buyer.repo.findCanonicalOrderById(BUYER_BUSINESS, created.orderId)!!.state)

        val sellerBaseline = seller.repo.findCanonicalOrderById(SELLER_BUSINESS, created.orderId)!!
        val line = sellerBaseline.lines.single()
        val revised = seller.repo.proposeOrderRevision(
            SELLER_BUSINESS, queued.envelopeId, sellerBaseline,
            listOf(
                OrderRevisionLineChange(
                    line.lineId, line.linkedProductId, line.snapshotProductName, line.snapshotUnit, line.snapshotSku,
                    "12", line.unitPriceAmount, line.unitPriceCurrencyCode, line.priceState, "1200",
                ),
            ),
            "Need 12 units",
            actionAuthority(CommercialAction.SellerRevise, SELLER_BUSINESS, SELLER_ACTOR, SELLER_DEVICE, created.orderId, 1),
            ts(500), "revision:${created.orderId}:v2",
        )!!
        assertEquals(2, revised.version)
        assertEquals(CanonicalOrderState.RevisionPending, revised.state)
        assertEquals("12", revised.lines.single().quantity)
        assertRoles(revised)
        val revisionEnvelope = seller.repo.enqueueOrderDelivery(revised, ts(510))
        seller.repo.markRevisionSent(SELLER_BUSINESS, created.orderId, revisionEnvelope)
        val v2Encoded = OrderVersionSnapshot.fromCanonicalOrder(
            seller.repo.findCanonicalOrderById(SELLER_BUSINESS, created.orderId)!!,
            BUYER_BUSINESS, revisionEnvelope.envelopeId,
        ).deterministicEncoding()
        val v2Snapshot = requireNotNull(OrderVersionSnapshot.parse(v2Encoded))
        assertEquals(2, v2Snapshot.orderVersion)
        assertEquals(BUYER_BUSINESS, v2Snapshot.buyerBusinessId)
        assertEquals(SELLER_BUSINESS, v2Snapshot.sellerBusinessId)
        val v2Item = orderMailboxItem(v2Snapshot, SELLER_ACTOR, SELLER_DEVICE, mailboxSequence = 2)
        assertTrue(buyer.repo.ingestReceivedOrderVersion(BUYER_BUSINESS, v2Item, v2Snapshot, ts(520)))
        assertTrue(buyer.repo.ingestReceivedOrderVersion(BUYER_BUSINESS, v2Item, v2Snapshot, ts(521)))
        val buyerV2 = buyer.repo.findCanonicalOrderById(BUYER_BUSINESS, created.orderId)!!
        assertEquals(2, buyerV2.version)
        assertEquals("12", buyerV2.lines.single().quantity)
        assertEquals(CanonicalOrderState.RevisionSent, buyerV2.state)
        assertRoles(buyerV2)
        assertEquals("10", buyer.repo.findArchivedOrderVersion(BUYER_BUSINESS, created.orderId, 1)!!.lines.single().quantity)
        assertFalse(buyer.repo.ingestReceivedOrderVersion(BUYER_BUSINESS, v1Item, v1Snapshot, ts(522)))
        val staleSellerV1 = requireNotNull(
            OrderVersionSnapshot.parse(
                OrderVersionSnapshot.fromCanonicalOrder(
                    requireNotNull(seller.repo.findArchivedOrderVersion(SELLER_BUSINESS, created.orderId, 1)),
                    BUYER_BUSINESS,
                    "stale-seller-v1",
                ).deterministicEncoding(),
            ),
        )
        assertTrue(
            buyer.repo.ingestReceivedOrderVersion(
                BUYER_BUSINESS,
                orderMailboxItem(staleSellerV1, SELLER_ACTOR, SELLER_DEVICE, mailboxSequence = 99),
                staleSellerV1,
                ts(523),
            ),
        )
        val afterStaleV1 = buyer.repo.findCanonicalOrderById(BUYER_BUSINESS, created.orderId)!!
        assertEquals(2, afterStaleV1.version)
        assertEquals("12", afterStaleV1.lines.single().quantity)
        assertEquals(CanonicalOrderState.RevisionSent, afterStaleV1.state)

        val buyerOpen = OrderStructuredOpenEvent(
            eventId = "seen-v2", idempotencyKey = "seen:${created.orderId}:v2:$BUYER_BUSINESS",
            orderId = created.orderId, orderVersion = 2, objectType = "CANONICAL_ORDER",
            viewerBusinessId = BUYER_BUSINESS, viewerActorId = BUYER_ACTOR, viewerDeviceId = BUYER_DEVICE,
            senderBusinessId = SELLER_BUSINESS, openedAt = ts(530),
        )
        buyer.repo.recordOrderSeenFromOpenEvent(BUYER_BUSINESS, revisionEnvelope.envelopeId, buyerOpen, seenAuthority(buyerOpen))
        assertEquals(CanonicalOrderState.RevisionSeen, buyer.repo.findCanonicalOrderById(BUYER_BUSINESS, created.orderId)!!.state)
        val acceptAuthority = actionAuthority(
            CommercialAction.BuyerAcceptRevision, BUYER_BUSINESS, BUYER_ACTOR, BUYER_DEVICE, created.orderId, 2,
        )
        val accepted = buyer.repo.recordOrderRevisionAcceptFromBuyerAction(
            BUYER_BUSINESS, revisionEnvelope.envelopeId, acceptAuthority,
            "accept-v2", "accept:${created.orderId}:v2:$BUYER_BUSINESS", ts(600),
        )!!
        val acceptRetry = buyer.repo.recordOrderRevisionAcceptFromBuyerAction(
            BUYER_BUSINESS, revisionEnvelope.envelopeId, acceptAuthority,
            "accept-v2", "accept:${created.orderId}:v2:$BUYER_BUSINESS", ts(601),
        )!!
        assertEquals(accepted.eventId, acceptRetry.eventId)
        assertEquals(CanonicalOrderState.Confirmed, buyer.repo.findCanonicalOrderById(BUYER_BUSINESS, created.orderId)!!.state)
        assertEquals(1, buyer.eventCount(created.orderId, 2, OrderCommercialEventType.RevisionAccepted))
        val acceptReturn = commercialMailboxItem(
            buyer, "return:accept:${created.orderId}:v2:$BUYER_BUSINESS", BUYER_BUSINESS, mailboxSequence = 12,
        )
        assertTrue(seller.repo.ingestReceivedCommercialEvent(SELLER_BUSINESS, acceptReturn.item, acceptReturn.event, ts(610)))
        assertTrue(seller.repo.ingestReceivedCommercialEvent(SELLER_BUSINESS, acceptReturn.item, acceptReturn.event, ts(611)))
        assertEquals(CanonicalOrderState.Confirmed, seller.repo.findCanonicalOrderById(SELLER_BUSINESS, created.orderId)!!.state)
        assertEquals(1, seller.eventCount(created.orderId, 2, OrderCommercialEventType.RevisionAccepted))
        assertFalse(
            seller.repo.ingestReceivedCommercialEvent(
                SELLER_BUSINESS,
                acceptReturn.item.copy(envelopeId = "return-stale-v1", objectVersion = 1),
                acceptReturn.event.copy(eventId = "accept-stale", idempotencyKey = "accept:stale", orderVersion = 1),
                ts(612),
            ),
        )
        assertEquals(CanonicalOrderState.Confirmed, seller.repo.findCanonicalOrderById(SELLER_BUSINESS, created.orderId)!!.state)
        assertEquals(2, seller.repo.findCanonicalOrderById(SELLER_BUSINESS, created.orderId)!!.version)
        assertRoles(seller.repo.findCanonicalOrderById(SELLER_BUSINESS, created.orderId)!!)
        assertRoles(buyer.repo.findCanonicalOrderById(BUYER_BUSINESS, created.orderId)!!)

        reopenBoth()
        val finalBuyer = buyer.repo.findCanonicalOrderById(BUYER_BUSINESS, created.orderId)!!
        val finalSeller = seller.repo.findCanonicalOrderById(SELLER_BUSINESS, created.orderId)!!
        assertEquals(CanonicalOrderState.Confirmed, finalBuyer.state)
        assertEquals(CanonicalOrderState.Confirmed, finalSeller.state)
        assertEquals(2, finalBuyer.version)
        assertEquals(2, finalSeller.version)
        assertEquals("12", finalBuyer.lines.single().quantity)
        assertEquals("12", finalSeller.lines.single().quantity)
        assertRoles(finalBuyer)
        assertRoles(finalSeller)
        assertEquals(1, buyer.eventCount(created.orderId, 1, OrderCommercialEventType.Seen))
        assertEquals(1, buyer.eventCount(created.orderId, 1, OrderCommercialEventType.Confirmed))
        assertEquals(1, buyer.eventCount(created.orderId, 2, OrderCommercialEventType.RevisionAccepted))
        assertEquals(1, seller.eventCount(created.orderId, 1, OrderCommercialEventType.Seen))
        assertEquals(1, seller.eventCount(created.orderId, 1, OrderCommercialEventType.Confirmed))
        assertEquals(1, seller.eventCount(created.orderId, 2, OrderCommercialEventType.RevisionAccepted))
        assertEquals("10", buyer.repo.findArchivedOrderVersion(BUYER_BUSINESS, created.orderId, 1)!!.lines.single().quantity)
        assertNull(buyer.repo.findCanonicalOrderById(SELLER_BUSINESS, created.orderId))
        assertNull(seller.repo.findCanonicalOrderById(BUYER_BUSINESS, created.orderId))
    }

    private fun reopenBoth() {
        buyer.closeQuietly()
        seller.closeQuietly()
        buyer = IsolatedBusinessPersistence.open(context, BUYER_FILE, BUYER_BUSINESS)
        seller = IsolatedBusinessPersistence.open(context, SELLER_FILE, SELLER_BUSINESS)
    }

    private suspend fun IsolatedBusinessPersistence.eventCount(
        orderId: String,
        version: Int,
        type: OrderCommercialEventType,
    ): Int = db.orderCommercialEventDao().findAllForOrderVersion(localBusinessId, orderId, version)
        .count { it.eventType == type.columnValue }

    private companion object {
        const val BUYER_FILE = "buyer-task6.db"
        const val SELLER_FILE = "seller-task6.db"
        const val BUYER_BUSINESS = "buyer-co"
        const val SELLER_BUSINESS = "seller-co"
        const val SELLER_PARTY = "seller-party"
        const val BUYER_ACTOR = "actor-b"
        const val BUYER_DEVICE = "device-b"
        const val SELLER_ACTOR = "actor-s"
        const val SELLER_DEVICE = "device-s"

        fun ts(millis: Long) = TransactionTimestamp(millis, TransactionTimestampSource.DeviceLocalProvisional)

        fun assertRoles(order: CanonicalOrder) {
            assertEquals(BUYER_BUSINESS, order.buyerBusinessId)
            assertEquals(SELLER_BUSINESS, order.sellerBusinessId)
        }

        fun creationAuthority() = CommercialActionAuthorityRequest(
            CommercialAction.BuyerCreateOrder, BUYER_BUSINESS, BUYER_ACTOR, BUYER_DEVICE, 1,
            "", 0, "", 0, SELLER_BUSINESS, BUYER_BUSINESS, 100,
        )

        fun seenAuthority(open: OrderStructuredOpenEvent) = CommercialActionAuthorityRequest(
            CommercialAction.ReturnSeen, open.viewerBusinessId, open.viewerActorId, open.viewerDeviceId, 1,
            open.orderId, open.orderVersion, open.orderId, open.orderVersion,
            SELLER_BUSINESS, BUYER_BUSINESS, open.openedAt.epochMillis,
        )

        fun actionAuthority(
            action: CommercialAction,
            viewer: String,
            actor: String,
            device: String,
            orderId: String,
            version: Int,
        ) = CommercialActionAuthorityRequest(
            action, viewer, actor, device, 1, orderId, version, orderId, version,
            SELLER_BUSINESS, BUYER_BUSINESS, 600,
        )
    }
}

private class IsolatedBusinessPersistence(
    val fileName: String,
    val localBusinessId: String,
    val db: AppDatabase,
    val repo: TransactionRepositoryImpl,
) {
    fun closeQuietly() {
        runCatching { db.close() }
    }

    companion object {
        fun open(context: Context, fileName: String, localBusinessId: String): IsolatedBusinessPersistence {
            val db = Room.databaseBuilder(context, AppDatabase::class.java, fileName).build()
            return IsolatedBusinessPersistence(
                fileName, localBusinessId, db,
                TransactionRepositoryImpl(
                    db.estimatePoDao(), db.estimatePoLineItemDao(), db.sellerInboxEntryDao(),
                    db.commercialTransactionDao(), db.termsAcknowledgmentDao(), db.paymentEventDao(),
                    db.ledgerIntentDao(), db.catalogueAccessGrantDao(),
                    object : TransactionSubmissionPort {
                        override suspend fun deliverToSellerInbox(companyId: String, estimatePoId: String) =
                            throw TransportUnavailableException("unused")
                    },
                    NoOpTransactionReminderScheduler(),
                    UnusedPartyRepository,
                    DefaultDispatcherProvider(),
                    db.canonicalOrderDao(), db.orderOutboxDao(), db.structuredRecipientInboxDao(),
                    db.orderCommercialEventDao(), db.orderVersionArchiveDao(),
                    RoomCommercialDbTransaction(db),
                    HarnessBindingRepository,
                    HarnessAuthorityResolver,
                ),
            )
        }
    }
}

private fun orderMailboxItem(
    snapshot: OrderVersionSnapshot,
    senderActorId: String,
    senderDeviceId: String,
    mailboxSequence: Long,
) = RelayMailboxDeliveryItem(
    snapshot.envelopeId, mailboxSequence, "CANONICAL_ORDER", snapshot.orderId, snapshot.orderVersion,
    snapshot.senderBusinessId, senderActorId, senderDeviceId, snapshot.recipientBusinessId, "orders",
    "relay_accepted", snapshot.createdAtEpochMillis, "accept-${snapshot.envelopeId}", byteArrayOf(1),
    snapshot.deterministicEncoding(), "application/vnd.budcom.order-snapshot+json",
    OrderVersionSnapshot.CURRENT_CONTRACT_VERSION,
)

private data class ParsedCommercialDelivery(
    val item: RelayMailboxDeliveryItem,
    val event: CommercialReturnEvent,
)

private fun commercialMailboxItem(
    source: IsolatedBusinessPersistence,
    idempotencyKey: String,
    senderBusinessId: String,
    mailboxSequence: Long,
): ParsedCommercialDelivery {
    val envelope = runBlocking { source.db.orderOutboxDao().findByIdempotencyKey(source.localBusinessId, idempotencyKey) }
        ?: error("missing outbox $idempotencyKey")
    val canonical = requireNotNull(envelope.commercialContentCanonical)
    val event = requireNotNull(CommercialReturnEvent.parse(canonical))
    val senderActor = if (senderBusinessId == "seller-co") "actor-s" else "actor-b"
    val senderDevice = if (senderBusinessId == "seller-co") "device-s" else "device-b"
    val item = RelayMailboxDeliveryItem(
        envelope.envelopeId, mailboxSequence, "COMMERCIAL_EVENT", envelope.orderId, envelope.orderVersion,
        senderBusinessId, senderActor, senderDevice, requireNotNull(envelope.recipientBusinessId), "orders",
        "relay_accepted", envelope.createdAt, "accept-${envelope.envelopeId}", byteArrayOf(2),
        canonical, envelope.commercialContentType ?: COMMERCIAL_EVENT_CONTENT_TYPE,
        envelope.commercialContentVersion ?: COMMERCIAL_EVENT_CONTENT_VERSION,
    )
    return ParsedCommercialDelivery(item, event)
}

/**
 * Grants Verified authority without credentials. Persistence-only harness; not an authentication proof.
 */
private object HarnessAuthorityResolver : CommercialActionAuthorityResolver {
    override suspend fun resolve(request: CommercialActionAuthorityRequest): CommercialActionAuthorityOutcome {
        val actor = request.expectedActorId ?: return CommercialActionAuthorityOutcome.Unavailable
        return CommercialActionAuthorityOutcome.Verified(
            CommercialActionAuthorityContext(
                request.viewerBusinessId, actor, request.expectedDeviceId, "harness-credential", 1, 1,
                setOfNotNull(CommercialActionAuthorityPolicy.requiredScope(request.action)),
                request.orderId, request.orderVersion, request.action,
            ),
        )
    }
}

/**
 * Maps the seller Party id to the seller business id. Does not rewrite order role fields.
 */
private object HarnessBindingRepository : AuthenticatedCounterpartyBindingRepository {
    override suspend fun verifyAndRecord(
        localBusinessId: String,
        partyId: String,
        verificationRequest: CredentialVerificationRequest,
        verifiedAtEpochMillis: Long,
    ) = null

    override suspend fun resolveActive(localBusinessId: String, partyId: String) =
        if (localBusinessId == "buyer-co" && partyId == "seller-party") {
            AuthenticatedCounterpartyBinding(
                localBusinessId, partyId, "seller-co", "actor-b", "device-b", 1, "harness-binding",
                CounterpartyBindingStatus.Active, 0,
            )
        } else {
            null
        }

    override suspend fun revoke(localBusinessId: String, partyId: String) = Unit
}

private object UnusedPartyRepository : PartyRepository {
    private fun unused(): Nothing = throw UnsupportedOperationException("PartyRepository is unused by this persistence harness")
    override suspend fun getPartyById(companyId: String, partyId: String): Party? = unused()
    override suspend fun getPartyForLedger(companyId: String, ledgerId: String): Party? = unused()
    override suspend fun listByClassification(companyId: String, classification: PartyClassification, page: Int, pageSize: Int): PartyPage = unused()
    override suspend fun searchParties(companyId: String, query: String, classification: PartyClassification?, page: Int, pageSize: Int): PartyPage = unused()
    override suspend fun getContactPersons(companyId: String, partyId: String): List<PartyContactPerson> = unused()
    override suspend fun getTagsForParty(companyId: String, partyId: String): List<Tag> = unused()
    override suspend fun getSourceLinksForCompany(companyId: String): List<PartySourceLink> = unused()
    override suspend fun getTagsForCompany(companyId: String): Map<String, List<Tag>> = unused()
    override suspend fun getFieldProvenance(companyId: String, partyId: String): List<PartyFieldProvenance> = unused()
    override suspend fun updateBudcomOnlyField(companyId: String, partyId: String, fieldName: String, value: String?): FieldProvenanceState = unused()
    override suspend fun confirmFieldFromTally(companyId: String, partyId: String, fieldName: String, tallyValue: String?): FieldProvenanceState = unused()
    override suspend fun reconcilePartiesFromEligibleLedgers(companyId: String, seeds: List<EligibleLedgerSeed>): List<Party> = unused()
    override suspend fun applyLedgerContactDetailsBulk(companyId: String, items: List<LedgerContactDetails>): BulkContactSeedResult = unused()
    override suspend fun createProspect(companyId: String, draft: ProspectDraft): Party = unused()
    override suspend fun getSourceLinkForParty(companyId: String, partyId: String): PartySourceLink? = unused()
    override suspend fun upsertContactPerson(companyId: String, partyId: String, contactPersonId: String?, name: String, designation: String?, mobile: String?, whatsappNumber: String?, email: String?, isPrimary: Boolean): PartyContactPerson = unused()
    override suspend fun deleteContactPerson(companyId: String, contactPersonId: String) = unused()
    override suspend fun getAllTags(): List<Tag> = unused()
    override suspend fun createOrGetTag(name: String, parentTagId: String?): Tag = unused()
    override suspend fun assignTag(companyId: String, partyId: String, tagId: String) = unused()
    override suspend fun unassignTag(companyId: String, partyId: String, tagId: String) = unused()
    override suspend fun addNote(companyId: String, partyId: String, body: String, linkedVoucherId: String?, type: NoteType, dueAt: Long?, issueId: String?): PartyNote = unused()
    override suspend fun editNote(companyId: String, noteId: String, body: String, type: NoteType, dueAt: Long?, issueId: String?): PartyNote? = unused()
    override suspend fun setNoteCompletion(companyId: String, noteId: String, completedAt: Long?): PartyNote? = unused()
    override suspend fun deleteNote(companyId: String, noteId: String) = unused()
    override suspend fun getNotesForParty(companyId: String, partyId: String, page: Int, pageSize: Int): PartyNotePage = unused()
    override suspend fun getTimelineForParty(companyId: String, partyId: String, page: Int, pageSize: Int, issueId: String?): TimelineEntryPage = unused()
    override suspend fun createIssue(companyId: String, partyId: String, title: String): PartyIssue = unused()
    override suspend fun resolveIssue(companyId: String, issueId: String): PartyIssue? = unused()
    override suspend fun reopenIssue(companyId: String, issueId: String): PartyIssue? = unused()
    override suspend fun getIssuesForParty(companyId: String, partyId: String): List<PartyIssue> = unused()
    override suspend fun getIssueActivitySummary(companyId: String, partyId: String): Map<String, IssueActivitySummary> = unused()
    override suspend fun getExportCandidates(companyId: String, partyId: String): List<TallyFieldExportCandidate> = unused()
    override suspend fun recordExport(companyId: String, partyId: String, outputFileName: String, fieldNames: List<String>): PartyExportEvent = unused()
    override suspend fun reconcileExportedFieldFromTally(companyId: String, partyId: String, fieldName: String, tallyRawValue: String?): FieldProvenanceState = unused()
    override suspend fun getExportHistory(companyId: String, partyId: String, limit: Int): List<PartyExportEvent> = unused()
    override suspend fun promoteProspectToCustomer(companyId: String, partyId: String): Party? = unused()
}
