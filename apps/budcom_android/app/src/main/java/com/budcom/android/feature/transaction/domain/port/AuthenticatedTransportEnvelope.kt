package com.budcom.android.feature.transaction.domain.port

data class AuthenticatedTransportEnvelope(
    val envelope: EnvelopeSubmission,
    val senderActorId: String,
    val deviceKeyId: String,
    val deviceKeyVersion: Int,
    val deviceFingerprint: String,
    val credentialId: String,
    val credentialVersion: Int,
    val credentialEpoch: Long,
    val recipient: RecipientBinding,
    val signatureAlgorithm: String,
    val signature: ByteArray,
    val commercialSnapshotCanonical: String = "",
    val commercialContentType: String = ORDER_SNAPSHOT_CONTENT_TYPE,
    val commercialContentVersion: Int = ORDER_SNAPSHOT_CONTENT_VERSION,
) {
    fun signingBytes(): ByteArray = buildString {
        append(envelope.deterministicEncoding())
        append("|actor=").append(senderActorId)
        append("|keyId=").append(deviceKeyId).append("|keyVersion=").append(deviceKeyVersion)
        append("|fingerprint=").append(deviceFingerprint)
        append("|credential=").append(credentialId).append("|credentialVersion=").append(credentialVersion)
        append("|credentialEpoch=").append(credentialEpoch)
        append("|recipientBusiness=").append(recipient.businessId.orEmpty())
        append("|recipientParty=").append(recipient.partyId.orEmpty())
        append("|recipientMailbox=").append(recipient.mailboxReference.orEmpty())
        append("|commercialContentType=").append(commercialContentType)
        append("|commercialContentVersion=").append(commercialContentVersion)
        append("|snapshot=").append(commercialSnapshotCanonical)
    }.toByteArray(Charsets.UTF_8)
}

const val ORDER_SNAPSHOT_CONTENT_TYPE = "application/vnd.budcom.order-snapshot+json"
const val ORDER_SNAPSHOT_CONTENT_VERSION = 2

class AuthenticatedEnvelopeBinder(private val keyStore: VartalapDeviceKeyStore) {
    suspend fun bind(
        envelope: EnvelopeSubmission,
        identity: DeviceSigningIdentity,
        credential: BusinessDeviceCredential,
        recipient: RecipientBinding,
        commercialSnapshotCanonical: String = "",
    ): AuthenticatedTransportEnvelope? {
        if (identity.lifecycleStatus != DeviceKeyLifecycleStatus.Active || envelope.senderDeviceId != identity.deviceId ||
            credential.deviceId != identity.deviceId || credential.businessId != envelope.senderBusinessId ||
            envelope.recipientBusinessId != recipient.businessId || envelope.recipientPartyId != recipient.partyId) return null
        val unsigned = AuthenticatedTransportEnvelope(
            envelope, credential.actorId, identity.keyId, identity.keyVersion,
            identity.publicKeyFingerprint, credential.verificationReference, credential.credentialVersion, credential.credentialEpoch,
            recipient, "SHA256withECDSA", byteArrayOf(), commercialSnapshotCanonical,
        )
        val result = keyStore.sign(identity, unsigned.signingBytes()) as? DeviceSigningResult.Success ?: return null
        return unsigned.copy(signature = result.signature)
    }
}
