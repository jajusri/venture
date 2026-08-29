package com.budcom.android.core.trust.domain

/**
 * What a human (an administrator's operator, today via the backend's `dev-provision.ts
 * create-enrollment-grant` CLI) hands the enrolling device out of band -- a QR code or manual entry
 * is a UI concern outside this module's scope. Neither field is ever logged.
 */
data class EnrollmentGrantProof(val grantId: String, val grantSecret: String) {
    init {
        require(grantId.isNotBlank())
        require(grantSecret.isNotBlank())
    }
}

/**
 * The device-signing-identity fields Trust's enrollment endpoint needs, decoupled from
 * [com.budcom.android.feature.transaction.domain.port.DeviceSigningIdentity] (that type lives in
 * the protected `feature/transaction` package this module must not depend on for anything beyond
 * read-only interface satisfaction -- see [com.budcom.android.core.trust.data.TrustBackedRelayCredentialSource]).
 */
data class DeviceEnrollmentIdentity(
    val deviceId: String,
    val deviceKeyId: String,
    val deviceKeyVersion: Int,
    val publicKey: ByteArray,
    val publicKeyFingerprint: String,
) {
    init {
        require(deviceId.isNotBlank() && deviceKeyId.isNotBlank() && deviceKeyVersion > 0)
        require(publicKey.isNotEmpty() && publicKeyFingerprint.isNotBlank())
    }
}

/** Everything about an issued Trust device credential this app needs to keep -- mirrors Trust's
 * own `BusinessDeviceCredentialClaims` shape (`backend/services/trust/src/domain/authority.ts`)
 * plus the raw signature, stored exactly as issued so the credential can be handed to the wire
 * protocol (`AuthenticatedEnvelopeBinder`) without re-deriving anything. */
data class StoredTrustCredential(
    val credentialVersion: Int,
    val credentialId: String,
    val businessId: String,
    val actorId: String,
    val membershipId: String,
    val deviceId: String,
    val deviceKeyId: String,
    val deviceKeyVersion: Int,
    val devicePublicKeyFingerprint: String,
    val authorityScope: List<String>,
    val authorityEpoch: Long,
    val issuedAtEpochMillis: Long,
    val notBeforeEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val issuerId: String,
    val issuerKeyId: String,
    val signatureBase64: String,
) {
    init {
        require(credentialVersion > 0)
        require(credentialId.isNotBlank() && businessId.isNotBlank() && actorId.isNotBlank() && membershipId.isNotBlank())
        require(deviceId.isNotBlank() && deviceKeyId.isNotBlank() && deviceKeyVersion > 0)
        require(devicePublicKeyFingerprint.isNotBlank() && issuerId.isNotBlank() && issuerKeyId.isNotBlank() && signatureBase64.isNotBlank())
        require(expiresAtEpochMillis > issuedAtEpochMillis)
    }

    fun isExpired(nowEpochMillis: Long): Boolean = nowEpochMillis >= expiresAtEpochMillis
}

/** Every distinct way enrollment can fail, kept separate from a generic network-error taxonomy
 * because Trust's own enrollment-specific rejection codes (`grant_not_found`, `grant_expired`, ...)
 * are meaningful to show a human differently than "the network is down" -- see Gate 3E's matrix. */
sealed interface TrustEnrollmentOutcome {
    data class Success(val credential: StoredTrustCredential) : TrustEnrollmentOutcome
    data class Rejected(val code: String) : TrustEnrollmentOutcome
    data object TrustUnavailable : TrustEnrollmentOutcome
    data object MalformedResponse : TrustEnrollmentOutcome
    data class Unexpected(val message: String) : TrustEnrollmentOutcome
}
