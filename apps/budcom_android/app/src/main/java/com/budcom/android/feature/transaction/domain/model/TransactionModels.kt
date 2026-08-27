package com.budcom.android.feature.transaction.domain.model

/**
 * Transaction Mode domain models (docs/architecture/BUDCOM-TRANSACTION-MODE-ARCHITECTURE.md §4-§10).
 *
 * Every enum below carries an explicit [columnValue] rather than relying on Kotlin's `.name`, so the
 * on-disk string is stable and independent of the Kotlin identifier — matches
 * [com.budcom.android.feature.catalogue.domain.model.CatalogueOverrideAttribute]'s own
 * `PriceSyncMode("PRICE_SYNC_MODE")` precedent, and the exact string values already committed to in
 * the architecture document's own table definitions.
 */

enum class TransactionEntryPointType(val columnValue: String) {
    Catalogue("CATALOGUE"),
    /** Not producible by any code in this session (architecture §3: "Catalogue is the only
     * producer built now") — the value exists so the schema is entry-point-agnostic without a
     * future migration, per Q10's own lock. */
    Chat("CHAT"),
    ;

    companion object {
        fun fromColumn(value: String): TransactionEntryPointType = entries.first { it.columnValue == value }
    }
}

enum class TransactionSubmissionType(val columnValue: String) {
    Estimate("ESTIMATE"),
    PurchaseOrder("PURCHASE_ORDER"),
    ;

    companion object {
        fun fromColumn(value: String): TransactionSubmissionType = entries.first { it.columnValue == value }
    }
}

enum class TransactionDeliveryChannel(val columnValue: String) {
    WhatsAppShared("WHATSAPP_SHARED"),
    InAppSubmitted("IN_APP_SUBMITTED"),
    ;

    companion object {
        fun fromColumn(value: String): TransactionDeliveryChannel = entries.first { it.columnValue == value }
    }
}

/** Architecture §4: "deliberately not the same state vocabulary as the seller inbox or the
 * Transaction — only ever answers 'was this delivered,' never 'has the seller acted on it.'" */
enum class EstimatePoStatus(val columnValue: String) {
    Shared("SHARED"),
    Submitted("SUBMITTED"),
    ;

    companion object {
        fun fromColumn(value: String): EstimatePoStatus = entries.first { it.columnValue == value }
    }
}

/** Architecture §5. Only [TransactionDeliveryChannel.InAppSubmitted] Estimates/POs ever get a row
 * carrying this state at all. */
enum class SellerInboxState(val columnValue: String) {
    New("NEW"),
    Acknowledged("ACKNOWLEDGED"),
    ChangesRequested("CHANGES_REQUESTED"),
    Accepted("ACCEPTED"),
    Converted("CONVERTED"),
    ;

    companion object {
        fun fromColumn(value: String): SellerInboxState = entries.first { it.columnValue == value }
    }
}

/** Architecture §8. Overdue is deliberately NOT a member of this enum — it is always a derived
 * read-time modifier (see [com.budcom.android.feature.transaction.domain.model.isOverdue]), never a
 * persisted state value. */
enum class CommercialTransactionState(val columnValue: String) {
    PendingConfirmation("PENDING_CONFIRMATION"),
    Agreed("AGREED"),
    PaymentInitiated("PAYMENT_INITIATED"),
    PaymentConfirmed("PAYMENT_CONFIRMED"),
    Completed("COMPLETED"),
    ;

    companion object {
        fun fromColumn(value: String): CommercialTransactionState = entries.first { it.columnValue == value }
    }
}

enum class PaymentTiming(val columnValue: String) {
    Advance("ADVANCE"),
    OnDelivery("ON_DELIVERY"),
    CreditDays("CREDIT_X_DAYS"),
    Partial("PARTIAL"),
    ;

    companion object {
        fun fromColumn(value: String): PaymentTiming = entries.first { it.columnValue == value }
    }
}

/** Q9 — the seller's Tally ledger-group intent, recorded for a future human-mediated XML export
 * (architecture §6). Never used to write a Tally Ledger directly. */
enum class LedgerGroupChoice(val columnValue: String) {
    Debtor("DEBTOR"),
    Creditor("CREDITOR"),
    ;

    companion object {
        fun fromColumn(value: String): LedgerGroupChoice = entries.first { it.columnValue == value }
    }
}

enum class PaymentClaimStatus(val columnValue: String) {
    Initiated("INITIATED"),
    Paid("PAID"),
    ;

    companion object {
        fun fromColumn(value: String): PaymentClaimStatus = entries.first { it.columnValue == value }
    }
}

/** Mirrors [com.budcom.android.feature.catalogue.domain.model.CatalogueTimestampSource]'s own
 * discipline (architecture §9), deliberately a distinct type rather than a shared import — this is
 * a general "is our paired Connector reachable" concept, not something Transaction Mode borrows
 * from Catalogue's own module. */
enum class TransactionTimestampSource { Connector, DeviceLocalProvisional }

data class TransactionTimestamp(val epochMillis: Long, val source: TransactionTimestampSource)

/** See [com.budcom.android.feature.transaction.data.TransactionClockImpl] for resolution logic. */
fun interface TransactionClock {
    suspend fun now(): TransactionTimestamp
}

// ---------------------------------------------------------------------------------------------
// §4 — the generic Estimate/PO object
// ---------------------------------------------------------------------------------------------

data class TransactionLineItem(
    val estimatePoId: String,
    val lineItemId: String,
    /** References `catalogue_product(companyId, productId)` by convention — nullable to keep this
     * shape entry-point-agnostic (a future chat-originated line has no Catalogue product to link).
     * Never a Room `ForeignKey`, matching this codebase's own no-FK convention on every other
     * convention-keyed reference. */
    val linkedProductId: String?,
    /** Denormalized at submission time (architecture §4) — Catalogue content can change or the
     * product can be archived after submission; this line item must stay legible regardless. */
    val snapshotProductName: String,
    val snapshotUnit: String?,
    val snapshotSku: String?,
    val quantity: String,
    val unitPriceAmount: String?,
    val unitPriceCurrencyCode: String?,
    /** `null` distinguishes a genuine "Contact for price" line (no computable line total) from a
     * line that simply has a zero amount — mirrors
     * [com.budcom.android.feature.catalogue.domain.model.CataloguePriceState]'s own
     * no-price-supplied-vs-contact-for-price discipline, applied here to a submitted line. */
    val lineTotalAmount: String?,
    val isContactForPrice: Boolean,
)

enum class CanonicalOrderState(val columnValue: String) {
    Draft("DRAFT"),
    ;

    companion object {
        fun fromColumn(value: String): CanonicalOrderState = entries.first { it.columnValue == value }
    }
}

data class CanonicalOrder(
    val companyId: String,
    val orderId: String,
    val creationKey: String,
    val sellerCompanyId: String,
    val buyerPartyId: String?,
    val state: CanonicalOrderState,
    val source: TransactionEntryPointType,
    val submissionType: TransactionSubmissionType,
    val note: String?,
    val createdAt: TransactionTimestamp,
    val version: Int,
    val lines: List<CanonicalOrderLine>,
)

data class CanonicalOrderLine(
    val orderId: String,
    val lineId: String,
    val linkedProductId: String?,
    val snapshotProductName: String,
    val snapshotUnit: String?,
    val snapshotSku: String?,
    val quantity: String,
    val unitPriceAmount: String?,
    val unitPriceCurrencyCode: String?,
    val priceState: TransactionDraftPriceState,
    val lineTotalAmount: String?,
)

data class EstimatePo(
    val companyId: String,
    val estimatePoId: String,
    val entryPointType: TransactionEntryPointType,
    val submissionType: TransactionSubmissionType,
    val deliveryChannel: TransactionDeliveryChannel,
    val buyerPartyId: String?,
    val totalAmount: String,
    val currencyCode: String?,
    val status: EstimatePoStatus,
    val submittedAt: TransactionTimestamp,
    val lineItems: List<TransactionLineItem>,
)

// ---------------------------------------------------------------------------------------------
// §5 — seller inbox
// ---------------------------------------------------------------------------------------------

data class SellerInboxEntry(
    val companyId: String,
    val inboxEntryId: String,
    val estimatePoId: String,
    val state: SellerInboxState,
    val acknowledgedAt: TransactionTimestamp?,
    val changeRequestNote: String?,
    val respondedAt: TransactionTimestamp?,
    val convertedTransactionId: String?,
)

// ---------------------------------------------------------------------------------------------
// §6 — Prospect -> Ledger intent (never mutates PartyEntity's schema; see TransactionRepository)
// ---------------------------------------------------------------------------------------------

data class LedgerIntent(
    val companyId: String,
    val transactionId: String,
    val buyerPartyId: String,
    val chosenLedgerGroup: LedgerGroupChoice,
    /** Non-null only if this Accept action actually flipped the Party from Prospect to Customer —
     * `null` if the Party was already Customer/Supplier/Other (architecture §6, idempotent). */
    val promotedProspectAt: TransactionTimestamp?,
    val recordedAt: TransactionTimestamp,
)

// ---------------------------------------------------------------------------------------------
// §7 — Terms Acknowledgment (three structured fields; never a legal-contract representation)
// ---------------------------------------------------------------------------------------------

data class TermsAcknowledgment(
    val companyId: String,
    val transactionId: String,
    val paymentTiming: PaymentTiming,
    val creditDays: Int?,
    val partialAdvancePercent: String?,
    val partialBalanceTiming: String?,
    /** Denormalized copy of the transaction's total at proposal time (architecture §7) — never a
     * second entry point for the figure. */
    val amount: String,
    val currencyCode: String?,
    /** Enforced at the write boundary to <=100 characters — see
     * [com.budcom.android.feature.transaction.domain.model.MAX_TERMS_NOTE_LENGTH]. */
    val note: String?,
    val proposedAt: TransactionTimestamp,
    val buyerConfirmedAt: TransactionTimestamp?,
    val sellerConfirmedAt: TransactionTimestamp?,
)

const val MAX_TERMS_NOTE_LENGTH = 100

// ---------------------------------------------------------------------------------------------
// §8 — the transaction itself + payment events
// ---------------------------------------------------------------------------------------------

data class CommercialTransaction(
    val companyId: String,
    val transactionId: String,
    val estimatePoId: String,
    val buyerPartyId: String,
    val state: CommercialTransactionState,
    val totalAmount: String,
    val currencyCode: String?,
    val acceptedAt: TransactionTimestamp,
    val completedAt: TransactionTimestamp?,
)

data class PaymentEvent(
    val companyId: String,
    val transactionId: String,
    val paymentEventId: String,
    val installmentSequence: Int,
    val buyerClaimStatus: PaymentClaimStatus,
    val buyerClaimedAmount: String,
    val currencyCode: String?,
    val buyerClaimedAt: TransactionTimestamp,
    val sellerConfirmed: Boolean,
    val sellerConfirmedAt: TransactionTimestamp?,
    /** Factual-only (architecture §11's non-scope audit) — never a severity/rating field, never
     * aggregated across transactions, never written back onto `PartyEntity`. */
    val sellerDiscrepancyNote: String?,
)

// ---------------------------------------------------------------------------------------------
// §10 — chat-based Catalogue access grant
// ---------------------------------------------------------------------------------------------

/** Architecture §10/Finding 3: Catalogue's real pricing model has no tiers, only
 * Open/Contact-for-price — so this is modeled as a visibility override, not a tier selection.
 * Flagged in the architecture document as an interpretation requiring product-owner confirmation. */
enum class CataloguePriceVisibilityGrant(val columnValue: String) {
    Open("OPEN"),
    ;

    companion object {
        fun fromColumn(value: String): CataloguePriceVisibilityGrant = entries.first { it.columnValue == value }
    }
}

data class CatalogueAccessGrant(
    val companyId: String,
    val grantId: String,
    val buyerPartyId: String,
    val priceVisibility: CataloguePriceVisibilityGrant,
    val grantedAt: TransactionTimestamp,
    /** A chosen future point in time (1 day/1 week/1 month from [grantedAt], or `null` = "Always",
     * Q18 explicitly allows this) — not a fact read from a clock, so unlike every other timestamp
     * in this feature this is a plain epoch millis, not a [TransactionTimestamp] with a source. */
    val expiresAtEpochMillis: Long?,
    val revokedAt: TransactionTimestamp?,
) {
    /** Checked-on-read, deliberately (architecture §10/§13's own Milestone-6 finding: no
     * scheduler/WorkManager infrastructure exists in this codebase to expire this on a timer). */
    fun isActive(nowEpochMillis: Long): Boolean =
        revokedAt == null && (expiresAtEpochMillis == null || expiresAtEpochMillis > nowEpochMillis)
}
