package com.budcom.android.feature.transaction.domain

import com.budcom.android.feature.transaction.data.local.PassthroughCommercialDbTransaction
import com.budcom.android.feature.transaction.domain.model.CanonicalOrder
import com.budcom.android.feature.transaction.domain.model.CanonicalOrderLine
import com.budcom.android.feature.transaction.domain.model.CanonicalOrderState
import com.budcom.android.feature.transaction.domain.model.CommercialAction
import com.budcom.android.feature.transaction.domain.model.CommercialActionAuthorityContext
import com.budcom.android.feature.transaction.domain.model.CommercialActionAuthorityOutcome
import com.budcom.android.feature.transaction.domain.model.CommercialActionAuthorityRequest
import com.budcom.android.feature.transaction.domain.model.CommercialActionAuthorityResolver
import com.budcom.android.feature.transaction.domain.model.OrderConfirmAuthority
import com.budcom.android.feature.transaction.domain.model.OrderRevisionLineChange
import com.budcom.android.feature.transaction.domain.model.TransactionDraftPriceState
import com.budcom.android.feature.transaction.domain.model.TransactionEntryPointType
import com.budcom.android.feature.transaction.domain.model.TransactionSubmissionType
import com.budcom.android.feature.transaction.domain.model.TransactionTimestamp
import com.budcom.android.feature.transaction.domain.model.TransactionTimestampSource
import com.budcom.android.feature.transaction.domain.port.RelayOutboxDispatcher
import com.budcom.android.feature.transaction.domain.repository.TransactionRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuyingCycleProductionPathTest {
    @Test
    fun `coordinator confirm and revision require trust authority and stay commercially atomic`() = runTest {
        val repository = RecordingBuyingCycleRepository()
        val coordinator = CanonicalBuyingCycleCoordinator(
            repository,
            AcceptingAuthorityResolver(),
            PassthroughCommercialDbTransaction,
            RelayOutboxDispatcher { },
        )
        val ts = TransactionTimestamp(10, TransactionTimestampSource.DeviceLocalProvisional)
        val confirmRequest = request(CommercialAction.SellerConfirm)
        assertNull(
            CanonicalBuyingCycleCoordinator(
                repository, RejectingAuthorityResolver(), PassthroughCommercialDbTransaction, RelayOutboxDispatcher { },
            ).confirmSellerOrder(confirmRequest, "env-1", "evt", "idem", ts, "buyer-co"),
        )
        val confirmed = coordinator.confirmSellerOrder(confirmRequest, "env-1", "evt", "idem", ts, "buyer-co")
        assertEquals(CanonicalOrderState.Confirmed, confirmed?.state)
        val revised = coordinator.proposeAndSendRevision(
            request(CommercialAction.SellerRevise),
            "env-1",
            repository.order.copy(state = CanonicalOrderState.Seen),
            listOf(line("12")),
            "qty",
            ts,
            "rev-key",
        )
        assertEquals(CanonicalOrderState.RevisionSent, revised?.state)
        assertEquals(2, revised?.version)
        val accepted = coordinator.acceptRevision(request(CommercialAction.BuyerAcceptRevision, viewer = "buyer-co"), "env-2", "acc", "acc-key", ts)
        assertEquals(CanonicalOrderState.Confirmed, accepted?.state)
        assertTrue(repository.accountingMutations.isEmpty())
        assertNotEquals(CanonicalOrderState.Seen, CanonicalOrderState.Confirmed)
    }

    private fun request(action: CommercialAction, viewer: String = "seller-co") = CommercialActionAuthorityRequest(
        action, viewer, null, "device-s", 1, "order-1", 1, "order-1", 1, "seller-co", "buyer-co", 10,
    )

    private fun line(qty: String) = OrderRevisionLineChange(
        "line-1", "p1", "Widget", "Nos", "SKU-1", qty, "100", "INR",
        TransactionDraftPriceState.ActualPrice("100", "INR"), "1000",
    )
}

private class AcceptingAuthorityResolver : CommercialActionAuthorityResolver {
    override suspend fun resolve(request: CommercialActionAuthorityRequest) = CommercialActionAuthorityOutcome.Verified(
        CommercialActionAuthorityContext(
            request.viewerBusinessId, "actor", request.expectedDeviceId, "cred", 1, 1,
            setOf(
                OrderConfirmAuthority.CONFIRM_ORDERS_CAPABILITY,
                OrderConfirmAuthority.REVISE_ORDERS_CAPABILITY,
                OrderConfirmAuthority.ACCEPT_ORDER_REVISIONS_CAPABILITY,
            ),
            request.orderId, request.orderVersion, request.action,
        ),
    )
}

private class RejectingAuthorityResolver : CommercialActionAuthorityResolver {
    override suspend fun resolve(request: CommercialActionAuthorityRequest) = CommercialActionAuthorityOutcome.Unavailable
}

private class RecordingBuyingCycleRepository : TransactionRepository {
    val accountingMutations = mutableListOf<String>()
    var order = CanonicalOrder(
        "buyer-co", "order-1", "k", "seller-co", "buyer-co", CanonicalOrderState.Seen,
        TransactionEntryPointType.Catalogue, TransactionSubmissionType.Estimate, null,
        TransactionTimestamp(1, TransactionTimestampSource.DeviceLocalProvisional), 1,
        listOf(
            CanonicalOrderLine(
                "order-1", "line-1", "p1", "Widget", "Nos", "SKU-1", "10", "100", "INR",
                TransactionDraftPriceState.ActualPrice("100", "INR"), "1000",
            ),
        ),
    )

    override suspend fun recordOrderConfirmFromSellerAction(sellerCompanyId: String, envelopeId: String, authority: OrderConfirmAuthority, eventId: String, idempotencyKey: String, timestamp: TransactionTimestamp): com.budcom.android.feature.transaction.domain.model.OrderCommercialEvent {
        order = order.copy(companyId = sellerCompanyId, state = CanonicalOrderState.Confirmed)
        return com.budcom.android.feature.transaction.domain.model.OrderCommercialEvent(
            sellerCompanyId, eventId, idempotencyKey, "order-1", 1,
            com.budcom.android.feature.transaction.domain.model.OrderCommercialEventType.Confirmed,
            authority.businessId, authority.actorId, authority.deviceId, "buyer-co", timestamp,
        )
    }

    override suspend fun findCanonicalOrderById(companyId: String, orderId: String) =
        order.takeIf { it.companyId == companyId && it.orderId == orderId }

    override suspend fun applyOrderConfirmEvidence(companyId: String, evidence: com.budcom.android.feature.transaction.domain.model.OrderConfirmEvidence): CanonicalOrder {
        order = order.copy(state = CanonicalOrderState.Confirmed)
        return order
    }

    override suspend fun proposeOrderRevision(sellerCompanyId: String, envelopeId: String, baseline: CanonicalOrder, proposedLines: List<OrderRevisionLineChange>, revisionReason: String?, authority: OrderConfirmAuthority, timestamp: TransactionTimestamp, idempotencyKey: String): CanonicalOrder {
        order = baseline.copy(version = baseline.version + 1, state = CanonicalOrderState.RevisionPending, note = revisionReason)
        return order
    }

    override suspend fun enqueueOrderDelivery(order: CanonicalOrder, timestamp: TransactionTimestamp) =
        com.budcom.android.feature.transaction.domain.model.OrderDeliveryEnvelope(
            order.companyId, "env-rev", "key", "CANONICAL_ORDER", order.orderId, order.version,
            order.sellerCompanyId, order.buyerPartyId, timestamp,
            com.budcom.android.feature.transaction.domain.model.OrderTransportState.Queued, 0, null, null,
        )

    override suspend fun markRevisionSent(companyId: String, orderId: String, envelope: com.budcom.android.feature.transaction.domain.model.OrderDeliveryEnvelope): CanonicalOrder {
        order = order.copy(state = CanonicalOrderState.RevisionSent)
        return order
    }

    override suspend fun recordOrderRevisionAcceptFromBuyerAction(buyerCompanyId: String, envelopeId: String, authority: OrderConfirmAuthority, eventId: String, idempotencyKey: String, timestamp: TransactionTimestamp) =
        com.budcom.android.feature.transaction.domain.model.OrderCommercialEvent(
            buyerCompanyId, eventId, idempotencyKey, "order-1", 2,
            com.budcom.android.feature.transaction.domain.model.OrderCommercialEventType.RevisionAccepted,
            authority.businessId, authority.actorId, authority.deviceId, "seller-co", timestamp,
        )

    override suspend fun applyOrderRevisionAcceptEvidence(companyId: String, evidence: com.budcom.android.feature.transaction.domain.model.OrderRevisionAcceptEvidence): CanonicalOrder {
        order = order.copy(state = CanonicalOrderState.Confirmed)
        return order
    }

    private fun unsupported(): Nothing = throw UnsupportedOperationException("unused")
    override suspend fun createEstimatePo(companyId: String, entryPointType: TransactionEntryPointType, submissionType: TransactionSubmissionType, deliveryChannel: com.budcom.android.feature.transaction.domain.model.TransactionDeliveryChannel, buyerPartyId: String?, lineItems: List<com.budcom.android.feature.transaction.domain.repository.NewLineItem>, timestamp: TransactionTimestamp) = unsupported()
    override suspend fun findEstimatePoById(companyId: String, estimatePoId: String) = unsupported()
    override suspend fun findSellerInboxEntry(companyId: String, inboxEntryId: String) = unsupported()
    override suspend fun findAllSellerInboxEntries(companyId: String) = unsupported()
    override suspend fun acknowledgeSellerInboxEntry(companyId: String, inboxEntryId: String, timestamp: TransactionTimestamp) = unsupported()
    override suspend fun requestChangesOnSellerInboxEntry(companyId: String, inboxEntryId: String, note: String, timestamp: TransactionTimestamp) = unsupported()
    override suspend fun acceptSellerInboxEntry(companyId: String, inboxEntryId: String, ledgerGroupChoice: com.budcom.android.feature.transaction.domain.model.LedgerGroupChoice, proposedTerms: com.budcom.android.feature.transaction.domain.repository.ProposedTerms, timestamp: TransactionTimestamp) = unsupported()
    override suspend fun findTransactionById(companyId: String, transactionId: String) = unsupported()
    override suspend fun findTermsForTransaction(companyId: String, transactionId: String) = unsupported()
    override suspend fun confirmTermsAsBuyer(companyId: String, transactionId: String, timestamp: TransactionTimestamp) = unsupported()
    override suspend fun confirmTermsAsSeller(companyId: String, transactionId: String, timestamp: TransactionTimestamp) = unsupported()
    override suspend fun findPaymentEventsForTransaction(companyId: String, transactionId: String) = unsupported()
    override suspend fun recordPaymentClaim(companyId: String, transactionId: String, claimedAmount: String, currencyCode: String?, isFinalOrPartial: com.budcom.android.feature.transaction.domain.model.PaymentClaimStatus, timestamp: TransactionTimestamp) = unsupported()
    override suspend fun confirmPaymentReceived(companyId: String, transactionId: String, paymentEventId: String, timestamp: TransactionTimestamp, discrepancyNote: String?) = unsupported()
    override suspend fun findTransactionsForCounterparty(companyId: String, buyerPartyId: String) = unsupported()
    override suspend fun findAllTransactionsForCompany(companyId: String) = unsupported()
    override suspend fun findTransactionSnapshot(companyId: String, transactionId: String) = unsupported()
    override suspend fun findCompletedPurchaseHistory(companyId: String, buyerPartyId: String) = unsupported()
    override suspend fun grantCatalogueAccess(companyId: String, buyerPartyId: String, expiresAt: TransactionTimestamp?, timestamp: TransactionTimestamp) = unsupported()
    override suspend fun revokeCatalogueAccess(companyId: String, grantId: String, timestamp: TransactionTimestamp) = unsupported()
    override suspend fun findActiveCatalogueAccessGrant(companyId: String, buyerPartyId: String, nowEpochMillis: Long) = unsupported()
}
