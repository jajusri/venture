package com.budcom.android.core.util

import android.os.Build
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Narrow physical-device vs emulator distinction.
 *
 * The only thing this gates: whether the emulator-oriented `BuildConfig.CONNECTOR_BASE_URL`
 * default (`10.0.2.2`, the documented Android emulator alias for the host machine) may be
 * used automatically as a first-install connection target. On a physical device that address
 * is never reachable, so first-install pairing must go through Connector discovery instead —
 * see `ConnectorEnrolmentGate`.
 */
interface DeviceEnvironment {
    fun isLikelyEmulator(): Boolean
}

@Singleton
class DefaultDeviceEnvironment @Inject constructor() : DeviceEnvironment {
    override fun isLikelyEmulator(): Boolean = isProbablyRunningOnEmulator(
        fingerprint = Build.FINGERPRINT,
        model = Build.MODEL,
        manufacturer = Build.MANUFACTURER,
        brand = Build.BRAND,
        device = Build.DEVICE,
        product = Build.PRODUCT,
        hardware = Build.HARDWARE,
    )
}

/**
 * Standard, widely-used emulator heuristic (matches the signals Android Studio's AVDs,
 * Genymotion, and CI emulator images all set). Pure function for direct unit-test coverage
 * without needing to fake `android.os.Build`.
 */
internal fun isProbablyRunningOnEmulator(
    fingerprint: String,
    model: String,
    manufacturer: String,
    brand: String,
    device: String,
    product: String,
    hardware: String,
): Boolean {
    return fingerprint.startsWith("generic") ||
        fingerprint.startsWith("unknown") ||
        model.contains("google_sdk") ||
        model.contains("Emulator") ||
        model.contains("Android SDK built for x86") ||
        manufacturer.contains("Genymotion") ||
        (brand.startsWith("generic") && device.startsWith("generic")) ||
        product == "google_sdk" ||
        hardware.contains("goldfish") ||
        hardware.contains("ranchu")
}
