package com.budcom.android.feature.transaction.domain.port

data class SignedEnvelopeSubmission(
    val envelope: EnvelopeSubmission,
    val keyId: String,
    val keyVersion: Int,
    val publicKeyFingerprint: String,
    val signatureAlgorithm: String,
    val signature: ByteArray,
) {
    override fun equals(other: Any?): Boolean = other is SignedEnvelopeSubmission &&
        envelope == other.envelope && keyId == other.keyId && keyVersion == other.keyVersion &&
        publicKeyFingerprint == other.publicKeyFingerprint && signatureAlgorithm == other.signatureAlgorithm &&
        signature.contentEquals(other.signature)

    override fun hashCode(): Int = 31 * listOf(envelope, keyId, keyVersion, publicKeyFingerprint, signatureAlgorithm).hashCode() + signature.contentHashCode()
}

sealed interface EnvelopeSigningOutcome {
    data class Signed(val submission: SignedEnvelopeSubmission) : EnvelopeSigningOutcome
    data object IdentityMismatch : EnvelopeSigningOutcome
    data class Failed(val result: DeviceSigningResult) : EnvelopeSigningOutcome
}

class StructuredEnvelopeSigner(private val keyStore: VartalapDeviceKeyStore) {
    suspend fun sign(envelope: EnvelopeSubmission, identity: DeviceSigningIdentity): EnvelopeSigningOutcome {
        if (envelope.senderDeviceId != identity.deviceId || identity.lifecycleStatus != DeviceKeyLifecycleStatus.Active) {
            return EnvelopeSigningOutcome.IdentityMismatch
        }
        return when (val result = keyStore.sign(identity, envelope.deterministicEncoding().toByteArray(Charsets.UTF_8))) {
            is DeviceSigningResult.Success -> EnvelopeSigningOutcome.Signed(
                SignedEnvelopeSubmission(envelope, identity.keyId, identity.keyVersion, identity.publicKeyFingerprint, "SHA256withECDSA", result.signature),
            )
            else -> EnvelopeSigningOutcome.Failed(result)
        }
    }
}
