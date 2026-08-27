package com.budcom.android.feature.transaction.domain.port

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class TransportCredentialVerifierTest {
    @Test fun `test fake can distinguish binding failures without becoming production trust`() = runTest {
        val credential = BusinessDeviceCredential(1, "business-1", "actor-1", "device-1", "ORDER_SEND", 10, 20, 3, "issuer", "verification")
        val recipient = RecipientBinding("business-2", null, null)
        val fake = TestOnlyCredentialVerifier(signatureValid = true)
        val valid = fake.verify(CredentialVerificationRequest(credential, "business-1", "actor-1", "device-1", recipient, recipient, 15))
        assertEquals("business-1", (valid as CredentialVerificationOutcome.Valid).authorityContext.businessId)
        assertEquals(CredentialVerificationOutcome.Expired, fake.verify(CredentialVerificationRequest(credential, "business-1", "actor-1", "device-1", recipient, recipient, 21)))
        assertEquals(CredentialVerificationOutcome.WrongDevice, fake.verify(CredentialVerificationRequest(credential, "business-1", "actor-1", "other", recipient, recipient, 15)))
        assertEquals(CredentialVerificationOutcome.InvalidSignature, TestOnlyCredentialVerifier(false).verify(CredentialVerificationRequest(credential, "business-1", "actor-1", "device-1", recipient, recipient, 15)))
    }
}

private class TestOnlyCredentialVerifier(private val signatureValid: Boolean) : TransportCredentialVerifier {
    override suspend fun verify(request: CredentialVerificationRequest): CredentialVerificationOutcome {
        val c = request.credential
        if (c.credentialVersion != 1) return CredentialVerificationOutcome.UnsupportedVersion
        if (!signatureValid) return CredentialVerificationOutcome.InvalidSignature
        if (c.revoked) return CredentialVerificationOutcome.Revoked
        if (request.nowEpochMillis < c.issuedAtEpochMillis || c.expiresAtEpochMillis?.let { request.nowEpochMillis >= it } == true) return CredentialVerificationOutcome.Expired
        if (c.businessId != request.expectedBusinessId) return CredentialVerificationOutcome.WrongBusiness
        if (request.expectedActorId != null && c.actorId != request.expectedActorId) return CredentialVerificationOutcome.WrongActor
        if (c.deviceId != request.expectedDeviceId) return CredentialVerificationOutcome.WrongDevice
        if (request.credentialRecipient != request.expectedRecipient) return CredentialVerificationOutcome.WrongRecipient
        return CredentialVerificationOutcome.Valid(TransportAuthorityContext(c.businessId, c.actorId, c.deviceId, c.authority, c.credentialEpoch))
    }
}
