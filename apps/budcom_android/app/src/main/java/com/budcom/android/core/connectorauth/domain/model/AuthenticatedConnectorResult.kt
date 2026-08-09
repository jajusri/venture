package com.budcom.android.core.connectorauth.domain.model

/**
 * A bounded, immutable UTF-8 JSON payload from a successful authenticated response. Deserializing
 * feature-specific DTOs out of this is deferred to the Phase 3S repository cutover — core
 * networking stays payload-shape-agnostic.
 */
data class AuthenticatedConnectorResponsePayload(val rawJson: String)

/**
 * Outcome of one [com.budcom.android.core.connectorauth.data.remote.AuthenticatedConnectorApiPort]
 * call. Distinguishes context-resolution failures (no request was ever attempted — see
 * [com.budcom.android.core.connectorauth.domain.AuthenticatedConnectorContextResolution]) from
 * transport-level and HTTP-status outcomes, and never collapses a 401/403 into a generic failure.
 */
sealed class AuthenticatedConnectorResult {

    data class Success(val payload: AuthenticatedConnectorResponsePayload) : AuthenticatedConnectorResult()

    /** No secure-pairing credential has ever been stored on this device. Zero network calls made. */
    data object Unpaired : AuthenticatedConnectorResult()

    /** Redeemed but not yet proven. Zero network calls made. */
    data object PendingVerification : AuthenticatedConnectorResult()

    /** Known revoked/unusable — device must re-pair. Zero network calls made. */
    data object RePairRequired : AuthenticatedConnectorResult()

    /** ACTIVE record exists but decryption failed (Keystore loss/tamper). Zero network calls made. */
    data object CredentialUnavailable : AuthenticatedConnectorResult()

    /**
     * HTTP 401 — the presented credential itself was rejected. [credentialId] is the exact
     * [com.budcom.android.core.pairing.domain.model.SecurePairingCredentialRecord.credentialId]
     * that was used for the rejected request (from [AuthenticatedConnectorContext.credentialId]),
     * not necessarily whichever credential is current by the time this result is handled — a
     * rejection handler must invalidate this specific credential, never blindly the vault's
     * current record, so a credential replaced by a newer re-pair mid-flight is never wrongly
     * invalidated by an older request's rejection.
     */
    data class Unauthorized(val credentialId: String) : AuthenticatedConnectorResult()

    /** HTTP 403 — credential accepted, but not authorized for this route. */
    data object Forbidden : AuthenticatedConnectorResult()

    /** HTTP 404. */
    data object NotFound : AuthenticatedConnectorResult()

    /** HTTP 409. */
    data object Conflict : AuthenticatedConnectorResult()

    /** HTTP 429. */
    data object RateLimited : AuthenticatedConnectorResult()

    /**
     * HTTP 400 — bounded, sanitized Connector fields only; never the raw response body.
     * [isNoCompanySelected] is true only when the body is well-formed JSON whose `status` field
     * is exactly `"NO_COMPANY_SELECTED"` (the Connector's session-validation shape, see
     * `session-validator.ts`) — a fixed allowlist match, never a pass-through of arbitrary body
     * content. It is the only HTTP 400 sub-case this app treats as auto-recoverable via company
     * reselection (TD-013); every other value, or an absent/malformed body, leaves it false.
     */
    data class ValidationFailure(
        val sanitizedCode: String?,
        val isNoCompanySelected: Boolean = false,
    ) : AuthenticatedConnectorResult()

    /**
     * HTTP 410 whose bounded JSON body identifies the Connector's exact `SESSION_EXPIRED`
     * session-validation outcome. This is recoverable through one authenticated company
     * reselection; it is never treated as a transport or generic server failure.
     */
    data object SessionExpired : AuthenticatedConnectorResult()

    /** HTTP 5xx. */
    data class ServerFailure(val httpStatus: Int) : AuthenticatedConnectorResult()

    /** The live certificate uses a different SPKI than the explicitly trusted Connector. */
    data object IdentityMismatch : AuthenticatedConnectorResult()

    /** The pinned certificate was present but expired, not yet valid, missing, or malformed. */
    data object CertificateInvalid : AuthenticatedConnectorResult()

    /** Socket/DNS/timeout failure, or another non-trust transport-level `IOException`. */
    data object TransportFailure : AuthenticatedConnectorResult()

    /** A 2xx body that was not well-formed JSON, or exceeded the bounded read size. */
    data object MalformedResponse : AuthenticatedConnectorResult()

    /** The calling coroutine was cancelled while the request was in flight. */
    data object Cancelled : AuthenticatedConnectorResult()
}
