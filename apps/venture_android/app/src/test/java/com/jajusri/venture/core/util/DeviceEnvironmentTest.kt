package com.jajusri.venture.core.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceEnvironmentTest {

    @Test
    fun `standard Android Studio AVD fingerprint is recognized as emulator`() {
        assertTrue(
            isProbablyRunningOnEmulator(
                fingerprint = "google/sdk_gphone64_x86_64/emulator64_x86_64:14/UE1A/1234:userdebug/dev-keys",
                model = "sdk_gphone64_x86_64",
                manufacturer = "Google",
                brand = "google",
                device = "emulator64_x86_64",
                product = "sdk_gphone64_x86_64",
                hardware = "ranchu",
            ),
        )
    }

    @Test
    fun `generic goldfish hardware is recognized as emulator`() {
        assertTrue(
            isProbablyRunningOnEmulator(
                fingerprint = "unknown",
                model = "Android SDK built for x86",
                manufacturer = "unknown",
                brand = "generic",
                device = "generic",
                product = "sdk",
                hardware = "goldfish",
            ),
        )
    }

    @Test
    fun `real physical device manufacturer values are never flagged as emulator`() {
        assertFalse(
            isProbablyRunningOnEmulator(
                fingerprint = "OnePlus/OP6131L1/OP6131L1:15/AP3A/1234:user/release-keys",
                model = "CPH2707",
                manufacturer = "OnePlus",
                brand = "OnePlus",
                device = "OP6131L1",
                product = "OP6131L1",
                hardware = "qcom",
            ),
        )
    }
}
