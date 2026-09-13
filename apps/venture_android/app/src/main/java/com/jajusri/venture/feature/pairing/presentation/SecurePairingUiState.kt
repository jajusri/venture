package com.jajusri.venture.feature.pairing.presentation

import com.jajusri.venture.feature.pairing.data.scanner.SecurePairingScanResult

/**
 * Immutable UI state for secure pairing. Deliberately narrow: everything sensitive
 * (QR secret, bearer credential, encrypted credential, IV, full raw QR JSON, Desktop control
 * token, private key material) is handled entirely inside the ViewModel/use-case layer and never
 * assigned to any field here — see the class-level security note in [SecurePairingViewModel].
 */
data class SecurePairingUiState(
    val phase: SecurePairingPhase = SecurePairingPhase.Idle,
    val connectorName: String? = null,
    val abbreviatedFingerprint: String? = null,
    val expiresAtEpochMillis: Long? = null,
    val progressDescription: String? = null,
    val canRetryPendingVerification: Boolean = false,
    val shortCodeEntryAvailable: Boolean = false,
    val showShortCodeEntry: Boolean = false,
    val shortCodeInput: String = "",
    val cameraPermanentlyDenied: Boolean = false,
    val errorMessage: String? = null,
)

enum class SecurePairingPhase {
    Idle,
    ScannerLaunching,
    Scanning,
    ValidatingPayload,
    AwaitingConfirmation,
    Redeeming,
    StoringPendingCredential,
    VerifyingCredential,
    PendingVerification,
    Active,
    PermissionDenied,
    CameraUnavailable,
    InvalidPayload,
    ExpiredPayload,
    FingerprintRejected,
    NetworkFailure,
    Unauthorized,
    RePairRequired,
    Failed,
}

sealed interface SecurePairingEvent {
    data object StartQrScan : SecurePairingEvent
    data class ScannerResultReceived(val result: SecurePairingScanResult) : SecurePairingEvent
    data object ConfirmConnector : SecurePairingEvent
    data object RejectConnector : SecurePairingEvent
    data object RetryPendingVerification : SecurePairingEvent
    data object OpenTrustedShortCodeEntry : SecurePairingEvent
    data class ShortCodeInputChanged(val value: String) : SecurePairingEvent
    data object SubmitTrustedShortCode : SecurePairingEvent
    data object Cancel : SecurePairingEvent
    data object ClearError : SecurePairingEvent
}

/** One-off UI effects the Route composable must act on (never something to store in state). */
sealed interface SecurePairingUiEffect {
    /** Route composable must request camera permission (if needed) then launch the QR scanner. */
    data object LaunchScanner : SecurePairingUiEffect

    /**
     * A user-initiated pairing or re-pair operation has been verified and published as ACTIVE.
     * This is intentionally an event, not inferred from the steady-state [SecurePairingPhase.Active]:
     * opening pairing management for an existing ACTIVE credential must keep the management UI
     * visible so the user can explicitly replace its trust.
     */
    data object PairingCompleted : SecurePairingUiEffect
}

/**
 * Abbreviates a canonical `sha256/<base64>` fingerprint for display — preserves enough of the
 * digest to distinguish two different Connectors at a glance without showing the full 44-character
 * base64 string. The full fingerprint stays inside [com.jajusri.venture.core.pairing.domain.model.TrustedConnectorEndpoint];
 * this function only ever touches a value already meant to be public.
 */
fun abbreviateFingerprint(fingerprint: String): String {
    val encoded = fingerprint.substringAfter('/', fingerprint)
    return if (encoded.length <= 12) encoded else "${encoded.take(6)}…${encoded.takeLast(6)}"
}
