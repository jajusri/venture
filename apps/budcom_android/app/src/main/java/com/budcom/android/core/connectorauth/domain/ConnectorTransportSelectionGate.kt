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
        return if (record.state == SecurePairingCredentialState.ACTIVE) {
            ConnectorTransportSelection.AUTHENTICATED
        } else {
            ConnectorTransportSelection.LEGACY
        }
    }
}
