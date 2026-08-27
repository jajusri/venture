package com.budcom.android.feature.transaction.domain.port

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature

class AuthenticatedTransportEnvelopeTest {
    @Test fun `all identity bindings are covered by the device signature`() = runTest {
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val identity = DeviceSigningIdentity("device-1", "key-1", 1, pair.public.encoded, "fp", 1, DeviceKeySecurityLevel.SecureKeystore)
        val store = SigningFakeKeyStore(identity) { bytes -> Signature.getInstance("SHA256withECDSA").apply { initSign(pair.private); update(bytes) }.sign() }
        val envelope = EnvelopeSubmission(1, "env", "idem", "ORDER", "order", 2, "business-1", "device-1", "party-2", "business-2", 10)
        val recipient = RecipientBinding("business-2", "party-2", "mailbox-2")
        val credential = BusinessDeviceCredential(1, "business-1", "actor-1", "device-1", "ORDER_SEND", 1, 20, 4, "issuer", "cred-1")
        val authenticated = AuthenticatedEnvelopeBinder(store).bind(envelope, identity, credential, recipient)
        assertNotNull(authenticated)
        fun verifies(value: AuthenticatedTransportEnvelope) = Signature.getInstance("SHA256withECDSA").apply { initVerify(pair.public); update(value.signingBytes()) }.verify(authenticated!!.signature)
        assertTrue(verifies(authenticated!!))
        assertFalse(verifies(authenticated.copy(senderActorId = "actor-x")))
        assertFalse(verifies(authenticated.copy(recipient = recipient.copy(mailboxReference = "other"))))
        assertFalse(verifies(authenticated.copy(credentialEpoch = 5)))
        assertNull(AuthenticatedEnvelopeBinder(store).bind(envelope.copy(senderDeviceId = "other"), identity, credential, recipient))
    }
}
