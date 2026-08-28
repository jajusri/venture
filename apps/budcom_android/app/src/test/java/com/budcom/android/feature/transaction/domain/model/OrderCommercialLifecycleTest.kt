package com.budcom.android.feature.transaction.domain.model

import com.budcom.android.feature.transaction.domain.model.TransactionEntryPointType.Catalogue
import com.budcom.android.feature.transaction.domain.model.TransactionSubmissionType.Estimate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderMaterialChangeClassifierTest {
    private fun order(note: String? = null) = CanonicalOrder(
        companyId = "buyer-co", orderId = "order-1", creationKey = "k", sellerCompanyId = "buyer-co", buyerPartyId = "seller-co",
        state = CanonicalOrderState.Sent, source = Catalogue, submissionType = Estimate, note = note,
        createdAt = TransactionTimestamp(1, TransactionTimestampSource.DeviceLocalProvisional), version = 1,
        lines = listOf(
            CanonicalOrderLine(
                orderId = "order-1", lineId = "line-1", linkedProductId = "p1", snapshotProductName = "Widget",
                snapshotUnit = "Nos", snapshotSku = "SKU-1", quantity = "10", unitPriceAmount = "100",
                unitPriceCurrencyCode = "INR", priceState = TransactionDraftPriceState.ActualPrice("100", "INR"), lineTotalAmount = "1000",
            ),
        ),
    )

    private fun line(quantity: String = "10") = OrderRevisionLineChange(
        lineId = "line-1", linkedProductId = "p1", snapshotProductName = "Widget", snapshotUnit = "Nos", snapshotSku = "SKU-1",
        quantity = quantity, unitPriceAmount = "100", unitPriceCurrencyCode = "INR",
        priceState = TransactionDraftPriceState.ActualPrice("100", "INR"), lineTotalAmount = "1000",
    )

    @Test
    fun `quantity change is material and note-only change is not`() {
        assertTrue(OrderMaterialChangeClassifier.classify(order(), listOf(line("12")), null).isMaterial)
        assertFalse(OrderMaterialChangeClassifier.classify(order(), listOf(line()), null).isMaterial)
        assertFalse(OrderMaterialChangeClassifier.classify(order("Deliver Friday"), listOf(line()), "Deliver Friday").isMaterial)
        assertTrue(OrderMaterialChangeClassifier.classify(order(), listOf(line()), "Need credit terms").isMaterial)
    }
}

class CanonicalOrderLifecycleIntegrityTest {
    @Test
    fun `legal transitions follow sent seen confirmed and revision path`() {
        assertTrue(CanonicalOrderLifecycleIntegrity.isLegalTransition(CanonicalOrderState.Draft, CanonicalOrderState.Sent))
        assertTrue(CanonicalOrderLifecycleIntegrity.isLegalTransition(CanonicalOrderState.Sent, CanonicalOrderState.Seen))
        assertTrue(CanonicalOrderLifecycleIntegrity.isLegalTransition(CanonicalOrderState.Seen, CanonicalOrderState.Confirmed))
        assertTrue(CanonicalOrderLifecycleIntegrity.isLegalTransition(CanonicalOrderState.Seen, CanonicalOrderState.RevisionPending))
        assertTrue(CanonicalOrderLifecycleIntegrity.isLegalTransition(CanonicalOrderState.RevisionPending, CanonicalOrderState.RevisionSent))
        assertTrue(CanonicalOrderLifecycleIntegrity.isLegalTransition(CanonicalOrderState.RevisionSent, CanonicalOrderState.RevisionSeen))
        assertTrue(CanonicalOrderLifecycleIntegrity.isLegalTransition(CanonicalOrderState.RevisionSeen, CanonicalOrderState.Confirmed))
    }

    @Test
    fun `illegal transitions are rejected`() {
        assertTrue(CanonicalOrderLifecycleIntegrity.rejectsIllegalDirectTransition(CanonicalOrderState.Draft, CanonicalOrderState.Seen))
        assertFalse(CanonicalOrderLifecycleIntegrity.rejectsIllegalDirectTransition(CanonicalOrderState.Sent, CanonicalOrderState.Confirmed))
        assertFalse(CanonicalOrderLifecycleIntegrity.isLegalTransition(CanonicalOrderState.Draft, CanonicalOrderState.Confirmed))
    }
}

class OrderConfirmAuthorityTest {
    @Test
    fun `confirm requires confirm_orders capability`() {
        val allowed = OrderConfirmAuthority("seller-co", "actor", "device", setOf("confirm_orders"), 1)
        val denied = OrderConfirmAuthority("seller-co", "actor", "device", setOf("send_orders"), 1)
        assertTrue(allowed.permitsOrderConfirm())
        assertFalse(denied.permitsOrderConfirm())
    }
}

class CanonicalOrderConfirmedTransitionsTest {
    @Test
    fun `seen without confirm stays seen and explicit confirm succeeds`() {
        val order = CanonicalOrder(
            companyId = "buyer-co", orderId = "order-1", creationKey = "k", sellerCompanyId = "buyer-co", buyerPartyId = "seller-co",
            state = CanonicalOrderState.Seen, source = Catalogue, submissionType = Estimate, note = null,
            createdAt = TransactionTimestamp(1, TransactionTimestampSource.DeviceLocalProvisional), version = 1, lines = emptyList(),
        )
        val evidence = OrderConfirmEvidence(
            eventId = "confirm-1", orderId = "order-1", orderVersion = 1, confirmingBusinessId = "seller-co",
            confirmingActorId = "actor-s", confirmingDeviceId = "device-s", senderBusinessId = "buyer-co",
            authorityEpoch = 1, authorityScopeFingerprint = "confirm_orders", confirmedAt = TransactionTimestamp(2, TransactionTimestampSource.DeviceLocalProvisional),
        )
        assertEquals(CanonicalOrderState.Confirmed, CanonicalOrderConfirmedTransitions.apply(order.copy(state = CanonicalOrderState.Sent), evidence))
        assertEquals(CanonicalOrderState.Confirmed, CanonicalOrderConfirmedTransitions.apply(order, evidence))
        assertEquals(CanonicalOrderState.Confirmed, CanonicalOrderConfirmedTransitions.apply(order.copy(state = CanonicalOrderState.Confirmed), evidence))
    }
}
