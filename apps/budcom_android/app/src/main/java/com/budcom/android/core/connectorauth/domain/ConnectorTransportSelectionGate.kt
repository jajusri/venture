package com.budcom.android.core.connectorauth.domain

import com.budcom.android.core.pairing.data.local.SecureCredentialVault
import com.budcom.android.core.pairing.domain.model.SecurePairingCredentialState
import javax.inject.Inject
import javax.inject.Singleton

enum class ConnectorTransportSelection { LEGACY, AUTHENTICATED }

/**
 * The shared LEGACY/AUTHENTICATED decision point every repository cutover consults before
 * choosing a transport for one operation (company in Phase 3S-A; ledger/stock/voucher in later
 * phases). Reads only [SecureCredentialVault.read] — never decrypts the credential, never
 * contacts the network, never mutates the vault, never inspects legacy pairing state, and never
 * caches its result across calls, so a vault-state change is observed by the very next
 * [resolve] call.
 *
 * AUTHENTICATED once any secure-pairing credential record exists, regardless of its state — not
 * only ACTIVE. A device that has entered secure pairing and is currently PENDING_VERIFICATION or
 * RE_PAIR_REQUIRED must never fall back to the unauthenticated legacy transport: the authenticated
 * port itself resolves those states into a typed zero-network-call result (see
 * [com.budcom.android.core.connectorauth.domain.AuthenticatedConnectorContextResolution]) rather
 * than this gate silently downgrading past them. LEGACY applies only to a device that has never
 * stored a secure-pairing credential record at all — e.g. an existing pre-secure-pairing
 * installation, or a clean install before pairing has begun.
 */
interface ConnectorTransportSelectionGate {
    suspend fun resolve(): ConnectorTransportSelection
}

@Singleton
class DefaultConnectorTransportSelectionGate @Inject constructor(
    private val vault: SecureCredentialVault,
) : ConnectorTransportSelectionGate {

    override suspend fun resolve(): ConnectorTransportSelection {
        val record = vault.read() ?: return ConnectorTransportSelection.LEGACY
        return when (record.state) {
            SecurePairingCredentialState.ACTIVE,
            SecurePairingCredentialState.PENDING_VERIFICATION,
            SecurePairingCredentialState.RE_PAIR_REQUIRED,
            -> ConnectorTransportSelection.AUTHENTICATED
        }
    }
}
