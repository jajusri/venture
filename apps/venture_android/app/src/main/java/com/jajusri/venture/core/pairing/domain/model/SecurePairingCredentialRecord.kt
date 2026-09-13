package com.jajusri.venture.core.pairing.domain.model

import com.jajusri.venture.core.security.EncryptedPayload

/**
 * Persisted state of a single device's pairing credential, keyed one-to-one with the
 * [TrustedConnectorEndpoint] it was issued for. There is only ever at most one such record on
 * device — pairing with a different Connector atomically replaces it, it is never merged or
 * appended to.
 */
enum class SecurePairingCredentialState {
    /** Redeemed and encrypted, but not yet proven against `/device/pairing-credential/self`. */
    PENDING_VERIFICATION,

    /** Verified via a successful self-status call. Usable for future secure-pairing operations. */
    ACTIVE,

    /**
     * The credential is known to no longer be usable (self-status returned unauthorized, or it
     * was explicitly revoked) — the device must re-pair. The record is kept (not cleared) purely
     * so its metadata remains inspectable; the credential itself can never authenticate again.
     */
    RE_PAIR_REQUIRED,
}

data class SecurePairingCredentialRecord(
    val credentialId: String,
    val deviceId: String,
    val encryptedCredential: EncryptedPayload,
    val endpoint: TrustedConnectorEndpoint,
    val createdAtEpochMillis: Long,
    val lastVerifiedAtEpochMillis: Long?,
    val state: SecurePairingCredentialState,
) {
    override fun toString(): String =
        "SecurePairingCredentialRecord(credentialId=$credentialId, state=$state, " +
            "connectorId=${endpoint.connectorId}, deviceId=<redacted>, encryptedCredential=<redacted>)"
}
