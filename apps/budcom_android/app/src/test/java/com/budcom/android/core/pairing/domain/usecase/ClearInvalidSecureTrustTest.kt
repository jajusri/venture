package com.budcom.android.core.pairing.domain.usecase

import com.budcom.android.core.pairing.data.local.FakeSecureCredentialVault
import com.budcom.android.core.pairing.domain.model.SecurePairingCredentialState
import com.budcom.android.core.pairing.domain.model.TrustedConnectorEndpoint
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

class ClearInvalidSecureTrustTest {

    @Test
    fun `clears a RE_PAIR_REQUIRED record`() = runTest {
        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", "raw-token", testEndpoint, 1_000L)
        vault.markRePairRequired("cred-1")

        ClearInvalidSecureTrust(vault).invoke()

        assertNull(vault.read())
    }

    @Test
    fun `never clears an ACTIVE record`() = runTest {
        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", "raw-token", testEndpoint, 1_000L)
        vault.markActive("cred-1", 1_500L)

        ClearInvalidSecureTrust(vault).invoke()

        assertNotNull(vault.read())
        assertEquals(SecurePairingCredentialState.ACTIVE, vault.read()?.state)
    }

    @Test
    fun `never clears a PENDING_VERIFICATION record`() = runTest {
        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", "raw-token", testEndpoint, 1_000L)

        ClearInvalidSecureTrust(vault).invoke()

        assertNotNull(vault.read())
        assertEquals(SecurePairingCredentialState.PENDING_VERIFICATION, vault.read()?.state)
    }

    @Test
    fun `is a no-op when there is no trust record at all`() = runTest {
        val vault = FakeSecureCredentialVault()

        ClearInvalidSecureTrust(vault).invoke()

        assertNull(vault.read())
    }
}
