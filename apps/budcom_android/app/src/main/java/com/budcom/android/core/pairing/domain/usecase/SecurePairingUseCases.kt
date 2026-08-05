package com.budcom.android.core.pairing.domain.usecase

import com.budcom.android.core.pairing.data.local.PairingDeviceIdentityLocalDataSource
import com.budcom.android.core.pairing.data.local.SecureCredentialVault
import com.budcom.android.core.pairing.data.local.SecureCredentialVaultWriteResult
import com.budcom.android.core.pairing.data.remote.PairingRedeemOutcome
import com.budcom.android.core.pairing.data.remote.PairingSelfRevokeOutcome
import com.budcom.android.core.pairing.data.remote.PairingSelfStatusOutcome
import com.budcom.android.core.pairing.data.remote.SecurePairingApiPort
import com.budcom.android.core.pairing.domain.SecurePairingState
import com.budcom.android.core.pairing.domain.model.SecurePairingCredentialRecord
import com.budcom.android.core.pairing.domain.model.SecurePairingCredentialState
import com.budcom.android.core.pairing.domain.model.SecurePairingQrPayloadParseResult
import com.budcom.android.core.pairing.domain.model.SecurePairingQrPayloadParser
import com.budcom.android.core.pairing.domain.model.SecurePairingQrValidationPolicy
import com.budcom.android.core.pairing.domain.model.TrustedConnectorEndpoint
import com.budcom.android.core.security.CredentialDecryptionResult
import com.budcom.android.core.util.TimeProvider
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of a redemption attempt or a retried pending-verification. */
sealed class SecurePairingRedemptionOutcome {
    data class Verified(val record: SecurePairingCredentialRecord) : SecurePairingRedemptionOutcome()
    data class PendingRetryable(val record: SecurePairingCredentialRecord) : SecurePairingRedemptionOutcome()
    data class RedemptionRejected(val reasonCode: String?, val httpStatus: Int) : SecurePairingRedemptionOutcome()
    data object TransportFailure : SecurePairingRedemptionOutcome()
    data object InvalidPayload : SecurePairingRedemptionOutcome()
    data object KeystoreUnavailable : SecurePairingRedemptionOutcome()
    data object MalformedResponse : SecurePairingRedemptionOutcome()
    data object NoPendingCredential : SecurePairingRedemptionOutcome()
}

sealed class SecurePairingRevocationOutcome {
    data object Revoked : SecurePairingRevocationOutcome()
    data object NoActiveCredential : SecurePairingRevocationOutcome()
    data object Unauthorized : SecurePairingRevocationOutcome()
    data object TransportFailure : SecurePairingRevocationOutcome()
}

/**
 * Validates a raw scanned/pasted QR payload WITHOUT redeeming it — the narrow seam a presentation
 * layer uses to show an explicit Connector/fingerprint confirmation step before any network call
 * is made. [RedeemSecurePairingSession] parses the payload again internally when the caller
 * confirms (validation is cheap and side-effect-free, so re-validating at confirm-time is
 * deliberate defense against a payload going stale between scan and confirm, e.g. its expiry
 * elapsing) rather than trusting a `Valid` result carried across that gap.
 */
class ValidateSecurePairingPayload @Inject constructor(
    private val parser: SecurePairingQrPayloadParser,
) {
    operator fun invoke(
        rawPayload: String,
        policy: SecurePairingQrValidationPolicy = SecurePairingQrValidationPolicy(),
    ): SecurePairingQrPayloadParseResult = parser.parse(rawPayload, policy)
}

/**
 * The shared "prove a pending credential" step used by both a fresh redemption
 * ([RedeemSecurePairingSession]) and a retried verification after interruption
 * ([RetryPendingCredentialVerification]) — kept as one class so the promote/demote vault-state
 * rules can never drift between the two call sites.
 */
@Singleton
class PendingCredentialVerificationStep @Inject constructor(
    private val pairingApi: SecurePairingApiPort,
    private val vault: SecureCredentialVault,
    private val timeProvider: TimeProvider,
) {
    suspend fun verify(
        credentialId: String,
        rawCredential: String,
        endpoint: TrustedConnectorEndpoint,
        expectedDeviceId: String? = null,
    ): SecurePairingRedemptionOutcome {
        return when (val statusOutcome = pairingApi.getCredentialSelf(endpoint, rawCredential)) {
            is PairingSelfStatusOutcome.Active -> {
                val deviceIdMismatch = expectedDeviceId != null && statusOutcome.deviceId != null && statusOutcome.deviceId != expectedDeviceId
                if (statusOutcome.credentialId != credentialId || statusOutcome.connectorId != endpoint.connectorId || deviceIdMismatch) {
                    vault.markRePairRequired(credentialId)
                    return SecurePairingRedemptionOutcome.RedemptionRejected(reasonCode = "IDENTITY_MISMATCH", httpStatus = 0)
                }
                vault.markActive(credentialId, timeProvider.nowEpochMillis())
                vault.read()?.let { SecurePairingRedemptionOutcome.Verified(it) } ?: SecurePairingRedemptionOutcome.TransportFailure
            }
            PairingSelfStatusOutcome.Unauthorized -> {
                // Verification was definitively rejected (not merely interrupted) — the pending
                // credential can never become usable, so re-pairing is required rather than
                // retryable. The record itself is kept (not cleared) for inspectability.
                vault.markRePairRequired(credentialId)
                SecurePairingRedemptionOutcome.RedemptionRejected(reasonCode = "UNAUTHORIZED", httpStatus = 401)
            }
            PairingSelfStatusOutcome.TransportFailure, PairingSelfStatusOutcome.MalformedResponse ->
                // Interrupted, not rejected — the encrypted pending credential is left exactly as
                // it was so a later RetryPendingCredentialVerification can prove it without
                // redeeming (and therefore consuming) a second one-time session.
                vault.read()?.let { SecurePairingRedemptionOutcome.PendingRetryable(it) } ?: SecurePairingRedemptionOutcome.TransportFailure
        }
    }
}

/**
 * Implements the approved redemption→persist→verify ordering: the pairing session is consumed
 * the moment the Connector accepts redemption, so the credential is encrypted and persisted as
 * PENDING_VERIFICATION *before* the self-status proof call — a transport interruption between
 * those two steps must never lose the already-issued credential.
 */
class RedeemSecurePairingSession @Inject constructor(
    private val payloadParser: SecurePairingQrPayloadParser,
    private val pairingApi: SecurePairingApiPort,
    private val vault: SecureCredentialVault,
    private val deviceIdentityProvider: PairingDeviceIdentityLocalDataSource,
    private val timeProvider: TimeProvider,
    private val verificationStep: PendingCredentialVerificationStep,
) {
    suspend fun redeemQr(
        rawQrPayload: String,
        policy: SecurePairingQrValidationPolicy = SecurePairingQrValidationPolicy(),
    ): SecurePairingRedemptionOutcome {
        val parseResult = payloadParser.parse(rawQrPayload, policy)
        val payload = (parseResult as? SecurePairingQrPayloadParseResult.Valid)?.payload
            ?: return SecurePairingRedemptionOutcome.InvalidPayload

        val deviceIdentity = deviceIdentityProvider.getOrCreate()
        val endpoint = TrustedConnectorEndpoint.fromValidatedQrPayload(payload)
        val redeemOutcome = pairingApi.redeemQr(payload, deviceIdentity)
        return finishRedemption(redeemOutcome, endpoint, deviceIdentity.logicalDeviceId)
    }

    suspend fun redeemShortCode(shortCode: String, endpoint: TrustedConnectorEndpoint): SecurePairingRedemptionOutcome {
        val deviceIdentity = deviceIdentityProvider.getOrCreate()
        val redeemOutcome = pairingApi.redeemShortCode(shortCode, endpoint, deviceIdentity)
        return finishRedemption(redeemOutcome, endpoint, deviceIdentity.logicalDeviceId)
    }

    private suspend fun finishRedemption(
        redeemOutcome: PairingRedeemOutcome,
        endpoint: TrustedConnectorEndpoint,
        logicalDeviceId: String,
    ): SecurePairingRedemptionOutcome {
        val success = when (redeemOutcome) {
            is PairingRedeemOutcome.Success -> redeemOutcome
            is PairingRedeemOutcome.Rejected ->
                return SecurePairingRedemptionOutcome.RedemptionRejected(redeemOutcome.reasonCode, redeemOutcome.httpStatus)
            PairingRedeemOutcome.TransportFailure -> return SecurePairingRedemptionOutcome.TransportFailure
            PairingRedeemOutcome.MalformedResponse -> return SecurePairingRedemptionOutcome.MalformedResponse
        }

        val storeResult = vault.storePendingVerification(
            credentialId = success.credentialId,
            deviceId = logicalDeviceId,
            rawCredential = success.token,
            endpoint = endpoint,
            createdAtEpochMillis = timeProvider.nowEpochMillis(),
        )
        if (storeResult is SecureCredentialVaultWriteResult.KeystoreUnavailable) {
            return SecurePairingRedemptionOutcome.KeystoreUnavailable
        }

        return verificationStep.verify(success.credentialId, success.token, endpoint, expectedDeviceId = logicalDeviceId)
    }
}

/** Retries the self-status proof for whatever is currently PENDING_VERIFICATION — no new redemption. */
class RetryPendingCredentialVerification @Inject constructor(
    private val vault: SecureCredentialVault,
    private val verificationStep: PendingCredentialVerificationStep,
) {
    suspend operator fun invoke(): SecurePairingRedemptionOutcome {
        val record = vault.read() ?: return SecurePairingRedemptionOutcome.NoPendingCredential
        if (record.state != SecurePairingCredentialState.PENDING_VERIFICATION) {
            return SecurePairingRedemptionOutcome.NoPendingCredential
        }
        val decrypted = vault.readDecryptedCredential()
        val rawCredential = (decrypted as? CredentialDecryptionResult.Success)?.plaintext?.toString(Charsets.UTF_8)
            ?: run {
                vault.markRePairRequired(record.credentialId)
                return SecurePairingRedemptionOutcome.RedemptionRejected(reasonCode = "CREDENTIAL_UNRECOVERABLE", httpStatus = 0)
            }
        return verificationStep.verify(record.credentialId, rawCredential, record.endpoint, expectedDeviceId = record.deviceId)
    }
}

/** Read-only snapshot of the current secure-pairing trust state — never polls, never blocks on the network. */
class ReadSecurePairingState @Inject constructor(
    private val vault: SecureCredentialVault,
) {
    suspend operator fun invoke(): SecurePairingState {
        val record = vault.read() ?: return SecurePairingState.Unpaired
        return when (record.state) {
            SecurePairingCredentialState.PENDING_VERIFICATION -> SecurePairingState.PendingVerification(record.credentialId)
            SecurePairingCredentialState.ACTIVE -> SecurePairingState.Active(record.credentialId, record.endpoint.connectorId)
            SecurePairingCredentialState.RE_PAIR_REQUIRED -> SecurePairingState.RePairRequired
        }
    }
}

/**
 * Self-revocation only — authenticates with the current record's own credential, exactly
 * matching the Connector's self-revoke contract (never a Desktop-admin-style explicit target).
 */
class RevokeSecurePairingCredential @Inject constructor(
    private val vault: SecureCredentialVault,
    private val pairingApi: SecurePairingApiPort,
) {
    suspend operator fun invoke(): SecurePairingRevocationOutcome {
        val record = vault.read() ?: return SecurePairingRevocationOutcome.NoActiveCredential
        val decrypted = vault.readDecryptedCredential()
        val rawCredential = (decrypted as? CredentialDecryptionResult.Success)?.plaintext?.toString(Charsets.UTF_8)
            ?: run {
                // Nothing recoverable to revoke server-side with — the local record is unusable
                // either way, so clear it rather than leaving a permanently stuck trust record.
                vault.clear()
                return SecurePairingRevocationOutcome.NoActiveCredential
            }

        return when (pairingApi.revokeSelf(record.endpoint, rawCredential)) {
            PairingSelfRevokeOutcome.Revoked -> {
                vault.clear()
                SecurePairingRevocationOutcome.Revoked
            }
            PairingSelfRevokeOutcome.Unauthorized -> {
                vault.clear()
                SecurePairingRevocationOutcome.Unauthorized
            }
            PairingSelfRevokeOutcome.TransportFailure, PairingSelfRevokeOutcome.MalformedResponse ->
                SecurePairingRevocationOutcome.TransportFailure
        }
    }
}

/** Clears a trust record only when it is already known-invalid (RE_PAIR_REQUIRED) — never a valid or pending one. */
class ClearInvalidSecureTrust @Inject constructor(
    private val vault: SecureCredentialVault,
) {
    suspend operator fun invoke() {
        val record = vault.read() ?: return
        if (record.state == SecurePairingCredentialState.RE_PAIR_REQUIRED) {
            vault.clear()
        }
    }
}
