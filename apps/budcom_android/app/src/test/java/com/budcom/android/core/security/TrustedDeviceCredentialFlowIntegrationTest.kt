package com.budcom.android.core.security

import com.budcom.android.feature.transaction.domain.port.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature

class TrustedDeviceCredentialFlowIntegrationTest {
    @Test fun `verified trust credential authorizes a device signed envelope and rejects changed authority context`() = runTest {
        val issuerKeys = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val deviceKeys = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val unsignedCredential = TrustedBusinessDeviceCredential(
            1, "credential-1", "business-1", "actor-1", "membership-1", "device-1", "device-key-1", 1,
            "device-fingerprint", setOf("send_orders"), 1, 1_000, 1_000, 61_000,
            "issuer-1", "issuer-key-1", "P256-SHA256-v1", byteArrayOf(),
        )
        val credential = unsignedCredential.copy(signature = Signature.getInstance("SHA256withECDSA").apply {
            initSign(issuerKeys.private); update(unsignedCredential.signingBytes())
        }.sign())
        val verifier = CachedTransportCredentialVerifier(
            { _, _ -> CachedIssuerVerificationKey("issuer-1", "issuer-key-1", "P256-SHA256-v1", issuerKeys.public.encoded, false) },
            { _, _, _ -> 1 },
        )
        val recipient = RecipientBinding("business-2", "party-2", "mailbox-2")
        val request = CredentialVerificationRequest(credential, "business-1", "actor-1", "device-1", 1, recipient, recipient, 2_000)
        val authority = (verifier.verify(request) as CredentialVerificationOutcome.Valid).authorityContext
        assertEquals(setOf("send_orders"), authority.authorityScope)

        val identity = DeviceSigningIdentity("device-1", "device-key-1", 1, deviceKeys.public.encoded, "device-fingerprint", 1, DeviceKeySecurityLevel.SecureKeystore)
        val keyStore = SigningFakeKeyStore(identity) { bytes -> Signature.getInstance("SHA256withECDSA").apply {
            initSign(deviceKeys.private); update(bytes)
        }.sign() }
        val envelope = EnvelopeSubmission(1, "envelope-1", "intent-1", "ORDER", "order-1", 2, "business-1", "device-1", "party-2", "business-2", 2_000)
        val transportCredential = BusinessDeviceCredential(1, authority.businessId, authority.actorId, authority.deviceId, "ORDER_SEND", 1, 61_000, authority.authorityEpoch, "issuer-1", "credential-1")
        assertNotNull(AuthenticatedEnvelopeBinder(keyStore).bind(envelope, identity, transportCredential, recipient))

        val staleVerifier = CachedTransportCredentialVerifier(
            { _, _ -> CachedIssuerVerificationKey("issuer-1", "issuer-key-1", "P256-SHA256-v1", issuerKeys.public.encoded, false) },
            { _, _, _ -> 2 },
        )
        assertEquals(CredentialVerificationOutcome.Revoked, staleVerifier.verify(request))
        assertEquals(CredentialVerificationOutcome.WrongRecipient, verifier.verify(request.copy(actualRecipient = recipient.copy(mailboxReference = "changed"))))
        assertNull(AuthenticatedEnvelopeBinder(keyStore).bind(envelope.copy(senderBusinessId = "business-x"), identity, transportCredential, recipient))
    }
}
