package com.jajusri.venture.core.connectorauth.domain

import com.jajusri.venture.core.pairing.data.local.FakeSecureCredentialVault
import com.jajusri.venture.core.pairing.data.local.InMemoryVaultBackingStore
import com.jajusri.venture.core.pairing.domain.model.SecurePairingCredentialRecord
import com.jajusri.venture.core.pairing.domain.model.SecurePairingCredentialState
import com.jajusri.venture.core.pairing.domain.model.TrustedConnectorEndpoint
import com.jajusri.venture.core.security.EncryptedPayload
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    // ============================== Phase 3T-R1: enrollment-history non-downgrade ==============================

    // 5/6/7/8. Unreadable selects AUTHENTICATED, never LEGACY — including a genuinely corrupted,
    // previously-enrolled record (the exact gap Phase 3T-R1 closes).
    @Test
    fun `resolves AUTHENTICATED, never LEGACY, when the vault outcome is Unreadable`() = runTest {
        val store = InMemoryVaultBackingStore()
        val vault = FakeSecureCredentialVault(backingStore = store)
        vault.storePendingVerification("cred-1", "device-1", "raw-token", sampleEndpoint(), 1_000L)
        store.unreadable = true
        val gate = DefaultConnectorTransportSelectionGate(vault)

        val result = gate.resolve()

        assertEquals(ConnectorTransportSelection.AUTHENTICATED, result)
        assertTrue(result != ConnectorTransportSelection.LEGACY)
    }

    // 9. a truly cleared/no-record vault can still select LEGACY (Unreadable is not a one-way trap)
    @Test
    fun `a genuinely empty vault (never enrolled or intentionally cleared) still resolves LEGACY`() = runTest {
        val store = InMemoryVaultBackingStore()
        val vault = FakeSecureCredentialVault(backingStore = store)
        vault.storePendingVerification("cred-1", "device-1", "raw-token", sampleEndpoint(), 1_000L)
        vault.clear()
        val gate = DefaultConnectorTransportSelectionGate(vault)

        assertEquals(ConnectorTransportSelection.LEGACY, gate.resolve())
    }

    // 11/12/13. NoRecord -> Present -> Unreadable -> Present transitions are each observed correctly
    // by the very next call, with no downgrade to LEGACY once any record has ever existed.
    @Test
    fun `NoRecord to Present to Unreadable to Present transitions are each observed without downgrading`() = runTest {
        val store = InMemoryVaultBackingStore()
        val vault = FakeSecureCredentialVault(backingStore = store)
        val gate = DefaultConnectorTransportSelectionGate(vault)

        assertEquals(ConnectorTransportSelection.LEGACY, gate.resolve())

        vault.storePendingVerification("cred-1", "device-1", "raw-token", sampleEndpoint(), 1_000L)
        assertEquals(ConnectorTransportSelection.AUTHENTICATED, gate.resolve())

        store.unreadable = true
        assertEquals(ConnectorTransportSelection.AUTHENTICATED, gate.resolve())

        store.unreadable = false
        vault.markActive("cred-1", 2_000L)
        assertEquals(ConnectorTransportSelection.AUTHENTICATED, gate.resolve())
    }
}

private fun sampleEndpoint() = TrustedConnectorEndpoint.fromTrustedPublicMetadata(
    connectorId = "connector-abc",
    connectorName = "Front Desk",
    host = "10.0.0.5",
    securePort = 8443,
    transportFingerprint = "sha256/AAAA",
    fingerprintAlgorithm = "sha256",
    transportIdentityVersion = 1,
)

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
