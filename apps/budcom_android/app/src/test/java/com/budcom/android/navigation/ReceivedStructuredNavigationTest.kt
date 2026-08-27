package com.budcom.android.navigation

import com.budcom.android.feature.transaction.domain.model.RecipientInboxTransportState
import com.budcom.android.feature.transaction.domain.model.StructuredRecipientInboxEntry
import com.budcom.android.feature.transaction.domain.model.TransactionTimestamp
import com.budcom.android.feature.transaction.domain.model.TransactionTimestampSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceivedStructuredNavigationTest {
    private fun entry(objectType: String, version: Int) = StructuredRecipientInboxEntry(
        companyId = "buyer-co",
        envelopeId = "env-$version",
        idempotencyKey = "inbox-$version",
        objectType = objectType,
        objectId = "order-1",
        objectVersion = version,
        senderBusinessId = "seller-co",
        senderActorId = "actor-s",
        senderDeviceId = "device-s",
        mailboxSequence = version.toLong(),
        acceptanceId = "accept-$version",
        acceptedAt = TransactionTimestamp(1, TransactionTimestampSource.DeviceLocalProvisional),
        ingestedAt = TransactionTimestamp(2, TransactionTimestampSource.DeviceLocalProvisional),
        transportState = RecipientInboxTransportState.Received,
    )

    @Test
    fun `version one routes to received order with preserved identifiers`() {
        val route = ReceivedStructuredNavigation.routeFor(entry(ReceivedStructuredNavigation.OBJECT_TYPE_CANONICAL_ORDER, 1))!!
        assertTrue(route.startsWith("transaction/received/"))
        assertTrue(route.contains("env-1"))
        assertTrue(route.contains("seller-co"))
        assertTrue(route.contains("order-1"))
        assertTrue(route.endsWith("/1"))
    }

    @Test
    fun `revision routes to received revision with exact version`() {
        val route = ReceivedStructuredNavigation.routeFor(entry(ReceivedStructuredNavigation.OBJECT_TYPE_CANONICAL_ORDER, 2))!!
        assertTrue(route.startsWith("transaction/revision/"))
        assertTrue(route.contains("env-2"))
        assertTrue(route.contains("seller-co"))
        assertTrue(route.contains("order-1"))
        assertTrue(route.endsWith("/2"))
    }

    @Test
    fun `wrong object type does not route`() {
        assertNull(ReceivedStructuredNavigation.routeFor(entry("CHAT_MESSAGE", 1)))
        assertEquals(ReceivedStructuredNavigation.Kind.Unsupported, ReceivedStructuredNavigation.kindFor(entry("CHAT_MESSAGE", 1)))
    }

    @Test
    fun `ordinary order and revision kinds stay distinct`() {
        assertEquals(ReceivedStructuredNavigation.Kind.ReceivedOrder, ReceivedStructuredNavigation.kindFor(entry(ReceivedStructuredNavigation.OBJECT_TYPE_CANONICAL_ORDER, 1)))
        assertEquals(ReceivedStructuredNavigation.Kind.ReceivedRevision, ReceivedStructuredNavigation.kindFor(entry(ReceivedStructuredNavigation.OBJECT_TYPE_CANONICAL_ORDER, 3)))
    }
}
