package com.budcom.android.feature.transaction.data.repository

import com.budcom.android.core.util.DispatcherProvider
import com.budcom.android.feature.party.domain.model.PartyClassification
import com.budcom.android.feature.party.domain.repository.PartyRepository
import com.budcom.android.feature.transaction.data.local.CatalogueAccessGrantDao
import com.budcom.android.feature.transaction.data.local.CatalogueAccessGrantEntity
import com.budcom.android.feature.transaction.data.local.CanonicalOrderDao
import com.budcom.android.feature.transaction.data.local.CanonicalOrderEntity
import com.budcom.android.feature.transaction.data.local.CanonicalOrderLineEntity
import com.budcom.android.feature.transaction.data.local.OrderDeliveryEnvelopeEntity
import com.budcom.android.feature.transaction.data.local.OrderOutboxDao
import com.budcom.android.feature.transaction.data.local.OrderCommercialEventDao
import com.budcom.android.feature.transaction.data.local.OrderCommercialEventEntity
import com.budcom.android.feature.transaction.data.local.OrderVersionArchiveDao
import com.budcom.android.feature.transaction.data.local.OrderVersionArchiveEntity
import com.budcom.android.feature.transaction.data.local.OrderVersionLineArchiveEntity
import com.budcom.android.feature.transaction.data.local.StructuredRecipientInboxDao
import com.budcom.android.feature.transaction.data.local.StructuredRecipientInboxEntity
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
import com.budcom.android.feature.transaction.data.port.toDomain
import com.budcom.android.feature.transaction.domain.model.CatalogueAccessGrant
import com.budcom.android.feature.transaction.domain.model.CataloguePriceVisibilityGrant
import com.budcom.android.feature.transaction.domain.model.CanonicalOrder
import com.budcom.android.feature.transaction.domain.model.CanonicalOrderLine
import com.budcom.android.feature.transaction.domain.model.CanonicalOrderConfirmedTransitions
import com.budcom.android.feature.transaction.domain.model.CanonicalOrderRevisionAcceptTransitions
import com.budcom.android.feature.transaction.domain.model.CanonicalOrderSeenTransitions
import com.budcom.android.feature.transaction.domain.model.CanonicalOrderSentTransitions
import com.budcom.android.feature.transaction.domain.model.OrderConfirmAuthority
import com.budcom.android.feature.transaction.domain.model.OrderConfirmAuthorityValidation
import com.budcom.android.feature.transaction.domain.model.OrderConfirmEvidence
import com.budcom.android.feature.transaction.domain.model.OrderMaterialChangeClassifier
import com.budcom.android.feature.transaction.domain.model.OrderRevisionAcceptEvidence
import com.budcom.android.feature.transaction.domain.model.OrderRevisionLineChange
import com.budcom.android.feature.transaction.domain.model.OrderCommercialEvent
import com.budcom.android.feature.transaction.domain.model.OrderCommercialEventType
import com.budcom.android.feature.transaction.domain.model.OrderSeenEvidence
import com.budcom.android.feature.transaction.domain.model.OrderStructuredOpenEvent
import com.budcom.android.feature.transaction.domain.model.RecipientInboxTransportState
import com.budcom.android.feature.transaction.domain.model.RecipientOrderSeenOpenTransitions
import com.budcom.android.feature.transaction.domain.model.CanonicalOrderState
import com.budcom.android.feature.transaction.domain.model.RelayAcceptanceEvidence
import com.budcom.android.feature.transaction.domain.model.OrderDeliveryEnvelope
import com.budcom.android.feature.transaction.domain.model.OrderTransportState
import com.budcom.android.feature.transaction.domain.model.CommercialTransaction
import com.budcom.android.feature.transaction.domain.model.CommercialTransactionState
import com.budcom.android.feature.transaction.domain.model.EstimatePo
import com.budcom.android.feature.transaction.domain.model.EstimatePoStatus
import com.budcom.android.feature.transaction.domain.model.LedgerGroupChoice
import com.budcom.android.feature.transaction.domain.model.LedgerIntent
import com.budcom.android.feature.transaction.domain.model.MAX_TERMS_NOTE_LENGTH
import com.budcom.android.feature.transaction.domain.model.PaymentClaimStatus
import com.budcom.android.feature.transaction.domain.model.PaymentEvent
import com.budcom.android.feature.transaction.domain.model.PaymentTiming
import com.budcom.android.feature.transaction.domain.model.SellerInboxAction
import com.budcom.android.feature.transaction.domain.model.SellerInboxEntry
import com.budcom.android.feature.transaction.domain.model.SellerInboxState
import com.budcom.android.feature.transaction.domain.model.SellerInboxTransitions
import com.budcom.android.feature.transaction.domain.model.TermsAcknowledgment
import com.budcom.android.feature.transaction.domain.model.TransactionClock
import com.budcom.android.feature.transaction.domain.model.TransactionDeliveryChannel
import com.budcom.android.feature.transaction.domain.model.TransactionDraft
import com.budcom.android.feature.transaction.domain.model.TransactionDraftLine
import com.budcom.android.feature.transaction.domain.model.TransactionDraftPriceState
import com.budcom.android.feature.transaction.domain.model.TransactionEntryPointType
import com.budcom.android.feature.transaction.domain.model.TransactionLineItem
import com.budcom.android.feature.transaction.domain.model.TransactionStateDerivation
import com.budcom.android.feature.transaction.domain.model.TransactionSubmissionType
import com.budcom.android.feature.transaction.domain.model.TransactionTimestamp
import com.budcom.android.feature.transaction.domain.model.TransactionTimestampSource
import com.budcom.android.feature.transaction.domain.model.toBigDecimalOrNullSafe
import com.budcom.android.feature.transaction.domain.port.OrderSentFromRelayEvidence
import com.budcom.android.feature.transaction.domain.port.TransactionReminderScheduler
import com.budcom.android.feature.transaction.domain.port.TransactionSubmissionPort
import com.budcom.android.feature.transaction.domain.repository.AcceptSellerInboxEntryResult
import com.budcom.android.feature.transaction.domain.repository.NewLineItem
import com.budcom.android.feature.transaction.domain.repository.ProposedTerms
import com.budcom.android.feature.transaction.domain.repository.SellerInboxActionResult
import com.budcom.android.feature.transaction.domain.repository.TransactionRepository
import com.budcom.android.feature.transaction.domain.repository.TransactionSnapshot
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Catalogue integration boundary in code (architecture §3, task's "Catalogue integration
 * boundary" requirement): this class never reads or writes any `catalogue_*` table directly except
 * [NewLineItem.linkedProductId], which is stored opaquely as a convention-keyed reference, exactly
 * like [com.budcom.android.feature.catalogue.data.repository.CatalogueRepositoryImpl] itself
 * references `cached_stock_items` — never a Room `ForeignKey`, never a live re-join back to
 * Catalogue after submission (the whole point of the line-item snapshot, see [createEstimatePo]).
 *
 * Cross-DAO writes here are sequential, matching this codebase's own existing convention —
 * [com.budcom.android.feature.catalogue.data.repository.CatalogueRepositoryImpl] itself has no
 * multi-table `@Transaction`/`withTransaction` wrapper anywhere either. This is an inherited
 * property of the codebase, not a new gap introduced here.
 */
@Singleton
class TransactionRepositoryImpl @Inject constructor(
    private val estimatePoDao: EstimatePoDao,
    private val lineItemDao: EstimatePoLineItemDao,
    private val sellerInboxEntryDao: SellerInboxEntryDao,
    private val transactionDao: CommercialTransactionDao,
    private val termsDao: TermsAcknowledgmentDao,
    private val paymentEventDao: PaymentEventDao,
    private val ledgerIntentDao: LedgerIntentDao,
    private val accessGrantDao: CatalogueAccessGrantDao,
    private val submissionPort: TransactionSubmissionPort,
    private val reminderScheduler: TransactionReminderScheduler,
    private val partyRepository: PartyRepository,
    private val dispatchers: DispatcherProvider,
    private val canonicalOrderDao: CanonicalOrderDao,
    private val orderOutboxDao: OrderOutboxDao,
    private val recipientInboxDao: StructuredRecipientInboxDao,
    private val orderCommercialEventDao: OrderCommercialEventDao,
    private val orderVersionArchiveDao: OrderVersionArchiveDao,
) : TransactionRepository, OrderSentFromRelayEvidence {

    override suspend fun createDraftOrder(
        draft: TransactionDraft,
        creationKey: String,
        note: String?,
        timestamp: TransactionTimestamp,
    ): CanonicalOrder = withContext(dispatchers.io) {
        require(creationKey.isNotBlank())
        require(draft.lines.isNotEmpty())
        require(draft.lines.none { it.priceState == TransactionDraftPriceState.Hidden })
        val existing = canonicalOrderDao.findByCreationKey(draft.companyId, creationKey)
        if (existing != null) return@withContext existing.toDomain(canonicalOrderDao.findLines(draft.companyId, existing.orderId))
        val orderId = UUID.randomUUID().toString()
        val entity = CanonicalOrderEntity(
            companyId = draft.companyId,
            orderId = orderId,
            creationKey = creationKey,
            sellerCompanyId = draft.companyId,
            buyerPartyId = draft.buyerPartyId,
            state = CanonicalOrderState.Draft.columnValue,
            source = TransactionEntryPointType.Catalogue.columnValue,
            submissionType = draft.submissionType.columnValue,
            note = note?.trim()?.takeIf { it.isNotEmpty() },
            createdAt = timestamp.epochMillis,
            createdAtSource = timestamp.source.name,
            version = 1,
        )
        try {
            canonicalOrderDao.insert(entity)
        } catch (_: android.database.SQLException) {
            val raced = canonicalOrderDao.findByCreationKey(draft.companyId, creationKey)
                ?: throw IllegalStateException("Draft Order could not be created")
            return@withContext raced.toDomain(canonicalOrderDao.findLines(draft.companyId, raced.orderId))
        }
        canonicalOrderDao.upsertLines(draft.lines.mapIndexed { index, line ->
            CanonicalOrderLineEntity(
                companyId = draft.companyId,
                orderId = orderId,
                lineId = index.toString().padStart(8, '0'),
                linkedProductId = line.linkedProductId,
                snapshotProductName = line.snapshotProductName,
                snapshotUnit = line.snapshotUnit,
                snapshotSku = line.snapshotSku,
                quantity = line.quantity,
                unitPriceAmount = (line.priceState as? TransactionDraftPriceState.ActualPrice)?.unitAmount,
                unitPriceCurrencyCode = (line.priceState as? TransactionDraftPriceState.ActualPrice)?.currencyCode,
                priceState = line.priceState.toColumnValue(),
                lineTotalAmount = line.lineTotalAmount(),
            )
        })
        entity.toDomain(canonicalOrderDao.findLines(draft.companyId, orderId))
    }

    override suspend fun enqueueOrderDelivery(order: CanonicalOrder, timestamp: TransactionTimestamp): OrderDeliveryEnvelope = withContext(dispatchers.io) {
        require(order.state == CanonicalOrderState.Draft || order.state == CanonicalOrderState.RevisionPending)
        val key = "order:${order.orderId}:v${order.version}"
        val existing = orderOutboxDao.findByIdempotencyKey(order.companyId, key)
        if (existing != null) return@withContext existing.toDomain()
        val entity = OrderDeliveryEnvelopeEntity(
            companyId = order.companyId,
            envelopeId = UUID.randomUUID().toString(),
            idempotencyKey = key,
            objectType = "CANONICAL_ORDER",
            orderId = order.orderId,
            orderVersion = order.version,
            senderCompanyId = order.sellerCompanyId,
            recipientPartyId = order.buyerPartyId,
            createdAt = timestamp.epochMillis,
            createdAtSource = timestamp.source.name,
            state = OrderTransportState.Queued.columnValue,
            attemptCount = 0,
            lastAttemptAt = null,
            lastAttemptAtSource = null,
            lastError = null,
        )
        try {
            orderOutboxDao.insert(entity)
        } catch (_: android.database.SQLException) {
            return@withContext orderOutboxDao.findByIdempotencyKey(order.companyId, key)?.toDomain()
                ?: throw IllegalStateException("Order delivery could not be queued")
        }
        entity.toDomain()
    }

    override suspend fun markOrderSentFromRelayEvidence(
        companyId: String,
        envelope: OrderDeliveryEnvelope,
        evidence: RelayAcceptanceEvidence,
    ): CanonicalOrder? = withContext(dispatchers.io) {
        val stored = canonicalOrderDao.findById(companyId, envelope.orderId) ?: return@withContext null
        val order = stored.toDomain(canonicalOrderDao.findLines(companyId, envelope.orderId))
        val next = CanonicalOrderSentTransitions.apply(order, envelope, evidence) ?: return@withContext null
        if (order.state != next) canonicalOrderDao.updateState(companyId, order.orderId, next.columnValue)
        order.copy(state = next)
    }

    override suspend fun recordOrderSeenFromOpenEvent(
        viewerCompanyId: String,
        envelopeId: String,
        open: OrderStructuredOpenEvent,
    ): OrderCommercialEvent? = withContext(dispatchers.io) {
        val inboxEntity = recipientInboxDao.findByEnvelopeId(viewerCompanyId, envelopeId) ?: return@withContext null
        val inbox = inboxEntity.toInboxDomain()
        val evidence = RecipientOrderSeenOpenTransitions.toEvidence(inbox, open, viewerCompanyId) ?: return@withContext null
        val existing = orderCommercialEventDao.findByIdempotencyKey(viewerCompanyId, open.idempotencyKey)
        if (existing != null) {
            if (existing.eventId != evidence.eventId || existing.orderId != evidence.orderId || existing.orderVersion != evidence.orderVersion) {
                return@withContext null
            }
            return@withContext existing.toDomain()
        }
        val entity = OrderCommercialEventEntity(
            companyId = viewerCompanyId,
            eventId = evidence.eventId,
            idempotencyKey = open.idempotencyKey,
            orderId = evidence.orderId,
            orderVersion = evidence.orderVersion,
            eventType = OrderCommercialEventType.Seen.columnValue,
            actorBusinessId = evidence.viewerBusinessId,
            actorId = evidence.viewerActorId,
            actorDeviceId = evidence.viewerDeviceId,
            counterpartyBusinessId = evidence.senderBusinessId,
            occurredAt = evidence.seenAt.epochMillis,
            occurredAtSource = evidence.seenAt.source.name,
        )
        try {
            orderCommercialEventDao.insert(entity)
        } catch (_: android.database.SQLException) {
            return@withContext orderCommercialEventDao.findByIdempotencyKey(viewerCompanyId, open.idempotencyKey)?.toDomain()
        }
        entity.toDomain()
    }

    override suspend fun applyOrderSeenEvidence(
        companyId: String,
        evidence: OrderSeenEvidence,
    ): CanonicalOrder? = withContext(dispatchers.io) {
        val stored = canonicalOrderDao.findById(companyId, evidence.orderId) ?: return@withContext null
        val order = stored.toDomain(canonicalOrderDao.findLines(companyId, evidence.orderId))
        val next = CanonicalOrderSeenTransitions.apply(order, evidence) ?: return@withContext null
        if (order.state != next) canonicalOrderDao.updateState(companyId, order.orderId, next.columnValue)
        order.copy(state = next)
    }

    override suspend fun findOrderSeenEvidence(
        companyId: String,
        orderId: String,
        orderVersion: Int,
    ): OrderSeenEvidence? = withContext(dispatchers.io) {
        orderCommercialEventDao.findByOrderVersionAndType(
            companyId, orderId, orderVersion, OrderCommercialEventType.Seen.columnValue,
        )?.toSeenEvidence()
    }

    override suspend fun findCanonicalOrderById(companyId: String, orderId: String): CanonicalOrder? = withContext(dispatchers.io) {
        val stored = canonicalOrderDao.findById(companyId, orderId) ?: return@withContext null
        stored.toDomain(canonicalOrderDao.findLines(companyId, orderId))
    }

    override suspend fun recordOrderConfirmFromSellerAction(
        sellerCompanyId: String,
        envelopeId: String,
        authority: OrderConfirmAuthority,
        eventId: String,
        idempotencyKey: String,
        timestamp: TransactionTimestamp,
    ): OrderCommercialEvent? = withContext(dispatchers.io) {
        val existing = orderCommercialEventDao.findByIdempotencyKey(sellerCompanyId, idempotencyKey)
        if (existing != null) return@withContext existing.toDomain()
        val inboxEntity = recipientInboxDao.findByEnvelopeId(sellerCompanyId, envelopeId) ?: return@withContext null
        val inbox = inboxEntity.toInboxDomain()
        if (!authority.permitsOrderConfirm() || authority.businessId != sellerCompanyId) return@withContext null
        if (inbox.companyId != sellerCompanyId || inbox.senderBusinessId == sellerCompanyId) return@withContext null
        orderCommercialEventDao.findByOrderVersionAndType(
            sellerCompanyId, inbox.objectId, inbox.objectVersion, OrderCommercialEventType.Seen.columnValue,
        ) ?: return@withContext null
        val entity = OrderCommercialEventEntity(
            companyId = sellerCompanyId,
            eventId = eventId,
            idempotencyKey = idempotencyKey,
            orderId = inbox.objectId,
            orderVersion = inbox.objectVersion,
            eventType = OrderCommercialEventType.Confirmed.columnValue,
            actorBusinessId = authority.businessId,
            actorId = authority.actorId,
            actorDeviceId = authority.deviceId,
            counterpartyBusinessId = inbox.senderBusinessId,
            occurredAt = timestamp.epochMillis,
            occurredAtSource = timestamp.source.name,
            authorityEpoch = authority.authorityEpoch,
            authorityScopeFingerprint = authority.scopeFingerprint(),
        )
        try {
            orderCommercialEventDao.insert(entity)
        } catch (_: android.database.SQLException) {
            return@withContext orderCommercialEventDao.findByIdempotencyKey(sellerCompanyId, idempotencyKey)?.toDomain()
        }
        entity.toDomain()
    }

    override suspend fun applyOrderConfirmEvidence(companyId: String, evidence: OrderConfirmEvidence): CanonicalOrder? =
        withContext(dispatchers.io) {
            val stored = canonicalOrderDao.findById(companyId, evidence.orderId) ?: return@withContext null
            val order = stored.toDomain(canonicalOrderDao.findLines(companyId, evidence.orderId))
            val next = CanonicalOrderConfirmedTransitions.apply(order, evidence) ?: return@withContext null
            if (order.state != next) canonicalOrderDao.updateState(companyId, order.orderId, next.columnValue)
            order.copy(state = next)
        }

    override suspend fun proposeOrderRevision(
        sellerCompanyId: String,
        envelopeId: String,
        baseline: CanonicalOrder,
        proposedLines: List<OrderRevisionLineChange>,
        revisionReason: String?,
        authority: OrderConfirmAuthority,
        timestamp: TransactionTimestamp,
        idempotencyKey: String,
    ): CanonicalOrder? = withContext(dispatchers.io) {
        if (!authority.permitsOrderConfirm()) return@withContext null
        val inboxEntity = recipientInboxDao.findByEnvelopeId(sellerCompanyId, envelopeId) ?: return@withContext null
        val inbox = inboxEntity.toInboxDomain()
        if (inbox.objectId != baseline.orderId || inbox.objectVersion != baseline.version) return@withContext null
        val material = OrderMaterialChangeClassifier.classify(baseline, proposedLines, revisionReason)
        if (!material.isMaterial) return@withContext null
        val existingRevision = orderCommercialEventDao.findByIdempotencyKey(sellerCompanyId, idempotencyKey)
        if (existingRevision != null) {
            return@withContext findCanonicalOrderById(sellerCompanyId, baseline.orderId)
        }
        val stored = canonicalOrderDao.findById(sellerCompanyId, baseline.orderId)
        if (stored == null) {
            canonicalOrderDao.insert(
                CanonicalOrderEntity(
                    companyId = sellerCompanyId,
                    orderId = baseline.orderId,
                    creationKey = "revision:${baseline.orderId}",
                    sellerCompanyId = inbox.senderBusinessId,
                    buyerPartyId = sellerCompanyId,
                    state = CanonicalOrderState.Seen.columnValue,
                    source = baseline.source.columnValue,
                    submissionType = baseline.submissionType.columnValue,
                    note = baseline.note,
                    createdAt = baseline.createdAt.epochMillis,
                    createdAtSource = baseline.createdAt.source.name,
                    version = baseline.version,
                ),
            )
            canonicalOrderDao.upsertLines(
                baseline.lines.map { line ->
                    CanonicalOrderLineEntity(
                        companyId = sellerCompanyId,
                        orderId = baseline.orderId,
                        lineId = line.lineId,
                        linkedProductId = line.linkedProductId,
                        snapshotProductName = line.snapshotProductName,
                        snapshotUnit = line.snapshotUnit,
                        snapshotSku = line.snapshotSku,
                        quantity = line.quantity,
                        unitPriceAmount = line.unitPriceAmount,
                        unitPriceCurrencyCode = line.unitPriceCurrencyCode,
                        priceState = line.priceState.toColumnValue(),
                        lineTotalAmount = line.lineTotalAmount,
                    )
                },
            )
        }
        archiveCurrentOrderVersion(sellerCompanyId, baseline.orderId, timestamp, revisionReason)
        val nextVersion = baseline.version + 1
        val nextLines = proposedLines.map { line ->
            CanonicalOrderLineEntity(
                companyId = sellerCompanyId,
                orderId = baseline.orderId,
                lineId = line.lineId,
                linkedProductId = line.linkedProductId,
                snapshotProductName = line.snapshotProductName,
                snapshotUnit = line.snapshotUnit,
                snapshotSku = line.snapshotSku,
                quantity = line.quantity,
                unitPriceAmount = line.unitPriceAmount,
                unitPriceCurrencyCode = line.unitPriceCurrencyCode,
                priceState = line.priceState.toColumnValue(),
                lineTotalAmount = line.lineTotalAmount,
            )
        }
        canonicalOrderDao.deleteLines(sellerCompanyId, baseline.orderId)
        canonicalOrderDao.upsertLines(nextLines)
        canonicalOrderDao.updateVersionStateAndNote(
            sellerCompanyId,
            baseline.orderId,
            nextVersion,
            CanonicalOrderState.RevisionPending.columnValue,
            revisionReason,
        )
        val revisionEvent = OrderCommercialEventEntity(
            companyId = sellerCompanyId,
            eventId = UUID.randomUUID().toString(),
            idempotencyKey = idempotencyKey,
            orderId = baseline.orderId,
            orderVersion = nextVersion,
            eventType = OrderCommercialEventType.RevisionProposed.columnValue,
            actorBusinessId = authority.businessId,
            actorId = authority.actorId,
            actorDeviceId = authority.deviceId,
            counterpartyBusinessId = inbox.senderBusinessId,
            occurredAt = timestamp.epochMillis,
            occurredAtSource = timestamp.source.name,
            authorityEpoch = authority.authorityEpoch,
            authorityScopeFingerprint = authority.scopeFingerprint(),
        )
        try {
            orderCommercialEventDao.insert(revisionEvent)
        } catch (_: android.database.SQLException) {
            return@withContext findCanonicalOrderById(sellerCompanyId, baseline.orderId)
        }
        findCanonicalOrderById(sellerCompanyId, baseline.orderId)
    }

    override suspend fun markRevisionSent(companyId: String, orderId: String, envelope: OrderDeliveryEnvelope): CanonicalOrder? =
        withContext(dispatchers.io) {
            val stored = canonicalOrderDao.findById(companyId, orderId) ?: return@withContext null
            val order = stored.toDomain(canonicalOrderDao.findLines(companyId, orderId))
            if (order.version != envelope.orderVersion) return@withContext null
            if (order.state != CanonicalOrderState.RevisionPending) return@withContext null
            canonicalOrderDao.updateState(companyId, orderId, CanonicalOrderState.RevisionSent.columnValue)
            order.copy(state = CanonicalOrderState.RevisionSent)
        }

    override suspend fun recordOrderRevisionAcceptFromBuyerAction(
        buyerCompanyId: String,
        envelopeId: String,
        authority: OrderConfirmAuthority,
        eventId: String,
        idempotencyKey: String,
        timestamp: TransactionTimestamp,
    ): OrderCommercialEvent? = withContext(dispatchers.io) {
        val existing = orderCommercialEventDao.findByIdempotencyKey(buyerCompanyId, idempotencyKey)
        if (existing != null) return@withContext existing.toDomain()
        if (!authority.permitsOrderConfirm()) return@withContext null
        val inboxEntity = recipientInboxDao.findByEnvelopeId(buyerCompanyId, envelopeId) ?: return@withContext null
        val inbox = inboxEntity.toInboxDomain()
        val order = canonicalOrderDao.findById(buyerCompanyId, inbox.objectId) ?: return@withContext null
        if (order.version != inbox.objectVersion) return@withContext null
        val seen = orderCommercialEventDao.findByOrderVersionAndType(
            buyerCompanyId, inbox.objectId, inbox.objectVersion, OrderCommercialEventType.Seen.columnValue,
        ) ?: return@withContext null
        val entity = OrderCommercialEventEntity(
            companyId = buyerCompanyId,
            eventId = eventId,
            idempotencyKey = idempotencyKey,
            orderId = inbox.objectId,
            orderVersion = inbox.objectVersion,
            eventType = OrderCommercialEventType.RevisionAccepted.columnValue,
            actorBusinessId = authority.businessId,
            actorId = authority.actorId,
            actorDeviceId = authority.deviceId,
            counterpartyBusinessId = inbox.senderBusinessId,
            occurredAt = timestamp.epochMillis,
            occurredAtSource = timestamp.source.name,
            authorityEpoch = authority.authorityEpoch,
            authorityScopeFingerprint = authority.scopeFingerprint(),
        )
        try {
            orderCommercialEventDao.insert(entity)
        } catch (_: android.database.SQLException) {
            return@withContext orderCommercialEventDao.findByIdempotencyKey(buyerCompanyId, idempotencyKey)?.toDomain()
        }
        entity.toDomain()
    }

    override suspend fun applyOrderRevisionAcceptEvidence(
        companyId: String,
        evidence: OrderRevisionAcceptEvidence,
    ): CanonicalOrder? = withContext(dispatchers.io) {
        val stored = canonicalOrderDao.findById(companyId, evidence.orderId) ?: return@withContext null
        val order = stored.toDomain(canonicalOrderDao.findLines(companyId, evidence.orderId))
        val next = CanonicalOrderRevisionAcceptTransitions.apply(order, evidence) ?: return@withContext null
        if (order.state != next) canonicalOrderDao.updateState(companyId, order.orderId, next.columnValue)
        order.copy(state = next)
    }

    override suspend fun findArchivedOrderVersion(companyId: String, orderId: String, version: Int): CanonicalOrder? =
        withContext(dispatchers.io) {
            val archived = orderVersionArchiveDao.findOrder(companyId, orderId, version) ?: return@withContext null
            val lines = orderVersionArchiveDao.findLines(companyId, orderId, version)
            archived.toDomain(lines)
        }

    private suspend fun archiveCurrentOrderVersion(
        companyId: String,
        orderId: String,
        timestamp: TransactionTimestamp,
        revisionReason: String?,
    ) {
        val stored = canonicalOrderDao.findById(companyId, orderId) ?: return
        val lines = canonicalOrderDao.findLines(companyId, orderId)
        orderVersionArchiveDao.insertOrder(
            OrderVersionArchiveEntity(
                companyId = stored.companyId,
                orderId = stored.orderId,
                version = stored.version,
                creationKey = stored.creationKey,
                sellerCompanyId = stored.sellerCompanyId,
                buyerPartyId = stored.buyerPartyId,
                state = stored.state,
                source = stored.source,
                submissionType = stored.submissionType,
                note = stored.note,
                createdAt = stored.createdAt,
                createdAtSource = stored.createdAtSource,
                supersedesVersion = if (stored.version > 1) stored.version - 1 else null,
                revisionReason = revisionReason,
                archivedAt = timestamp.epochMillis,
                archivedAtSource = timestamp.source.name,
            ),
        )
        orderVersionArchiveDao.insertLines(
            lines.map { line ->
                OrderVersionLineArchiveEntity(
                    companyId = line.companyId,
                    orderId = line.orderId,
                    version = stored.version,
                    lineId = line.lineId,
                    linkedProductId = line.linkedProductId,
                    snapshotProductName = line.snapshotProductName,
                    snapshotUnit = line.snapshotUnit,
                    snapshotSku = line.snapshotSku,
                    quantity = line.quantity,
                    unitPriceAmount = line.unitPriceAmount,
                    unitPriceCurrencyCode = line.unitPriceCurrencyCode,
                    priceState = line.priceState,
                    lineTotalAmount = line.lineTotalAmount,
                )
            },
        )
    }

    // ============================== §4: Estimate/PO ==============================

    override suspend fun createEstimatePo(
        companyId: String,
        entryPointType: TransactionEntryPointType,
        submissionType: TransactionSubmissionType,
        deliveryChannel: TransactionDeliveryChannel,
        buyerPartyId: String?,
        lineItems: List<NewLineItem>,
        timestamp: TransactionTimestamp,
    ): EstimatePo = withContext(dispatchers.io) {
        val estimatePoId = UUID.randomUUID().toString()
        val currencyCode = lineItems.firstNotNullOfOrNull { it.unitPriceCurrencyCode }
        val total = lineItems.sumAmounts { it.lineTotalAmount }

        val status = when (deliveryChannel) {
            TransactionDeliveryChannel.WhatsAppShared -> EstimatePoStatus.Shared
            TransactionDeliveryChannel.InAppSubmitted -> EstimatePoStatus.Submitted
        }

        estimatePoDao.upsert(
            EstimatePoEntity(
                companyId = companyId,
                estimatePoId = estimatePoId,
                entryPointType = entryPointType.columnValue,
                submissionType = submissionType.columnValue,
                deliveryChannel = deliveryChannel.columnValue,
                buyerPartyId = buyerPartyId,
                totalAmount = total,
                currencyCode = currencyCode,
                status = status.columnValue,
                submittedAt = timestamp.epochMillis,
                submittedAtSource = timestamp.source.name,
            ),
        )

        val lineEntities = lineItems.map { line ->
            EstimatePoLineItemEntity(
                companyId = companyId,
                estimatePoId = estimatePoId,
                lineItemId = UUID.randomUUID().toString(),
                linkedProductId = line.linkedProductId,
                snapshotProductName = line.snapshotProductName,
                snapshotUnit = line.snapshotUnit,
                snapshotSku = line.snapshotSku,
                quantity = line.quantity,
                unitPriceAmount = line.unitPriceAmount,
                unitPriceCurrencyCode = line.unitPriceCurrencyCode,
                lineTotalAmount = line.lineTotalAmount,
                isContactForPrice = line.isContactForPrice,
            )
        }
        if (lineEntities.isNotEmpty()) lineItemDao.upsertAll(lineEntities)

        // Architecture §5: only IN_APP_SUBMITTED ever creates a seller-inbox entry — enforced
        // structurally here, not merely by UI convention.
        if (deliveryChannel == TransactionDeliveryChannel.InAppSubmitted) {
            submissionPort.deliverToSellerInbox(companyId, estimatePoId)
        }

        EstimatePo(
            companyId = companyId,
            estimatePoId = estimatePoId,
            entryPointType = entryPointType,
            submissionType = submissionType,
            deliveryChannel = deliveryChannel,
            buyerPartyId = buyerPartyId,
            totalAmount = total,
            currencyCode = currencyCode,
            status = status,
            submittedAt = timestamp,
            lineItems = lineEntities.map { it.toDomain() },
        )
    }

    override suspend fun findEstimatePoById(companyId: String, estimatePoId: String): EstimatePo? =
        withContext(dispatchers.io) {
            val entity = estimatePoDao.findById(companyId, estimatePoId) ?: return@withContext null
            val lines = lineItemDao.findAllForEstimatePo(companyId, estimatePoId)
            entity.toDomain(lines.map { it.toDomain() })
        }

    // ============================== §5: seller inbox ==============================

    override suspend fun findSellerInboxEntry(companyId: String, inboxEntryId: String): SellerInboxEntry? =
        withContext(dispatchers.io) { sellerInboxEntryDao.findById(companyId, inboxEntryId)?.toDomain() }

    override suspend fun findAllSellerInboxEntries(companyId: String): List<SellerInboxEntry> =
        withContext(dispatchers.io) { sellerInboxEntryDao.findAllForCompany(companyId).map { it.toDomain() } }

    override suspend fun acknowledgeSellerInboxEntry(
        companyId: String,
        inboxEntryId: String,
        timestamp: TransactionTimestamp,
    ): SellerInboxActionResult = withContext(dispatchers.io) {
        val entity = sellerInboxEntryDao.findById(companyId, inboxEntryId)
            ?: return@withContext SellerInboxActionResult.NotFound
        val current = SellerInboxState.fromColumn(entity.state)
        val next = SellerInboxTransitions.transition(current, SellerInboxAction.Acknowledge)
            ?: return@withContext SellerInboxActionResult.InvalidState
        val updated = entity.copy(
            state = next.columnValue,
            acknowledgedAt = timestamp.epochMillis,
            acknowledgedAtSource = timestamp.source.name,
        )
        sellerInboxEntryDao.upsert(updated)
        SellerInboxActionResult.Success(updated.toDomain())
    }

    override suspend fun requestChangesOnSellerInboxEntry(
        companyId: String,
        inboxEntryId: String,
        note: String,
        timestamp: TransactionTimestamp,
    ): SellerInboxActionResult = withContext(dispatchers.io) {
        val entity = sellerInboxEntryDao.findById(companyId, inboxEntryId)
            ?: return@withContext SellerInboxActionResult.NotFound
        val current = SellerInboxState.fromColumn(entity.state)
        val next = SellerInboxTransitions.transition(current, SellerInboxAction.RequestChanges)
            ?: return@withContext SellerInboxActionResult.InvalidState
        val updated = entity.copy(
            state = next.columnValue,
            changeRequestNote = note,
            respondedAt = timestamp.epochMillis,
            respondedAtSource = timestamp.source.name,
        )
        sellerInboxEntryDao.upsert(updated)
        SellerInboxActionResult.Success(updated.toDomain())
    }

    override suspend fun acceptSellerInboxEntry(
        companyId: String,
        inboxEntryId: String,
        ledgerGroupChoice: LedgerGroupChoice,
        proposedTerms: ProposedTerms,
        timestamp: TransactionTimestamp,
    ): AcceptSellerInboxEntryResult = withContext(dispatchers.io) {
        if ((proposedTerms.note?.length ?: 0) > MAX_TERMS_NOTE_LENGTH) {
            return@withContext AcceptSellerInboxEntryResult.NoteTooLong
        }

        val inboxEntity = sellerInboxEntryDao.findById(companyId, inboxEntryId)
            ?: return@withContext AcceptSellerInboxEntryResult.InboxEntryNotFound
        val currentInboxState = SellerInboxState.fromColumn(inboxEntity.state)
        SellerInboxTransitions.transition(currentInboxState, SellerInboxAction.Accept)
            ?: return@withContext AcceptSellerInboxEntryResult.InvalidState

        val estimatePo = estimatePoDao.findById(companyId, inboxEntity.estimatePoId)
            ?: return@withContext AcceptSellerInboxEntryResult.InboxEntryNotFound
        val buyerPartyId = estimatePo.buyerPartyId
            ?: return@withContext AcceptSellerInboxEntryResult.InvalidState

        // (2) Transaction
        val transactionId = UUID.randomUUID().toString()
        val transactionEntity = CommercialTransactionEntity(
            companyId = companyId,
            transactionId = transactionId,
            estimatePoId = estimatePo.estimatePoId,
            buyerPartyId = buyerPartyId,
            state = CommercialTransactionState.PendingConfirmation.columnValue,
            totalAmount = estimatePo.totalAmount,
            currencyCode = estimatePo.currencyCode,
            acceptedAt = timestamp.epochMillis,
            acceptedAtSource = timestamp.source.name,
            completedAt = null,
            completedAtSource = null,
        )
        transactionDao.upsert(transactionEntity)

        // (3) Terms Acknowledgment — seller-proposed, both confirmations still pending.
        val termsEntity = TermsAcknowledgmentEntity(
            companyId = companyId,
            transactionId = transactionId,
            paymentTiming = proposedTerms.paymentTiming.columnValue,
            creditDays = proposedTerms.creditDays,
            partialAdvancePercent = proposedTerms.partialAdvancePercent,
            partialBalanceTiming = proposedTerms.partialBalanceTiming,
            amount = estimatePo.totalAmount,
            currencyCode = estimatePo.currencyCode,
            note = proposedTerms.note,
            proposedAt = timestamp.epochMillis,
            proposedAtSource = timestamp.source.name,
            buyerConfirmedAt = null,
            buyerConfirmedAtSource = null,
            sellerConfirmedAt = null,
            sellerConfirmedAtSource = null,
        )
        termsDao.upsert(termsEntity)

        // (4) Prospect -> Ledger (architecture §6) — reuses the existing Party foundation only;
        // never creates a parallel identity table, never writes Tally directly.
        val party = partyRepository.getPartyById(companyId, buyerPartyId)
        val wasProspect = party?.classification == PartyClassification.Prospect
        if (wasProspect) {
            partyRepository.promoteProspectToCustomer(companyId, buyerPartyId)
        }
        ledgerIntentDao.upsert(
            LedgerIntentEntity(
                companyId = companyId,
                transactionId = transactionId,
                buyerPartyId = buyerPartyId,
                chosenLedgerGroup = ledgerGroupChoice.columnValue,
                promotedProspectAt = if (wasProspect) timestamp.epochMillis else null,
                promotedProspectAtSource = if (wasProspect) timestamp.source.name else null,
                recordedAt = timestamp.epochMillis,
                recordedAtSource = timestamp.source.name,
            ),
        )

        // (1) Inbox: Accepted -> Converted, linking back to the new transaction.
        sellerInboxEntryDao.upsert(
            inboxEntity.copy(
                state = SellerInboxState.Converted.columnValue,
                respondedAt = timestamp.epochMillis,
                respondedAtSource = timestamp.source.name,
                convertedTransactionId = transactionId,
            ),
        )

        AcceptSellerInboxEntryResult.Success(transactionEntity.toDomain(), termsEntity.toDomain())
    }

    // ============================== §7/§8: terms, state, payment ==============================

    override suspend fun findTransactionById(companyId: String, transactionId: String): CommercialTransaction? =
        withContext(dispatchers.io) { transactionDao.findById(companyId, transactionId)?.toDomain() }

    override suspend fun findTermsForTransaction(companyId: String, transactionId: String): TermsAcknowledgment? =
        withContext(dispatchers.io) { termsDao.findByTransactionId(companyId, transactionId)?.toDomain() }

    override suspend fun confirmTermsAsBuyer(
        companyId: String,
        transactionId: String,
        timestamp: TransactionTimestamp,
    ): TermsAcknowledgment? = withContext(dispatchers.io) {
        val terms = termsDao.findByTransactionId(companyId, transactionId) ?: return@withContext null
        val updated = terms.copy(buyerConfirmedAt = timestamp.epochMillis, buyerConfirmedAtSource = timestamp.source.name)
        termsDao.upsert(updated)
        recomputeAndPersistState(companyId, transactionId)
        updated.toDomain()
    }

    override suspend fun confirmTermsAsSeller(
        companyId: String,
        transactionId: String,
        timestamp: TransactionTimestamp,
    ): TermsAcknowledgment? = withContext(dispatchers.io) {
        val terms = termsDao.findByTransactionId(companyId, transactionId) ?: return@withContext null
        val updated = terms.copy(sellerConfirmedAt = timestamp.epochMillis, sellerConfirmedAtSource = timestamp.source.name)
        termsDao.upsert(updated)
        val transaction = recomputeAndPersistState(companyId, transactionId)
        if (transaction?.state == CommercialTransactionState.Agreed) {
            reminderScheduler.scheduleReminders(companyId, transactionId)
        }
        updated.toDomain()
    }

    override suspend fun findPaymentEventsForTransaction(companyId: String, transactionId: String): List<PaymentEvent> =
        withContext(dispatchers.io) { paymentEventDao.findAllForTransaction(companyId, transactionId).map { it.toDomain() } }

    override suspend fun recordPaymentClaim(
        companyId: String,
        transactionId: String,
        claimedAmount: String,
        currencyCode: String?,
        isFinalOrPartial: PaymentClaimStatus,
        timestamp: TransactionTimestamp,
    ): PaymentEvent? = withContext(dispatchers.io) {
        if (transactionDao.findById(companyId, transactionId) == null) return@withContext null
        val existing = paymentEventDao.findAllForTransaction(companyId, transactionId)
        val entity = PaymentEventEntity(
            companyId = companyId,
            transactionId = transactionId,
            paymentEventId = UUID.randomUUID().toString(),
            installmentSequence = existing.size + 1,
            buyerClaimStatus = isFinalOrPartial.columnValue,
            buyerClaimedAmount = claimedAmount,
            currencyCode = currencyCode,
            buyerClaimedAt = timestamp.epochMillis,
            buyerClaimedAtSource = timestamp.source.name,
            sellerConfirmed = false,
            sellerConfirmedAt = null,
            sellerConfirmedAtSource = null,
            sellerDiscrepancyNote = null,
        )
        paymentEventDao.upsert(entity)
        recomputeAndPersistState(companyId, transactionId)
        entity.toDomain()
    }

    override suspend fun confirmPaymentReceived(
        companyId: String,
        transactionId: String,
        paymentEventId: String,
        timestamp: TransactionTimestamp,
        discrepancyNote: String?,
    ): CommercialTransaction? = withContext(dispatchers.io) {
        val event = paymentEventDao.findById(companyId, transactionId, paymentEventId) ?: return@withContext null
        paymentEventDao.upsert(
            event.copy(
                sellerConfirmed = true,
                sellerConfirmedAt = timestamp.epochMillis,
                sellerConfirmedAtSource = timestamp.source.name,
                sellerDiscrepancyNote = discrepancyNote,
            ),
        )
        val transaction = recomputeAndPersistState(companyId, transactionId, completionTimestamp = timestamp)
        if (transaction?.state == CommercialTransactionState.Completed) {
            reminderScheduler.cancelReminders(companyId, transactionId)
        }
        transaction
    }

    /** Single place [CommercialTransactionEntity.state]/[CommercialTransactionEntity.completedAt]
     * are ever written after initial creation — always a projection of
     * [TransactionStateDerivation.deriveState], never set ad hoc at a call site (architecture §8's
     * persisted-vs-derived discipline, enforced structurally by funneling every state-affecting
     * write through this one function). */
    private suspend fun recomputeAndPersistState(
        companyId: String,
        transactionId: String,
        completionTimestamp: TransactionTimestamp? = null,
    ): CommercialTransaction? {
        val transactionEntity = transactionDao.findById(companyId, transactionId) ?: return null
        val terms = termsDao.findByTransactionId(companyId, transactionId) ?: return transactionEntity.toDomain()
        val events = paymentEventDao.findAllForTransaction(companyId, transactionId).map { it.toDomain() }
        val transaction = transactionEntity.toDomain()
        val newState = TransactionStateDerivation.deriveState(transaction, terms.toDomain(), events)

        val alreadyCompleted = transactionEntity.completedAt != null
        val nowCompleting = newState == CommercialTransactionState.Completed && !alreadyCompleted
        val updated = transactionEntity.copy(
            state = newState.columnValue,
            completedAt = if (nowCompleting) completionTimestamp?.epochMillis ?: transactionEntity.completedAt else transactionEntity.completedAt,
            completedAtSource = if (nowCompleting) completionTimestamp?.source?.name ?: transactionEntity.completedAtSource else transactionEntity.completedAtSource,
        )
        transactionDao.upsert(updated)
        return updated.toDomain()
    }

    // ============================== §8 Q13: My Transactions ==============================

    override suspend fun findTransactionsForCounterparty(companyId: String, buyerPartyId: String): List<CommercialTransaction> =
        withContext(dispatchers.io) { transactionDao.findAllForBuyer(companyId, buyerPartyId).map { it.toDomain() } }

    override suspend fun findAllTransactionsForCompany(companyId: String): List<CommercialTransaction> =
        withContext(dispatchers.io) { transactionDao.findAllForCompany(companyId).map { it.toDomain() } }

    override suspend fun findTransactionSnapshot(companyId: String, transactionId: String): TransactionSnapshot? =
        withContext(dispatchers.io) {
            val transaction = transactionDao.findById(companyId, transactionId)?.toDomain() ?: return@withContext null
            val terms = termsDao.findByTransactionId(companyId, transactionId)?.toDomain() ?: return@withContext null
            val events = paymentEventDao.findAllForTransaction(companyId, transactionId).map { it.toDomain() }
            TransactionSnapshot(
                transaction = transaction,
                terms = terms,
                paymentEvents = events,
                mutualAgreementStatus = TransactionStateDerivation.mutualAgreementStatus(terms),
                isOverdue = TransactionStateDerivation.isOverdue(transaction, terms, System.currentTimeMillis()),
            )
        }

    // ============================== §8 task-9: buying history ==============================

    override suspend fun findCompletedPurchaseHistory(companyId: String, buyerPartyId: String): List<TransactionLineItem> =
        withContext(dispatchers.io) {
            val completed = transactionDao.findAllForBuyerInState(companyId, buyerPartyId, CommercialTransactionState.Completed.columnValue)
            completed.sortedBy { it.acceptedAt }
                .flatMap { lineItemDao.findAllForEstimatePo(companyId, it.estimatePoId) }
                .map { it.toDomain() }
        }

    // ============================== §10: Catalogue access grant ==============================

    override suspend fun grantCatalogueAccess(
        companyId: String,
        buyerPartyId: String,
        expiresAt: TransactionTimestamp?,
        timestamp: TransactionTimestamp,
    ): CatalogueAccessGrant = withContext(dispatchers.io) {
        val entity = CatalogueAccessGrantEntity(
            companyId = companyId,
            grantId = UUID.randomUUID().toString(),
            buyerPartyId = buyerPartyId,
            priceVisibility = CataloguePriceVisibilityGrant.Open.columnValue,
            grantedAt = timestamp.epochMillis,
            grantedAtSource = timestamp.source.name,
            expiresAt = expiresAt?.epochMillis,
            revokedAt = null,
            revokedAtSource = null,
        )
        accessGrantDao.upsert(entity)
        entity.toDomain()
    }

    override suspend fun revokeCatalogueAccess(companyId: String, grantId: String, timestamp: TransactionTimestamp) {
        withContext(dispatchers.io) {
            val entity = accessGrantDao.findById(companyId, grantId) ?: return@withContext
            accessGrantDao.upsert(entity.copy(revokedAt = timestamp.epochMillis, revokedAtSource = timestamp.source.name))
        }
    }

    override suspend fun findActiveCatalogueAccessGrant(
        companyId: String,
        buyerPartyId: String,
        nowEpochMillis: Long,
    ): CatalogueAccessGrant? = withContext(dispatchers.io) {
        accessGrantDao.findAllForBuyer(companyId, buyerPartyId)
            .map { it.toDomain() }
            .firstOrNull { it.isActive(nowEpochMillis) }
    }
}

// ============================== mapping ==============================

internal fun toTimestampOrNull(epochMillis: Long?, source: String?): TransactionTimestamp? =
    if (epochMillis == null || source == null) null else TransactionTimestamp(epochMillis, TransactionTimestampSource.valueOf(source))

private fun List<NewLineItem>.sumAmounts(selector: (NewLineItem) -> String?): String =
    this.fold(java.math.BigDecimal.ZERO) { acc, item ->
        val amount = selector(item)?.let { runCatching { java.math.BigDecimal(it) }.getOrNull() } ?: java.math.BigDecimal.ZERO
        acc + amount
    }.toPlainString()

private fun TransactionDraftPriceState.toColumnValue(): String = when (this) {
    is TransactionDraftPriceState.ActualPrice -> "ACTUAL"
    TransactionDraftPriceState.NoPriceSupplied -> "NO_PRICE_SUPPLIED"
    TransactionDraftPriceState.ContactForPrice -> "CONTACT_FOR_PRICE"
    TransactionDraftPriceState.Hidden -> "HIDDEN"
}

private fun TransactionDraftLine.lineTotalAmount(): String? {
    val price = (priceState as? TransactionDraftPriceState.ActualPrice)?.unitAmount?.toBigDecimalOrNullSafe() ?: return null
    val quantity = quantity.toBigDecimalOrNullSafe() ?: return null
    return price.multiply(quantity).toPlainString()
}

private fun CanonicalOrderEntity.toDomain(lines: List<CanonicalOrderLineEntity>): CanonicalOrder = CanonicalOrder(
    companyId = companyId,
    orderId = orderId,
    creationKey = creationKey,
    sellerCompanyId = sellerCompanyId,
    buyerPartyId = buyerPartyId,
    state = CanonicalOrderState.fromColumn(state),
    source = TransactionEntryPointType.fromColumn(source),
    submissionType = TransactionSubmissionType.fromColumn(submissionType),
    note = note,
    createdAt = TransactionTimestamp(createdAt, TransactionTimestampSource.valueOf(createdAtSource)),
    version = version,
    lines = lines.map { it.toDomain() },
)

private fun CanonicalOrderLineEntity.toDomain(): CanonicalOrderLine = CanonicalOrderLine(
    orderId = orderId,
    lineId = lineId,
    linkedProductId = linkedProductId,
    snapshotProductName = snapshotProductName,
    snapshotUnit = snapshotUnit,
    snapshotSku = snapshotSku,
    quantity = quantity,
    unitPriceAmount = unitPriceAmount,
    unitPriceCurrencyCode = unitPriceCurrencyCode,
    priceState = when (priceState) {
        "ACTUAL" -> TransactionDraftPriceState.ActualPrice(requireNotNull(unitPriceAmount), unitPriceCurrencyCode)
        "NO_PRICE_SUPPLIED" -> TransactionDraftPriceState.NoPriceSupplied
        "CONTACT_FOR_PRICE" -> TransactionDraftPriceState.ContactForPrice
        else -> TransactionDraftPriceState.Hidden
    },
    lineTotalAmount = lineTotalAmount,
)

private fun OrderDeliveryEnvelopeEntity.toDomain(): OrderDeliveryEnvelope = OrderDeliveryEnvelope(
    companyId = companyId,
    envelopeId = envelopeId,
    idempotencyKey = idempotencyKey,
    objectType = objectType,
    orderId = orderId,
    orderVersion = orderVersion,
    senderCompanyId = senderCompanyId,
    recipientPartyId = recipientPartyId,
    createdAt = TransactionTimestamp(createdAt, TransactionTimestampSource.valueOf(createdAtSource)),
    state = OrderTransportState.fromColumn(state),
    attemptCount = attemptCount,
    lastAttemptAt = toTimestampOrNull(lastAttemptAt, lastAttemptAtSource),
    lastError = lastError,
)

private fun EstimatePoLineItemEntity.toDomain(): TransactionLineItem = TransactionLineItem(
    estimatePoId = estimatePoId,
    lineItemId = lineItemId,
    linkedProductId = linkedProductId,
    snapshotProductName = snapshotProductName,
    snapshotUnit = snapshotUnit,
    snapshotSku = snapshotSku,
    quantity = quantity,
    unitPriceAmount = unitPriceAmount,
    unitPriceCurrencyCode = unitPriceCurrencyCode,
    lineTotalAmount = lineTotalAmount,
    isContactForPrice = isContactForPrice,
)

private fun EstimatePoEntity.toDomain(lines: List<TransactionLineItem>): EstimatePo = EstimatePo(
    companyId = companyId,
    estimatePoId = estimatePoId,
    entryPointType = TransactionEntryPointType.fromColumn(entryPointType),
    submissionType = TransactionSubmissionType.fromColumn(submissionType),
    deliveryChannel = TransactionDeliveryChannel.fromColumn(deliveryChannel),
    buyerPartyId = buyerPartyId,
    totalAmount = totalAmount,
    currencyCode = currencyCode,
    status = EstimatePoStatus.fromColumn(status),
    submittedAt = TransactionTimestamp(submittedAt, TransactionTimestampSource.valueOf(submittedAtSource)),
    lineItems = lines,
)

private fun CommercialTransactionEntity.toDomain(): CommercialTransaction = CommercialTransaction(
    companyId = companyId,
    transactionId = transactionId,
    estimatePoId = estimatePoId,
    buyerPartyId = buyerPartyId,
    state = CommercialTransactionState.fromColumn(state),
    totalAmount = totalAmount,
    currencyCode = currencyCode,
    acceptedAt = TransactionTimestamp(acceptedAt, TransactionTimestampSource.valueOf(acceptedAtSource)),
    completedAt = toTimestampOrNull(completedAt, completedAtSource),
)

private fun TermsAcknowledgmentEntity.toDomain(): TermsAcknowledgment = TermsAcknowledgment(
    companyId = companyId,
    transactionId = transactionId,
    paymentTiming = PaymentTiming.fromColumn(paymentTiming),
    creditDays = creditDays,
    partialAdvancePercent = partialAdvancePercent,
    partialBalanceTiming = partialBalanceTiming,
    amount = amount,
    currencyCode = currencyCode,
    note = note,
    proposedAt = TransactionTimestamp(proposedAt, TransactionTimestampSource.valueOf(proposedAtSource)),
    buyerConfirmedAt = toTimestampOrNull(buyerConfirmedAt, buyerConfirmedAtSource),
    sellerConfirmedAt = toTimestampOrNull(sellerConfirmedAt, sellerConfirmedAtSource),
)

private fun PaymentEventEntity.toDomain(): PaymentEvent = PaymentEvent(
    companyId = companyId,
    transactionId = transactionId,
    paymentEventId = paymentEventId,
    installmentSequence = installmentSequence,
    buyerClaimStatus = PaymentClaimStatus.fromColumn(buyerClaimStatus),
    buyerClaimedAmount = buyerClaimedAmount,
    currencyCode = currencyCode,
    buyerClaimedAt = TransactionTimestamp(buyerClaimedAt, TransactionTimestampSource.valueOf(buyerClaimedAtSource)),
    sellerConfirmed = sellerConfirmed,
    sellerConfirmedAt = toTimestampOrNull(sellerConfirmedAt, sellerConfirmedAtSource),
    sellerDiscrepancyNote = sellerDiscrepancyNote,
)

private fun LedgerIntentEntity.toDomain(): LedgerIntent = LedgerIntent(
    companyId = companyId,
    transactionId = transactionId,
    buyerPartyId = buyerPartyId,
    chosenLedgerGroup = LedgerGroupChoice.fromColumn(chosenLedgerGroup),
    promotedProspectAt = toTimestampOrNull(promotedProspectAt, promotedProspectAtSource),
    recordedAt = TransactionTimestamp(recordedAt, TransactionTimestampSource.valueOf(recordedAtSource)),
)

private fun CatalogueAccessGrantEntity.toDomain(): CatalogueAccessGrant = CatalogueAccessGrant(
    companyId = companyId,
    grantId = grantId,
    buyerPartyId = buyerPartyId,
    priceVisibility = CataloguePriceVisibilityGrant.fromColumn(priceVisibility),
    grantedAt = TransactionTimestamp(grantedAt, TransactionTimestampSource.valueOf(grantedAtSource)),
    expiresAtEpochMillis = expiresAt,
    revokedAt = toTimestampOrNull(revokedAt, revokedAtSource),
)

private fun StructuredRecipientInboxEntity.toInboxDomain() = com.budcom.android.feature.transaction.domain.model.StructuredRecipientInboxEntry(
    companyId = companyId,
    envelopeId = envelopeId,
    idempotencyKey = idempotencyKey,
    objectType = objectType,
    objectId = objectId,
    objectVersion = objectVersion,
    senderBusinessId = senderBusinessId,
    senderActorId = senderActorId,
    senderDeviceId = senderDeviceId,
    mailboxSequence = mailboxSequence,
    acceptanceId = acceptanceId,
    acceptedAt = TransactionTimestamp(acceptedAt, TransactionTimestampSource.valueOf(acceptedAtSource)),
    ingestedAt = TransactionTimestamp(ingestedAt, TransactionTimestampSource.valueOf(ingestedAtSource)),
    transportState = RecipientInboxTransportState.fromColumn(transportState),
)

private fun OrderCommercialEventEntity.toDomain() = OrderCommercialEvent(
    companyId = companyId,
    eventId = eventId,
    idempotencyKey = idempotencyKey,
    orderId = orderId,
    orderVersion = orderVersion,
    eventType = OrderCommercialEventType.fromColumn(eventType),
    actorBusinessId = actorBusinessId,
    actorId = actorId,
    actorDeviceId = actorDeviceId,
    counterpartyBusinessId = counterpartyBusinessId,
    occurredAt = TransactionTimestamp(occurredAt, TransactionTimestampSource.valueOf(occurredAtSource)),
)

private fun OrderCommercialEventEntity.toSeenEvidence() = OrderSeenEvidence(
    eventId = eventId,
    orderId = orderId,
    orderVersion = orderVersion,
    viewerBusinessId = actorBusinessId,
    viewerActorId = actorId,
    viewerDeviceId = actorDeviceId.orEmpty(),
    senderBusinessId = counterpartyBusinessId,
    seenAt = TransactionTimestamp(occurredAt, TransactionTimestampSource.valueOf(occurredAtSource)),
)

private fun OrderVersionArchiveEntity.toDomain(lines: List<OrderVersionLineArchiveEntity>): CanonicalOrder = CanonicalOrder(
    companyId = companyId,
    orderId = orderId,
    creationKey = creationKey,
    sellerCompanyId = sellerCompanyId,
    buyerPartyId = buyerPartyId,
    state = CanonicalOrderState.fromColumn(state),
    source = TransactionEntryPointType.fromColumn(source),
    submissionType = TransactionSubmissionType.fromColumn(submissionType),
    note = note,
    createdAt = TransactionTimestamp(createdAt, TransactionTimestampSource.valueOf(createdAtSource)),
    version = version,
    lines = lines.map { line ->
        CanonicalOrderLine(
            orderId = line.orderId,
            lineId = line.lineId,
            linkedProductId = line.linkedProductId,
            snapshotProductName = line.snapshotProductName,
            snapshotUnit = line.snapshotUnit,
            snapshotSku = line.snapshotSku,
            quantity = line.quantity,
            unitPriceAmount = line.unitPriceAmount,
            unitPriceCurrencyCode = line.unitPriceCurrencyCode,
            priceState = when (line.priceState) {
                "ACTUAL" -> TransactionDraftPriceState.ActualPrice(requireNotNull(line.unitPriceAmount), line.unitPriceCurrencyCode)
                "NO_PRICE_SUPPLIED" -> TransactionDraftPriceState.NoPriceSupplied
                "CONTACT_FOR_PRICE" -> TransactionDraftPriceState.ContactForPrice
                else -> TransactionDraftPriceState.Hidden
            },
            lineTotalAmount = line.lineTotalAmount,
        )
    },
)
