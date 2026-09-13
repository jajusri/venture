package com.jajusri.venture.core.connectorauth.domain

import com.jajusri.venture.core.pairing.data.local.SecureCredentialVault
import com.jajusri.venture.core.pairing.data.local.SecureCredentialVaultReadOutcome
import com.jajusri.venture.core.pairing.domain.model.SecurePairingCredentialState
import javax.inject.Inject
import javax.inject.Singleton

enum class ConnectorTransportSelection { LEGACY, AUTHENTICATED }

/**
 * The shared LEGACY/AUTHENTICATED decision point every repository cutover consults before
 * choosing a transport for one operation (company in Phase 3S-A; ledger/stock/voucher in later
 * phases). Reads only [SecureCredentialVault.readOutcome] — never decrypts the credential, never
 * contacts the network, never mutates the vault, never inspects legacy pairing state, and never
 * caches its result across calls, so a vault-state change is observed by the very next
 * [resolve] call.
 *
 * AUTHENTICATED once any secure-pairing credential record exists, regardless of its state — not
 * only ACTIVE. A device that has entered secure pairing and is currently PENDING_VERIFICATION or
 * RE_PAIR_REQUIRED must never fall back to the unauthenticated legacy transport: the authenticated
 * port itself resolves those states into a typed zero-network-call result (see
 * [com.jajusri.venture.core.connectorauth.domain.AuthenticatedConnectorContextResolution]) rather
 * than this gate silently downgrading past them. AUTHENTICATED also applies when the vault record
 * exists but is [SecureCredentialVaultReadOutcome.Unreadable] (storage corruption, or a persisted
 * field that no longer parses) — an installation that ever began secure enrollment must never be
 * reclassified as legacy-eligible merely because that enrollment record currently fails to read
 * back cleanly; see Phase 3T-R1. LEGACY applies only to
 * [SecureCredentialVaultReadOutcome.NoRecord] — a device that has never stored a secure-pairing
 * credential record at all, e.g. an existing pre-secure-pairing installation, or a clean install
 * before pairing has begun.
 */
interface ConnectorTransportSelectionGate {
    suspend fun resolve(): ConnectorTransportSelection
}

@Singleton
class DefaultConnectorTransportSelectionGate @Inject constructor(
    private val vault: SecureCredentialVault,
) : ConnectorTransportSelectionGate {

    override suspend fun resolve(): ConnectorTransportSelection = when (val outcome = vault.readOutcome()) {
        SecureCredentialVaultReadOutcome.NoRecord -> ConnectorTransportSelection.LEGACY
        SecureCredentialVaultReadOutcome.Unreadable -> ConnectorTransportSelection.AUTHENTICATED
        is SecureCredentialVaultReadOutcome.Present -> when (outcome.record.state) {
            SecurePairingCredentialState.ACTIVE,
            SecurePairingCredentialState.PENDING_VERIFICATION,
            SecurePairingCredentialState.RE_PAIR_REQUIRED,
            -> ConnectorTransportSelection.AUTHENTICATED
        }
    }
}
