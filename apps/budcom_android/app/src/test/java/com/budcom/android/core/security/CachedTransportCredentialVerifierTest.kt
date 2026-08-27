package com.budcom.android.core.security

import com.budcom.android.feature.transaction.domain.port.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature

class CachedTransportCredentialVerifierTest {
    @Test fun `verifies cached authority and rejects tampering stale epoch and wrong recipient`() = runTest {
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val unsigned = TrustedBusinessDeviceCredential(1, "cred-1", "business-1", "actor-1", "member-1", "device-1", "device-key-1", 2, "fp", setOf("send_orders"), 4, 10, 10, 100, "issuer-1", "issuer-key-1", "P256-SHA256-v1", byteArrayOf())
        val signed = unsigned.copy(signature = Signature.getInstance("SHA256withECDSA").apply { initSign(pair.private); update(unsigned.signingBytes()) }.sign())
        val verifier = CachedTransportCredentialVerifier({ _, _ -> CachedIssuerVerificationKey("issuer-1", "issuer-key-1", "P256-SHA256-v1", pair.public.encoded, false) }, { _, _, _ -> 4 })
        val recipient = RecipientBinding("business-2", "party-2", null)
        val request = CredentialVerificationRequest(signed, "business-1", "actor-1", "device-1", 2, recipient, recipient, 50)
        assertEquals("business-1", (verifier.verify(request) as CredentialVerificationOutcome.Valid).authorityContext.businessId)
        assertEquals(CredentialVerificationOutcome.InvalidSignature, verifier.verify(request.copy(credential = signed.copy(authorityEpoch = 5))))
        assertEquals(CredentialVerificationOutcome.WrongRecipient, verifier.verify(request.copy(actualRecipient = recipient.copy(partyId = "other"))))
        val stale = CachedTransportCredentialVerifier({ _, _ -> CachedIssuerVerificationKey("issuer-1", "issuer-key-1", "P256-SHA256-v1", pair.public.encoded, false) }, { _, _, _ -> 5 })
        assertEquals(CredentialVerificationOutcome.Revoked, stale.verify(request))
        val unknown = CachedTransportCredentialVerifier({ _, _ -> null }, { _, _, _ -> 4 })
        assertEquals(CredentialVerificationOutcome.TemporarilyUnverifiable, unknown.verify(request))
        assertEquals(CredentialVerificationOutcome.UnsupportedVersion, verifier.verify(request.copy(credential = signed.copy(credentialVersion = 99))))
        assertEquals(CredentialVerificationOutcome.Expired, verifier.verify(request.copy(nowEpochMillis = 100)))
    }
}
