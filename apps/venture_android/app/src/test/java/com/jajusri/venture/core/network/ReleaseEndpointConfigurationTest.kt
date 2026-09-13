package com.jajusri.venture.core.network

import com.jajusri.venture.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseEndpointConfigurationTest {
    @Test
    fun `customer release has no default Connector address while debug keeps emulator convenience`() {
        assertFalse(BuildConfig.CONNECTOR_BOOTSTRAP_BASE_URL.contains("10.0.2.2"))
        assertTrue(BuildConfig.CONNECTOR_BOOTSTRAP_BASE_URL.startsWith("https://"))
        assertTrue(BuildConfig.RELAY_DEFAULT_BASE_URL.isBlank())
        assertFalse(BuildConfig.RELAY_DEFAULT_BASE_URL.contains("10.0.2.2"))
        if (BuildConfig.DEBUG) {
            assertEquals("http://10.0.2.2:8080/", BuildConfig.CONNECTOR_DEFAULT_BASE_URL)
        } else {
            assertTrue(BuildConfig.CONNECTOR_DEFAULT_BASE_URL.isBlank())
        }
    }
}
