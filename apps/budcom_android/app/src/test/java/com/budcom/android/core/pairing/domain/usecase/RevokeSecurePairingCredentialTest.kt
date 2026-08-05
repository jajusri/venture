package com.budcom.android.core.pairing.domain.usecase

import com.budcom.android.core.pairing.data.local.FakeSecureCredentialVault
import com.budcom.android.core.pairing.data.remote.FakeSecurePairingApiPort
import com.budcom.android.core.pairing.data.remote.PairingSelfRevokeOutcome
import com.budcom.android.core.pairing.domain.SecurePairingState
import com.budcom.android.core.pairing.domain.model.SecurePairingCredentialState
import com.budcom.android.core.pairing.domain.model.TrustedConnectorEndpoint
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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

class RevokeSecurePairingCredentialTest {

    @Test
    fun `no local credential produces NoActiveCredential without any network call`() = runTest {
        val api = FakeSecurePairingApiPort()
        val useCase = RevokeSecurePairingCredential(FakeSecureCredentialVault(), api)

        val outcome = useCase()

        assertEquals(SecurePairingRevocationOutcome.NoActiveCredential, outcome)
        assertEquals(0, api.revokeSelfCallCount)
    }

    // 53. self-revoke uses caller credential
    @Test
    fun `revoke authenticates with the vault's own decrypted credential`() = runTest {
        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", "my-own-raw-token", testEndpoint, 1_000L)
        vault.markActive("cred-1", 1_500L)
        val api = FakeSecurePairingApiPort(revokeResult = PairingSelfRevokeOutcome.Revoked)

        RevokeSecurePairingCredential(vault, api).invoke()

        assertEquals(1, api.revokeSelfCallCount)
        assertEquals("my-own-raw-token", api.lastRevokeBearer)
        assertEquals(testEndpoint, api.lastRevokeEndpoint)
    }

    // 54. successful revoke clears/marks only that trust record
    @Test
    fun `a successful revoke clears the local trust record`() = runTest {
        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", "raw-token", testEndpoint, 1_000L)
        vault.markActive("cred-1", 1_500L)
        val api = FakeSecurePairingApiPort(revokeResult = PairingSelfRevokeOutcome.Revoked)

        val outcome = RevokeSecurePairingCredential(vault, api).invoke()

        assertEquals(SecurePairingRevocationOutcome.Revoked, outcome)
        assertNull(vault.read())
    }

    // 55. self-revoked credential cannot remain active
    @Test
    fun `after revocation the credential can no longer be read back as active`() = runTest {
        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", "raw-token", testEndpoint, 1_000L)
        vault.markActive("cred-1", 1_500L)
        val api = FakeSecurePairingApiPort(revokeResult = PairingSelfRevokeOutcome.Revoked)

        RevokeSecurePairingCredential(vault, api).invoke()

        val stateAfter = ReadSecurePairingState(vault).invoke()
        assertEquals(SecurePairingState.Unpaired, stateAfter)
    }

    @Test
    fun `an already-unauthorized credential still clears the local record`() = runTest {
        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", "raw-token", testEndpoint, 1_000L)
        vault.markActive("cred-1", 1_500L)
        val api = FakeSecurePairingApiPort(revokeResult = PairingSelfRevokeOutcome.Unauthorized)

        val outcome = RevokeSecurePairingCredential(vault, api).invoke()

        assertEquals(SecurePairingRevocationOutcome.Unauthorized, outcome)
        assertNull(vault.read())
    }

    @Test
    fun `a transport failure during revoke leaves the local record intact for a later retry`() = runTest {
        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", "raw-token", testEndpoint, 1_000L)
        vault.markActive("cred-1", 1_500L)
        val api = FakeSecurePairingApiPort(revokeResult = PairingSelfRevokeOutcome.TransportFailure)

        val outcome = RevokeSecurePairingCredential(vault, api).invoke()

        assertEquals(SecurePairingRevocationOutcome.TransportFailure, outcome)
        assertEquals(SecurePairingCredentialState.ACTIVE, vault.read()?.state)
    }
}
