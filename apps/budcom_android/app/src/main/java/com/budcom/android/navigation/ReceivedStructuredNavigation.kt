package com.budcom.android.navigation

import com.budcom.android.feature.transaction.domain.model.StructuredRecipientInboxEntry

/** Resolves durable structured recipient inbox rows into the canonical received-order screens. */
object ReceivedStructuredNavigation {
    const val OBJECT_TYPE_CANONICAL_ORDER = "CANONICAL_ORDER"

    enum class Kind {
        ReceivedOrder,
        ReceivedRevision,
        Unsupported,
    }

    fun kindFor(entry: StructuredRecipientInboxEntry): Kind = when {
        entry.objectType != OBJECT_TYPE_CANONICAL_ORDER -> Kind.Unsupported
        entry.objectVersion > 1 -> Kind.ReceivedRevision
        entry.objectVersion == 1 -> Kind.ReceivedOrder
        else -> Kind.Unsupported
    }

    fun routeFor(entry: StructuredRecipientInboxEntry): String? = when (kindFor(entry)) {
        Kind.ReceivedOrder -> Routes.receivedOrder(
            entry.envelopeId,
            entry.senderBusinessId,
            entry.objectId,
            entry.objectVersion,
        )
        Kind.ReceivedRevision -> Routes.receivedRevision(
            entry.envelopeId,
            entry.senderBusinessId,
            entry.objectId,
            entry.objectVersion,
        )
        Kind.Unsupported -> null
    }
}
