package com.budcom.android.feature.transaction.domain.port

data class CredentialVerificationRequest(
    val credential: BusinessDeviceCredential,
    val expectedBusinessId: String,
    val expectedActorId: String?,
    val expectedDeviceId: String,
    val credentialRecipient: RecipientBinding,
    val expectedRecipient: RecipientBinding,
    val nowEpochMillis: Long,
)

data class TransportAuthorityContext(
    val businessId: String,
    val actorId: String,
    val deviceId: String,
    val authority: String,
    val credentialEpoch: Long,
)

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

/** Implementations must validate against approved Trust Service verification material.
 * No production implementation exists until that trust root is available. */
fun interface TransportCredentialVerifier {
    suspend fun verify(request: CredentialVerificationRequest): CredentialVerificationOutcome
}
