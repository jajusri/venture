package com.budcom.android.feature.transaction.domain.port

import com.budcom.android.feature.transaction.domain.model.OrderDeliveryEnvelope

/** Versioned, bounded client contract for a future Vartalap gateway. It carries a canonical
 * reference and routing identity only; it is deliberately not a serialization of Room entities. */
data class EnvelopeSubmission(
    val contractVersion: Int,
    val envelopeId: String,
    val idempotencyKey: String,
    val objectType: String,
    val objectId: String,
    val objectVersion: Int,
    val senderBusinessId: String,
    val senderDeviceId: String,
    val recipientPartyId: String?,
    val recipientBusinessId: String?,
    val createdAtEpochMillis: Long,
    val boundedMetadata: Map<String, String> = emptyMap(),
) {
    init {
        require(contractVersion > 0)
        require(objectType.length <= MAX_FIELD_LENGTH)
        require(objectId.length <= MAX_FIELD_LENGTH)
        require(senderBusinessId.length <= MAX_FIELD_LENGTH)
        require(senderDeviceId.length in 1..MAX_FIELD_LENGTH)
        require((recipientPartyId?.length ?: 0) <= MAX_FIELD_LENGTH)
        require((recipientBusinessId?.length ?: 0) <= MAX_FIELD_LENGTH)
        require(boundedMetadata.size <= MAX_METADATA_ENTRIES)
        require(boundedMetadata.entries.sumOf { it.key.length + it.value.length } <= MAX_METADATA_LENGTH)
    }

    /** Stable field order for transport adapters and test fixtures. This is a contract encoding,
     * not a selected wire protocol; an adapter may map it to its approved protocol later. */
    fun deterministicEncoding(): String = buildString {
        append("v=").append(contractVersion)
        append("|envelope=").append(escape(envelopeId))
        append("|key=").append(escape(idempotencyKey))
        append("|type=").append(escape(objectType))
        append("|object=").append(escape(objectId))
        append("|version=").append(objectVersion)
        append("|senderBusiness=").append(escape(senderBusinessId))
        append("|senderDevice=").append(escape(senderDeviceId))
        append("|recipientParty=").append(escape(recipientPartyId.orEmpty()))
        append("|recipientBusiness=").append(escape(recipientBusinessId.orEmpty()))
        append("|created=").append(createdAtEpochMillis)
        boundedMetadata.toSortedMap().forEach { (key, value) ->
            append("|meta.").append(escape(key)).append('=').append(escape(value))
        }
    }

    companion object {
        const val CURRENT_VERSION = 1
        const val MAX_FIELD_LENGTH = 256
        const val MAX_METADATA_ENTRIES = 16
        const val MAX_METADATA_LENGTH = 1024

        fun fromEnvelope(envelope: OrderDeliveryEnvelope, senderDeviceId: String, recipientBusinessId: String? = null): EnvelopeSubmission =
            EnvelopeSubmission(
                contractVersion = CURRENT_VERSION,
                envelopeId = envelope.envelopeId,
                idempotencyKey = envelope.idempotencyKey,
                objectType = envelope.objectType,
                objectId = envelope.orderId,
                objectVersion = envelope.orderVersion,
                senderBusinessId = envelope.senderCompanyId,
                senderDeviceId = senderDeviceId,
                recipientPartyId = envelope.recipientPartyId,
                recipientBusinessId = recipientBusinessId,
                createdAtEpochMillis = envelope.createdAt.epochMillis,
            )

        private fun escape(value: String): String = value.replace("\\", "\\\\").replace("|", "\\|").replace("=", "\\=")
    }
}