package com.budcom.android.core.pairing.domain.usecase

import com.budcom.android.core.pairing.data.local.FakePairingDeviceIdentityLocalDataSource
import com.budcom.android.core.pairing.data.local.FakeSecureCredentialVault
import com.budcom.android.core.pairing.data.local.SecureCredentialVault
import com.budcom.android.core.pairing.data.remote.FakeSecurePairingApiPort
import com.budcom.android.core.pairing.data.remote.PairingRedeemOutcome
import com.budcom.android.core.pairing.data.remote.PairingSelfStatusOutcome
import com.budcom.android.core.pairing.domain.model.PairingDeviceIdentity
import com.budcom.android.core.pairing.domain.model.SecurePairingCredentialState
import com.budcom.android.core.pairing.domain.model.SecurePairingQrPayloadParser
import com.budcom.android.core.pairing.domain.model.TrustedConnectorEndpoint
import com.budcom.android.core.util.TimeProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64

private const val NOW = 1_800_000_000_000L

private fun testFingerprint(): String {
    val keyPair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
    val digest = MessageDigest.getInstance("SHA-256").digest(keyPair.public.encoded)
    return "sha256/" + Base64.getEncoder().encodeToString(digest)
}

private fun qrJson(connectorId: String = "connector-abc", host: String = "10.0.0.5"): String = """
    {
      "schemaVersion": "1",
      "pairingSessionId": "11111111-1111-1111-1111-111111111111",
      "secret": "one-time-secret",
      "connectorId": "$connectorId",
      "connectorName": "Front Desk",
      "host": "$host",
      "port": 8080,
      "securePort": 8443,
      "transportProtocol": "https",
      "transportFingerprint": "${testFingerprint()}",
      "fingerprintAlgorithm": "sha256",
      "transportIdentityVersion": 1,
      "expiresAt": "${Instant.ofEpochMilli(NOW + 120_000L)}"
    }
""".trimIndent()

class RedeemSecurePairingSessionTest {

    private val timeProvider = TimeProvider { NOW }
    private val parser = SecurePairingQrPayloadParser(timeProvider)

    private fun useCaseOf(
        api: FakeSecurePairingApiPort,
        vault: SecureCredentialVault = FakeSecureCredentialVault(),
        deviceIdentity: FakePairingDeviceIdentityLocalDataSource = FakePairingDeviceIdentityLocalDataSource(),
    ): RedeemSecurePairingSession {
        val step = PendingCredentialVerificationStep(api, vault, timeProvider)
        return RedeemSecurePairingSession(parser, api, vault, deviceIdentity, timeProvider, step)
    }

    private suspend fun seedActive(vault: SecureCredentialVault): com.budcom.android.core.pairing.domain.model.SecurePairingCredentialRecord {
        val endpoint = TrustedConnectorEndpoint.fromTrustedPublicMetadata(
            connectorId = "old-connector",
            connectorName = "Old Connector",
            host = "10.0.0.2",
            securePort = 8443,
            transportFingerprint = "sha256/OLD",
            fingerprintAlgorithm = "sha256",
            transportIdentityVersion = 1,
        )
        vault.storePendingVerification("old-credential", "device-uuid-fixed", "old-token", endpoint, NOW - 1_000L)
        vault.markActive("old-credential", NOW - 500L)
        return requireNotNull(vault.read())
    }

    // 44. successful redemption first stores encrypted pending state
    @Test
    fun `successful redemption stores the encrypted credential as PENDING_VERIFICATION`() = runTest {
        val vault = FakeSecureCredentialVault()
        val api = FakeSecurePairingApiPort(
            redeemResult = PairingRedeemOutcome.Success("connector-abc", "Front Desk", "cred-1", "raw-token"),
            selfStatusResult = PairingSelfStatusOutcome.TransportFailure,
        )

        useCaseOf(api, vault).redeemQr(qrJson())

        val record = vault.read()
        assertEquals(SecurePairingCredentialState.PENDING_VERIFICATION, record?.state)
        assertEquals("cred-1", record?.credentialId)
    }

    // 45. successful self-status promotes pending to active
    @Test
    fun `successful self-status promotes the pending credential to ACTIVE`() = runTest {
        val api = FakeSecurePairingApiPort(
            redeemResult = PairingRedeemOutcome.Success("connector-abc", "Front Desk", "cred-1", "raw-token"),
            selfStatusResult = PairingSelfStatusOutcome.Active("cred-1", "device-uuid-fixed", null, "connector-abc", "2026-01-01T00:00:00Z", null, "active"),
        )
        val vault = FakeSecureCredentialVault()

        val outcome = useCaseOf(api, vault).redeemQr(qrJson())

        assertTrue(outcome is SecurePairingRedemptionOutcome.Verified)
        assertEquals(SecurePairingCredentialState.ACTIVE, vault.read()?.state)
    }

    // 46. transient self-status failure retains encrypted pending state
    @Test
    fun `transient self-status failure retains the encrypted pending credential for retry`() = runTest {
        val api = FakeSecurePairingApiPort(
            redeemResult = PairingRedeemOutcome.Success("connector-abc", "Front Desk", "cred-1", "raw-token"),
            selfStatusResult = PairingSelfStatusOutcome.TransportFailure,
        )
        val vault = FakeSecureCredentialVault()

        val outcome = useCaseOf(api, vault).redeemQr(qrJson())

        assertTrue(outcome is SecurePairingRedemptionOutcome.PendingRetryable)
        assertEquals(SecurePairingCredentialState.PENDING_VERIFICATION, vault.read()?.state)
        assertNotNull(vault.readDecryptedCredential())
    }

    // 48. unauthorized self-status causes re-pair-required state
    @Test
    fun `unauthorized self-status marks the record RE_PAIR_REQUIRED`() = runTest {
        val api = FakeSecurePairingApiPort(
            redeemResult = PairingRedeemOutcome.Success("connector-abc", "Front Desk", "cred-1", "raw-token"),
            selfStatusResult = PairingSelfStatusOutcome.Unauthorized,
        )
        val vault = FakeSecureCredentialVault()

        val outcome = useCaseOf(api, vault).redeemQr(qrJson())

        assertTrue(outcome is SecurePairingRedemptionOutcome.RedemptionRejected)
        assertEquals(SecurePairingCredentialState.RE_PAIR_REQUIRED, vault.read()?.state)
    }

    // 49. Connector-ID mismatch rejected
    @Test
    fun `a self-status response for a different Connector ID is rejected`() = runTest {
        val api = FakeSecurePairingApiPort(
            redeemResult = PairingRedeemOutcome.Success("connector-abc", "Front Desk", "cred-1", "raw-token"),
            selfStatusResult = PairingSelfStatusOutcome.Active("cred-1", "device-uuid-fixed", null, "connector-DIFFERENT", "2026-01-01T00:00:00Z", null, "active"),
        )
        val vault = FakeSecureCredentialVault()

        val outcome = useCaseOf(api, vault).redeemQr(qrJson())

        assertTrue(outcome is SecurePairingRedemptionOutcome.RedemptionRejected)
        assertEquals(SecurePairingCredentialState.RE_PAIR_REQUIRED, vault.read()?.state)
    }

    // 50. credential-ID mismatch rejected
    @Test
    fun `a self-status response for a different credential ID is rejected`() = runTest {
        val api = FakeSecurePairingApiPort(
            redeemResult = PairingRedeemOutcome.Success("connector-abc", "Front Desk", "cred-1", "raw-token"),
            selfStatusResult = PairingSelfStatusOutcome.Active("cred-DIFFERENT", "device-uuid-fixed", null, "connector-abc", "2026-01-01T00:00:00Z", null, "active"),
        )

        val outcome = useCaseOf(api).redeemQr(qrJson())

        assertTrue(outcome is SecurePairingRedemptionOutcome.RedemptionRejected)
    }

    // 51. device-ID mismatch rejected when present
    @Test
    fun `a self-status response for a different device ID is rejected when a device ID is present`() = runTest {
        val api = FakeSecurePairingApiPort(
            redeemResult = PairingRedeemOutcome.Success("connector-abc", "Front Desk", "cred-1", "raw-token"),
            selfStatusResult = PairingSelfStatusOutcome.Active("cred-1", "some-other-device-id", null, "connector-abc", "2026-01-01T00:00:00Z", null, "active"),
        )

        val outcome = useCaseOf(api).redeemQr(qrJson())

        assertTrue(outcome is SecurePairingRedemptionOutcome.RedemptionRejected)
    }

    @Test
    fun `a null device ID in the self-status response is not treated as a mismatch`() = runTest {
        val api = FakeSecurePairingApiPort(
            redeemResult = PairingRedeemOutcome.Success("connector-abc", "Front Desk", "cred-1", "raw-token"),
            selfStatusResult = PairingSelfStatusOutcome.Active("cred-1", null, null, "connector-abc", "2026-01-01T00:00:00Z", null, "active"),
        )

        val outcome = useCaseOf(api).redeemQr(qrJson())

        assertTrue(outcome is SecurePairingRedemptionOutcome.Verified)
    }

    // 39/40. QR redemption sends required fields, including device id/label
    @Test
    fun `redeemQr forwards the parsed payload and the device identity to the api client`() = runTest {
        val api = FakeSecurePairingApiPort(redeemResult = PairingRedeemOutcome.TransportFailure)
        val deviceIdentity = FakePairingDeviceIdentityLocalDataSource(PairingDeviceIdentity("device-xyz", "Sri's Phone"))

        useCaseOf(api, deviceIdentity = deviceIdentity).redeemQr(qrJson(connectorId = "connector-abc"))

        assertEquals(1, api.redeemQrCallCount)
        assertEquals("connector-abc", api.lastRedeemQrPayload?.connectorId)
        assertEquals("device-xyz", api.lastRedeemDeviceIdentity?.logicalDeviceId)
        assertEquals("Sri's Phone", api.lastRedeemDeviceIdentity?.deviceLabel)
    }

    // 41. short-code flow requires trusted endpoint context
    @Test
    fun `redeemShortCode uses the caller-supplied trusted endpoint, never re-deriving one`() = runTest {
        val api = FakeSecurePairingApiPort(redeemResult = PairingRedeemOutcome.TransportFailure)
        val endpoint = TrustedConnectorEndpoint.fromTrustedPublicMetadata(
            connectorId = "connector-xyz",
            connectorName = "Back Office",
            host = "10.0.0.9",
            securePort = 8443,
            transportFingerprint = "sha256/AAAA",
            fingerprintAlgorithm = "sha256",
            transportIdentityVersion = 1,
        )

        useCaseOf(api).redeemShortCode("ABCDEFGH", endpoint)

        assertEquals(1, api.redeemShortCodeCallCount)
        assertEquals(endpoint, api.lastShortCodeEndpoint)
        assertEquals("ABCDEFGH", api.lastShortCode)
    }

    @Test
    fun `an invalid QR payload is rejected before any network call`() = runTest {
        val api = FakeSecurePairingApiPort()

        val outcome = useCaseOf(api).redeemQr("not valid json")

        assertEquals(SecurePairingRedemptionOutcome.InvalidPayload, outcome)
        assertEquals(0, api.redeemQrCallCount)
    }

    @Test
    fun `redemption rejected by the Connector never stores a pending credential`() = runTest {
        val api = FakeSecurePairingApiPort(redeemResult = PairingRedeemOutcome.Rejected("REDEMPTION_FAILED", 401))
        val vault = FakeSecureCredentialVault()

        val outcome = useCaseOf(api, vault).redeemQr(qrJson())

        assertTrue(outcome is SecurePairingRedemptionOutcome.RedemptionRejected)
        assertNull(vault.read())
    }

    // 58. no polling introduced — exactly one redeem call and one self-status call, no loop
    @Test
    fun `exactly one redeem call and one self-status call are made per redemption`() = runTest {
        val api = FakeSecurePairingApiPort(
            redeemResult = PairingRedeemOutcome.Success("connector-abc", "Front Desk", "cred-1", "raw-token"),
            selfStatusResult = PairingSelfStatusOutcome.Active("cred-1", "device-uuid-fixed", null, "connector-abc", "2026-01-01T00:00:00Z", null, "active"),
        )

        useCaseOf(api).redeemQr(qrJson())

        assertEquals(1, api.redeemQrCallCount)
        assertEquals(1, api.getCredentialSelfCallCount)
    }

    @Test
    fun `re-pair transport failure leaves the existing ACTIVE record untouched`() = runTest {
        val vault = FakeSecureCredentialVault()
        val original = seedActive(vault)
        val api = FakeSecurePairingApiPort(
            redeemResult = PairingRedeemOutcome.Success("connector-abc", "Front Desk", "new-credential", "new-token"),
            selfStatusResult = PairingSelfStatusOutcome.TransportFailure,
        )

        val outcome = useCaseOf(api, vault).redeemQr(qrJson())

        assertEquals(SecurePairingRedemptionOutcome.TransportFailure, outcome)
        assertEquals(original, vault.read())
    }

    @Test
    fun `re-pair identity mismatch leaves the existing ACTIVE record untouched`() = runTest {
        val vault = FakeSecureCredentialVault()
        val original = seedActive(vault)
        val api = FakeSecurePairingApiPort(
            redeemResult = PairingRedeemOutcome.Success("connector-abc", "Front Desk", "new-credential", "new-token"),
            selfStatusResult = PairingSelfStatusOutcome.Active(
                "different-credential", "device-uuid-fixed", null, "connector-abc", "2026-01-01T00:00:00Z", null, "active",
            ),
        )

        val outcome = useCaseOf(api, vault).redeemQr(qrJson())

        assertTrue(outcome is SecurePairingRedemptionOutcome.RedemptionRejected)
        assertEquals(original, vault.read())
    }

    @Test
    fun `verified re-pair atomically replaces the old record with the new ACTIVE record`() = runTest {
        val vault = FakeSecureCredentialVault()
        seedActive(vault)
        val api = FakeSecurePairingApiPort(
            redeemResult = PairingRedeemOutcome.Success("connector-abc", "Front Desk", "new-credential", "new-token"),
            selfStatusResult = PairingSelfStatusOutcome.Active(
                "new-credential", "device-uuid-fixed", null, "connector-abc", "2026-01-01T00:00:00Z", null, "active",
            ),
        )

        val outcome = useCaseOf(api, vault).redeemQr(qrJson())

        assertTrue(outcome is SecurePairingRedemptionOutcome.Verified)
        assertEquals("new-credential", vault.read()?.credentialId)
        assertEquals(SecurePairingCredentialState.ACTIVE, vault.read()?.state)
        assertEquals("connector-abc", vault.read()?.endpoint?.connectorId)
    }
}
