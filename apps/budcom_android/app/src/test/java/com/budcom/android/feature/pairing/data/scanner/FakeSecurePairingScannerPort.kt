package com.budcom.android.feature.pairing.data.scanner

class FakeSecurePairingScannerPort(
    private val cameraAvailable: Boolean = true,
) : SecurePairingScannerPort {
    var beginScanAttemptCallCount = 0
        private set

    override fun isCameraAvailable(): Boolean = cameraAvailable

    override fun beginScanAttempt() {
        beginScanAttemptCallCount++
    }

    override fun onRawScanResult(rawPayload: String?): SecurePairingScanResult? = null
    override fun onScanOutcome(outcome: SecurePairingScanResult): SecurePairingScanResult? = null
}
