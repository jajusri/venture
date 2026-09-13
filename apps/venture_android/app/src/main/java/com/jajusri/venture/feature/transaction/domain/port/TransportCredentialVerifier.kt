package com.jajusri.venture.feature.transaction.domain.port

data class TrustedBusinessDeviceCredential(
    val credentialVersion: Int, val credentialId: String, val businessId: String, val actorId: String,
    val membershipId: String, val deviceId: String, val deviceKeyId: String, val deviceKeyVersion: Int,
    val devicePublicKeyFingerprint: String, val authorityScope: Set<String>, val authorityEpoch: Long,
    val issuedAtEpochMillis: Long, val notBeforeEpochMillis: Long, val expiresAtEpochMillis: Long,
    val issuerId: String, val issuerKeyId: String, val signatureProfile: String, val signature: ByteArray,
) {
    fun signingBytes(): ByteArray = listOf(credentialVersion, credentialId, businessId, actorId, membershipId,
        deviceId, deviceKeyId, deviceKeyVersion, devicePublicKeyFingerprint, authorityScope.sorted().joinToString(","),
        authorityEpoch, issuedAtEpochMillis, notBeforeEpochMillis, expiresAtEpochMillis, issuerId, issuerKeyId)
        .joinToString("|") { it.toString().replace("\\", "\\\\").replace("|", "\\|") }.toByteArray(Charsets.UTF_8)
}
data class CredentialVerificationRequest(
    val credential: TrustedBusinessDeviceCredential, val expectedBusinessId: String, val expectedActorId: String?,
    val expectedDeviceId: String, val expectedDeviceKeyVersion: Int, val actualRecipient: RecipientBinding,
    val expectedRecipient: RecipientBinding, val nowEpochMillis: Long,
)
data class TransportAuthorityContext(val businessId: String, val actorId: String, val deviceId: String, val authorityScope: Set<String>, val authorityEpoch: Long)
sealed interface CredentialVerificationOutcome {
    data class Valid(val authorityContext: TransportAuthorityContext) : CredentialVerificationOutcome
    data object Expired : CredentialVerificationOutcome
    data object Revoked : CredentialVerificationOutcome
    data object WrongBusiness : CredentialVerificationOutcome
    data object WrongActor : CredentialVerificationOutcome
    data object WrongDevice : CredentialVerificationOutcome
    data object WrongRecipient : CredentialVerificationOutcome
    data object InvalidSignature : CredentialVerificationOutcome
    data object UnsupportedVersion : CredentialVerificationOutcome
    data object TemporarilyUnverifiable : CredentialVerificationOutcome
}
fun interface TransportCredentialVerifier { suspend fun verify(request: CredentialVerificationRequest): CredentialVerificationOutcome }
