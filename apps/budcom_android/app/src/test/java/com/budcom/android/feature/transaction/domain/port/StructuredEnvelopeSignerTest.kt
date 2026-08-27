package com.budcom.android.feature.transaction.domain.port

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature

class StructuredEnvelopeSignerTest {
    @Test fun `signature binds deterministic envelope and does not grant business authority`() = runTest {
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val identity = DeviceSigningIdentity("device-1", "key-1", 1, pair.public.encoded, "fp", 1, DeviceKeySecurityLevel.SecureKeystore)
        val store = SigningFakeKeyStore(identity) { bytes -> Signature.getInstance("SHA256withECDSA").apply { initSign(pair.private); update(bytes) }.sign() }
        val envelope = EnvelopeSubmission(1, "env-1", "idem-1", "ORDER", "order-1", 1, "business-1", "device-1", "party-2", "business-2", 10)

        val signed = (StructuredEnvelopeSigner(store).sign(envelope, identity) as EnvelopeSigningOutcome.Signed).submission
        val verifier = Signature.getInstance("SHA256withECDSA").apply { initVerify(pair.public); update(envelope.deterministicEncoding().toByteArray()) }
        assertTrue(verifier.verify(signed.signature))
        val tampered = envelope.copy(recipientBusinessId = "business-3")
        assertFalse(Signature.getInstance("SHA256withECDSA").apply { initVerify(pair.public); update(tampered.deterministicEncoding().toByteArray()) }.verify(signed.signature))
        val wrongPair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        assertFalse(Signature.getInstance("SHA256withECDSA").apply { initVerify(wrongPair.public); update(envelope.deterministicEncoding().toByteArray()) }.verify(signed.signature))
        assertTrue(signed.envelope === envelope)
    }
}

private class SigningFakeKeyStore(private val identity: DeviceSigningIdentity, private val signer: (ByteArray) -> ByteArray) : VartalapDeviceKeyStore {
    override suspend fun getCurrentIdentity() = identity
    override suspend fun getOrCreateIdentity(deviceId: String) = identity
    override suspend fun rotate(deviceId: String) = identity
    override suspend fun inspect(deviceId: String, keyVersion: Int) = identity
    override suspend fun sign(identity: DeviceSigningIdentity, boundedBytes: ByteArray) = DeviceSigningResult.Success(signer(boundedBytes))
    override suspend fun remove(deviceId: String, keyVersion: Int) = false
}
