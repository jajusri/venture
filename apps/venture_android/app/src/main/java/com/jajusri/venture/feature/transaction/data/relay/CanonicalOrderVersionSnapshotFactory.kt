package com.jajusri.venture.feature.transaction.data.relay

import com.jajusri.venture.feature.transaction.data.local.CanonicalOrderDao
import com.jajusri.venture.feature.transaction.data.repository.toDomain
import com.jajusri.venture.feature.transaction.domain.model.OrderDeliveryEnvelope
import com.jajusri.venture.feature.transaction.domain.model.OrderVersionSnapshot
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CanonicalOrderVersionSnapshotFactory @Inject constructor(
    private val canonicalOrderDao: CanonicalOrderDao,
) {
    suspend fun forEnvelope(envelope: OrderDeliveryEnvelope): OrderVersionSnapshot? {
        val stored = canonicalOrderDao.findById(envelope.companyId, envelope.orderId) ?: return null
        // Business routing authority MUST come from the envelope's own authenticated Business field
        // (recipientBusinessId), never from recipientPartyId -- a Party reference is never
        // interchangeable with Business identity, and materializing/signing a snapshot with a Party
        // ID in the Business slot makes it structurally unverifiable by the recipient's own
        // ingestReceivedOrderVersion(), which correctly compares against its real Business id (relay-
        // authority-repair, 2026-08-29 Party/Business materialization gap). Fail closed, never fall
        // back to recipientPartyId.
        val recipientBusinessId = envelope.recipientBusinessId ?: return null
        val order = stored.toDomain(canonicalOrderDao.findLines(envelope.companyId, envelope.orderId))
        if (order.version != envelope.orderVersion) return null
        return OrderVersionSnapshot.fromCanonicalOrder(order, recipientBusinessId, envelope.envelopeId)
    }
}
