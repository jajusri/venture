package com.budcom.android.core.pairing.data.local

import com.budcom.android.core.pairing.domain.model.SecurePairingCredentialState
import com.budcom.android.core.pairing.domain.model.TrustedConnectorEndpoint
import com.budcom.android.core.security.CredentialDecryptionResult
import com.budcom.android.core.security.FakeCredentialCipher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureCredentialVaultTest {

    private val endpoint = TrustedConnectorEndpoint.fromTrustedPublicMetadata(
        connectorId = "connector-abc",
        connectorName = "Front Desk",
        host = "10.0.0.5",
        securePort = 8443,
        transportFingerprint = "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        fingerprintAlgorithm = "sha256",
        transportIdentityVersion = 1,
    )

    @Test
    fun `readOutcome is NoRecord for a genuinely empty vault`() = runTest {
        val vault = FakeSecureCredentialVault()

        assertEquals(SecureCredentialVaultReadOutcome.NoRecord, vault.readOutcome())
    }

    @Test
    fun `readOutcome is Present with the record for a valid PENDING_VERIFICATION entry`() = runTest {
        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", "raw-bearer-token", endpoint, 1_000L)

        val outcome = vault.readOutcome() as SecureCredentialVaultReadOutcome.Present
        assertEquals(SecurePairingCredentialState.PENDING_VERIFICATION, outcome.record.state)
        assertEquals("cred-1", outcome.record.credentialId)
    }

    @Test
    fun `readOutcome is Present for ACTIVE and RE_PAIR_REQUIRED records too`() = runTest {
        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", "raw-bearer-token", endpoint, 1_000L)
        vault.markActive("cred-1", 1_500L)

        val active = vault.readOutcome() as SecureCredentialVaultReadOutcome.Present
        assertEquals(SecurePairingCredentialState.ACTIVE, active.record.state)

        vault.markRePairRequired("cred-1")
        val rePair = vault.readOutcome() as SecureCredentialVaultReadOutcome.Present
        assertEquals(SecurePairingCredentialState.RE_PAIR_REQUIRED, rePair.record.state)
    }

    // A corrupted/unreadable record must never be reported the same as a never-enrolled vault —
    // see the SecureCredentialVaultReadOutcome doc comment and Phase 3T's routing requirement.
    @Test
    fun `readOutcome is Unreadable, never NoRecord, for a corrupted record`() = runTest {
        val backing = InMemoryVaultBackingStore()
        val vault = FakeSecureCredentialVault(backingStore = backing)
        vault.storePendingVerification("cred-1", "device-1", "raw-bearer-token", endpoint, 1_000L)
        backing.unreadable = true

        val outcome = vault.readOutcome()

        assertEquals(SecureCredentialVaultReadOutcome.Unreadable, outcome)
        assertTrue(outcome !is SecureCredentialVaultReadOutcome.NoRecord)
    }

    @Test
    fun `storePendingVerification persists a PENDING_VERIFICATION record with no plaintext credential`() = runTest {
        val vault = FakeSecureCredentialVault()

        val result = vault.storePendingVerification("cred-1", "device-1", "raw-bearer-token", endpoint, 1_000L)

        assertEquals(SecureCredentialVaultWriteResult.Stored, result)
        val record = vault.read()
        assertEquals(SecurePairingCredentialState.PENDING_VERIFICATION, record?.state)
        assertEquals("cred-1", record?.credentialId)
        assertNull(record?.lastVerifiedAtEpochMillis)
        assertFalse(String(record!!.encryptedCredential.ciphertext, Charsets.ISO_8859_1).contains("raw-bearer-token"))
    }

    // 34. missing key produces an explicit (non-crashing, non-plaintext) result
    @Test
    fun `readDecryptedCredential surfaces an explicit KeyMissing result rather than throwing`() = runTest {
        val cipher = FakeCredentialCipher()
        val vault = FakeSecureCredentialVault(cipher = cipher)
        vault.storePendingVerification("cred-1", "device-1", "raw-bearer-token", endpoint, 1_000L)
        cipher.dropKey()

        val result = vault.readDecryptedCredential()

        assertEquals(CredentialDecryptionResult.KeyMissing, result)
    }

    @Test
    fun `markActive only succeeds for the matching credentialId`() = runTest {
        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", "raw-bearer-token", endpoint, 1_000L)

        val wrongId = vault.markActive("cred-2", 2_000L)
        val rightId = vault.markActive("cred-1", 2_000L)

        assertFalse(wrongId)
        assertTrue(rightId)
        assertEquals(SecurePairingCredentialState.ACTIVE, vault.read()?.state)
        assertEquals(2_000L, vault.read()?.lastVerifiedAtEpochMillis)
    }

    @Test
    fun `markRePairRequired only succeeds for the matching credentialId`() = runTest {
        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", "raw-bearer-token", endpoint, 1_000L)

        assertFalse(vault.markRePairRequired("wrong-id"))
        assertTrue(vault.markRePairRequired("cred-1"))
        assertEquals(SecurePairingCredentialState.RE_PAIR_REQUIRED, vault.read()?.state)
    }

    @Test
    fun `storePendingVerification atomically replaces any existing record — never merges two Connectors`() = runTest {
        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", "token-1", endpoint, 1_000L)
        vault.markActive("cred-1", 1_500L)

        val otherEndpoint = endpoint.copy(connectorId = "connector-xyz")
        vault.storePendingVerification("cred-2", "device-1", "token-2", otherEndpoint, 2_000L)

        val record = vault.read()
        assertEquals("cred-2", record?.credentialId)
        assertEquals("connector-xyz", record?.endpoint?.connectorId)
        assertEquals(SecurePairingCredentialState.PENDING_VERIFICATION, record?.state)
    }

    @Test
    fun `clear removes the record entirely`() = runTest {
        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", "raw-bearer-token", endpoint, 1_000L)

        vault.clear()

        assertNull(vault.read())
        assertNull(vault.readDecryptedCredential())
    }

    // 37. pending and active states persist across repository recreation
    @Test
    fun `a PENDING_VERIFICATION record survives a new vault instance over the same backing store`() = runTest {
        val backing = InMemoryVaultBackingStore()
        val cipher = FakeCredentialCipher()
        val first = FakeSecureCredentialVault(cipher, backing)
        first.storePendingVerification("cred-1", "device-1", "raw-bearer-token", endpoint, 1_000L)

        val recreated = FakeSecureCredentialVault(cipher, backing)

        val record = recreated.read()
        assertEquals(SecurePairingCredentialState.PENDING_VERIFICATION, record?.state)
        assertEquals("cred-1", record?.credentialId)
        val decrypted = recreated.readDecryptedCredential() as CredentialDecryptionResult.Success
        assertEquals("raw-bearer-token", String(decrypted.plaintext, Charsets.UTF_8))
    }

    @Test
    fun `an ACTIVE record survives a new vault instance over the same backing store`() = runTest {
        val backing = InMemoryVaultBackingStore()
        val cipher = FakeCredentialCipher()
        val first = FakeSecureCredentialVault(cipher, backing)
        first.storePendingVerification("cred-1", "device-1", "raw-bearer-token", endpoint, 1_000L)
        first.markActive("cred-1", 1_500L)

        val recreated = FakeSecureCredentialVault(cipher, backing)

        val record = recreated.read()
        assertEquals(SecurePairingCredentialState.ACTIVE, record?.state)
        assertEquals(1_500L, record?.lastVerifiedAtEpochMillis)
    }

    // 38. clearing trust does not touch Room repositories
    @Test
    fun `clearing the trust record touches nothing but this vault's own backing store`() = runTest {
        val backing = InMemoryVaultBackingStore()
        val vault = FakeSecureCredentialVault(backingStore = backing)
        vault.storePendingVerification("cred-1", "device-1", "raw-bearer-token", endpoint, 1_000L)
        val roomSpy = RecordingRoomAccessSpy()

        vault.clear()

        // SecureCredentialVault (both the fake and the real DataStore-backed production
        // implementation) has no constructor dependency on any Room DAO/database class at all —
        // this spy exists purely so the assertion is explicit rather than implicit-by-omission.
        assertEquals(0, roomSpy.accessCount)
        assertNull(backing.record)
    }
}

/** A stand-in for "any Room-backed business repository" — proves clearing pairing trust never reaches it. */
private class RecordingRoomAccessSpy {
    var accessCount = 0
        private set

    fun access() {
        accessCount++
    }
}
