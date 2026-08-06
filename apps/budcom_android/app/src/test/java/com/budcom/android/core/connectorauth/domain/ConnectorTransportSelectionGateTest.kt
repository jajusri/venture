package com.budcom.android.core.connectorauth.domain

import com.budcom.android.core.pairing.data.local.FakeSecureCredentialVault
import com.budcom.android.core.pairing.data.local.InMemoryVaultBackingStore
import com.budcom.android.core.pairing.domain.model.SecurePairingCredentialRecord
import com.budcom.android.core.pairing.domain.model.SecurePairingCredentialState
import com.budcom.android.core.pairing.domain.model.TrustedConnectorEndpoint
import com.budcom.android.core.security.EncryptedPayload
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectorTransportSelectionGateTest {

    @Test
    fun `resolves LEGACY when no credential has ever been stored`() = runTest {
        val gate = DefaultConnectorTransportSelectionGate(FakeSecureCredentialVault())

        assertEquals(ConnectorTransportSelection.LEGACY, gate.resolve())
    }

    @Test
    fun `resolves AUTHENTICATED when the stored credential is ACTIVE`() = runTest {
        val store = InMemoryVaultBackingStore().apply { record = sampleRecord(SecurePairingCredentialState.ACTIVE) }
        val gate = DefaultConnectorTransportSelectionGate(FakeSecureCredentialVault(backingStore = store))

        assertEquals(ConnectorTransportSelection.AUTHENTICATED, gate.resolve())
    }

    @Test
    fun `resolves AUTHENTICATED when the stored credential is PENDING_VERIFICATION — never downgrades to LEGACY`() = runTest {
        val store = InMemoryVaultBackingStore().apply { record = sampleRecord(SecurePairingCredentialState.PENDING_VERIFICATION) }
        val gate = DefaultConnectorTransportSelectionGate(FakeSecureCredentialVault(backingStore = store))

        assertEquals(ConnectorTransportSelection.AUTHENTICATED, gate.resolve())
    }

    @Test
    fun `resolves AUTHENTICATED when the stored credential is RE_PAIR_REQUIRED — never downgrades to LEGACY`() = runTest {
        val store = InMemoryVaultBackingStore().apply { record = sampleRecord(SecurePairingCredentialState.RE_PAIR_REQUIRED) }
        val gate = DefaultConnectorTransportSelectionGate(FakeSecureCredentialVault(backingStore = store))

        assertEquals(ConnectorTransportSelection.AUTHENTICATED, gate.resolve())
    }

    @Test
    fun `an existing no-record installation (e g the pre-secure-pairing OnePlus device) still resolves LEGACY`() = runTest {
        val gate = DefaultConnectorTransportSelectionGate(FakeSecureCredentialVault())

        assertEquals(ConnectorTransportSelection.LEGACY, gate.resolve())
    }

    @Test
    fun `observes a vault state change on the very next call`() = runTest {
        val store = InMemoryVaultBackingStore()
        val gate = DefaultConnectorTransportSelectionGate(FakeSecureCredentialVault(backingStore = store))

        assertEquals(ConnectorTransportSelection.LEGACY, gate.resolve())

        store.record = sampleRecord(SecurePairingCredentialState.ACTIVE)
        assertEquals(ConnectorTransportSelection.AUTHENTICATED, gate.resolve())
    }

    @Test
    fun `observes a credential moving from ACTIVE to RE_PAIR_REQUIRED without ever falling back to LEGACY`() = runTest {
        val store = InMemoryVaultBackingStore().apply { record = sampleRecord(SecurePairingCredentialState.ACTIVE) }
        val gate = DefaultConnectorTransportSelectionGate(FakeSecureCredentialVault(backingStore = store))

        assertEquals(ConnectorTransportSelection.AUTHENTICATED, gate.resolve())

        store.record = sampleRecord(SecurePairingCredentialState.RE_PAIR_REQUIRED)
        assertEquals(ConnectorTransportSelection.AUTHENTICATED, gate.resolve())
    }
}

private fun sampleRecord(state: SecurePairingCredentialState) = SecurePairingCredentialRecord(
    credentialId = "cred-1",
    deviceId = "device-1",
    encryptedCredential = EncryptedPayload(ciphertext = byteArrayOf(1, 2, 3), iv = byteArrayOf(4, 5, 6), formatVersion = 1),
    endpoint = TrustedConnectorEndpoint(
        connectorId = "connector-1",
        connectorName = "Test Connector",
        host = "192.168.1.10",
        securePort = 8443,
        transportFingerprint = "sha256/AAAA",
        fingerprintAlgorithm = "sha256",
        transportIdentityVersion = 1,
    ),
    createdAtEpochMillis = 1_000L,
    lastVerifiedAtEpochMillis = null,
    state = state,
)
