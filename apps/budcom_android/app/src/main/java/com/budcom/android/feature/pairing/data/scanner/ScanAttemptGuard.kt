package com.budcom.android.feature.pairing.data.scanner

/**
 * Pure, platform-independent "exactly one accepted result per scan attempt" + payload-size-bound
 * logic, extracted out of [ZxingSecurePairingScannerAdapter] so it's directly unit-testable
 * without a real Android `Context` (this project has no Robolectric dependency).
 */
internal class ScanAttemptGuard {

    @Volatile
    private var resolved = true

    fun begin() {
        resolved = false
    }

    fun onRawResult(rawPayload: String?): SecurePairingScanResult? {
        if (resolved) return null
        resolved = true
        return when {
            rawPayload == null -> SecurePairingScanResult.Cancelled
            rawPayload.toByteArray(Charsets.UTF_8).size > MAX_RAW_PAYLOAD_BYTES ->
                SecurePairingScanResult.Failed("The scanned code is too large to be a valid pairing QR.")
            else -> SecurePairingScanResult.PayloadCaptured(rawPayload)
        }
    }

    fun onOutcome(outcome: SecurePairingScanResult): SecurePairingScanResult? {
        if (resolved) return null
        resolved = true
        return outcome
    }

    companion object {
        /** Matches the bound already enforced by SecurePairingQrPayloadParser (Phase 3N) — checked
         * again here so an oversized candidate never even reaches the parser. */
        const val MAX_RAW_PAYLOAD_BYTES = 4_096
    }
}
