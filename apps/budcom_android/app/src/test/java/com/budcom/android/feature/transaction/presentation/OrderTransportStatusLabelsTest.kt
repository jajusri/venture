package com.budcom.android.feature.transaction.presentation

import com.budcom.android.feature.transaction.domain.model.OrderTransportState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OrderTransportStatusLabelsTest {
    @Test
    fun `transport labels stay separate from commercial wording`() {
        assertEquals("Queued", OrderTransportStatusLabels.testerFacing(OrderTransportState.Queued))
        assertEquals("Relay accepted", OrderTransportStatusLabels.testerFacing(OrderTransportState.RelayAccepted))
        assertEquals("Retrying", OrderTransportStatusLabels.testerFacing(OrderTransportState.Retrying))
        assertEquals("Failed", OrderTransportStatusLabels.testerFacing(OrderTransportState.Failed))
    }

    @Test
    fun `transport detail only surfaces retry and failure reasons`() {
        assertNull(OrderTransportStatusLabels.detail(OrderTransportState.Queued, "ignored"))
        assertEquals("relay offline", OrderTransportStatusLabels.detail(OrderTransportState.Retrying, "relay offline"))
        assertEquals("rejected", OrderTransportStatusLabels.detail(OrderTransportState.Failed, "rejected"))
    }
}
