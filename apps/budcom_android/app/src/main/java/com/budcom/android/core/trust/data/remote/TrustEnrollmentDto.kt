package com.budcom.android.core.trust.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire shape for `POST /v1/trust/enrollment/consume` -- mirrors
 * `backend/services/trust/src/http/map-enrollment.ts`'s `DeviceEnrollmentRequestBody` field for
 * field. `publicKey`/`publicKeyFingerprint` are base64, matching Trust's own base64 decode there. */
@Serializable
data class TrustEnrollmentRequestDto(
    val grantId: String,
    val grantSecret: String,
    val deviceId: String,
    val deviceKeyId: String,
    val deviceKeyVersion: Int,
    val publicKey: String,
    val publicKeyFingerprint: String,
)

@Serializable
data class TrustEnrollmentResponseDto(
    val device: TrustEnrolledDeviceDto,
    val credential: TrustIssuedCredentialDto,
)

@Serializable
data class TrustEnrolledDeviceDto(
    val businessId: String,
    val deviceId: String,
    val deviceKeyVersion: Int,
    val publicKeyFingerprint: String,
)

@Serializable
data class TrustIssuedCredentialDto(
    val claims: TrustCredentialClaimsDto,
    val signature: String,
)

/** Mirrors Trust's `BusinessDeviceCredentialClaims` (`backend/services/trust/src/domain/authority.ts`)
 * exactly, field for field -- see `app.ts`'s response-shaping block for the exact JSON this decodes. */
@Serializable
data class TrustCredentialClaimsDto(
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
    val issuedAt: String,
    val notBefore: String,
    val expiresAt: String,
    val issuerId: String,
    val issuerKeyId: String,
)

/** Mirrors `ServiceErrorBody` (`backend/services/trust/src/errors.ts`) -- `code` is one of the
 * `DeviceEnrollmentRejectionReason` values (`app.ts`'s `ENROLLMENT_REJECTION_STATUS`) when the
 * rejection came from the enrollment grant itself, or `invalid_enrollment_request`/`internal_error`/
 * `enrollment_rejected` for the other mapped cases -- see `TrustEnrollmentRepository`'s own mapping. */
@Serializable
data class TrustErrorResponseDto(@SerialName("error") val error: TrustErrorBodyDto)

@Serializable
data class TrustErrorBodyDto(val code: String, val message: String, val requestId: String)
