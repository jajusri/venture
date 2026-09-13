package com.jajusri.venture.core.pairing.domain

/**
 * Explicit secure-pairing domain states. [ReadSecurePairingState][com.jajusri.venture.core.pairing.domain.usecase.ReadSecurePairingState]
 * only ever produces the four "steady" states below (a direct reflection of the vault's own
 * [com.jajusri.venture.core.pairing.domain.model.SecurePairingCredentialState], plus the
 * no-record case) — the transient in-flight states exist as a documented vocabulary for a
 * future UI layer to bind against; the use cases in this phase run their own transitions
 * internally and return one final outcome rather than emitting a live stream through them.
 */
sealed class SecurePairingState {
    data object Unpaired : SecurePairingState()
    data object ValidatingPayload : SecurePairingState()
    data object Redeeming : SecurePairingState()
    data object StoringPendingCredential : SecurePairingState()
    data object VerifyingCredential : SecurePairingState()
    data class PendingVerification(val credentialId: String) : SecurePairingState()
    data class Active(val credentialId: String, val connectorId: String) : SecurePairingState()
    data object Revoking : SecurePairingState()
    data object Revoked : SecurePairingState()
    data object RePairRequired : SecurePairingState()
    data class Failed(val reason: String) : SecurePairingState()
}
