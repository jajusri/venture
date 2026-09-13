package com.jajusri.venture.core.util

import android.os.Build
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Narrow physical-device vs emulator distinction.
 *
 * The only thing this gates is the debug-emulator bootstrap convenience. Customer builds have no
 * default Connector address; first-install connection on a physical device must use discovery and
 * explicit secure pairing. See `ConnectorEnrolmentGate`.
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
