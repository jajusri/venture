package com.jajusri.venture.feature.transaction.domain.model

enum class RecipientInboxTransportState(val columnValue: String) {
    Received("RECEIVED"),
    ;

    companion object {
        fun fromColumn(value: String): RecipientInboxTransportState = entries.first { it.columnValue == value }
    }
}

data class StructuredRecipientInboxEntry(
    val companyId: String,
    val envelopeId: String,
    val idempotencyKey: String,
    val objectType: String,
    val objectId: String,
    val objectVersion: Int,
    val senderBusinessId: String,
    val senderActorId: String,
    val senderDeviceId: String,
    val mailboxSequence: Long,
    val acceptanceId: String,
    val acceptedAt: TransactionTimestamp,
    val ingestedAt: TransactionTimestamp,
    val transportState: RecipientInboxTransportState,
)

object StructuredRecipientInboxValidation {
    fun validate(item: com.jajusri.venture.feature.transaction.domain.port.RelayMailboxDeliveryItem, recipientCompanyId: String): Boolean {
        if (item.status != "relay_accepted") return false
        if (item.recipientBusinessId != recipientCompanyId) return false
        if (item.objectType.isBlank() || item.objectId.isBlank() || item.objectVersion < 1) return false
        if (item.senderBusinessId.isBlank() || item.senderActorId.isBlank() || item.senderDeviceId.isBlank()) return false
        if (item.authenticatedEnvelope.isEmpty() || item.authenticatedEnvelope.size > 256 * 1024) return false
        if (item.status.contains("delivered", ignoreCase = true) || item.status.contains("seen", ignoreCase = true)) return false
        return true
    }
}
