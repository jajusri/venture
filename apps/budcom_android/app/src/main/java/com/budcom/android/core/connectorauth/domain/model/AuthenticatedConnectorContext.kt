package com.budcom.android.core.connectorauth.domain.model

import com.budcom.android.core.pairing.domain.model.TrustedConnectorEndpoint

/**
 * Holds a decrypted bearer credential only long enough to build one request's Authorization
 * header. Deliberately not a `data class` (no generated `copy()`/`equals()`/`hashCode()` that
 * would encourage retaining or comparing the plaintext) and never logged, since [toString] is
 * overridden to redact the value unconditionally.
 */
class RedactedBearerCredential(private val rawValue: String) {
    fun asBearerHeaderValue(): String = "Bearer $rawValue"
    override fun toString(): String = "RedactedBearerCredential(<redacted>)"
}

/**
 * The narrow, single-request context [com.budcom.android.core.connectorauth.data.remote.AuthenticatedConnectorApiPort]
 * needs: the [TrustedConnectorEndpoint] to dial and a transiently-held bearer credential to
 * present. Constructible only by
 * [com.budcom.android.core.connectorauth.domain.AuthenticatedConnectorContextProvider] from a
 * freshly-read ACTIVE vault record plus a successful decrypt — never cached, never stored in a
 * singleton field, never placed in a StateFlow/SharedFlow/SavedStateHandle. Not Parcelable, not
 * Serializable; scoped to the lifetime of one request execution. Deliberately not a `data class`
 * so no `copy()` exists to accidentally propagate the credential elsewhere.
 *
 * [credentialId] is the non-secret [com.budcom.android.core.pairing.domain.model.SecurePairingCredentialRecord.credentialId]
 * this context's bearer was decrypted from — carried through to a rejected request's
 * [com.budcom.android.core.connectorauth.domain.model.AuthenticatedConnectorResult.Unauthorized]
 * so a rejection handler can invalidate the exact credential that was actually used, never
 * whichever credential happens to be current when the rejection is handled (a credential can be
 * replaced by a newer re-pair between the request being sent and its response being processed).
 */
class AuthenticatedConnectorContext(
    val endpoint: TrustedConnectorEndpoint,
    val logicalDeviceId: String,
    val credentialId: String,
    private val bearerCredential: RedactedBearerCredential,
) {
    fun bearerHeaderValue(): String = bearerCredential.asBearerHeaderValue()

    override fun toString(): String =
        "AuthenticatedConnectorContext(endpoint=$endpoint, logicalDeviceId=<redacted>, credentialId=$credentialId, bearerCredential=<redacted>)"
}
