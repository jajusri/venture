package com.budcom.android.feature.pairing.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budcom.android.core.pairing.data.local.SecureCredentialVault
import com.budcom.android.core.pairing.domain.model.SecurePairingCredentialState
import com.budcom.android.core.pairing.domain.model.SecurePairingQrPayloadParseResult
import com.budcom.android.core.pairing.domain.model.SecurePairingQrPayloadRejection
import com.budcom.android.core.pairing.domain.model.TrustedConnectorEndpoint
import com.budcom.android.core.pairing.domain.usecase.RedeemSecurePairingSession
import com.budcom.android.core.pairing.domain.usecase.RetryPendingCredentialVerification
import com.budcom.android.core.pairing.domain.usecase.SecurePairingRedemptionOutcome
import com.budcom.android.core.pairing.domain.usecase.ValidateSecurePairingPayload
import com.budcom.android.feature.pairing.data.scanner.SecurePairingScanResult
import com.budcom.android.feature.pairing.data.scanner.SecurePairingScannerPort
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives secure-pairing QR/short-code redemption.
 *
 * Security note: this ViewModel touches raw QR payloads (the scanned string) and lets the
 * existing Phase 3N use cases handle bearer credentials internally — neither ever becomes a
 * [SecurePairingUiState] field, a [SavedStateHandle] entry, or a log line. The raw payload string
 * is held only in a private, non-state field for the brief window between a successful scan and
 * either explicit confirmation or rejection/cancellation, exactly as long as the confirmation
 * step requires it to exist, then cleared.
 *
 * Not wired into any route yet — see the Phase 3O evidence report for why navigation integration
 * remains dormant this phase.
 */
@HiltViewModel
class SecurePairingViewModel @Inject constructor(
    private val scannerPort: SecurePairingScannerPort,
    private val validatePayload: ValidateSecurePairingPayload,
    private val redeemSession: RedeemSecurePairingSession,
    private val retryPendingVerification: RetryPendingCredentialVerification,
    private val vault: SecureCredentialVault,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SecurePairingUiState())
    val uiState: StateFlow<SecurePairingUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<SecurePairingUiEffect>(extraBufferCapacity = 4)
    val effects: SharedFlow<SecurePairingUiEffect> = _effects.asSharedFlow()

    /** The raw scanned QR string, held only between a successful scan and confirm/reject/cancel. */
    private var pendingRawPayload: String? = null
    private var trustedEndpointForShortCode: TrustedConnectorEndpoint? = null
    private var activeOperation: Job? = null

    init {
        viewModelScope.launch { syncWithPersistedState() }
    }

    /**
     * Route-composable-facing delegation to the injected [SecurePairingScannerPort] — keeps the
     * port itself private to this ViewModel while still letting the Route composable (which owns
     * the actual Activity Result launchers) apply the same one-result-per-attempt/size-bound
     * rules before ever dispatching a [SecurePairingEvent.ScannerResultReceived]. Returns null
     * when the callback must be ignored (e.g. a duplicate result for an attempt already resolved).
     */
    fun interpretRawScanResult(rawPayload: String?): SecurePairingScanResult? = scannerPort.onRawScanResult(rawPayload)

    fun interpretScanOutcome(outcome: SecurePairingScanResult): SecurePairingScanResult? = scannerPort.onScanOutcome(outcome)

    fun onEvent(event: SecurePairingEvent) {
        when (event) {
            SecurePairingEvent.StartQrScan -> startQrScan()
            is SecurePairingEvent.ScannerResultReceived -> handleScanResult(event.result)
            SecurePairingEvent.ConfirmConnector -> confirmConnector()
            SecurePairingEvent.RejectConnector -> rejectConnector()
            SecurePairingEvent.RetryPendingVerification -> retryVerification()
            SecurePairingEvent.OpenTrustedShortCodeEntry -> openShortCodeEntry()
            is SecurePairingEvent.ShortCodeInputChanged -> updateShortCodeInput(event.value)
            SecurePairingEvent.SubmitTrustedShortCode -> submitShortCode()
            SecurePairingEvent.Cancel -> cancel()
            SecurePairingEvent.ClearError -> clearError()
        }
    }

    /**
     * Reads the current persisted trust record ONCE — both to seed the initial phase correctly
     * (a PENDING_VERIFICATION or RE_PAIR_REQUIRED record from a prior app session must not be
     * silently presented as Idle) and to determine short-code-entry availability. Short-code
     * entry is available only when a previously PROVEN (ACTIVE, not merely pending) trust record
     * exists — never derived from mDNS, a typed URL, or the legacy paired-Connector record. A
     * completely clean device therefore never sees either.
     */
    private suspend fun syncWithPersistedState() {
        val record = vault.read()
        val available = record?.state == SecurePairingCredentialState.ACTIVE
        trustedEndpointForShortCode = if (available) record?.endpoint else null
        val initialPhase = when (record?.state) {
            SecurePairingCredentialState.PENDING_VERIFICATION -> SecurePairingPhase.PendingVerification
            SecurePairingCredentialState.ACTIVE -> SecurePairingPhase.Active
            SecurePairingCredentialState.RE_PAIR_REQUIRED -> SecurePairingPhase.RePairRequired
            null -> SecurePairingPhase.Idle
        }
        _uiState.update {
            it.copy(
                phase = initialPhase,
                connectorName = record?.endpoint?.connectorName,
                canRetryPendingVerification = record?.state == SecurePairingCredentialState.PENDING_VERIFICATION,
                shortCodeEntryAvailable = available,
            )
        }
    }

    private fun startQrScan() {
        // Reachable from any resting phase — Idle on a clean install, or a "Scan again"/"Scan New
        // QR" action from an error/RePairRequired screen — but never while a scan or redemption
        // is already in flight (guards against a stray duplicate dispatch mid-operation).
        if (_uiState.value.phase in BUSY_PHASES) return
        pendingRawPayload = null
        if (!scannerPort.isCameraAvailable()) {
            _uiState.update { it.copy(phase = SecurePairingPhase.CameraUnavailable) }
            return
        }
        scannerPort.beginScanAttempt()
        _uiState.update { it.copy(phase = SecurePairingPhase.ScannerLaunching, errorMessage = null) }
        viewModelScope.launch { _effects.emit(SecurePairingUiEffect.LaunchScanner) }
    }

    private fun handleScanResult(result: SecurePairingScanResult) {
        when (result) {
            is SecurePairingScanResult.PayloadCaptured -> validateScannedPayload(result.rawPayload)
            SecurePairingScanResult.Cancelled ->
                _uiState.update { it.copy(phase = SecurePairingPhase.Cancelled) }
            is SecurePairingScanResult.PermissionDenied ->
                _uiState.update { it.copy(phase = SecurePairingPhase.PermissionDenied, cameraPermanentlyDenied = result.permanentlyDenied) }
            SecurePairingScanResult.CameraUnavailable ->
                _uiState.update { it.copy(phase = SecurePairingPhase.CameraUnavailable) }
            SecurePairingScanResult.Unsupported ->
                _uiState.update { it.copy(phase = SecurePairingPhase.Failed, errorMessage = "QR scanning is not supported on this device.") }
            is SecurePairingScanResult.Failed ->
                _uiState.update { it.copy(phase = SecurePairingPhase.Failed, errorMessage = result.sanitizedReason) }
        }
    }

    private fun validateScannedPayload(rawPayload: String) {
        _uiState.update { it.copy(phase = SecurePairingPhase.ValidatingPayload) }
        when (val result = validatePayload(rawPayload)) {
            is SecurePairingQrPayloadParseResult.Valid -> {
                pendingRawPayload = rawPayload
                _uiState.update {
                    it.copy(
                        phase = SecurePairingPhase.AwaitingConfirmation,
                        connectorName = result.payload.connectorName,
                        abbreviatedFingerprint = abbreviateFingerprint(result.payload.transportFingerprint),
                        expiresAtEpochMillis = result.payload.expiresAtEpochMillis,
                    )
                }
            }
            is SecurePairingQrPayloadParseResult.Invalid -> {
                pendingRawPayload = null
                _uiState.update {
                    it.copy(phase = rejectionPhase(result.rejection), errorMessage = rejectionMessage(result.rejection))
                }
            }
        }
    }

    private fun confirmConnector() {
        if (_uiState.value.phase != SecurePairingPhase.AwaitingConfirmation) return
        val rawPayload = pendingRawPayload ?: return
        activeOperation?.cancel()
        activeOperation = viewModelScope.launch {
            _uiState.update { it.copy(phase = SecurePairingPhase.Redeeming, progressDescription = "Securing credential…") }
            val outcome = redeemSession.redeemQr(rawPayload)
            pendingRawPayload = null
            applyRedemptionOutcome(outcome)
        }
    }

    private fun rejectConnector() {
        if (_uiState.value.phase != SecurePairingPhase.AwaitingConfirmation) return
        pendingRawPayload = null
        _uiState.update { it.copy(phase = SecurePairingPhase.Idle, connectorName = null, abbreviatedFingerprint = null, expiresAtEpochMillis = null, errorMessage = null) }
    }

    private fun retryVerification() {
        if (_uiState.value.phase != SecurePairingPhase.PendingVerification) return
        activeOperation?.cancel()
        activeOperation = viewModelScope.launch {
            _uiState.update { it.copy(phase = SecurePairingPhase.VerifyingCredential, progressDescription = "Verifying Connector…") }
            val outcome = retryPendingVerification()
            applyRedemptionOutcome(outcome)
        }
    }

    private fun applyRedemptionOutcome(outcome: SecurePairingRedemptionOutcome) {
        when (outcome) {
            is SecurePairingRedemptionOutcome.Verified -> {
                _uiState.update {
                    it.copy(phase = SecurePairingPhase.Active, connectorName = outcome.record.endpoint.connectorName, canRetryPendingVerification = false)
                }
                viewModelScope.launch { syncWithPersistedState() }
            }
            is SecurePairingRedemptionOutcome.PendingRetryable -> _uiState.update {
                it.copy(
                    phase = SecurePairingPhase.PendingVerification,
                    canRetryPendingVerification = true,
                    connectorName = outcome.record.endpoint.connectorName,
                )
            }
            is SecurePairingRedemptionOutcome.RedemptionRejected -> {
                val phase = if (outcome.reasonCode == "UNAUTHORIZED" || outcome.reasonCode == "IDENTITY_MISMATCH") {
                    SecurePairingPhase.RePairRequired
                } else {
                    SecurePairingPhase.InvalidPayload
                }
                _uiState.update { it.copy(phase = phase, canRetryPendingVerification = false, errorMessage = "This pairing code is no longer valid. Scan a new QR.") }
            }
            SecurePairingRedemptionOutcome.TransportFailure, SecurePairingRedemptionOutcome.MalformedResponse ->
                _uiState.update {
                    it.copy(
                        phase = SecurePairingPhase.NetworkFailure,
                        // A transient failure right after redemption keeps the encrypted credential
                        // pending and retryable (see RedeemSecurePairingSession); a failure with
                        // nothing pending yet (e.g. the redeem call itself never reached the
                        // Connector) is not retryable via RetryPendingCredentialVerification.
                        canRetryPendingVerification = it.canRetryPendingVerification,
                        errorMessage = "Could not reach the Connector. Check the network and try again.",
                    )
                }
            SecurePairingRedemptionOutcome.InvalidPayload ->
                _uiState.update { it.copy(phase = SecurePairingPhase.InvalidPayload) }
            SecurePairingRedemptionOutcome.KeystoreUnavailable ->
                _uiState.update { it.copy(phase = SecurePairingPhase.Failed, errorMessage = "Could not securely store the credential on this device.") }
            SecurePairingRedemptionOutcome.NoPendingCredential ->
                _uiState.update { it.copy(phase = SecurePairingPhase.Failed, errorMessage = "There is nothing to verify.") }
        }
    }

    private fun openShortCodeEntry() {
        if (!_uiState.value.shortCodeEntryAvailable) return
        _uiState.update { it.copy(showShortCodeEntry = true, shortCodeInput = "") }
    }

    private fun updateShortCodeInput(value: String) {
        if (!_uiState.value.showShortCodeEntry) return
        _uiState.update { it.copy(shortCodeInput = value) }
    }

    private fun submitShortCode() {
        val state = _uiState.value
        if (!state.shortCodeEntryAvailable || !state.showShortCodeEntry) return
        val endpoint = trustedEndpointForShortCode ?: return
        val code = state.shortCodeInput.trim()
        if (!isValidShortCodeFormat(code)) {
            _uiState.update { it.copy(errorMessage = "Enter the 8-character code shown on the Desktop screen.") }
            return
        }
        activeOperation?.cancel()
        activeOperation = viewModelScope.launch {
            _uiState.update {
                it.copy(phase = SecurePairingPhase.Redeeming, progressDescription = "Securing credential…", shortCodeInput = "", showShortCodeEntry = false)
            }
            val outcome = redeemSession.redeemShortCode(code, endpoint)
            applyRedemptionOutcome(outcome)
        }
    }

    /**
     * Cancels any in-flight operation and closes whatever transient sub-flow was open (scanning,
     * short-code entry, an error screen). Deliberately does NOT blank an already-proven
     * Active/PendingVerification/RePairRequired steady state back to Idle — those reflect real,
     * currently-persisted vault state and cancelling a sub-flow (e.g. dismissing short-code entry)
     * must not make an already-paired device appear unpaired.
     */
    private fun cancel() {
        activeOperation?.cancel()
        activeOperation = null
        pendingRawPayload = null
        val restingPhase = when (_uiState.value.phase) {
            SecurePairingPhase.Active, SecurePairingPhase.PendingVerification, SecurePairingPhase.RePairRequired -> _uiState.value.phase
            else -> SecurePairingPhase.Idle
        }
        _uiState.update {
            it.copy(
                phase = restingPhase,
                showShortCodeEntry = false,
                shortCodeInput = "",
                errorMessage = null,
                cameraPermanentlyDenied = false,
                abbreviatedFingerprint = if (restingPhase == SecurePairingPhase.Idle) null else it.abbreviatedFingerprint,
                expiresAtEpochMillis = if (restingPhase == SecurePairingPhase.Idle) null else it.expiresAtEpochMillis,
                connectorName = if (restingPhase == SecurePairingPhase.Idle) null else it.connectorName,
            )
        }
    }

    private fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun isValidShortCodeFormat(code: String): Boolean = code.length == SHORT_CODE_LENGTH && code.all { it.isLetterOrDigit() }

    private companion object {
        const val SHORT_CODE_LENGTH = 8
        val BUSY_PHASES = setOf(
            SecurePairingPhase.ScannerLaunching,
            SecurePairingPhase.Scanning,
            SecurePairingPhase.ValidatingPayload,
            SecurePairingPhase.AwaitingConfirmation,
            SecurePairingPhase.Redeeming,
            SecurePairingPhase.StoringPendingCredential,
            SecurePairingPhase.VerifyingCredential,
        )
    }
}

/**
 * A malformed/wrong-length/wrong-algorithm fingerprint is a client-side-detectable defect in the
 * QR payload itself. A LIVE fingerprint mismatch during the actual pinned TLS handshake is
 * deliberately NOT distinguishable from any other transport failure (see
 * `SecurePairingApiClient`'s doc comment from Phase 3N) — collapsing that distinction is the
 * point, not a gap, so this mapping only ever produces FingerprintRejected from a parse-time
 * rejection, never from a redemption-time transport failure.
 */
private fun rejectionPhase(rejection: SecurePairingQrPayloadRejection): SecurePairingPhase = when (rejection) {
    SecurePairingQrPayloadRejection.UnsupportedFingerprintAlgorithm,
    SecurePairingQrPayloadRejection.MalformedFingerprint,
    SecurePairingQrPayloadRejection.InvalidFingerprintLength,
    -> SecurePairingPhase.FingerprintRejected
    SecurePairingQrPayloadRejection.ExpiryNotInFuture,
    SecurePairingQrPayloadRejection.ExpiryTooFarInFuture,
    SecurePairingQrPayloadRejection.MalformedExpiry,
    -> SecurePairingPhase.ExpiredPayload
    SecurePairingQrPayloadRejection.PayloadTooLarge,
    SecurePairingQrPayloadRejection.MalformedJson,
    is SecurePairingQrPayloadRejection.MissingFields,
    is SecurePairingQrPayloadRejection.DuplicateFields,
    is SecurePairingQrPayloadRejection.UnsupportedSchema,
    SecurePairingQrPayloadRejection.UnsupportedTransportProtocol,
    SecurePairingQrPayloadRejection.InvalidPort,
    is SecurePairingQrPayloadRejection.InvalidHost,
    -> SecurePairingPhase.InvalidPayload
}

private fun rejectionMessage(rejection: SecurePairingQrPayloadRejection): String = when (rejectionPhase(rejection)) {
    SecurePairingPhase.FingerprintRejected -> "This QR code's security details look wrong. Scan the code shown on the Desktop screen again."
    SecurePairingPhase.ExpiredPayload -> "This QR code has expired. Ask Desktop to show a new one."
    else -> "This QR code is not a valid BUDCOM pairing code."
}
