package com.budcom.android.feature.transaction.data.relay

import com.budcom.android.feature.transaction.data.local.CanonicalOrderDao
import com.budcom.android.feature.transaction.data.repository.toDomain
import com.budcom.android.feature.transaction.domain.model.OrderDeliveryEnvelope
import com.budcom.android.feature.transaction.domain.model.OrderVersionSnapshot
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CanonicalOrderVersionSnapshotFactory @Inject constructor(
    private val canonicalOrderDao: CanonicalOrderDao,
) {
    suspend fun forEnvelope(envelope: OrderDeliveryEnvelope): OrderVersionSnapshot? {
        val stored = canonicalOrderDao.findById(envelope.companyId, envelope.orderId) ?: return null
        val recipient = envelope.recipientPartyId ?: return null
        val order = stored.toDomain(canonicalOrderDao.findLines(envelope.companyId, envelope.orderId))
        if (order.version != envelope.orderVersion) return null
        return OrderVersionSnapshot.fromCanonicalOrder(order, recipient, envelope.envelopeId)
    }
}
