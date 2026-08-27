package com.budcom.android.feature.transaction.domain.port

data class RelayMailboxDeliveryItem(
    val envelopeId: String,
    val mailboxSequence: Long,
    val objectType: String,
    val objectId: String,
    val objectVersion: Int,
    val senderBusinessId: String,
    val senderActorId: String,
    val senderDeviceId: String,
    val recipientBusinessId: String,
    val mailboxId: String,
    val status: String,
    val acceptedAtEpochMillis: Long,
    val acceptanceId: String,
    val authenticatedEnvelope: ByteArray,
    val commercialSnapshotCanonical: String? = null,
) {
    override fun equals(other: Any?): Boolean = other is RelayMailboxDeliveryItem &&
        envelopeId == other.envelopeId &&
        mailboxSequence == other.mailboxSequence &&
        authenticatedEnvelope.contentEquals(other.authenticatedEnvelope)

    override fun hashCode(): Int = envelopeId.hashCode()
}

data class RelayMailboxPage(
    val items: List<RelayMailboxDeliveryItem>,
    val nextCursor: String?,
)

fun interface RelayRecipientInboxIngester {
    suspend fun ingestPending(companyId: String)
}
