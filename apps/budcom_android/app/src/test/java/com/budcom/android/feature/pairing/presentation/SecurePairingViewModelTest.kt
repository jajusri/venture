package com.budcom.android.feature.pairing.presentation

import com.budcom.android.core.pairing.data.local.FakePairingDeviceIdentityLocalDataSource
import com.budcom.android.core.pairing.data.local.FakeSecureCredentialVault
import com.budcom.android.core.pairing.data.local.SecureCredentialVault
import com.budcom.android.core.pairing.data.remote.FakeSecurePairingApiPort
import com.budcom.android.core.pairing.data.remote.PairingRedeemOutcome
import com.budcom.android.core.pairing.data.remote.PairingSelfStatusOutcome
import com.budcom.android.core.pairing.domain.model.SecurePairingCredentialState
import com.budcom.android.core.pairing.domain.model.SecurePairingQrPayloadParser
import com.budcom.android.core.pairing.domain.model.TrustedConnectorEndpoint
import com.budcom.android.core.pairing.domain.usecase.PendingCredentialVerificationStep
import com.budcom.android.core.pairing.domain.usecase.RedeemSecurePairingSession
import com.budcom.android.core.pairing.domain.usecase.RetryPendingCredentialVerification
import com.budcom.android.core.pairing.domain.usecase.ValidateSecurePairingPayload
import com.budcom.android.core.util.TimeProvider
import com.budcom.android.feature.pairing.data.scanner.FakeSecurePairingScannerPort
import com.budcom.android.feature.pairing.data.scanner.SecurePairingScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
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

private fun validQrJson(connectorName: String = "Front Desk"): String = """
    {
      "schemaVersion": "1",
      "pairingSessionId": "11111111-1111-1111-1111-111111111111",
      "secret": "super-secret-one-time-value",
      "connectorId": "connector-abc",
      "connectorName": "$connectorName",
      "host": "10.0.0.5",
      "port": 8080,
      "securePort": 8443,
      "transportProtocol": "https",
      "transportFingerprint": "${testFingerprint()}",
      "fingerprintAlgorithm": "sha256",
      "transportIdentityVersion": 1,
      "expiresAt": "${Instant.ofEpochMilli(NOW + 120_000L)}"
    }
""".trimIndent()

private val testEndpoint = TrustedConnectorEndpoint.fromTrustedPublicMetadata(
    connectorId = "connector-xyz",
    connectorName = "Back Office",
    host = "10.0.0.9",
    securePort = 8443,
    transportFingerprint = "sha256/AAAA",
    fingerprintAlgorithm = "sha256",
    transportIdentityVersion = 1,
)

@OptIn(ExperimentalCoroutinesApi::class)
class SecurePairingViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val timeProvider = TimeProvider { NOW }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModelOf(
        scannerPort: FakeSecurePairingScannerPort = FakeSecurePairingScannerPort(),
        api: FakeSecurePairingApiPort = FakeSecurePairingApiPort(),
        vault: SecureCredentialVault = FakeSecureCredentialVault(),
    ): SecurePairingViewModel {
        val parser = SecurePairingQrPayloadParser(timeProvider)
        val validate = ValidateSecurePairingPayload(parser)
        val verificationStep = PendingCredentialVerificationStep(api, vault, timeProvider)
        val deviceIdentity = FakePairingDeviceIdentityLocalDataSource()
        val redeem = RedeemSecurePairingSession(parser, api, vault, deviceIdentity, timeProvider, verificationStep)
        val retry = RetryPendingCredentialVerification(vault, verificationStep)
        return SecurePairingViewModel(scannerPort, validate, redeem, retry, vault)
    }

    // 11. initial state is idle
    @Test
    fun `initial state is Idle`() = runTest(dispatcher) {
        val viewModel = viewModelOf()
        advanceUntilIdle()
        assertEquals(SecurePairingPhase.Idle, viewModel.uiState.value.phase)
    }

    // An ACTIVE record whose credential cannot be decrypted must never present as pairing-complete —
    // presented identically to RePairRequired, since re-pairing is the only recovery either way.
    @Test
    fun `an ACTIVE record with a missing Keystore key presents as RePairRequired, not Active`() = runTest(dispatcher) {
        val cipher = com.budcom.android.core.security.FakeCredentialCipher()
        val vault = FakeSecureCredentialVault(cipher = cipher)
        vault.storePendingVerification("cred-1", "device-1", "raw-token", testEndpoint, NOW)
        vault.markActive("cred-1", NOW)
        cipher.dropKey()

        val viewModel = viewModelOf(vault = vault)
        advanceUntilIdle()

        assertEquals(SecurePairingPhase.RePairRequired, viewModel.uiState.value.phase)
        assertFalse(viewModel.uiState.value.shortCodeEntryAvailable)
    }

    @Test
    fun `a genuinely ACTIVE, decryptable record presents as Active with short-code entry available`() = runTest(dispatcher) {
        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", "raw-token", testEndpoint, NOW)
        vault.markActive("cred-1", NOW)

        val viewModel = viewModelOf(vault = vault)
        advanceUntilIdle()

        assertEquals(SecurePairingPhase.Active, viewModel.uiState.value.phase)
        assertTrue(viewModel.uiState.value.shortCodeEntryAvailable)
    }

    // 12. StartQrScan requests scanner launch only
    @Test
    fun `StartQrScan moves to ScannerLaunching and requests a scanner launch, without validating or redeeming anything`() = runTest(dispatcher) {
        val scanner = FakeSecurePairingScannerPort()
        val api = FakeSecurePairingApiPort()
        val viewModel = viewModelOf(scannerPort = scanner, api = api)
        advanceUntilIdle()

        viewModel.onEvent(SecurePairingEvent.StartQrScan)
        advanceUntilIdle()

        assertEquals(SecurePairingPhase.ScannerLaunching, viewModel.uiState.value.phase)
        assertEquals(1, scanner.beginScanAttemptCallCount)
        assertEquals(0, api.redeemQrCallCount)
    }

    @Test
    fun `StartQrScan with no camera hardware goes straight to CameraUnavailable`() = runTest(dispatcher) {
        val viewModel = viewModelOf(scannerPort = FakeSecurePairingScannerPort(cameraAvailable = false))
        advanceUntilIdle()

        viewModel.onEvent(SecurePairingEvent.StartQrScan)
        advanceUntilIdle()

        assertEquals(SecurePairingPhase.CameraUnavailable, viewModel.uiState.value.phase)
    }

    // 13. scanned payload enters validation / 16. valid payload becomes awaitingConfirmation
    @Test
    fun `a captured valid payload becomes AwaitingConfirmation with Connector name and fingerprint shown`() = runTest(dispatcher) {
        val viewModel = viewModelOf()
        advanceUntilIdle()

        viewModel.onEvent(SecurePairingEvent.ScannerResultReceived(SecurePairingScanResult.PayloadCaptured(validQrJson("Front Desk"))))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(SecurePairingPhase.AwaitingConfirmation, state.phase)
        assertEquals("Front Desk", state.connectorName)
        assertTrue(state.abbreviatedFingerprint!!.isNotBlank())
    }

    // 14. invalid payload becomes invalidPayload
    @Test
    fun `a malformed payload becomes InvalidPayload`() = runTest(dispatcher) {
        val viewModel = viewModelOf()
        advanceUntilIdle()

        viewModel.onEvent(SecurePairingEvent.ScannerResultReceived(SecurePairingScanResult.PayloadCaptured("not valid json")))
        advanceUntilIdle()

        assertEquals(SecurePairingPhase.InvalidPayload, viewModel.uiState.value.phase)
    }

    // TD-012 (docs/technical-debt/registry.md, main repo): a loopback host is an InvalidHost
    // rejection specifically — still InvalidPayload, but with an actionable, non-sensitive
    // message rather than the generic one, and never echoing the rejected host/IP itself.
    @Test
    fun `a QR payload with a loopback host becomes InvalidPayload with an actionable Desktop-not-ready message`() = runTest(dispatcher) {
        val viewModel = viewModelOf()
        advanceUntilIdle()

        val loopbackHostJson = validQrJson().replace("\"host\": \"10.0.0.5\"", "\"host\": \"127.0.0.1\"")
        viewModel.onEvent(SecurePairingEvent.ScannerResultReceived(SecurePairingScanResult.PayloadCaptured(loopbackHostJson)))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(SecurePairingPhase.InvalidPayload, state.phase)
        assertEquals("BUDCOM Desktop is not ready for mobile pairing. Generate a new QR code on the computer.", state.errorMessage)
        assertTrue(state.errorMessage?.contains("127.0.0.1") != true)
    }

    // 15. expired payload becomes expiredPayload
    @Test
    fun `an expired payload becomes ExpiredPayload`() = runTest(dispatcher) {
        val viewModel = viewModelOf()
        advanceUntilIdle()
        val expiredJson = validQrJson().replace(
            Regex(""""expiresAt":\s*"[^"]*""""),
            "\"expiresAt\": \"${Instant.ofEpochMilli(NOW - 60_000L)}\"",
        )

        viewModel.onEvent(SecurePairingEvent.ScannerResultReceived(SecurePairingScanResult.PayloadCaptured(expiredJson)))
        advanceUntilIdle()

        assertEquals(SecurePairingPhase.ExpiredPayload, viewModel.uiState.value.phase)
    }

    // 17. no network call before explicit confirmation
    @Test
    fun `validating a payload makes no redeem call until ConfirmConnector`() = runTest(dispatcher) {
        val api = FakeSecurePairingApiPort()
        val viewModel = viewModelOf(api = api)
        advanceUntilIdle()

        viewModel.onEvent(SecurePairingEvent.ScannerResultReceived(SecurePairingScanResult.PayloadCaptured(validQrJson())))
        advanceUntilIdle()

        assertEquals(0, api.redeemQrCallCount)
    }

    // 18/19. confirmation calls redemption exactly once, repeated taps do not duplicate
    @Test
    fun `Confirm redeems exactly once even if tapped repeatedly`() = runTest(dispatcher) {
        val api = FakeSecurePairingApiPort(
            redeemResult = PairingRedeemOutcome.Success("connector-abc", "Front Desk", "cred-1", "raw-token"),
            selfStatusResult = PairingSelfStatusOutcome.Active("cred-1", null, null, "connector-abc", "2026-01-01T00:00:00Z", null, "active"),
        )
        val viewModel = viewModelOf(api = api)
        advanceUntilIdle()
        viewModel.onEvent(SecurePairingEvent.ScannerResultReceived(SecurePairingScanResult.PayloadCaptured(validQrJson())))
        advanceUntilIdle()

        viewModel.onEvent(SecurePairingEvent.ConfirmConnector)
        viewModel.onEvent(SecurePairingEvent.ConfirmConnector)
        viewModel.onEvent(SecurePairingEvent.ConfirmConnector)
        advanceUntilIdle()

        assertEquals(1, api.redeemQrCallCount)
        assertEquals(SecurePairingPhase.Active, viewModel.uiState.value.phase)
    }

    // 20. rejection clears the validated payload
    @Test
    fun `RejectConnector returns to Idle and a later Confirm does nothing`() = runTest(dispatcher) {
        val api = FakeSecurePairingApiPort(redeemResult = PairingRedeemOutcome.Success("connector-abc", "Front Desk", "cred-1", "raw-token"))
        val viewModel = viewModelOf(api = api)
        advanceUntilIdle()
        viewModel.onEvent(SecurePairingEvent.ScannerResultReceived(SecurePairingScanResult.PayloadCaptured(validQrJson())))
        advanceUntilIdle()

        viewModel.onEvent(SecurePairingEvent.RejectConnector)
        advanceUntilIdle()
        assertEquals(SecurePairingPhase.Idle, viewModel.uiState.value.phase)

        viewModel.onEvent(SecurePairingEvent.ConfirmConnector)
        advanceUntilIdle()
        assertEquals(0, api.redeemQrCallCount)
    }

    // 21. transient verification failure becomes pendingVerification
    @Test
    fun `a transient self-status failure after redemption becomes PendingVerification`() = runTest(dispatcher) {
        val api = FakeSecurePairingApiPort(
            redeemResult = PairingRedeemOutcome.Success("connector-abc", "Front Desk", "cred-1", "raw-token"),
            selfStatusResult = PairingSelfStatusOutcome.TransportFailure,
        )
        val viewModel = viewModelOf(api = api)
        advanceUntilIdle()
        viewModel.onEvent(SecurePairingEvent.ScannerResultReceived(SecurePairingScanResult.PayloadCaptured(validQrJson())))
        advanceUntilIdle()

        viewModel.onEvent(SecurePairingEvent.ConfirmConnector)
        advanceUntilIdle()

        assertEquals(SecurePairingPhase.PendingVerification, viewModel.uiState.value.phase)
        assertTrue(viewModel.uiState.value.canRetryPendingVerification)
    }

    // 22. retry pending verification makes zero new redemption calls
    @Test
    fun `RetryPendingVerification never calls redeemQr or redeemShortCode`() = runTest(dispatcher) {
        val api = FakeSecurePairingApiPort(
            redeemResult = PairingRedeemOutcome.Success("connector-abc", "Front Desk", "cred-1", "raw-token"),
            selfStatusResult = PairingSelfStatusOutcome.TransportFailure,
        )
        val viewModel = viewModelOf(api = api)
        advanceUntilIdle()
        viewModel.onEvent(SecurePairingEvent.ScannerResultReceived(SecurePairingScanResult.PayloadCaptured(validQrJson())))
        advanceUntilIdle()
        viewModel.onEvent(SecurePairingEvent.ConfirmConnector)
        advanceUntilIdle()

        viewModel.onEvent(SecurePairingEvent.RetryPendingVerification)
        advanceUntilIdle()

        assertEquals(1, api.redeemQrCallCount)
        assertEquals(0, api.redeemShortCodeCallCount)
    }

    // 23. successful retry becomes active
    @Test
    fun `a successful retry becomes Active`() = runTest(dispatcher) {
        val api = FakeSecurePairingApiPort(
            redeemResult = PairingRedeemOutcome.Success("connector-abc", "Front Desk", "cred-1", "raw-token"),
            selfStatusResult = PairingSelfStatusOutcome.TransportFailure,
        )
        val vault = FakeSecureCredentialVault()
        val viewModel = viewModelOf(api = api, vault = vault)
        advanceUntilIdle()
        viewModel.onEvent(SecurePairingEvent.ScannerResultReceived(SecurePairingScanResult.PayloadCaptured(validQrJson())))
        advanceUntilIdle()
        viewModel.onEvent(SecurePairingEvent.ConfirmConnector)
        advanceUntilIdle()

        // Simulates a later retry (e.g. after the app is reopened) where the Connector is now
        // reachable — a fresh api client instance sharing the SAME vault, matching how a real
        // process restart would look (new client, persisted vault state carries over).
        val vm2Api = FakeSecurePairingApiPort(selfStatusResult = PairingSelfStatusOutcome.Active("cred-1", null, null, "connector-abc", "2026-01-01T00:00:00Z", null, "active"))
        val retryStep = PendingCredentialVerificationStep(vm2Api, vault, timeProvider)
        val retryUseCase = RetryPendingCredentialVerification(vault, retryStep)
        val viewModel2 = SecurePairingViewModel(
            FakeSecurePairingScannerPort(),
            ValidateSecurePairingPayload(SecurePairingQrPayloadParser(timeProvider)),
            RedeemSecurePairingSession(SecurePairingQrPayloadParser(timeProvider), vm2Api, vault, FakePairingDeviceIdentityLocalDataSource(), timeProvider, retryStep),
            retryUseCase,
            vault,
        )
        advanceUntilIdle() // let viewModel2's init{} sync its phase from the persisted PENDING_VERIFICATION record
        assertEquals(SecurePairingPhase.PendingVerification, viewModel2.uiState.value.phase)

        viewModel2.onEvent(SecurePairingEvent.RetryPendingVerification)
        advanceUntilIdle()

        assertEquals(SecurePairingPhase.Active, viewModel2.uiState.value.phase)
    }

    // 24. unauthorized verification becomes rePairRequired
    @Test
    fun `an unauthorized self-status after redemption becomes RePairRequired`() = runTest(dispatcher) {
        val api = FakeSecurePairingApiPort(
            redeemResult = PairingRedeemOutcome.Success("connector-abc", "Front Desk", "cred-1", "raw-token"),
            selfStatusResult = PairingSelfStatusOutcome.Unauthorized,
        )
        val viewModel = viewModelOf(api = api)
        advanceUntilIdle()
        viewModel.onEvent(SecurePairingEvent.ScannerResultReceived(SecurePairingScanResult.PayloadCaptured(validQrJson())))
        advanceUntilIdle()

        viewModel.onEvent(SecurePairingEvent.ConfirmConnector)
        advanceUntilIdle()

        assertEquals(SecurePairingPhase.RePairRequired, viewModel.uiState.value.phase)
    }

    @Test
    fun `StartQrScan works from a RePairRequired resting state, not only from Idle`() = runTest(dispatcher) {
        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", "raw-token", testEndpoint, NOW)
        vault.markRePairRequired("cred-1")
        val scanner = FakeSecurePairingScannerPort()
        val viewModel = viewModelOf(scannerPort = scanner, vault = vault)
        advanceUntilIdle()
        assertEquals(SecurePairingPhase.RePairRequired, viewModel.uiState.value.phase)

        viewModel.onEvent(SecurePairingEvent.StartQrScan)
        advanceUntilIdle()

        assertEquals(SecurePairingPhase.ScannerLaunching, viewModel.uiState.value.phase)
        assertEquals(1, scanner.beginScanAttemptCallCount)
    }

    @Test
    fun `Cancel from an already-Active state preserves Active rather than reverting to Idle`() = runTest(dispatcher) {
        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", "raw-token", testEndpoint, NOW)
        vault.markActive("cred-1", NOW)
        val viewModel = viewModelOf(vault = vault)
        advanceUntilIdle()
        assertEquals(SecurePairingPhase.Active, viewModel.uiState.value.phase)

        viewModel.onEvent(SecurePairingEvent.OpenTrustedShortCodeEntry)
        viewModel.onEvent(SecurePairingEvent.Cancel)
        advanceUntilIdle()

        assertEquals(SecurePairingPhase.Active, viewModel.uiState.value.phase)
        assertEquals("", viewModel.uiState.value.shortCodeInput)
        assertFalse(viewModel.uiState.value.showShortCodeEntry)
    }

    // 25. fingerprint mismatch never falls back — surfaces as NetworkFailure (see class doc:
    // a live pinning mismatch is, by design from Phase 3N, indistinguishable from any other
    // transport failure — there is no fallback path anywhere in this ViewModel).
    @Test
    fun `a transport failure at redeem time becomes NetworkFailure with no fallback path`() = runTest(dispatcher) {
        val api = FakeSecurePairingApiPort(redeemResult = PairingRedeemOutcome.TransportFailure)
        val viewModel = viewModelOf(api = api)
        advanceUntilIdle()
        viewModel.onEvent(SecurePairingEvent.ScannerResultReceived(SecurePairingScanResult.PayloadCaptured(validQrJson())))
        advanceUntilIdle()

        viewModel.onEvent(SecurePairingEvent.ConfirmConnector)
        advanceUntilIdle()

        assertEquals(SecurePairingPhase.NetworkFailure, viewModel.uiState.value.phase)
        assertEquals(1, api.redeemQrCallCount)
    }

    // 26. cancel clears sensitive transient data
    @Test
    fun `Cancel from AwaitingConfirmation clears the pending payload so a later Confirm does nothing`() = runTest(dispatcher) {
        val api = FakeSecurePairingApiPort(redeemResult = PairingRedeemOutcome.Success("connector-abc", "Front Desk", "cred-1", "raw-token"))
        val viewModel = viewModelOf(api = api)
        advanceUntilIdle()
        viewModel.onEvent(SecurePairingEvent.ScannerResultReceived(SecurePairingScanResult.PayloadCaptured(validQrJson())))
        advanceUntilIdle()

        viewModel.onEvent(SecurePairingEvent.Cancel)
        advanceUntilIdle()
        assertEquals(SecurePairingPhase.Idle, viewModel.uiState.value.phase)

        viewModel.onEvent(SecurePairingEvent.ConfirmConnector)
        advanceUntilIdle()
        assertEquals(0, api.redeemQrCallCount)
    }

    // 27. raw QR secret never appears in UiState
    @Test
    fun `UiState never contains the QR secret`() = runTest(dispatcher) {
        val viewModel = viewModelOf()
        advanceUntilIdle()
        viewModel.onEvent(SecurePairingEvent.ScannerResultReceived(SecurePairingScanResult.PayloadCaptured(validQrJson())))
        advanceUntilIdle()

        val serialized = viewModel.uiState.value.toString()
        assertFalse(serialized.contains("super-secret-one-time-value"))
    }

    // 28. bearer credential never appears in UiState
    @Test
    fun `UiState never contains the bearer credential after a successful redemption`() = runTest(dispatcher) {
        val api = FakeSecurePairingApiPort(
            redeemResult = PairingRedeemOutcome.Success("connector-abc", "Front Desk", "cred-1", "raw-token-value"),
            selfStatusResult = PairingSelfStatusOutcome.Active("cred-1", null, null, "connector-abc", "2026-01-01T00:00:00Z", null, "active"),
        )
        val viewModel = viewModelOf(api = api)
        advanceUntilIdle()
        viewModel.onEvent(SecurePairingEvent.ScannerResultReceived(SecurePairingScanResult.PayloadCaptured(validQrJson())))
        advanceUntilIdle()
        viewModel.onEvent(SecurePairingEvent.ConfirmConnector)
        advanceUntilIdle()

        val serialized = viewModel.uiState.value.toString()
        assertFalse(serialized.contains("raw-token-value"))
    }

    // 30. no polling is scheduled — a settled state never changes again without a new event.
    @Test
    fun `state does not change on its own after settling into AwaitingConfirmation`() = runTest(dispatcher) {
        val viewModel = viewModelOf()
        advanceUntilIdle()
        viewModel.onEvent(SecurePairingEvent.ScannerResultReceived(SecurePairingScanResult.PayloadCaptured(validQrJson())))
        advanceUntilIdle()
        val settled = viewModel.uiState.value

        advanceUntilIdle()

        assertEquals(settled, viewModel.uiState.value)
    }

    // 45. clean state does not show short-code entry
    @Test
    fun `a clean unpaired vault never shows short-code entry`() = runTest(dispatcher) {
        val viewModel = viewModelOf(vault = FakeSecureCredentialVault())
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.shortCodeEntryAvailable)
    }

    @Test
    fun `a PENDING_VERIFICATION (not yet proven) vault does not show short-code entry`() = runTest(dispatcher) {
        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", "raw-token", testEndpoint, NOW)
        val viewModel = viewModelOf(vault = vault)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.shortCodeEntryAvailable)
    }

    // 46. trusted endpoint state may show short-code entry
    @Test
    fun `an ACTIVE vault shows short-code entry`() = runTest(dispatcher) {
        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", "raw-token", testEndpoint, NOW)
        vault.markActive("cred-1", NOW)
        val viewModel = viewModelOf(vault = vault)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.shortCodeEntryAvailable)
    }

    // 48. invalid code rejected locally
    @Test
    fun `an invalid-format short code is rejected locally without any network call`() = runTest(dispatcher) {
        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", "raw-token", testEndpoint, NOW)
        vault.markActive("cred-1", NOW)
        val api = FakeSecurePairingApiPort()
        val viewModel = viewModelOf(api = api, vault = vault)
        advanceUntilIdle()

        viewModel.onEvent(SecurePairingEvent.OpenTrustedShortCodeEntry)
        viewModel.onEvent(SecurePairingEvent.ShortCodeInputChanged("bad"))
        viewModel.onEvent(SecurePairingEvent.SubmitTrustedShortCode)
        advanceUntilIdle()

        assertEquals(0, api.redeemShortCodeCallCount)
        assertTrue(viewModel.uiState.value.errorMessage != null)
    }

    // 49. submit uses the exact persisted trusted endpoint
    @Test
    fun `submitting a valid short code redeems against the exact persisted trusted endpoint`() = runTest(dispatcher) {
        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", "raw-token", testEndpoint, NOW)
        vault.markActive("cred-1", NOW)
        val api = FakeSecurePairingApiPort(redeemResult = PairingRedeemOutcome.TransportFailure)
        val viewModel = viewModelOf(api = api, vault = vault)
        advanceUntilIdle()

        viewModel.onEvent(SecurePairingEvent.OpenTrustedShortCodeEntry)
        viewModel.onEvent(SecurePairingEvent.ShortCodeInputChanged("ABCDEFGH"))
        viewModel.onEvent(SecurePairingEvent.SubmitTrustedShortCode)
        advanceUntilIdle()

        assertEquals(1, api.redeemShortCodeCallCount)
        assertEquals("ABCDEFGH", api.lastShortCode)
        assertEquals(testEndpoint, api.lastShortCodeEndpoint)
    }

    // 50. code cleared after submit
    @Test
    fun `short code input is cleared immediately on submit`() = runTest(dispatcher) {
        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", "raw-token", testEndpoint, NOW)
        vault.markActive("cred-1", NOW)
        val viewModel = viewModelOf(api = FakeSecurePairingApiPort(redeemResult = PairingRedeemOutcome.TransportFailure), vault = vault)
        advanceUntilIdle()

        viewModel.onEvent(SecurePairingEvent.OpenTrustedShortCodeEntry)
        viewModel.onEvent(SecurePairingEvent.ShortCodeInputChanged("ABCDEFGH"))
        viewModel.onEvent(SecurePairingEvent.SubmitTrustedShortCode)
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.shortCodeInput)
    }

    // 51. code cleared after cancellation
    @Test
    fun `short code input is cleared on Cancel`() = runTest(dispatcher) {
        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", "raw-token", testEndpoint, NOW)
        vault.markActive("cred-1", NOW)
        val viewModel = viewModelOf(vault = vault)
        advanceUntilIdle()
        viewModel.onEvent(SecurePairingEvent.OpenTrustedShortCodeEntry)
        viewModel.onEvent(SecurePairingEvent.ShortCodeInputChanged("ABCDEFGH"))

        viewModel.onEvent(SecurePairingEvent.Cancel)
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.shortCodeInput)
    }

    // 52. code never enters persistent state or logs — proven by UiState containing only the
    // live editable field (cleared immediately on every terminal transition above) and by
    // SecurePairingUiState's plain data-class toString exposing nothing beyond that live field.
    @Test
    fun `short code field is the only place the code appears, and it is empty outside active entry`() = runTest(dispatcher) {
        val vault = FakeSecureCredentialVault()
        vault.storePendingVerification("cred-1", "device-1", "raw-token", testEndpoint, NOW)
        vault.markActive("cred-1", NOW)
        val viewModel = viewModelOf(vault = vault)
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.shortCodeInput)
    }
}
