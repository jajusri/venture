package com.jajusri.venture.navigation

import com.jajusri.venture.core.connection.ConnectorEnrolmentGate
import com.jajusri.venture.core.pairing.data.local.SecureCredentialVault
import com.jajusri.venture.core.pairing.data.local.SecureCredentialVaultReadOutcome
import com.jajusri.venture.core.pairing.domain.model.SecurePairingCredentialState
import com.jajusri.venture.core.security.CredentialDecryptionResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the single [StartupRoutingState] the rest of the app must route around, following the
 * decision order: existing secure enrollment history first (a record — even an unreadable one —
 * always outranks any legacy signal, so an installation that ever began secure pairing can never
 * silently fall back to legacy transport), then the existing, already-proven legacy-installation
 * signal ([ConnectorEnrolmentGate]), otherwise a clean install requiring pairing.
 *
 * Deliberately reuses [ConnectorEnrolmentGate] and [SecureCredentialVault] rather than
 * introducing a new persisted marker — both already exist, are already tested, and together are
 * sufficient to classify every required case without inventing new state.
 */
interface ResolveStartupRoutingState {
    suspend operator fun invoke(): StartupRoutingState
}

@Singleton
class DefaultResolveStartupRoutingState @Inject constructor(
    private val vault: SecureCredentialVault,
    private val enrolmentGate: ConnectorEnrolmentGate,
) : ResolveStartupRoutingState {

    override suspend fun invoke(): StartupRoutingState {
        return when (val outcome = vault.readOutcome()) {
            SecureCredentialVaultReadOutcome.Unreadable -> StartupRoutingState.SecureCredentialUnavailable

            SecureCredentialVaultReadOutcome.NoRecord ->
                if (enrolmentGate.needsEnrolment()) {
                    StartupRoutingState.PairingRequired
                } else {
                    StartupRoutingState.LegacyEligible
                }

            is SecureCredentialVaultReadOutcome.Present -> when (outcome.record.state) {
                SecurePairingCredentialState.PENDING_VERIFICATION -> StartupRoutingState.PairingPending
                SecurePairingCredentialState.RE_PAIR_REQUIRED -> StartupRoutingState.RePairRequired
                SecurePairingCredentialState.ACTIVE -> when (vault.readDecryptedCredential()) {
                    is CredentialDecryptionResult.Success -> StartupRoutingState.SecureActive
                    else -> StartupRoutingState.SecureCredentialUnavailable
                }
            }
        }
    }
}
