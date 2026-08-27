package com.budcom.android.feature.transaction.presentation

import com.budcom.android.feature.transaction.domain.model.OrderTransportState

/** Buyer-facing transport labels — separate from [com.budcom.android.feature.transaction.domain.model.CanonicalOrderStatusLabels]. */
object OrderTransportStatusLabels {
    fun testerFacing(state: OrderTransportState): String = when (state) {
        OrderTransportState.Queued -> "Queued"
        OrderTransportState.RelayAccepted -> "Relay accepted"
        OrderTransportState.Delivered -> "Delivered"
        OrderTransportState.Retrying -> "Retrying"
        OrderTransportState.Failed -> "Failed"
    }

    fun detail(state: OrderTransportState, lastError: String?): String? = when {
        state == OrderTransportState.Failed || state == OrderTransportState.Retrying -> lastError?.takeIf { it.isNotBlank() }
        else -> null
    }
}
