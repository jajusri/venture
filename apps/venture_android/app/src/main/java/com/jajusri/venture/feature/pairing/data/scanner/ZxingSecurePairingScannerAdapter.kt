package com.jajusri.venture.feature.pairing.data.scanner

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [SecurePairingScannerPort]. The actual QR decoding happens entirely inside the
 * `zxing-android-embedded` library's own `CaptureActivity` (launched via its `ScanContract` from
 * the Route composable, see `SecurePairingScreen.kt`) — this class never touches the camera
 * itself. Camera lifecycle (start only while the scan surface is visible, release on capture/
 * cancel/error/lifecycle stop) is therefore the responsibility of that library's own Activity,
 * which owns and is scoped to that Activity's lifecycle exactly like any other launched Activity.
 */
@Singleton
class ZxingSecurePairingScannerAdapter @Inject constructor(
    @ApplicationContext private val context: Context,
) : SecurePairingScannerPort {

    private val guard = ScanAttemptGuard()

    override fun isCameraAvailable(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

    override fun beginScanAttempt() = guard.begin()

    override fun onRawScanResult(rawPayload: String?): SecurePairingScanResult? = guard.onRawResult(rawPayload)

    override fun onScanOutcome(outcome: SecurePairingScanResult): SecurePairingScanResult? = guard.onOutcome(outcome)
}
