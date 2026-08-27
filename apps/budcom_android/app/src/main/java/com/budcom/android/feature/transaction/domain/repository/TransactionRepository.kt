package com.budcom.android.feature.transaction.domain.repository

import com.budcom.android.feature.transaction.domain.model.CommercialTransaction
import com.budcom.android.feature.transaction.domain.model.CanonicalOrder
import com.budcom.android.feature.transaction.domain.model.EstimatePo
import com.budcom.android.feature.transaction.domain.model.LedgerGroupChoice
import com.budcom.android.feature.transaction.domain.model.PaymentEvent
import com.budcom.android.feature.transaction.domain.model.PaymentTiming
import com.budcom.android.feature.transaction.domain.model.SellerInboxEntry
import com.budcom.android.feature.transaction.domain.model.TermsAcknowledgment
import com.budcom.android.feature.transaction.domain.model.TransactionDeliveryChannel
import com.budcom.android.feature.transaction.domain.model.TransactionEntryPointType
import com.budcom.android.feature.transaction.domain.model.TransactionLineItem
import com.budcom.android.feature.transaction.domain.model.TransactionSubmissionType
import com.budcom.android.feature.transaction.domain.model.TransactionTimestamp
import com.budcom.android.feature.transaction.domain.model.TransactionDraft

/** One line item as submitted — no `estimatePoId`/`lineItemId` yet, the repository assigns those. */
data class NewLineItem(
    val linkedProductId: String?,
    val snapshotProductName: String,
    val snapshotUnit: String?,
    val snapshotSku: String?,
    val quantity: String,
    val unitPriceAmount: String?,
    val unitPriceCurrencyCode: String?,
    val lineTotalAmount: String?,
    val isContactForPrice: Boolean,
)

/** The seller-proposed terms content an Accept action carries (architecture §7) — everything
 * except the confirmation timestamps and the denormalized amount, which the repository fills in. */
data class ProposedTerms(
    val paymentTiming: PaymentTiming,
    val creditDays: Int? = null,
    val partialAdvancePercent: String? = null,
    val partialBalanceTiming: String? = null,
    val note: String? = null,
)

sealed interface AcceptSellerInboxEntryResult {
    data class Success(val transaction: CommercialTransaction, val terms: TermsAcknowledgment) : AcceptSellerInboxEntryResult
    data object InboxEntryNotFound : AcceptSellerInboxEntryResult
    /** The entry's current state does not permit Accept (architecture §5's transition table) —
     * left completely unchanged, matching this codebase's "invalid transitions must be rejected,
     * never silently accepted" convention. */
    data object InvalidState : AcceptSellerInboxEntryResult
    /** [ProposedTerms.note] exceeded [com.budcom.android.feature.transaction.domain.model.MAX_TERMS_NOTE_LENGTH]. */
    data object NoteTooLong : AcceptSellerInboxEntryResult
}

sealed interface SellerInboxActionResult {
    data class Success(val entry: SellerInboxEntry) : SellerInboxActionResult
    data object NotFound : SellerInboxActionResult
    data object InvalidState : SellerInboxActionResult
}

/**
 * The Catalogue integration boundary (architecture §3, task's own "Catalogue integration
 * boundary" requirement): Catalogue remains responsible for product discovery, price visibility,
 * selection, and quantities; this repository is the entire surface by which that selection becomes
 * an Estimate/PO, a seller-inbox entry, a Transaction, and eventually a completed/history record.
 * Nothing here reaches back into Catalogue's own tables to write anything — Catalogue is read from
 * (product snapshots at submission time), never written to.
 */
interface TransactionRepository {

    /** Creates or returns one durable local Draft Order for the caller's intentional operation.
     * The key is persisted so retries are idempotent; this operation never sends or changes any
     * Estimate/PO, seller inbox, commercial transaction, or accounting record. */
    suspend fun createDraftOrder(
        draft: TransactionDraft,
        creationKey: String,
        note: String? = null,
        timestamp: TransactionTimestamp,
    ): CanonicalOrder = throw UnsupportedOperationException("Draft Order creation is not implemented by this repository")

    // ---- §4: generic Estimate/PO ----

    /**
     * Creates the generic, entry-point-agnostic Estimate/PO object (architecture §4). For
     * [TransactionDeliveryChannel.InAppSubmitted], also attempts delivery into the seller's inbox
     * via the injected [com.budcom.android.feature.transaction.domain.port.TransactionSubmissionPort]
     * — see that port's own doc comment for the hard transport boundary this call may hit.
     * [TransactionDeliveryChannel.WhatsAppShared] never touches the seller inbox at all
     * (architecture §5, structural, not a UI convention).
     */
    suspend fun createEstimatePo(
        companyId: String,
        entryPointType: TransactionEntryPointType,
        submissionType: TransactionSubmissionType,
        deliveryChannel: TransactionDeliveryChannel,
        buyerPartyId: String?,
        lineItems: List<NewLineItem>,
        timestamp: TransactionTimestamp,
    ): EstimatePo

    suspend fun findEstimatePoById(companyId: String, estimatePoId: String): EstimatePo?

    // ---- §5: seller inbox ----

    suspend fun findSellerInboxEntry(companyId: String, inboxEntryId: String): SellerInboxEntry?

    /** Seller's urgency-relevant default view (architecture §8 Q13) — every non-Converted entry,
     * newest submission first. Ordering by computed urgency (overdue-first) is a `commercial_transaction`-level
     * concern, not this table's — this list is intentionally simple. */
    suspend fun findAllSellerInboxEntries(companyId: String): List<SellerInboxEntry>

    suspend fun acknowledgeSellerInboxEntry(companyId: String, inboxEntryId: String, timestamp: TransactionTimestamp): SellerInboxActionResult

    suspend fun requestChangesOnSellerInboxEntry(
        companyId: String,
        inboxEntryId: String,
        note: String,
        timestamp: TransactionTimestamp,
    ): SellerInboxActionResult

    /**
     * Atomically: (1) transitions the inbox entry Accepted -> Converted, (2) creates the
     * [CommercialTransaction], (3) creates [TermsAcknowledgment] with the seller-proposed terms,
     * (4) runs the Prospect -> Ledger flow (architecture §6) if the buyer Party is currently a
     * Prospect. Either the whole sequence commits or none of it does — "fail honestly, never
     * partially" (architecture §5).
     */
    suspend fun acceptSellerInboxEntry(
        companyId: String,
        inboxEntryId: String,
        ledgerGroupChoice: LedgerGroupChoice,
        proposedTerms: ProposedTerms,
        timestamp: TransactionTimestamp,
    ): AcceptSellerInboxEntryResult

    // ---- §7/§8: terms confirmation, transaction state, payment ----

    suspend fun findTransactionById(companyId: String, transactionId: String): CommercialTransaction?

    suspend fun findTermsForTransaction(companyId: String, transactionId: String): TermsAcknowledgment?

    /** Q12's Mutual Agreement moment — records whichever party's confirmation this call
     * represents; fires the shared `Agreed` derivation (architecture §8) the instant both are set,
     * regardless of order. Returns `null` if no transaction/terms exist for this id. */
    suspend fun confirmTermsAsBuyer(companyId: String, transactionId: String, timestamp: TransactionTimestamp): TermsAcknowledgment?
    suspend fun confirmTermsAsSeller(companyId: String, transactionId: String, timestamp: TransactionTimestamp): TermsAcknowledgment?

    suspend fun findPaymentEventsForTransaction(companyId: String, transactionId: String): List<PaymentEvent>

    /** Q16: a buyer's claim, never itself confirmed. */
    suspend fun recordPaymentClaim(
        companyId: String,
        transactionId: String,
        claimedAmount: String,
        currencyCode: String?,
        isFinalOrPartial: com.budcom.android.feature.transaction.domain.model.PaymentClaimStatus,
        timestamp: TransactionTimestamp,
    ): PaymentEvent?

    /** Q16: the seller's explicit cross-check — the only path that can move cumulative confirmed
     * amount forward. Automatically completes the transaction (architecture §8/Q17) the instant the
     * cumulative confirmed total reaches [CommercialTransaction.totalAmount] — never a manual
     * "mark complete" action. */
    suspend fun confirmPaymentReceived(
        companyId: String,
        transactionId: String,
        paymentEventId: String,
        timestamp: TransactionTimestamp,
        discrepancyNote: String? = null,
    ): CommercialTransaction?

    // ---- §8 Q13: My Transactions ----

    /** Per-counterparty shared truth (architecture §8 Q13) — every transaction for this
     * (seller-company, buyer) pair, oldest-accepted first. */
    suspend fun findTransactionsForCounterparty(companyId: String, buyerPartyId: String): List<CommercialTransaction>

    suspend fun findAllTransactionsForCompany(companyId: String): List<CommercialTransaction>

    // ---- §11 non-scope regression guard (architecture §11, §15 test strategy) ----

    /** Combines [findTransactionById]/[findTermsForTransaction]/[findPaymentEventsForTransaction]
     * plus derivation into one read — convenience for presentation-layer callers so no call site
     * has to re-derive [com.budcom.android.feature.transaction.domain.model.TransactionStateDerivation]
     * by hand. */
    suspend fun findTransactionSnapshot(companyId: String, transactionId: String): TransactionSnapshot?

    // ---- §8 buying-history foundation (task point 8) ----

    /** Every completed transaction's line items for a buyer, oldest first — the sole authoritative
     * input for a future Buy Again / Previously Bought / Suggested Quantity feature. Deliberately
     * only ever reads [CommercialTransactionState.Completed] transactions (an in-progress or
     * abandoned transaction must never contribute a phantom "previously bought" entry) and never
     * fabricates or infers a quantity beyond what was actually recorded. */
    suspend fun findCompletedPurchaseHistory(companyId: String, buyerPartyId: String): List<TransactionLineItem>

    // ---- §10: chat-based Catalogue access grant ----

    suspend fun grantCatalogueAccess(
        companyId: String,
        buyerPartyId: String,
        expiresAt: TransactionTimestamp?,
        timestamp: TransactionTimestamp,
    ): com.budcom.android.feature.transaction.domain.model.CatalogueAccessGrant

    suspend fun revokeCatalogueAccess(companyId: String, grantId: String, timestamp: TransactionTimestamp)

    /** The single active grant for this buyer, if any — see
     * [com.budcom.android.feature.transaction.domain.model.CatalogueAccessGrant.isActive]. */
    suspend fun findActiveCatalogueAccessGrant(companyId: String, buyerPartyId: String, nowEpochMillis: Long): com.budcom.android.feature.transaction.domain.model.CatalogueAccessGrant?
}

data class TransactionSnapshot(
    val transaction: CommercialTransaction,
    val terms: TermsAcknowledgment,
    val paymentEvents: List<PaymentEvent>,
    val mutualAgreementStatus: com.budcom.android.feature.transaction.domain.model.TransactionStateDerivation.MutualAgreementStatus,
    val isOverdue: Boolean,
)
