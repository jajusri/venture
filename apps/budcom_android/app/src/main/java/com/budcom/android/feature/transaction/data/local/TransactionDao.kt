package com.budcom.android.feature.transaction.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CanonicalOrderDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: CanonicalOrderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLines(entities: List<CanonicalOrderLineEntity>)

    @Query("SELECT * FROM txn_order WHERE companyId = :companyId AND creationKey = :creationKey")
    suspend fun findByCreationKey(companyId: String, creationKey: String): CanonicalOrderEntity?

    @Query("SELECT * FROM txn_order WHERE companyId = :companyId AND orderId = :orderId")
    suspend fun findById(companyId: String, orderId: String): CanonicalOrderEntity?

    @Query("UPDATE txn_order SET state = :state WHERE companyId = :companyId AND orderId = :orderId")
    suspend fun updateState(companyId: String, orderId: String, state: String)

    @Query(
        "UPDATE txn_order SET version = :version, state = :state, note = :note " +
            "WHERE companyId = :companyId AND orderId = :orderId",
    )
    suspend fun updateVersionStateAndNote(companyId: String, orderId: String, version: Int, state: String, note: String?)

    @Query("DELETE FROM txn_order_line WHERE companyId = :companyId AND orderId = :orderId")
    suspend fun deleteLines(companyId: String, orderId: String)

    @Query("SELECT * FROM txn_order_line WHERE companyId = :companyId AND orderId = :orderId ORDER BY lineId ASC")
    suspend fun findLines(companyId: String, orderId: String): List<CanonicalOrderLineEntity>
}

@Dao
interface OrderOutboxDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: OrderDeliveryEnvelopeEntity)

    @Query("SELECT * FROM txn_order_outbox WHERE companyId = :companyId AND idempotencyKey = :idempotencyKey")
    suspend fun findByIdempotencyKey(companyId: String, idempotencyKey: String): OrderDeliveryEnvelopeEntity?

    @Query("SELECT * FROM txn_order_outbox WHERE companyId = :companyId AND state IN ('QUEUED', 'RETRYING') ORDER BY createdAt ASC LIMIT :limit")
    suspend fun findPendingBatch(companyId: String, limit: Int): List<OrderDeliveryEnvelopeEntity>

    @Query("SELECT * FROM txn_order_outbox WHERE companyId = :companyId AND state IN ('QUEUED', 'RETRYING') ORDER BY createdAt ASC")
    suspend fun findPending(companyId: String): List<OrderDeliveryEnvelopeEntity>

    @Query(
        "UPDATE txn_order_outbox SET state = :state, attemptCount = :attemptCount, lastAttemptAt = :lastAttemptAt, " +
            "lastAttemptAtSource = :lastAttemptAtSource, lastError = :lastError " +
            "WHERE companyId = :companyId AND envelopeId = :envelopeId",
    )
    suspend fun updateTransportAttempt(
        companyId: String,
        envelopeId: String,
        state: String,
        attemptCount: Int,
        lastAttemptAt: Long,
        lastAttemptAtSource: String,
        lastError: String?,
    )
}

@Dao
interface StructuredRecipientInboxDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: StructuredRecipientInboxEntity)

    @Query("SELECT * FROM txn_recipient_inbox WHERE companyId = :companyId AND envelopeId = :envelopeId")
    suspend fun findByEnvelopeId(companyId: String, envelopeId: String): StructuredRecipientInboxEntity?

    @Query("SELECT * FROM txn_recipient_inbox WHERE companyId = :companyId ORDER BY mailboxSequence ASC LIMIT :limit OFFSET :offset")
    suspend fun findPage(companyId: String, limit: Int, offset: Int): List<StructuredRecipientInboxEntity>

    @Query("SELECT * FROM txn_recipient_inbox WHERE companyId = :companyId ORDER BY mailboxSequence ASC")
    suspend fun findAll(companyId: String): List<StructuredRecipientInboxEntity>
}

@Dao
interface RecipientInboxCursorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RecipientInboxCursorEntity)

    @Query("SELECT * FROM txn_recipient_inbox_cursor WHERE companyId = :companyId AND mailboxId = :mailboxId")
    suspend fun find(companyId: String, mailboxId: String): RecipientInboxCursorEntity?
}

@Dao
interface OrderCommercialEventDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: OrderCommercialEventEntity)

    @Query("SELECT * FROM txn_order_commercial_event WHERE companyId = :companyId AND idempotencyKey = :idempotencyKey")
    suspend fun findByIdempotencyKey(companyId: String, idempotencyKey: String): OrderCommercialEventEntity?

    @Query(
        "SELECT * FROM txn_order_commercial_event WHERE companyId = :companyId AND orderId = :orderId " +
            "AND orderVersion = :orderVersion AND eventType = :eventType LIMIT 1",
    )
    suspend fun findByOrderVersionAndType(
        companyId: String,
        orderId: String,
        orderVersion: Int,
        eventType: String,
    ): OrderCommercialEventEntity?

    @Query(
        "SELECT * FROM txn_order_commercial_event WHERE companyId = :companyId AND orderId = :orderId " +
            "AND orderVersion = :orderVersion ORDER BY occurredAt ASC",
    )
    suspend fun findAllForOrderVersion(companyId: String, orderId: String, orderVersion: Int): List<OrderCommercialEventEntity>
}

@Dao
interface OrderVersionArchiveDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOrder(entity: OrderVersionArchiveEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLines(entities: List<OrderVersionLineArchiveEntity>)

    @Query("SELECT * FROM txn_order_version_archive WHERE companyId = :companyId AND orderId = :orderId AND version = :version")
    suspend fun findOrder(companyId: String, orderId: String, version: Int): OrderVersionArchiveEntity?

    @Query("SELECT * FROM txn_order_line_version_archive WHERE companyId = :companyId AND orderId = :orderId AND version = :version ORDER BY lineId ASC")
    suspend fun findLines(companyId: String, orderId: String, version: Int): List<OrderVersionLineArchiveEntity>
}

@Dao
interface EstimatePoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: EstimatePoEntity)

    @Query("SELECT * FROM txn_estimate_po WHERE companyId = :companyId AND estimatePoId = :estimatePoId")
    suspend fun findById(companyId: String, estimatePoId: String): EstimatePoEntity?

    @Query("SELECT * FROM txn_estimate_po WHERE companyId = :companyId AND buyerPartyId = :buyerPartyId")
    suspend fun findAllForBuyer(companyId: String, buyerPartyId: String): List<EstimatePoEntity>
}

@Dao
interface EstimatePoLineItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<EstimatePoLineItemEntity>)

    @Query("SELECT * FROM txn_estimate_po_line_item WHERE companyId = :companyId AND estimatePoId = :estimatePoId")
    suspend fun findAllForEstimatePo(companyId: String, estimatePoId: String): List<EstimatePoLineItemEntity>
}

@Dao
interface SellerInboxEntryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SellerInboxEntryEntity)

    @Query("SELECT * FROM txn_seller_inbox_entry WHERE companyId = :companyId AND inboxEntryId = :inboxEntryId")
    suspend fun findById(companyId: String, inboxEntryId: String): SellerInboxEntryEntity?

    @Query("SELECT * FROM txn_seller_inbox_entry WHERE companyId = :companyId AND estimatePoId = :estimatePoId")
    suspend fun findByEstimatePoId(companyId: String, estimatePoId: String): SellerInboxEntryEntity?

    @Query("SELECT * FROM txn_seller_inbox_entry WHERE companyId = :companyId ORDER BY inboxEntryId DESC")
    suspend fun findAllForCompany(companyId: String): List<SellerInboxEntryEntity>
}

@Dao
interface CommercialTransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CommercialTransactionEntity)

    @Query("SELECT * FROM commercial_transaction WHERE companyId = :companyId AND transactionId = :transactionId")
    suspend fun findById(companyId: String, transactionId: String): CommercialTransactionEntity?

    @Query("SELECT * FROM commercial_transaction WHERE companyId = :companyId AND buyerPartyId = :buyerPartyId ORDER BY acceptedAt ASC")
    suspend fun findAllForBuyer(companyId: String, buyerPartyId: String): List<CommercialTransactionEntity>

    @Query("SELECT * FROM commercial_transaction WHERE companyId = :companyId ORDER BY acceptedAt DESC")
    suspend fun findAllForCompany(companyId: String): List<CommercialTransactionEntity>

    @Query("SELECT * FROM commercial_transaction WHERE companyId = :companyId AND buyerPartyId = :buyerPartyId AND state = :state")
    suspend fun findAllForBuyerInState(companyId: String, buyerPartyId: String, state: String): List<CommercialTransactionEntity>
}

@Dao
interface TermsAcknowledgmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TermsAcknowledgmentEntity)

    @Query("SELECT * FROM txn_terms_acknowledgment WHERE companyId = :companyId AND transactionId = :transactionId")
    suspend fun findByTransactionId(companyId: String, transactionId: String): TermsAcknowledgmentEntity?
}

@Dao
interface PaymentEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PaymentEventEntity)

    @Query("SELECT * FROM txn_payment_event WHERE companyId = :companyId AND transactionId = :transactionId ORDER BY installmentSequence ASC")
    suspend fun findAllForTransaction(companyId: String, transactionId: String): List<PaymentEventEntity>

    @Query(
        "SELECT * FROM txn_payment_event WHERE companyId = :companyId AND transactionId = :transactionId " +
            "AND paymentEventId = :paymentEventId",
    )
    suspend fun findById(companyId: String, transactionId: String, paymentEventId: String): PaymentEventEntity?
}

@Dao
interface LedgerIntentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LedgerIntentEntity)

    @Query("SELECT * FROM txn_ledger_intent WHERE companyId = :companyId AND transactionId = :transactionId")
    suspend fun findByTransactionId(companyId: String, transactionId: String): LedgerIntentEntity?
}

@Dao
interface CatalogueAccessGrantDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CatalogueAccessGrantEntity)

    @Query("SELECT * FROM catalogue_access_grant WHERE companyId = :companyId AND grantId = :grantId")
    suspend fun findById(companyId: String, grantId: String): CatalogueAccessGrantEntity?

    /** Every grant ever issued to this buyer, newest first — "active" is derived by the caller via
     * [com.budcom.android.feature.transaction.domain.model.CatalogueAccessGrant.isActive], never a
     * WHERE-clause on `now()` (SQLite has no reliable server-independent notion of "now" to filter
     * on safely, and this keeps expiry evaluation in one place, matching the architecture's
     * checked-on-read discipline). */
    @Query("SELECT * FROM catalogue_access_grant WHERE companyId = :companyId AND buyerPartyId = :buyerPartyId ORDER BY grantedAt DESC")
    suspend fun findAllForBuyer(companyId: String, buyerPartyId: String): List<CatalogueAccessGrantEntity>
}
