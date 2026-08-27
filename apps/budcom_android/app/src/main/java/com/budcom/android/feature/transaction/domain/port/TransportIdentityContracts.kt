package com.budcom.android.feature.transaction.domain.port

import com.budcom.android.feature.transaction.domain.model.OrderDeliveryEnvelope

data class DeviceIdentity(
    val deviceId: String,
    val publicKeyReference: String,
    val fingerprint: String,
    val createdAtEpochMillis: Long,
    val revoked: Boolean = false,
) {
    init {
        require(deviceId.isNotBlank())
        require(publicKeyReference.isNotBlank())
        require(fingerprint.isNotBlank())
    }
}

data class BusinessDeviceCredential(
    val credentialVersion: Int,
    val businessId: String,
    val actorId: String,
    val deviceId: String,
    val authority: String,
    val issuedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long?,
    val credentialEpoch: Long,
    val issuerReference: String,
    val verificationReference: String,
    val revoked: Boolean = false,
) {
    init {
        require(credentialVersion > 0)
        require(businessId.isNotBlank() && actorId.isNotBlank() && deviceId.isNotBlank())
        require(authority.isNotBlank() && issuerReference.isNotBlank() && verificationReference.isNotBlank())
        require(expiresAtEpochMillis == null || expiresAtEpochMillis > issuedAtEpochMillis)
    }

    fun isCurrentlyValid(device: DeviceIdentity, business: String, nowEpochMillis: Long): Boolean =
        !revoked && !device.revoked && device.deviceId == deviceId && businessId == business &&
            nowEpochMillis >= issuedAtEpochMillis && (expiresAtEpochMillis == null || nowEpochMillis < expiresAtEpochMillis)
}

data class RecipientBinding(
    val businessId: String?,
    val partyId: String?,
    val mailboxReference: String?,
) {
    init { require(businessId != null || partyId != null || mailboxReference != null) }
}

data class TransportAuthenticationContext(
    val device: DeviceIdentity,
    val credential: BusinessDeviceCredential,
    val intendedBusinessId: String,
    val recipient: RecipientBinding,
) {
    fun validates(envelope: OrderDeliveryEnvelope): Boolean =
        credential.isCurrentlyValid(device, intendedBusinessId, envelope.createdAt.epochMillis) &&
            envelope.senderCompanyId == credential.businessId &&
            envelope.recipientPartyId == recipient.partyId
}

enum class CredentialStatus { Valid, Expired, Revoked, WrongBusiness, WrongDevice, Invalid, TemporarilyUnverifiable }