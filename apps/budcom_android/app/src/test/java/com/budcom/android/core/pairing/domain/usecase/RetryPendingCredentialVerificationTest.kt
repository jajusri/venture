package com.budcom.android.core.pairing.domain.usecase

import com.budcom.android.core.pairing.data.local.FakeSecureCredentialVault
import com.budcom.android.core.pairing.data.local.InMemoryVaultBackingStore
import com.budcom.android.core.pairing.data.remote.FakeSecurePairingApiPort
import com.budcom.android.core.pairing.data.remote.PairingSelfStatusOutcome
import com.budcom.android.core.pairing.domain.model.SecurePairingCredentialState
import com.budcom.android.core.pairing.domain.model.TrustedConnectorEndpoint
import com.budcom.android.core.security.FakeCredentialCipher
import com.budcom.android.core.util.TimeProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val NOW = 1_800_000_000_000L

private val testEndpoint = TrustedConnectorEndpoint.fromTrustedPublicMetadata(
    connectorId = "connector-abc",
    connectorName = "Front Desk",
    host = "10.0.0.5",
    securePort = 8443,
    transportFingerprint = "sha256/AAAA",
    fingerprintAlgorithm = "sha256",
    transportIdentityVersion = 1,
)

class RetryPendingCredentialVerificationTest {

    private val timeProvider = TimeProvider { NOW }

    @Test
    fun `no pending credential produces NoPendingCredential without any network call`() = runTest {
        val api = FakeSecurePairingApiPort()
        val vault = FakeSecureCredentialVault()
        val useCase = RetryPendingCredentialVerification(vault, PendingCredentialVerificationStep(api, vault, timeProvider))

        val outcome = useCase()

        assertEquals(SecurePairingRedemptionOutcome.NoPendingCredential, outcome)
        assertEquals(0, api.getCredentialSelfCallCount)
    }

    @Test
    fun `an already-ACTIVE record is not retried`() = runTest {
        val api = FakeSecurePairingApiPort()
        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", "raw-token", testEndpoint, NOW)
        vault.markActive("cred-1", NOW)
        val useCase = RetryPendingCredentialVerification(vault, PendingCredentialVerificationStep(api, vault, timeProvider))

        val outcome = useCase()

        assertEquals(SecurePairingRedemptionOutcome.NoPendingCredential, outcome)
        assertEquals(0, api.getCredentialSelfCallCount)
    }

    // 47. pending verification retries after repository recreation (no new redemption call — the
    // encrypted credential from a PRIOR process is proven directly via self-status).
    @Test
    fun `retrying after a simulated process restart proves the pending credential without redeeming again`() = runTest {
        val backing = InMemoryVaultBackingStore()
        val cipher = FakeCredentialCipher()
        val firstProcessVault = FakeSecureCredentialVault(cipher, backing)
        firstProcessVault.storePendingVerification("cred-1", "device-1", "raw-token", testEndpoint, NOW)

        // Simulate an app restart: a fresh vault instance over the same underlying storage.
        val secondProcessVault = FakeSecureCredentialVault(cipher, backing)
        val api = FakeSecurePairingApiPort(
            selfStatusResult = PairingSelfStatusOutcome.Active("cred-1", "device-1", null, "connector-abc", "2026-01-01T00:00:00Z", null, "active"),
        )
        val useCase = RetryPendingCredentialVerification(secondProcessVault, PendingCredentialVerificationStep(api, secondProcessVault, timeProvider))

        val outcome = useCase()

        assertTrue(outcome is SecurePairingRedemptionOutcome.Verified)
        assertEquals(SecurePairingCredentialState.ACTIVE, secondProcessVault.read()?.state)
        assertEquals(0, api.redeemQrCallCount)
        assertEquals(0, api.redeemShortCodeCallCount)
        assertEquals(1, api.getCredentialSelfCallCount)
    }

    @Test
    fun `a retry that is still unauthorized marks the record RE_PAIR_REQUIRED`() = runTest {
        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", "raw-token", testEndpoint, NOW)
        val api = FakeSecurePairingApiPort(selfStatusResult = PairingSelfStatusOutcome.Unauthorized)
        val useCase = RetryPendingCredentialVerification(vault, PendingCredentialVerificationStep(api, vault, timeProvider))

        val outcome = useCase()

        assertTrue(outcome is SecurePairingRedemptionOutcome.RedemptionRejected)
        assertEquals(SecurePairingCredentialState.RE_PAIR_REQUIRED, vault.read()?.state)
    }

    @Test
    fun `a retry that is still transiently unreachable keeps the record PENDING_VERIFICATION`() = runTest {
        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", "raw-token", testEndpoint, NOW)
        val api = FakeSecurePairingApiPort(selfStatusResult = PairingSelfStatusOutcome.TransportFailure)
        val useCase = RetryPendingCredentialVerification(vault, PendingCredentialVerificationStep(api, vault, timeProvider))

        val outcome = useCase()

        assertTrue(outcome is SecurePairingRedemptionOutcome.PendingRetryable)
        assertEquals(SecurePairingCredentialState.PENDING_VERIFICATION, vault.read()?.state)
    }

    // 34 (use-case angle). A lost Keystore key while pending marks the record RE_PAIR_REQUIRED
    // rather than crashing or silently treating the credential as gone-but-recoverable.
    @Test
    fun `a lost encryption key on retry marks the record RE_PAIR_REQUIRED`() = runTest {
        val cipher = FakeCredentialCipher()
        val vault = FakeSecureCredentialVault(cipher = cipher)
        vault.storePendingVerification("cred-1", "device-1", "raw-token", testEndpoint, NOW)
        cipher.dropKey()
        val api = FakeSecurePairingApiPort()
        val useCase = RetryPendingCredentialVerification(vault, PendingCredentialVerificationStep(api, vault, timeProvider))

        val outcome = useCase()

        assertTrue(outcome is SecurePairingRedemptionOutcome.RedemptionRejected)
        assertEquals(SecurePairingCredentialState.RE_PAIR_REQUIRED, vault.read()?.state)
        assertEquals(0, api.getCredentialSelfCallCount)
    }
}
