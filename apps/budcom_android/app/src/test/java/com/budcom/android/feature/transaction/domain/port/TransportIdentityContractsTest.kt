package com.budcom.android.feature.transaction.domain.port

import com.budcom.android.feature.transaction.domain.model.OrderDeliveryEnvelope
import com.budcom.android.feature.transaction.domain.model.OrderTransportState
import com.budcom.android.feature.transaction.domain.model.TransactionTimestamp
import com.budcom.android.feature.transaction.domain.model.TransactionTimestampSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportIdentityContractsTest {
    @Test
    fun `valid credential binds device business recipient and canonical envelope`() {
        val context = context()
        assertTrue(context.validates(envelope()))
        assertTrue(context.credential.isCurrentlyValid(context.device, "business-1", 150L))
    }

    @Test
    fun `expired revoked wrong device and wrong business credentials fail`() {
        val base = context()
        assertFalse(base.credential.isCurrentlyValid(base.device, "business-1", 201L))
        assertFalse(base.copy(credential = base.credential.copy(revoked = true)).validates(envelope()))
        assertFalse(base.copy(device = base.device.copy(deviceId = "other-device")).validates(envelope()))
        assertFalse(base.copy(intendedBusinessId = "other-business").validates(envelope()))
    }

    private fun context() = TransportAuthenticationContext(
        device = DeviceIdentity("device-1", "key-ref-1", "fingerprint-1", 1L),
        credential = BusinessDeviceCredential(1, "business-1", "actor-1", "device-1", "ORDER_SEND", 100L, 200L, 1L, "issuer-1", "verified-1"),
        intendedBusinessId = "business-1",
        recipient = RecipientBinding("business-2", "party-2", "mailbox-2"),
    )

    private fun envelope() = OrderDeliveryEnvelope(
        "business-1", "envelope-1", "order:order-1:v1", "CANONICAL_ORDER", "order-1", 1,
        "business-1", "party-2", TransactionTimestamp(150L, TransactionTimestampSource.DeviceLocalProvisional),
        OrderTransportState.Queued, 0, null, null,
    )
}