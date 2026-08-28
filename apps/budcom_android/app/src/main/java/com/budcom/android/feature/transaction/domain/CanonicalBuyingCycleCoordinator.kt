package com.budcom.android.feature.transaction.domain

import com.budcom.android.feature.transaction.data.local.CommercialDbTransaction
import com.budcom.android.feature.transaction.domain.model.CanonicalOrder
import com.budcom.android.feature.transaction.domain.model.CanonicalOrderState
import com.budcom.android.feature.transaction.domain.model.CommercialAction
import com.budcom.android.feature.transaction.domain.model.CommercialActionAuthorityContext
import com.budcom.android.feature.transaction.domain.model.CommercialActionAuthorityOutcome
import com.budcom.android.feature.transaction.domain.model.CommercialActionAuthorityRequest
import com.budcom.android.feature.transaction.domain.model.CommercialActionAuthorityResolver
import com.budcom.android.feature.transaction.domain.model.OrderConfirmEvidence
import com.budcom.android.feature.transaction.domain.model.OrderRevisionAcceptEvidence
import com.budcom.android.feature.transaction.domain.model.OrderRevisionLineChange
import com.budcom.android.feature.transaction.domain.model.TransactionTimestamp
import com.budcom.android.feature.transaction.domain.port.RelayOutboxDispatcher
import com.budcom.android.feature.transaction.domain.repository.TransactionRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CanonicalBuyingCycleCoordinator @Inject constructor(
    private val repository: TransactionRepository,
    private val authorityResolver: CommercialActionAuthorityResolver,
    private val dbTransaction: CommercialDbTransaction,
    private val outbox: RelayOutboxDispatcher,
) {
    suspend fun confirmSellerOrder(
        request: CommercialActionAuthorityRequest,
        envelopeId: String,
        eventId: String,
        idempotencyKey: String,
        timestamp: TransactionTimestamp,
        applyOnCompanyId: String,
    ): CanonicalOrder? {
        if (applyOnCompanyId != request.buyerBusinessId) return null
        val recorded = dbTransaction.run {
            val recorded = repository.recordOrderConfirmFromSellerAction(
                request.viewerBusinessId, envelopeId, request.copy(action = CommercialAction.SellerConfirm), eventId, idempotencyKey, timestamp,
            ) ?: return@run null
            recorded
        }
        if (recorded != null) outbox.submitPending(request.viewerBusinessId)
        return recorded?.let { repository.findCanonicalOrderById(request.viewerBusinessId, it.orderId) }
    }

    suspend fun proposeAndSendRevision(
        request: CommercialActionAuthorityRequest,
        envelopeId: String,
        baseline: CanonicalOrder,
        proposedLines: List<OrderRevisionLineChange>,
        reason: String?,
        timestamp: TransactionTimestamp,
        idempotencyKey: String,
    ): CanonicalOrder? {
        val result = dbTransaction.run {
            val revised = repository.proposeOrderRevision(
                request.viewerBusinessId, envelopeId, baseline, proposedLines, reason,
                request.copy(action = CommercialAction.SellerRevise), timestamp, idempotencyKey,
            ) ?: return@run null
            verified(
                request.copy(action = CommercialAction.RevisionSend, orderVersion = revised.version, inboxOrderVersion = revised.version),
                CommercialAction.RevisionSend,
            ) ?: return@run revised
            val envelope = repository.enqueueOrderDelivery(revised, timestamp)
            repository.markRevisionSent(revised.companyId, revised.orderId, envelope) ?: revised
        }
        if (result?.state == CanonicalOrderState.RevisionSent) {
            outbox.submitPending(result.companyId)
        }
        return result
    }

    suspend fun acceptRevision(
        request: CommercialActionAuthorityRequest,
        envelopeId: String,
        eventId: String,
        idempotencyKey: String,
        timestamp: TransactionTimestamp,
    ): CanonicalOrder? {
        val result = dbTransaction.run {
            val recorded = repository.recordOrderRevisionAcceptFromBuyerAction(
                request.viewerBusinessId, envelopeId, request.copy(action = CommercialAction.BuyerAcceptRevision), eventId, idempotencyKey, timestamp,
            ) ?: return@run null
            repository.findCanonicalOrderById(request.viewerBusinessId, recorded.orderId)
        }
        if (result != null) outbox.submitPending(request.viewerBusinessId)
        return result
    }

    private suspend fun verified(request: CommercialActionAuthorityRequest, action: CommercialAction): CommercialActionAuthorityContext? =
        when (val outcome = authorityResolver.resolve(request.copy(action = action))) {
            is CommercialActionAuthorityOutcome.Verified -> outcome.context
            else -> null
        }
}
