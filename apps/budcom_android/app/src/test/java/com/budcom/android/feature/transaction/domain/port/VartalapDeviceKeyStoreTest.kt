package com.budcom.android.feature.transaction.domain.port

import org.junit.Assert.assertEquals
import org.junit.Test

class VartalapDeviceKeyStoreTest {
    @Test
    fun `identity keeps device and key version separate`() {
        val identity = DeviceSigningIdentity("device-1", "key-1", 2, byteArrayOf(1), "fp", 10L, DeviceKeySecurityLevel.SecureKeystore)
        assertEquals("device-1", identity.deviceId)
        assertEquals(2, identity.keyVersion)
    }
}