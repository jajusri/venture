package com.jajusri.venture.feature.pairing.data.scanner

/** Outcome of one QR-scan attempt. The scanner adapter only ever produces these — it never
 * decides whether a captured payload is trustworthy; that remains entirely the job of the
 * existing [com.jajusri.venture.core.pairing.domain.model.SecurePairingQrPayloadParser]. */
sealed class SecurePairingScanResult {
    /** A raw QR payload must never reach a log line or exception message — `toString()` is
     * overridden HERE (not just on the sealed parent) because Kotlin data classes always
     * generate their own `toString()`, which would otherwise shadow a parent-level override. */
    data class PayloadCaptured(val rawPayload: String) : SecurePairingScanResult() {
        override fun toString(): String = "SecurePairingScanResult.PayloadCaptured(redacted)"
    }

    data object Cancelled : SecurePairingScanResult()

    /** [permanentlyDenied] reflects `!shouldShowRequestPermissionRationale` after a denial — only the
     * Activity/Compose layer can determine this, so the UI layer decides it, not this port. */
    data class PermissionDenied(val permanentlyDenied: Boolean) : SecurePairingScanResult()
    data object CameraUnavailable : SecurePairingScanResult()
    data object Unsupported : SecurePairingScanResult()
    data class Failed(val sanitizedReason: String) : SecurePairingScanResult()
}

/**
 * The narrow boundary between the platform QR scanner and the secure-pairing presentation layer.
 *
 * Deliberately synchronous and Activity-Result-agnostic: the actual camera UI is launched by the
 * Composable/Route layer (Activity Result APIs can only be invoked from a Composable or an
 * Activity, never a ViewModel) — this port's only job is to interpret whatever raw outcome that
 * launch produces into one [SecurePairingScanResult], applying the two device-independent rules
 * that belong here rather than in UI code: a bounded payload size, and "exactly one accepted
 * result per scan attempt" (a second callback for the same attempt — e.g. a duplicate frame the
 * underlying scanner library re-delivers — is silently ignored by returning null).
 */
interface SecurePairingScannerPort {
    /** Whether this device currently exposes any camera hardware at all. */
    fun isCameraAvailable(): Boolean

    /** Arms the one-result-per-attempt guard. Call exactly once when a new scan begins. */
    fun beginScanAttempt()

    /**
     * Interprets one raw scanner callback. [rawPayload] is the decoded QR contents, or null for a
     * cancelled scan. Returns null (meaning: ignore this callback, dispatch nothing) if a result
     * for the current attempt has already been accepted.
     */
    fun onRawScanResult(rawPayload: String?): SecurePairingScanResult?

    /** Same one-shot-per-attempt rule as [onRawScanResult], for a non-payload platform outcome. */
    fun onScanOutcome(outcome: SecurePairingScanResult): SecurePairingScanResult?
}
