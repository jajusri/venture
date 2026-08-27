package com.budcom.android.feature.transaction.data.local

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "txn_order",
    primaryKeys = ["companyId", "orderId"],
    indices = [
        Index(value = ["companyId", "creationKey"], unique = true),
        Index(value = ["companyId", "buyerPartyId"]),
    ],
)
data class CanonicalOrderEntity(
    val companyId: String,
    val orderId: String,
    val creationKey: String,
    val sellerCompanyId: String,
    val buyerPartyId: String?,
    val state: String,
    val source: String,
    val submissionType: String,
    val note: String?,
    val createdAt: Long,
    val createdAtSource: String,
    val version: Int,
)

@Entity(
    tableName = "txn_order_line",
    primaryKeys = ["companyId", "orderId", "lineId"],
    indices = [Index(value = ["companyId", "orderId"])],
)
data class CanonicalOrderLineEntity(
    val companyId: String,
    val orderId: String,
    val lineId: String,
    val linkedProductId: String?,
    val snapshotProductName: String,
    val snapshotUnit: String?,
    val snapshotSku: String?,
    val quantity: String,
    val unitPriceAmount: String?,
    val unitPriceCurrencyCode: String?,
    val priceState: String,
    val lineTotalAmount: String?,
)

@Entity(
    tableName = "txn_order_outbox",
    primaryKeys = ["companyId", "envelopeId"],
    indices = [Index(value = ["companyId", "idempotencyKey"], unique = true), Index(value = ["companyId", "orderId"])],
)
data class OrderDeliveryEnvelopeEntity(
    val companyId: String,
    val envelopeId: String,
    val idempotencyKey: String,
    val objectType: String,
    val orderId: String,
    val orderVersion: Int,
    val senderCompanyId: String,
    val recipientPartyId: String?,
    val createdAt: Long,
    val createdAtSource: String,
    val state: String,
    val attemptCount: Int,
    val lastAttemptAt: Long?,
    val lastAttemptAtSource: String?,
    val lastError: String?,
)

@Entity(
    tableName = "txn_recipient_inbox",
    primaryKeys = ["companyId", "envelopeId"],
    indices = [
        Index(value = ["companyId", "mailboxId", "mailboxSequence"]),
        Index(value = ["companyId", "objectType", "objectId"]),
    ],
)
data class StructuredRecipientInboxEntity(
    val companyId: String,
    val envelopeId: String,
    val idempotencyKey: String,
    val objectType: String,
    val objectId: String,
    val objectVersion: Int,
    val senderBusinessId: String,
    val senderActorId: String,
    val senderDeviceId: String,
    val mailboxId: String,
    val mailboxSequence: Long,
    val acceptanceId: String,
    val acceptedAt: Long,
    val acceptedAtSource: String,
    val ingestedAt: Long,
    val ingestedAtSource: String,
    val transportState: String,
)

@Entity(
    tableName = "txn_recipient_inbox_cursor",
    primaryKeys = ["companyId", "mailboxId"],
)
data class RecipientInboxCursorEntity(
    val companyId: String,
    val mailboxId: String,
    val cursor: String?,
)

/**
 * Transaction Mode Room entities
 * (docs/architecture/BUDCOM-TRANSACTION-MODE-ARCHITECTURE.md §4-§10, §12, §14). Every table
 * carries `companyId` as the first key component, matching every other entity in this codebase
 * (Catalogue architecture §13, reused unchanged here).
 *
 * No existing table (`cached_parties`, `cached_ledgers`, `cached_stock_items`, any `catalogue_*`
 * table) is modified anywhere in this file — every entity here is new (architecture §14).
 *
 * Named `commercial_transaction`, not `transaction` — `TRANSACTION` is a reserved word in SQLite
 * (`BEGIN TRANSACTION`); avoiding the collision entirely is simpler than relying on Room's quoting.
 */

/** `txn_estimate_po` — architecture §4. */
@Entity(
    tableName = "txn_estimate_po",
    primaryKeys = ["companyId", "estimatePoId"],
    indices = [
        Index(value = ["companyId"]),
        Index(value = ["companyId", "buyerPartyId"]),
    ],
)
data class EstimatePoEntity(
    val companyId: String,
    val estimatePoId: String,
    /** "CATALOGUE" or "CHAT" — [com.budcom.android.feature.transaction.domain.model.TransactionEntryPointType]. */
    val entryPointType: String,
    /** "ESTIMATE" or "PURCHASE_ORDER". */
    val submissionType: String,
    /** "WHATSAPP_SHARED" or "IN_APP_SUBMITTED". */
    val deliveryChannel: String,
    val buyerPartyId: String?,
    val totalAmount: String,
    val currencyCode: String?,
    /** "SHARED" or "SUBMITTED". */
    val status: String,
    val submittedAt: Long,
    val submittedAtSource: String,
)

/** `txn_estimate_po_line_item` — architecture §4, a snapshot at submission time, never a live
 * reference re-resolved against current Catalogue content. */
@Entity(
    tableName = "txn_estimate_po_line_item",
    primaryKeys = ["companyId", "estimatePoId", "lineItemId"],
    indices = [Index(value = ["companyId", "estimatePoId"])],
)
data class EstimatePoLineItemEntity(
    val companyId: String,
    val estimatePoId: String,
    val lineItemId: String,
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

/** `txn_seller_inbox_entry` — architecture §5. Only created for
 * [com.budcom.android.feature.transaction.domain.model.TransactionDeliveryChannel.InAppSubmitted]
 * submissions, enforced structurally in [com.budcom.android.feature.transaction.data.repository.TransactionRepositoryImpl],
 * not merely by UI convention. */
@Entity(
    tableName = "txn_seller_inbox_entry",
    primaryKeys = ["companyId", "inboxEntryId"],
    indices = [
        Index(value = ["companyId", "estimatePoId"], unique = true),
        Index(value = ["companyId", "state"]),
    ],
)
data class SellerInboxEntryEntity(
    val companyId: String,
    val inboxEntryId: String,
    val estimatePoId: String,
    /** "NEW" / "ACKNOWLEDGED" / "CHANGES_REQUESTED" / "ACCEPTED" / "CONVERTED". */
    val state: String,
    val acknowledgedAt: Long?,
    val acknowledgedAtSource: String?,
    val changeRequestNote: String?,
    val respondedAt: Long?,
    val respondedAtSource: String?,
    val convertedTransactionId: String?,
)

/** `commercial_transaction` — architecture §8. */
@Entity(
    tableName = "commercial_transaction",
    primaryKeys = ["companyId", "transactionId"],
    indices = [
        Index(value = ["companyId"]),
        Index(value = ["companyId", "buyerPartyId"]),
        Index(value = ["companyId", "state"]),
    ],
)
data class CommercialTransactionEntity(
    val companyId: String,
    val transactionId: String,
    val estimatePoId: String,
    val buyerPartyId: String,
    /** "PENDING_CONFIRMATION" / "AGREED" / "PAYMENT_INITIATED" / "PAYMENT_CONFIRMED" / "COMPLETED"
     * — a cached projection of [com.budcom.android.feature.transaction.domain.model.TransactionStateDerivation.deriveState],
     * never an independently-editable source of truth (architecture §8's persisted-vs-derived
     * table). Overdue is never a value of this column — always computed at read time. */
    val state: String,
    val totalAmount: String,
    val currencyCode: String?,
    val acceptedAt: Long,
    val acceptedAtSource: String,
    val completedAt: Long?,
    val completedAtSource: String?,
)

/** `txn_terms_acknowledgment` — architecture §7. 1:1 with `commercial_transaction`. Deliberately
 * carries no signature/attestation/document-generation field of any kind — see the architecture
 * document's own "not a legal contract, enforced at the data level" section. */
@Entity(
    tableName = "txn_terms_acknowledgment",
    primaryKeys = ["companyId", "transactionId"],
)
data class TermsAcknowledgmentEntity(
    val companyId: String,
    val transactionId: String,
    /** "ADVANCE" / "ON_DELIVERY" / "CREDIT_X_DAYS" / "PARTIAL". */
    val paymentTiming: String,
    val creditDays: Int?,
    val partialAdvancePercent: String?,
    val partialBalanceTiming: String?,
    val amount: String,
    val currencyCode: String?,
    /** <=100 chars, enforced at the repository write boundary, not by a column constraint. */
    val note: String?,
    val proposedAt: Long,
    val proposedAtSource: String,
    val buyerConfirmedAt: Long?,
    val buyerConfirmedAtSource: String?,
    val sellerConfirmedAt: Long?,
    val sellerConfirmedAtSource: String?,
)

/** `txn_payment_event` — architecture §8/Q16. No money is ever processed here — status/claim/
 * confirm columns only, never a card/UPI/payment-gateway field of any kind. */
@Entity(
    tableName = "txn_payment_event",
    primaryKeys = ["companyId", "transactionId", "paymentEventId"],
    indices = [Index(value = ["companyId", "transactionId"])],
)
data class PaymentEventEntity(
    val companyId: String,
    val transactionId: String,
    val paymentEventId: String,
    val installmentSequence: Int,
    /** "INITIATED" or "PAID". */
    val buyerClaimStatus: String,
    val buyerClaimedAmount: String,
    val currencyCode: String?,
    val buyerClaimedAt: Long,
    val buyerClaimedAtSource: String,
    val sellerConfirmed: Boolean,
    val sellerConfirmedAt: Long?,
    val sellerConfirmedAtSource: String?,
    /** Factual-only (architecture §11 non-scope audit) — never a severity/rating field, never
     * aggregated, never written back onto `cached_parties`. */
    val sellerDiscrepancyNote: String?,
)

/** `txn_ledger_intent` — architecture §6. Deliberately NOT a column on `PartyEntity` (this task's
 * own migration constraint forbids touching existing tables); this is metadata for a future,
 * human-mediated Tally XML export, never a Tally write itself. */
@Entity(
    tableName = "txn_ledger_intent",
    primaryKeys = ["companyId", "transactionId"],
    indices = [Index(value = ["companyId", "buyerPartyId"])],
)
data class LedgerIntentEntity(
    val companyId: String,
    val transactionId: String,
    val buyerPartyId: String,
    /** "DEBTOR" or "CREDITOR". */
    val chosenLedgerGroup: String,
    val promotedProspectAt: Long?,
    val promotedProspectAtSource: String?,
    val recordedAt: Long,
    val recordedAtSource: String,
)

/** `catalogue_access_grant` — architecture §10. A buyer-scoped exception layered ALONGSIDE
 * Catalogue's existing Item -> Branch -> Stock-group -> Catalogue-wide override chain, never a
 * fifth level inside it (that chain has no buyer dimension at all). Does not modify
 * `catalogue_settings`/`catalogue_override`/any other Catalogue table. */
@Entity(
    tableName = "catalogue_access_grant",
    primaryKeys = ["companyId", "grantId"],
    indices = [Index(value = ["companyId", "buyerPartyId"])],
)
data class CatalogueAccessGrantEntity(
    val companyId: String,
    val grantId: String,
    val buyerPartyId: String,
    /** "OPEN" — see [com.budcom.android.feature.transaction.domain.model.CataloguePriceVisibilityGrant]'s
     * own doc comment on why this is effectively binary today, not a real pricing tier. */
    val priceVisibility: String,
    val grantedAt: Long,
    val grantedAtSource: String,
    val expiresAt: Long?,
    val revokedAt: Long?,
    val revokedAtSource: String?,
)
