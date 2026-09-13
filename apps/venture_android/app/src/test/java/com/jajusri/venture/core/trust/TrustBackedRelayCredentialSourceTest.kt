package com.jajusri.venture.core.trust

import com.jajusri.venture.core.trust.data.TrustBackedRelayCredentialSource
import com.jajusri.venture.core.trust.domain.StoredTrustCredential
import com.jajusri.venture.core.trust.domain.TrustCredentialReadOutcome
import com.jajusri.venture.core.trust.domain.TrustCredentialStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private class FakeRelayLookupCredentialStore(private var stored: StoredTrustCredential?) : TrustCredentialStore {
    override suspend fun current(): StoredTrustCredential? = stored
    override suspend fun readOutcome(): TrustCredentialReadOutcome = stored?.let { TrustCredentialReadOutcome.Present(it) } ?: TrustCredentialReadOutcome.NoRecord
    override suspend fun store(credential: StoredTrustCredential) { stored = credential }
    override suspend fun clear() { stored = null }
}

private fun credential(businessId: String = "business-1", deviceId: String = "device-1", expiresAtEpochMillis: Long = System.currentTimeMillis() + 3_600_000) =
    StoredTrustCredential(
        credentialVersion = 1, credentialId = "cred-1", businessId = businessId, actorId = "actor-1", membershipId = "membership-1",
        deviceId = deviceId, deviceKeyId = "device-1-key-1", deviceKeyVersion = 1, devicePublicKeyFingerprint = "fingerprint-1",
        authorityScope = listOf("send_orders"), authorityEpoch = 1L, issuedAtEpochMillis = expiresAtEpochMillis - 7_200_000,
        notBeforeEpochMillis = expiresAtEpochMillis - 7_200_000, expiresAtEpochMillis = expiresAtEpochMillis,
        issuerId = "issuer-1", issuerKeyId = "issuer-key-1", signatureBase64 = "c2ln",
    )

class TrustBackedRelayCredentialSourceTest {
    @Test
    fun `returns the stored credential when businessId and deviceId both match`() = runTest {
        val source = TrustBackedRelayCredentialSource(FakeRelayLookupCredentialStore(credential()))
        val result = source.credentialFor("business-1", "device-1")
        assertEquals("business-1", result?.businessId)
        assertEquals("device-1", result?.deviceId)
    }

    @Test
    fun `returns null when no credential has ever been stored`() = runTest {
        val source = TrustBackedRelayCredentialSource(FakeRelayLookupCredentialStore(null))
        assertNull(source.credentialFor("business-1", "device-1"))
    }

    @Test
    fun `never returns a credential for a business other than the one requested (business binding, Gate 3D)`() = runTest {
        val source = TrustBackedRelayCredentialSource(FakeRelayLookupCredentialStore(credential(businessId = "business-1")))
        assertNull(source.credentialFor("business-2", "device-1"))
    }

    @Test
    fun `never returns a credential for a device other than the one requested`() = runTest {
        val source = TrustBackedRelayCredentialSource(FakeRelayLookupCredentialStore(credential(deviceId = "device-1")))
        assertNull(source.credentialFor("business-1", "device-2"))
    }

    @Test
    fun `fails closed once the credential has expired, even though it is still on file`() = runTest {
        val source = TrustBackedRelayCredentialSource(FakeRelayLookupCredentialStore(credential(expiresAtEpochMillis = System.currentTimeMillis() - 1_000)))
        assertNull(source.credentialFor("business-1", "device-1"))
    }
}
