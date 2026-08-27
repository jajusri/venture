package com.budcom.android.feature.transaction.domain.model

/**
 * Transaction Mode state derivation/transition logic
 * (docs/architecture/BUDCOM-TRANSACTION-MODE-ARCHITECTURE.md §5, §8).
 *
 * Follows the exact discipline
 * [com.budcom.android.feature.catalogue.domain.model.CatalogueLifecycleTransitions] already
 * established: pure functions, `null`/`false` for an invalid transition rather than throwing or
 * silently no-op'ing, so callers (the repository) turn a rejected transition into an honest,
 * user-visible outcome. Every function here is a plain function over plain values — no
 * DAO/repository/coroutine dependency — so it is testable without any fake infrastructure.
 */

enum class SellerInboxAction { Acknowledge, RequestChanges, Accept }

object SellerInboxTransitions {
    /**
     * @return the new state, or `null` if [action] is not valid from [current]. Architecture §5's
     *   transition table: Acknowledge/RequestChanges/Accept are all only valid from `New` or
     *   `Acknowledged` — never from `ChangesRequested`, `Accepted`, or `Converted` (each of those
     *   is a closed record; a buyer who wants to revise resubmits a new Estimate/PO entirely,
     *   never mutates this one in place).
     */
    fun transition(current: SellerInboxState, action: SellerInboxAction): SellerInboxState? {
        val fromValidState = current == SellerInboxState.New || current == SellerInboxState.Acknowledged
        if (!fromValidState) return null
        return when (action) {
            SellerInboxAction.Acknowledge -> SellerInboxState.Acknowledged
            SellerInboxAction.RequestChanges -> SellerInboxState.ChangesRequested
            // Accept is modeled as landing directly on Accepted; the repository immediately moves
            // it to Converted as part of the same atomic Accept transaction (architecture §5) —
            // there is no meaningful window where an entry sits in Accepted-but-not-Converted.
            SellerInboxAction.Accept -> SellerInboxState.Accepted
        }
    }
}

/**
 * Everything here is derived, never stored as its own flag — architecture §8's explicit
 * persisted-vs-derived table. The only genuinely persisted facts are: which of
 * [TermsAcknowledgment.buyerConfirmedAt]/[TermsAcknowledgment.sellerConfirmedAt] are set, the list
 * of [PaymentEvent] rows and their `sellerConfirmed` flags, and [CommercialTransaction.completedAt].
 * Everything else — `AGREED`, `PAYMENT_INITIATED`, `PAYMENT_CONFIRMED`, Overdue — is computed fresh
 * from those facts every time it's asked for.
 */
object TransactionStateDerivation {

    /** Architecture §8: "Agreed" fires the moment the second of the two confirmation timestamps is
     * written, whichever party taps second — order-independent, matching Q12's own requirement. */
    fun isAgreed(terms: TermsAcknowledgment): Boolean =
        terms.buyerConfirmedAt != null && terms.sellerConfirmedAt != null

    /** Q12's asymmetric "waiting for [counterparty]" framing — a pure read-time interpretation of
     * the same two timestamp columns [isAgreed] reads, never a separately-maintained flag that
     * could disagree with them. */
    sealed interface MutualAgreementStatus {
        data object Agreed : MutualAgreementStatus
        data object WaitingForBuyer : MutualAgreementStatus
        data object WaitingForSeller : MutualAgreementStatus
        data object WaitingForBoth : MutualAgreementStatus
    }

    fun mutualAgreementStatus(terms: TermsAcknowledgment): MutualAgreementStatus = when {
        terms.buyerConfirmedAt != null && terms.sellerConfirmedAt != null -> MutualAgreementStatus.Agreed
        terms.buyerConfirmedAt != null -> MutualAgreementStatus.WaitingForSeller
        terms.sellerConfirmedAt != null -> MutualAgreementStatus.WaitingForBuyer
        else -> MutualAgreementStatus.WaitingForBoth
    }

    /** Architecture §8: true once at least one payment event exists claiming Initiated or Paid. */
    fun isPaymentInitiated(events: List<PaymentEvent>): Boolean = events.isNotEmpty()

    /** Architecture §8/Q16: true only once the SUM of seller-confirmed claimed amounts reaches the
     * transaction total — never upgraded from an unconfirmed buyer claim alone. Amounts are decimal
     * strings throughout this feature (matching the codebase's `MoneyAmount`-style convention), so
     * comparison goes through [java.math.BigDecimal] rather than float/double. */
    fun isPaymentConfirmed(transaction: CommercialTransaction, events: List<PaymentEvent>): Boolean {
        val total = transaction.totalAmount.toBigDecimalOrNull() ?: return false
        val confirmedSum = events.filter { it.sellerConfirmed }
            .fold(java.math.BigDecimal.ZERO) { acc, e -> acc + (e.buyerClaimedAmount.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO) }
        return confirmedSum >= total
    }

    /**
     * The full derived state for a transaction given its current persisted facts. This is the
     * single source of truth [com.budcom.android.feature.transaction.data.repository.TransactionRepositoryImpl]
     * writes back into [CommercialTransaction.state] after any event that could move it — the
     * column is a cached projection of this function, never an independently-editable value.
     */
    fun deriveState(
        transaction: CommercialTransaction,
        terms: TermsAcknowledgment,
        events: List<PaymentEvent>,
    ): CommercialTransactionState = when {
        // `isPaymentConfirmed` is the actual completion TRIGGER (Q17: fires the instant the seller
        // confirms the full/final amount) — `transaction.completedAt != null` is what keeps a
        // transaction pinned at Completed on every later recompute once that has already happened
        // once. Checking only `completedAt` here would be circular: completedAt is itself only ever
        // set when this function first returns Completed, so nothing could ever reach it.
        transaction.completedAt != null || isPaymentConfirmed(transaction, events) -> CommercialTransactionState.Completed
        isPaymentInitiated(events) -> CommercialTransactionState.PaymentInitiated
        isAgreed(terms) -> CommercialTransactionState.Agreed
        else -> CommercialTransactionState.PendingConfirmation
    }

    /**
     * Architecture §16 Risk 6: `ON_DELIVERY` has no delivery-tracked event anywhere in this
     * design, so no due date is computable for it — this function returns `null` rather than
     * guessing, and callers must treat `null` as "cannot compute Overdue / cannot schedule
     * reminders for this transaction," never as "due immediately" or "never due."
     *
     * `PARTIAL`'s balance-timing sub-field is free text (architecture §7 — Q11 never specified a
     * structured shape for "balance timing" beyond payment-timing's own top-level enum), so it is
     * equally not machine-resolvable to a date here. **This is a genuine refinement of the
     * architecture document's own Risk 6, discovered during this implementation pass**: the
     * unresolved-due-date problem is not unique to `ON_DELIVERY`, it also applies to `PARTIAL`.
     */
    fun resolveDueDateEpochMillis(terms: TermsAcknowledgment, transactionAcceptedAtEpochMillis: Long): Long? =
        when (terms.paymentTiming) {
            PaymentTiming.Advance -> transactionAcceptedAtEpochMillis
            PaymentTiming.CreditDays -> terms.creditDays?.let { days ->
                transactionAcceptedAtEpochMillis + days.toLong() * MILLIS_PER_DAY
            }
            PaymentTiming.OnDelivery -> null
            PaymentTiming.Partial -> null
        }

    /** Overdue is always a derived read-time modifier (architecture §8), never a member of
     * [CommercialTransactionState] and never persisted. A transaction with no computable due date
     * ([resolveDueDateEpochMillis] returning `null`) is never Overdue by construction — absence of
     * a due date is not the same as being overdue. A `Completed` transaction is never Overdue,
     * regardless of when it completed. */
    fun isOverdue(
        transaction: CommercialTransaction,
        terms: TermsAcknowledgment,
        nowEpochMillis: Long,
    ): Boolean {
        if (transaction.state == CommercialTransactionState.Completed) return false
        val dueAt = resolveDueDateEpochMillis(terms, transaction.acceptedAt.epochMillis) ?: return false
        return nowEpochMillis > dueAt
    }

    private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L

    private fun String.toBigDecimalOrNull(): java.math.BigDecimal? = runCatching { java.math.BigDecimal(this) }.getOrNull()
}

data class RelayAcceptanceEvidence(
    val acceptanceId: String,
    val envelopeId: String,
    val objectType: String,
    val objectId: String,
    val objectVersion: Int,
    val senderBusinessId: String,
    val recipientBusinessId: String,
    val acceptedAtEpochMillis: Long,
    val status: String,
)

object CanonicalOrderSentTransitions {
    fun apply(order: CanonicalOrder, envelope: OrderDeliveryEnvelope, evidence: RelayAcceptanceEvidence): CanonicalOrderState? {
        if (evidence.status != "relay_accepted") return null
        if (evidence.envelopeId != envelope.envelopeId) return null
        if (evidence.objectType != envelope.objectType) return null
        if (evidence.objectId != order.orderId || envelope.orderId != order.orderId) return null
        if (evidence.objectVersion != order.version || envelope.orderVersion != order.version) return null
        if (evidence.senderBusinessId != order.sellerCompanyId || envelope.senderCompanyId != order.sellerCompanyId) return null
        val recipient = order.buyerPartyId ?: envelope.recipientPartyId
        if (recipient == null || evidence.recipientBusinessId != recipient || envelope.recipientPartyId != recipient) return null
        return when (order.state) {
            CanonicalOrderState.Sent -> CanonicalOrderState.Sent
            CanonicalOrderState.Draft -> CanonicalOrderState.Sent
            CanonicalOrderState.Seen,
            CanonicalOrderState.Confirmed,
            CanonicalOrderState.RevisionPending,
            CanonicalOrderState.RevisionSent,
            CanonicalOrderState.RevisionSeen,
            -> null
        }
    }
}

object RecipientOrderSeenOpenTransitions {
    fun toEvidence(
        inbox: StructuredRecipientInboxEntry,
        open: OrderStructuredOpenEvent,
        viewerCompanyId: String,
    ): OrderSeenEvidence? {
        if (open.viewerBusinessId != viewerCompanyId || inbox.companyId != viewerCompanyId) return null
        if (open.orderId != inbox.objectId || open.orderVersion != inbox.objectVersion) return null
        if (open.objectType != inbox.objectType) return null
        if (open.senderBusinessId != inbox.senderBusinessId) return null
        if (open.viewerBusinessId == open.senderBusinessId) return null
        if (open.viewerActorId.isBlank() || open.viewerDeviceId.isBlank()) return null
        if (open.eventId.isBlank() || open.idempotencyKey.isBlank()) return null
        if (inbox.transportState != RecipientInboxTransportState.Received) return null
        return OrderSeenEvidence(
            eventId = open.eventId,
            orderId = open.orderId,
            orderVersion = open.orderVersion,
            viewerBusinessId = open.viewerBusinessId,
            viewerActorId = open.viewerActorId,
            viewerDeviceId = open.viewerDeviceId,
            senderBusinessId = open.senderBusinessId,
            seenAt = open.openedAt,
        )
    }
}

object CanonicalOrderSeenTransitions {
    fun apply(order: CanonicalOrder, evidence: OrderSeenEvidence): CanonicalOrderState? {
        if (evidence.orderId != order.orderId || evidence.orderVersion != order.version) return null
        if (evidence.viewerBusinessId == evidence.senderBusinessId) return null
        if (evidence.viewerBusinessId != order.companyId && evidence.senderBusinessId != order.companyId) return null
        return when (order.state) {
            CanonicalOrderState.Seen -> CanonicalOrderState.Seen
            CanonicalOrderState.Sent -> CanonicalOrderState.Seen
            CanonicalOrderState.RevisionSeen -> CanonicalOrderState.RevisionSeen
            CanonicalOrderState.RevisionSent -> CanonicalOrderState.RevisionSeen
            CanonicalOrderState.Draft,
            CanonicalOrderState.Confirmed,
            CanonicalOrderState.RevisionPending,
            -> null
        }
    }
}
