package com.jajusri.venture.feature.transaction.domain.port

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature

private fun credential(businessId: String = "business-1", deviceId: String = "device-1") = TrustedBusinessDeviceCredential(
    credentialVersion = 1, credentialId = "cred-1", businessId = businessId, actorId = "actor-1", membershipId = "membership-1",
    deviceId = deviceId, deviceKeyId = "key-1", deviceKeyVersion = 1, devicePublicKeyFingerprint = "fp",
    authorityScope = setOf("send_orders"), authorityEpoch = 4L, issuedAtEpochMillis = 1L, notBeforeEpochMillis = 1L,
    expiresAtEpochMillis = 999_999_999_999L, issuerId = "issuer-1", issuerKeyId = "issuer-key-1", signatureProfile = "P256-SHA256-v1",
    signature = byteArrayOf(9, 9),
)

class AuthenticatedTransportEnvelopeTest {
    @Test fun `submit binding signature covers every authority-significant field`() = runTest {
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val identity = DeviceSigningIdentity("device-1", "key-1", 1, pair.public.encoded, "fp", 1, DeviceKeySecurityLevel.SecureKeystore)
        val store = SigningFakeKeyStore(identity) { bytes -> Signature.getInstance("SHA256withECDSA").apply { initSign(pair.private); update(bytes) }.sign() }
        val envelope = EnvelopeSubmission(1, "env", "idem", "ORDER", "order", 2, "business-1", "device-1", "party-2", "business-2", 10)
        val recipient = RecipientBinding("business-2", "party-2", "mailbox-2")
        val credential = credential()
        val authenticated = AuthenticatedEnvelopeBinder(store).bind(envelope, identity, credential, recipient)
        assertNotNull(authenticated)
        fun verifies(bytes: ByteArray) = Signature.getInstance("SHA256withECDSA").apply { initVerify(pair.public); update(bytes) }.verify(authenticated!!.deviceSignature)
        fun bytesFor(boundRecipient: RecipientBinding = recipient, actorId: String = credential.actorId) =
            AuthenticatedTransportEnvelope.bindingSigningBytes(envelope, actorId, boundRecipient, "", ORDER_SNAPSHOT_CONTENT_TYPE, ORDER_SNAPSHOT_CONTENT_VERSION)
        assertTrue(verifies(bytesFor()))
        assertFalse(verifies(bytesFor(actorId = "actor-x")))
        assertFalse(verifies(bytesFor(boundRecipient = recipient.copy(mailboxReference = "other"))))
        assertNull(AuthenticatedEnvelopeBinder(store).bind(envelope.copy(senderDeviceId = "other"), identity, credential, recipient))
        assertNull(AuthenticatedEnvelopeBinder(store).bind(envelope, identity, credential.copy(businessId = "business-other"), recipient))
    }

    @Test fun `credential claims and Trust signature are carried verbatim, never reconstructed`() = runTest {
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val identity = DeviceSigningIdentity("device-1", "key-1", 1, pair.public.encoded, "fp", 1, DeviceKeySecurityLevel.SecureKeystore)
        val store = SigningFakeKeyStore(identity) { bytes -> Signature.getInstance("SHA256withECDSA").apply { initSign(pair.private); update(bytes) }.sign() }
        val envelope = EnvelopeSubmission(1, "env", "idem", "ORDER", "order", 2, "business-1", "device-1", "party-2", "business-2", 10)
        val recipient = RecipientBinding("business-2", "party-2", "mailbox-2")
        val credential = credential()
        val authenticated = AuthenticatedEnvelopeBinder(store).bind(envelope, identity, credential, recipient)
        assertEquals(credential, authenticated?.credential)
        assertTrue(credential.signature.contentEquals(authenticated!!.credential.signature))
    }

    @Test fun `mailbox fetch and acknowledgement binding signatures cover their own action fields`() = runTest {
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val identity = DeviceSigningIdentity("device-1", "key-1", 1, pair.public.encoded, "fp", 1, DeviceKeySecurityLevel.SecureKeystore)
        val store = SigningFakeKeyStore(identity) { bytes -> Signature.getInstance("SHA256withECDSA").apply { initSign(pair.private); update(bytes) }.sign() }
        val credential = credential()
        val binder = AuthenticatedEnvelopeBinder(store)
        val fetch = binder.bindRequest(identity, credential, "mailbox_fetch", "orders", listOf("cursor-1", "25"), "req-1", "2026-08-29T00:00:00Z")
        assertNotNull(fetch)
        fun verifies(request: AuthenticatedRelayRequest, bytes: ByteArray) =
            Signature.getInstance("SHA256withECDSA").apply { initVerify(pair.public); update(bytes) }.verify(request.requestSignature)
        val fetchBytes = AuthenticatedRelayRequest.bindingSigningBytes("mailbox_fetch", credential, "req-1", "2026-08-29T00:00:00Z", "orders", listOf("cursor-1", "25"))
        assertTrue(verifies(fetch!!, fetchBytes))
        val tamperedTarget = AuthenticatedRelayRequest.bindingSigningBytes("mailbox_fetch", credential, "req-1", "2026-08-29T00:00:00Z", "other-mailbox", listOf("cursor-1", "25"))
        assertFalse(verifies(fetch, tamperedTarget))

        val ack = binder.bindRequest(identity, credential, "acknowledge", "envelope-1", listOf("2026-08-29T00:00:01Z"), "req-2", "2026-08-29T00:00:00Z")
        assertNotNull(ack)
        val ackBytes = AuthenticatedRelayRequest.bindingSigningBytes("acknowledge", credential, "req-2", "2026-08-29T00:00:00Z", "envelope-1", listOf("2026-08-29T00:00:01Z"))
        assertTrue(verifies(ack!!, ackBytes))
        assertFalse(verifies(ack, fetchBytes))
        assertNull(binder.bindRequest(identity, credential.copy(deviceId = "other-device"), "acknowledge", "envelope-1", listOf("x"), "req-3", "t"))
    }

    @Test fun `pipe-delimited escaping matches the certified backend byte-for-byte`() {
        val escaped = pilotWireEscape("a\\b|c")
        assertEquals("a\\\\b\\|c", escaped)
    }
}
