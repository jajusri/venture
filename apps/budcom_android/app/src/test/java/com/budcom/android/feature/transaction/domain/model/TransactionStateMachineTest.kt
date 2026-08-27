package com.budcom.android.feature.transaction.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SellerInboxTransitionsTest {

    @Test
    fun `New can Acknowledge, RequestChanges, or Accept`() {
        assertEquals(SellerInboxState.Acknowledged, SellerInboxTransitions.transition(SellerInboxState.New, SellerInboxAction.Acknowledge))
        assertEquals(SellerInboxState.ChangesRequested, SellerInboxTransitions.transition(SellerInboxState.New, SellerInboxAction.RequestChanges))
        assertEquals(SellerInboxState.Accepted, SellerInboxTransitions.transition(SellerInboxState.New, SellerInboxAction.Accept))
    }

    @Test
    fun `Acknowledged can still RequestChanges or Accept`() {
        assertEquals(
            SellerInboxState.ChangesRequested,
            SellerInboxTransitions.transition(SellerInboxState.Acknowledged, SellerInboxAction.RequestChanges),
        )
        assertEquals(SellerInboxState.Accepted, SellerInboxTransitions.transition(SellerInboxState.Acknowledged, SellerInboxAction.Accept))
    }

    @Test
    fun `ChangesRequested is a closed record - every action rejected`() {
        SellerInboxAction.entries.forEach { action ->
            assertNull(SellerInboxTransitions.transition(SellerInboxState.ChangesRequested, action))
        }
    }

    @Test
    fun `Accepted is a closed record - every action rejected`() {
        SellerInboxAction.entries.forEach { action ->
            assertNull(SellerInboxTransitions.transition(SellerInboxState.Accepted, action))
        }
    }

    @Test
    fun `Converted is a closed record - every action rejected (no re-conversion)`() {
        SellerInboxAction.entries.forEach { action ->
            assertNull(SellerInboxTransitions.transition(SellerInboxState.Converted, action))
        }
    }

    @Test
    fun `repeated Accept from New is deterministic and idempotent in outcome`() {
        val first = SellerInboxTransitions.transition(SellerInboxState.New, SellerInboxAction.Accept)
        val second = SellerInboxTransitions.transition(SellerInboxState.New, SellerInboxAction.Accept)
        assertEquals(first, second)
    }
}

class CanonicalOrderSentTransitionsTest {
    private fun order(state: CanonicalOrderState = CanonicalOrderState.Draft, version: Int = 1) = CanonicalOrder(
        companyId = "co-1", orderId = "order-1", creationKey = "k", sellerCompanyId = "co-1", buyerPartyId = "buyer-1",
        state = state, source = TransactionEntryPointType.Catalogue, submissionType = TransactionSubmissionType.Estimate,
        note = null, createdAt = TransactionTimestamp(1, TransactionTimestampSource.DeviceLocalProvisional), version = version, lines = emptyList(),
    )
    private fun envelope() = OrderDeliveryEnvelope(
        companyId = "co-1", envelopeId = "env-1", idempotencyKey = "order:order-1:v1", objectType = "CANONICAL_ORDER",
        orderId = "order-1", orderVersion = 1, senderCompanyId = "co-1", recipientPartyId = "buyer-1",
        createdAt = TransactionTimestamp(1, TransactionTimestampSource.DeviceLocalProvisional),
        state = OrderTransportState.RelayAccepted, attemptCount = 1, lastAttemptAt = null, lastError = null,
    )
    private fun evidence() = RelayAcceptanceEvidence(
        "accept-1", "env-1", "CANONICAL_ORDER", "order-1", 1, "co-1", "buyer-1", 10, "relay_accepted",
    )

    @Test
    fun `draft plus matching relay acceptance becomes sent and never seen or delivered`() {
        assertEquals(CanonicalOrderState.Sent, CanonicalOrderSentTransitions.apply(order(), envelope(), evidence()))
        assertEquals(CanonicalOrderState.Sent, CanonicalOrderSentTransitions.apply(order(CanonicalOrderState.Sent), envelope(), evidence()))
        assertNull(CanonicalOrderSentTransitions.apply(order(), envelope(), evidence().copy(objectVersion = 2)))
        assertNull(CanonicalOrderSentTransitions.apply(order(), envelope(), evidence().copy(status = "delivered")))
        assertNull(CanonicalOrderSentTransitions.apply(order(), envelope(), evidence().copy(envelopeId = "other")))
        assertNull(CanonicalOrderSentTransitions.apply(order(), envelope(), evidence().copy(objectType = "CHAT")))
        assertNull(CanonicalOrderSentTransitions.apply(order(), envelope(), evidence().copy(senderBusinessId = "other")))
        assertNull(CanonicalOrderSentTransitions.apply(order(), envelope(), evidence().copy(recipientBusinessId = "other")))
        assertNull(CanonicalOrderSentTransitions.apply(order(), envelope(), evidence().copy(status = "seen")))
    }
}

class TransactionStateDerivationTest {

    private fun ts(millis: Long = 1_000L) = TransactionTimestamp(millis, TransactionTimestampSource.DeviceLocalProvisional)

    private fun terms(
        buyerConfirmedAt: TransactionTimestamp? = null,
        sellerConfirmedAt: TransactionTimestamp? = null,
        paymentTiming: PaymentTiming = PaymentTiming.Advance,
        creditDays: Int? = null,
    ) = TermsAcknowledgment(
        companyId = "co-1", transactionId = "t-1", paymentTiming = paymentTiming, creditDays = creditDays,
        partialAdvancePercent = null, partialBalanceTiming = null, amount = "1000", currencyCode = "INR",
        note = null, proposedAt = ts(), buyerConfirmedAt = buyerConfirmedAt, sellerConfirmedAt = sellerConfirmedAt,
    )

    private fun transaction(
        state: CommercialTransactionState = CommercialTransactionState.PendingConfirmation,
        completedAt: TransactionTimestamp? = null,
        acceptedAt: TransactionTimestamp = ts(0L),
        totalAmount: String = "1000",
    ) = CommercialTransaction(
        companyId = "co-1", transactionId = "t-1", estimatePoId = "e-1", buyerPartyId = "p-1",
        state = state, totalAmount = totalAmount, currencyCode = "INR", acceptedAt = acceptedAt, completedAt = completedAt,
    )

    private fun event(
        amount: String = "1000",
        confirmed: Boolean = true,
        status: PaymentClaimStatus = PaymentClaimStatus.Paid,
    ) = PaymentEvent(
        companyId = "co-1", transactionId = "t-1", paymentEventId = "pe-1", installmentSequence = 1,
        buyerClaimStatus = status, buyerClaimedAmount = amount, currencyCode = "INR", buyerClaimedAt = ts(),
        sellerConfirmed = confirmed, sellerConfirmedAt = if (confirmed) ts() else null, sellerDiscrepancyNote = null,
    )

    // ---- Agreed / mutual agreement asymmetric waiting states ----

    @Test
    fun `not agreed when neither party has confirmed`() {
        assertFalse(TransactionStateDerivation.isAgreed(terms()))
        assertEquals(TransactionStateDerivation.MutualAgreementStatus.WaitingForBoth, TransactionStateDerivation.mutualAgreementStatus(terms()))
    }

    @Test
    fun `waiting for seller when only buyer confirmed`() {
        val t = terms(buyerConfirmedAt = ts())
        assertFalse(TransactionStateDerivation.isAgreed(t))
        assertEquals(TransactionStateDerivation.MutualAgreementStatus.WaitingForSeller, TransactionStateDerivation.mutualAgreementStatus(t))
    }

    @Test
    fun `waiting for buyer when only seller confirmed`() {
        val t = terms(sellerConfirmedAt = ts())
        assertFalse(TransactionStateDerivation.isAgreed(t))
        assertEquals(TransactionStateDerivation.MutualAgreementStatus.WaitingForBuyer, TransactionStateDerivation.mutualAgreementStatus(t))
    }

    @Test
    fun `agreed once both confirm, order independent (buyer then seller)`() {
        val t = terms(buyerConfirmedAt = ts(1), sellerConfirmedAt = ts(2))
        assertTrue(TransactionStateDerivation.isAgreed(t))
        assertEquals(TransactionStateDerivation.MutualAgreementStatus.Agreed, TransactionStateDerivation.mutualAgreementStatus(t))
    }

    @Test
    fun `agreed once both confirm, order independent (seller then buyer)`() {
        val t = terms(sellerConfirmedAt = ts(1), buyerConfirmedAt = ts(2))
        assertTrue(TransactionStateDerivation.isAgreed(t))
    }

    // ---- payment initiation / confirmation ----

    @Test
    fun `no payment events means not initiated and not confirmed`() {
        assertFalse(TransactionStateDerivation.isPaymentInitiated(emptyList()))
        assertFalse(TransactionStateDerivation.isPaymentConfirmed(transaction(), emptyList()))
    }

    @Test
    fun `unconfirmed buyer claim is initiated but never confirmed - no silent upgrade`() {
        val unconfirmed = event(confirmed = false)
        assertTrue(TransactionStateDerivation.isPaymentInitiated(listOf(unconfirmed)))
        assertFalse(TransactionStateDerivation.isPaymentConfirmed(transaction(), listOf(unconfirmed)))
    }

    @Test
    fun `confirmed full amount is payment confirmed`() {
        val confirmed = event(amount = "1000", confirmed = true)
        assertTrue(TransactionStateDerivation.isPaymentConfirmed(transaction(totalAmount = "1000"), listOf(confirmed)))
    }

    @Test
    fun `partial confirmed installments sum toward total, not confirmed until full`() {
        val partial1 = event(amount = "400", confirmed = true).copy(paymentEventId = "pe-1", installmentSequence = 1)
        val partial2 = event(amount = "300", confirmed = true).copy(paymentEventId = "pe-2", installmentSequence = 2)
        assertFalse(TransactionStateDerivation.isPaymentConfirmed(transaction(totalAmount = "1000"), listOf(partial1, partial2)))

        val partial3 = event(amount = "300", confirmed = true).copy(paymentEventId = "pe-3", installmentSequence = 3)
        assertTrue(TransactionStateDerivation.isPaymentConfirmed(transaction(totalAmount = "1000"), listOf(partial1, partial2, partial3)))
    }

    // ---- full deriveState matrix ----

    @Test
    fun `deriveState - pending confirmation when nothing confirmed`() {
        assertEquals(CommercialTransactionState.PendingConfirmation, TransactionStateDerivation.deriveState(transaction(), terms(), emptyList()))
    }

    @Test
    fun `deriveState - agreed once both terms confirmations land, no payment yet`() {
        val t = terms(buyerConfirmedAt = ts(), sellerConfirmedAt = ts())
        assertEquals(CommercialTransactionState.Agreed, TransactionStateDerivation.deriveState(transaction(), t, emptyList()))
    }

    @Test
    fun `deriveState - payment initiated once a claim exists but unconfirmed`() {
        val t = terms(buyerConfirmedAt = ts(), sellerConfirmedAt = ts())
        val e = event(confirmed = false)
        assertEquals(CommercialTransactionState.PaymentInitiated, TransactionStateDerivation.deriveState(transaction(), t, listOf(e)))
    }

    @Test
    fun `deriveState - full seller-confirmed amount completes immediately, per Q17's automatic-completion rule`() {
        // Q17: completion is automatic, firing "the instant" the full amount is confirmed - there
        // is no persisted resting point at PaymentConfirmed distinct from Completed; the moment
        // isPaymentConfirmed becomes true IS the completion trigger (see TransactionStateDerivation.deriveState's
        // own doc comment for why checking completedAt alone would be circular).
        val t = terms(buyerConfirmedAt = ts(), sellerConfirmedAt = ts())
        val e = event(amount = "1000", confirmed = true)
        assertEquals(CommercialTransactionState.Completed, TransactionStateDerivation.deriveState(transaction(), t, listOf(e)))
    }

    @Test
    fun `deriveState - completed is authoritative once completedAt is set, regardless of other facts`() {
        val completedTransaction = transaction(completedAt = ts())
        assertEquals(
            CommercialTransactionState.Completed,
            TransactionStateDerivation.deriveState(completedTransaction, terms(), emptyList()),
        )
    }

    // ---- due date resolution / overdue modifier ----

    @Test
    fun `Advance timing is due immediately at acceptance`() {
        assertEquals(500L, TransactionStateDerivation.resolveDueDateEpochMillis(terms(paymentTiming = PaymentTiming.Advance), 500L))
    }

    @Test
    fun `CreditDays timing resolves to acceptedAt plus N days`() {
        val due = TransactionStateDerivation.resolveDueDateEpochMillis(
            terms(paymentTiming = PaymentTiming.CreditDays, creditDays = 7),
            0L,
        )
        assertEquals(7L * 24 * 60 * 60 * 1000, due)
    }

    @Test
    fun `CreditDays with no creditDays value is unresolved, not a guess`() {
        assertNull(TransactionStateDerivation.resolveDueDateEpochMillis(terms(paymentTiming = PaymentTiming.CreditDays, creditDays = null), 0L))
    }

    @Test
    fun `OnDelivery timing has no computable due date - architecture Risk 6, not guessed`() {
        assertNull(TransactionStateDerivation.resolveDueDateEpochMillis(terms(paymentTiming = PaymentTiming.OnDelivery), 0L))
    }

    @Test
    fun `Partial timing has no computable due date either - refinement of Risk 6 found during implementation`() {
        assertNull(TransactionStateDerivation.resolveDueDateEpochMillis(terms(paymentTiming = PaymentTiming.Partial), 0L))
    }

    @Test
    fun `isOverdue is false before the due date`() {
        val t = transaction(acceptedAt = ts(0L))
        val terms = terms(paymentTiming = PaymentTiming.CreditDays, creditDays = 7)
        val almostDue = 6L * 24 * 60 * 60 * 1000
        assertFalse(TransactionStateDerivation.isOverdue(t, terms, almostDue))
    }

    @Test
    fun `isOverdue is true strictly after the due date`() {
        val t = transaction(acceptedAt = ts(0L))
        val terms = terms(paymentTiming = PaymentTiming.CreditDays, creditDays = 7)
        val pastDue = 8L * 24 * 60 * 60 * 1000
        assertTrue(TransactionStateDerivation.isOverdue(t, terms, pastDue))
    }

    @Test
    fun `isOverdue is never true when no due date is computable (OnDelivery)`() {
        val t = transaction(acceptedAt = ts(0L))
        val terms = terms(paymentTiming = PaymentTiming.OnDelivery)
        assertFalse(TransactionStateDerivation.isOverdue(t, terms, Long.MAX_VALUE))
    }

    @Test
    fun `isOverdue is always false for a Completed transaction regardless of date`() {
        val t = transaction(state = CommercialTransactionState.Completed, completedAt = ts(), acceptedAt = ts(0L))
        val terms = terms(paymentTiming = PaymentTiming.Advance)
        assertFalse(TransactionStateDerivation.isOverdue(t, terms, Long.MAX_VALUE))
    }

    @Test
    fun `Overdue is never a member of CommercialTransactionState - always a modifier`() {
        assertTrue(CommercialTransactionState.entries.none { it.columnValue.contains("OVERDUE") })
    }
}
