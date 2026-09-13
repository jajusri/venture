package com.jajusri.venture.core.connectorauth.domain

import com.jajusri.venture.core.connectorauth.domain.model.AuthenticatedConnectorContext
import com.jajusri.venture.core.connectorauth.domain.model.RedactedBearerCredential
import com.jajusri.venture.core.pairing.data.local.SecureCredentialVault
import com.jajusri.venture.core.pairing.domain.model.SecurePairingCredentialState
import com.jajusri.venture.core.security.CredentialDecryptionResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Outcome of resolving the current trust state into request-ready context. [Ready] is the only
 * variant carrying decrypted material; every other variant means no request may be attempted.
 */
sealed class AuthenticatedConnectorContextResolution {
    class Ready(val context: AuthenticatedConnectorContext) : AuthenticatedConnectorContextResolution() {
        override fun toString(): String = "Ready(context=<redacted>)"
    }

    /** No secure-pairing credential has ever been stored on this device. */
    data object Unpaired : AuthenticatedConnectorContextResolution()

    /** Redeemed but not yet proven via a successful self-status call. */
    data object PendingVerification : AuthenticatedConnectorContextResolution()

    /** Known revoked or otherwise unusable — the device must re-pair. */
    data object RePairRequired : AuthenticatedConnectorContextResolution()

    /** An ACTIVE record exists but its credential could not be decrypted (Keystore loss/tamper). */
    data object CredentialUnavailable : AuthenticatedConnectorContextResolution()
}

/**
 * Resolves the current [AuthenticatedConnectorContext] for exactly one request. Every call
 * re-reads [SecureCredentialVault] — never caches a [AuthenticatedConnectorContextResolution.Ready]
 * result across calls, so a credential replaced, revoked, or marked RE_PAIR_REQUIRED between two
 * calls is observed by the very next one. Never generates a credential, never repairs or mutates
 * the vault, never uses legacy pairing state, and never makes a network call merely to build
 * context.
 */
interface AuthenticatedConnectorContextProvider {
    suspend fun resolve(): AuthenticatedConnectorContextResolution
}

@Singleton
class DefaultAuthenticatedConnectorContextProvider @Inject constructor(
    private val vault: SecureCredentialVault,
) : AuthenticatedConnectorContextProvider {

    override suspend fun resolve(): AuthenticatedConnectorContextResolution {
        val record = vault.read() ?: return AuthenticatedConnectorContextResolution.Unpaired

        return when (record.state) {
            SecurePairingCredentialState.PENDING_VERIFICATION -> AuthenticatedConnectorContextResolution.PendingVerification
            SecurePairingCredentialState.RE_PAIR_REQUIRED -> AuthenticatedConnectorContextResolution.RePairRequired
            SecurePairingCredentialState.ACTIVE -> when (val decrypted = vault.readDecryptedCredential()) {
                null -> AuthenticatedConnectorContextResolution.CredentialUnavailable
                is CredentialDecryptionResult.Success -> AuthenticatedConnectorContextResolution.Ready(
                    AuthenticatedConnectorContext(
                        endpoint = record.endpoint,
                        logicalDeviceId = record.deviceId,
                        credentialId = record.credentialId,
                        bearerCredential = RedactedBearerCredential(String(decrypted.plaintext, Charsets.UTF_8)),
                    ),
                )
                is CredentialDecryptionResult.KeyMissing,
                is CredentialDecryptionResult.InvalidCiphertext,
                -> AuthenticatedConnectorContextResolution.CredentialUnavailable
            }
        }
    }
}
