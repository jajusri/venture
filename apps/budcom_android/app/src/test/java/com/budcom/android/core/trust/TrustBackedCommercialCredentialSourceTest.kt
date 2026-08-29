package com.budcom.android.core.trust

import com.budcom.android.core.trust.data.TrustBackedCommercialCredentialSource
import com.budcom.android.core.trust.domain.StoredTrustCredential
import com.budcom.android.core.trust.domain.TrustCredentialReadOutcome
import com.budcom.android.core.trust.domain.TrustCredentialStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

private class FakeCommercialLookupCredentialStore(private var stored: StoredTrustCredential?) : TrustCredentialStore {
    override suspend fun current(): StoredTrustCredential? = stored
    override suspend fun readOutcome(): TrustCredentialReadOutcome = stored?.let { TrustCredentialReadOutcome.Present(it) } ?: TrustCredentialReadOutcome.NoRecord
    override suspend fun store(credential: StoredTrustCredential) { stored = credential }
    override suspend fun clear() { stored = null }
}

private fun credential(businessId: String = "business-1", deviceId: String = "device-1") = StoredTrustCredential(
    credentialVersion = 1, credentialId = "cred-1", businessId = businessId, actorId = "actor-1", membershipId = "membership-1",
    deviceId = deviceId, deviceKeyId = "device-1-key-1", deviceKeyVersion = 1, devicePublicKeyFingerprint = "fingerprint-1",
    authorityScope = listOf("send_orders"), authorityEpoch = 3L, issuedAtEpochMillis = 1_000L, notBeforeEpochMillis = 1_000L,
    expiresAtEpochMillis = 3_600_000L, issuerId = "issuer-1", issuerKeyId = "issuer-key-1",
    signatureBase64 = Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3, 4)),
)

class TrustBackedCommercialCredentialSourceTest {
    @Test
    fun `maps the stored credential to a fully-signed TrustedBusinessDeviceCredential when business and device match`() = runTest {
        val source = TrustBackedCommercialCredentialSource(FakeCommercialLookupCredentialStore(credential()))
        val result = source.trustedCredentialFor("business-1", "device-1")
        assertEquals("business-1", result?.businessId)
        assertEquals("membership-1", result?.membershipId)
        assertEquals(3L, result?.authorityEpoch)
        assertEquals(listOf<Byte>(1, 2, 3, 4), result?.signature?.toList())
    }

    @Test
    fun `returns null when no credential is stored`() = runTest {
        val source = TrustBackedCommercialCredentialSource(FakeCommercialLookupCredentialStore(null))
        assertNull(source.trustedCredentialFor("business-1", "device-1"))
    }

    @Test
    fun `never returns a credential for a business other than the one requested`() = runTest {
        val source = TrustBackedCommercialCredentialSource(FakeCommercialLookupCredentialStore(credential(businessId = "business-1")))
        assertNull(source.trustedCredentialFor("business-2", "device-1"))
    }

    @Test
    fun `never returns a credential for a device other than the one requested`() = runTest {
        val source = TrustBackedCommercialCredentialSource(FakeCommercialLookupCredentialStore(credential(deviceId = "device-1")))
        assertNull(source.trustedCredentialFor("business-1", "device-2"))
    }

    @Test
    fun `does not itself reject an expired credential -- lets the certified verifier classify it precisely`() = runTest {
        val expired = credential().copy(expiresAtEpochMillis = 2_000L)
        val source = TrustBackedCommercialCredentialSource(FakeCommercialLookupCredentialStore(expired))
        assertEquals(2_000L, source.trustedCredentialFor("business-1", "device-1")?.expiresAtEpochMillis)
    }
}
