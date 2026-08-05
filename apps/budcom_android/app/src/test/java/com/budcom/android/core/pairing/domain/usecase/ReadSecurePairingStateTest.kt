package com.budcom.android.core.pairing.domain.usecase

import com.budcom.android.core.pairing.data.local.FakeSecureCredentialVault
import com.budcom.android.core.pairing.domain.SecurePairingState
import com.budcom.android.core.pairing.domain.model.TrustedConnectorEndpoint
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private val testEndpoint = TrustedConnectorEndpoint.fromTrustedPublicMetadata(
    connectorId = "connector-abc",
    connectorName = "Front Desk",
    host = "10.0.0.5",
    securePort = 8443,
    transportFingerprint = "sha256/AAAA",
    fingerprintAlgorithm = "sha256",
    transportIdentityVersion = 1,
)

class ReadSecurePairingStateTest {

    @Test
    fun `no trust record reads as Unpaired`() = runTest {
        val state = ReadSecurePairingState(FakeSecureCredentialVault()).invoke()
        assertEquals(SecurePairingState.Unpaired, state)
    }

    @Test
    fun `a PENDING_VERIFICATION record reads as PendingVerification`() = runTest {
        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", "raw-token", testEndpoint, 1_000L)

        val state = ReadSecurePairingState(vault).invoke()

        assertEquals(SecurePairingState.PendingVerification("cred-1"), state)
    }

    @Test
    fun `an ACTIVE record reads as Active with its Connector ID`() = runTest {
        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", "raw-token", testEndpoint, 1_000L)
        vault.markActive("cred-1", 1_500L)

        val state = ReadSecurePairingState(vault).invoke()

        assertEquals(SecurePairingState.Active("cred-1", "connector-abc"), state)
    }

    @Test
    fun `a RE_PAIR_REQUIRED record reads as RePairRequired`() = runTest {
        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", "raw-token", testEndpoint, 1_000L)
        vault.markRePairRequired("cred-1")

        val state = ReadSecurePairingState(vault).invoke()

        assertEquals(SecurePairingState.RePairRequired, state)
    }
}
