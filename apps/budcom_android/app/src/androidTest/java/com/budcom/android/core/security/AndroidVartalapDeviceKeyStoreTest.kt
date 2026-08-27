package com.budcom.android.core.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.budcom.android.feature.transaction.domain.port.DeviceSigningResult
import com.budcom.android.feature.transaction.domain.port.DeviceKeySecurityLevel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AndroidVartalapDeviceKeyStoreTest {
    @Test
    fun keyIsStableSignsAndRotatesWithoutExportingPrivateMaterial() = runBlocking {
        val deviceId = "instrumentation-${UUID.randomUUID()}"
        val store = newStore()

        val first = store.getOrCreateIdentity(deviceId)
        val same = store.getOrCreateIdentity(deviceId)
        assertEquals(first.keyVersion, same.keyVersion)
        assertArrayEquals(first.publicKey, same.publicKey)
        assertEquals(first.publicKeyFingerprint, same.publicKeyFingerprint)
        assertEquals("budcom.vartalap.device.$deviceId.v1", first.keyId)
        val privateKey = (KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            .getEntry(first.keyId, null) as KeyStore.PrivateKeyEntry).privateKey
        assertNull(privateKey.encoded)
        assertTrue(first.securityLevel == DeviceKeySecurityLevel.SecureKeystore ||
            first.securityLevel == DeviceKeySecurityLevel.HardwareBacked)

        val message = "envelope-1|order-1|v1".toByteArray()
        val signature = (store.sign(first, message) as DeviceSigningResult.Success).signature
        val publicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(first.publicKey))
        assertTrue(Signature.getInstance("SHA256withECDSA").apply {
            initVerify(publicKey)
            update(message)
        }.verify(signature))
        assertEquals(DeviceSigningResult.InvalidInput, store.sign(first, byteArrayOf()))

        assertFalse(Signature.getInstance("SHA256withECDSA").apply {
            initVerify(publicKey)
            update("tampered".toByteArray())
        }.verify(signature))

        val otherPublicKey = KeyPairGenerator.getInstance("EC").generateKeyPair().public
        assertFalse(Signature.getInstance("SHA256withECDSA").apply {
            initVerify(otherPublicKey)
            update(message)
        }.verify(signature))

        val rotated = store.rotate(deviceId)
        assertEquals(deviceId, rotated.deviceId)
        assertEquals(first.keyVersion + 1, rotated.keyVersion)
        assertNotEquals(first.keyId, rotated.keyId)
        assertNotEquals(first.publicKeyFingerprint, rotated.publicKeyFingerprint)
        assertTrue(store.getCurrentIdentity()!!.keyVersion == rotated.keyVersion)
        assertTrue(store.remove(deviceId, rotated.keyVersion))
        assertNull(store.getCurrentIdentity())
    }

    @Test
    fun concurrentGetOrCreateReturnsOneActiveKey() = runBlocking {
        val deviceId = "instrumentation-${UUID.randomUUID()}"
        val store = newStore()

        val identities = List(12) { async { store.getOrCreateIdentity(deviceId) } }.awaitAll()

        assertEquals(setOf(1), identities.map { it.keyVersion }.toSet())
        assertEquals(1, identities.map { it.publicKeyFingerprint }.toSet().size)
        store.remove(deviceId, 1)
        Unit
    }

    private fun newStore() = AndroidVartalapDeviceKeyStore(
        androidx.test.core.app.ApplicationProvider.getApplicationContext(),
    )
}
