package com.jajusri.venture.feature.transaction.data.relay

import com.jajusri.venture.feature.transaction.data.local.CanonicalOrderEntity
import com.jajusri.venture.feature.transaction.data.local.CanonicalOrderLineEntity
import com.jajusri.venture.feature.transaction.data.repository.FakeCanonicalOrderDao
import com.jajusri.venture.feature.transaction.domain.model.CanonicalOrderState
import com.jajusri.venture.feature.transaction.domain.model.OrderDeliveryEnvelope
import com.jajusri.venture.feature.transaction.domain.model.OrderTransportState
import com.jajusri.venture.feature.transaction.domain.model.TransactionTimestamp
import com.jajusri.venture.feature.transaction.domain.model.TransactionTimestampSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Party/Business materialization gap regression (relay-authority-repair, 2026-08-29). Reproduces the
 * exact physically-observed bug: `forEnvelope()` once passed `envelope.recipientPartyId` (a local
 * Party UUID, meaningless to the receiving Business) into `OrderVersionSnapshot.recipientBusinessId`,
 * making the resulting snapshot structurally unverifiable by the recipient's own
 * `ingestReceivedOrderVersion()` -- Phone B's Fetch always found the item but never materialized it.
 */
class CanonicalOrderVersionSnapshotFactoryTest {
    private val dao = FakeCanonicalOrderDao()
    private val factory = CanonicalOrderVersionSnapshotFactory(dao)

    private suspend fun seedOrder() {
        dao.insert(
            CanonicalOrderEntity(
                companyId = "buyer-co", orderId = "order-1", creationKey = "k1", sellerCompanyId = "buyer-co",
                buyerPartyId = "party-seller-local-ref", state = CanonicalOrderState.Draft.columnValue,
                source = "CATALOGUE", submissionType = "ESTIMATE", note = null, createdAt = 100,
                createdAtSource = TransactionTimestampSource.DeviceLocalProvisional.name, version = 1,
                buyerBusinessId = "buyer-co", sellerBusinessId = "seller-co",
            ),
        )
        dao.upsertLines(
            listOf(
                CanonicalOrderLineEntity(
                    companyId = "buyer-co", orderId = "order-1", lineId = "line-1", linkedProductId = "p1",
                    snapshotProductName = "Widget", snapshotUnit = "Nos", snapshotSku = null, quantity = "1",
                    unitPriceAmount = null, unitPriceCurrencyCode = null, priceState = "NO_PRICE_SUPPLIED", lineTotalAmount = null,
                ),
            ),
        )
    }

    private fun envelope(recipientBusinessId: String?, recipientPartyId: String?) = OrderDeliveryEnvelope(
        companyId = "buyer-co", envelopeId = "env-1", idempotencyKey = "order:order-1:v1", objectType = "CANONICAL_ORDER",
        orderId = "order-1", orderVersion = 1, senderCompanyId = "buyer-co", recipientPartyId = recipientPartyId,
        createdAt = TransactionTimestamp(100, TransactionTimestampSource.DeviceLocalProvisional),
        state = OrderTransportState.Queued, attemptCount = 0, lastAttemptAt = null, lastError = null,
        recipientBusinessId = recipientBusinessId,
    )

    @Test fun `explicit recipientBusinessId is used verbatim, never recipientPartyId, even when they differ`() = runTest {
        seedOrder()
        val snapshot = factory.forEnvelope(envelope(recipientBusinessId = "seller-co", recipientPartyId = "party-seller-local-ref"))
        assertEquals("seller-co", snapshot?.recipientBusinessId)
    }

    @Test fun `reproduces the physical Phone B bug shape -- missing recipientBusinessId fails closed, never falls back to recipientPartyId`() = runTest {
        seedOrder()
        // Exact pre-fix shape: recipientBusinessId absent, only recipientPartyId present. The old
        // code silently built a snapshot carrying the Party id in the Business slot; that snapshot
        // could never be verified by the real recipient's ingestReceivedOrderVersion(). The fix must
        // refuse to build a snapshot at all rather than substitute recipientPartyId.
        val snapshot = factory.forEnvelope(envelope(recipientBusinessId = null, recipientPartyId = "party-seller-local-ref"))
        assertNull(snapshot)
    }

    @Test fun `missing recipientBusinessId with no recipientPartyId either still fails closed`() = runTest {
        seedOrder()
        assertNull(factory.forEnvelope(envelope(recipientBusinessId = null, recipientPartyId = null)))
    }

    @Test fun `unknown order still returns null regardless of recipient identity shape`() = runTest {
        assertNull(factory.forEnvelope(envelope(recipientBusinessId = "seller-co", recipientPartyId = "party-seller-local-ref")))
    }
}
