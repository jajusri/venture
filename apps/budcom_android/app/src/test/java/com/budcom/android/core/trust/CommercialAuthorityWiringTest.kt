package com.budcom.android.core.trust

import com.budcom.android.core.security.AuthorityEpochCache
import com.budcom.android.core.security.CachedIssuerVerificationKey
import com.budcom.android.core.security.CachedTransportCredentialVerifier
import com.budcom.android.core.security.IssuerVerificationKeyCache
import com.budcom.android.core.trust.data.TrustBackedCommercialCredentialSource
import com.budcom.android.core.trust.domain.StoredTrustCredential
import com.budcom.android.core.trust.domain.TrustCredentialReadOutcome
import com.budcom.android.core.trust.domain.TrustCredentialStore
import com.budcom.android.feature.transaction.domain.model.CommercialAction
import com.budcom.android.feature.transaction.domain.model.CommercialActionAuthorityOutcome
import com.budcom.android.feature.transaction.domain.model.CommercialActionAuthorityRequest
import com.budcom.android.feature.transaction.domain.model.TrustVerifiedCommercialActionAuthorityResolver
import com.budcom.android.feature.transaction.domain.port.DeviceKeySecurityLevel
import com.budcom.android.feature.transaction.domain.port.DeviceSigningIdentity
import com.budcom.android.feature.transaction.domain.port.DeviceSigningResult
import com.budcom.android.feature.transaction.domain.port.TrustedBusinessDeviceCredential
import com.budcom.android.feature.transaction.domain.port.VartalapDeviceKeyStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/**
 * End-to-end proof (Gate 5C, items 1-7) that the REAL, already-certified
 * `CachedTransportCredentialVerifier` + `TrustVerifiedCommercialActionAuthorityResolver`
 * (`feature/transaction`, unmodified) correctly authorize or reject a REAL ECDSA-signed credential
 * once fed by this round's new Trust-backed implementations
 * ([TrustBackedCommercialCredentialSource] and simple fakes standing in for
 * [IssuerVerificationKeyCache]/[AuthorityEpochCache]/[VartalapDeviceKeyStore] -- those three are
 * unit-tested for their own fetch/cache/PEM-decode behavior in their own dedicated test files;
 * this file's job is to prove the WIRING and the CRYPTO, not re-test caching).
 */
private class FakeWiringCredentialStore(private var stored: StoredTrustCredential?) : TrustCredentialStore {
    override suspend fun current(): StoredTrustCredential? = stored
    override suspend fun readOutcome(): TrustCredentialReadOutcome = stored?.let { TrustCredentialReadOutcome.Present(it) } ?: TrustCredentialReadOutcome.NoRecord
    override suspend fun store(credential: StoredTrustCredential) { stored = credential }
    override suspend fun clear() { stored = null }
}

private class FakeIdentityStore(private val identity: DeviceSigningIdentity?) : VartalapDeviceKeyStore {
    override suspend fun getCurrentIdentity(): DeviceSigningIdentity? = identity
    override suspend fun getOrCreateIdentity(deviceId: String): DeviceSigningIdentity = error("not used")
    override suspend fun rotate(deviceId: String): DeviceSigningIdentity = error("not used")
    override suspend fun inspect(deviceId: String, keyVersion: Int): DeviceSigningIdentity? = identity
    override suspend fun sign(identity: DeviceSigningIdentity, boundedBytes: ByteArray): DeviceSigningResult = error("not used")
    override suspend fun remove(deviceId: String, keyVersion: Int): Boolean = false
}

private class FixedIssuerKeyCache(private val key: CachedIssuerVerificationKey?) : IssuerVerificationKeyCache {
    override suspend fun get(issuerId: String, issuerKeyId: String): CachedIssuerVerificationKey? = key
}

private class FixedEpochCache(private val epoch: Long?) : AuthorityEpochCache {
    override suspend fun currentEpoch(businessId: String, membershipId: String, deviceId: String): Long? = epoch
}

private const val BUSINESS_ID = "business-1"
private const val SELLER_BUSINESS_ID = "business-2"
private const val ACTOR_ID = "actor-1"
private const val DEVICE_ID = "device-1"
private const val ISSUER_ID = "issuer-1"
private const val ISSUER_KEY_ID = "issuer-key-1"
private const val NOW = 10_000_000L

private fun signCredential(privateKey: PrivateKey, credential: TrustedBusinessDeviceCredential): ByteArray =
    Signature.getInstance("SHA256withECDSA").apply { initSign(privateKey); update(credential.signingBytes()) }.sign()

private fun unsignedCredential(deviceKeyVersion: Int = 1, authorityEpoch: Long = 3L, expiresAt: Long = NOW + 3_600_000) = TrustedBusinessDeviceCredential(
    credentialVersion = 1, credentialId = "cred-1", businessId = BUSINESS_ID, actorId = ACTOR_ID, membershipId = "membership-1",
    deviceId = DEVICE_ID, deviceKeyId = "device-1-key-1", deviceKeyVersion = deviceKeyVersion, devicePublicKeyFingerprint = "fingerprint-1",
    authorityScope = setOf("send_orders"), authorityEpoch = authorityEpoch, issuedAtEpochMillis = NOW - 1_000,
    notBeforeEpochMillis = NOW - 1_000, expiresAtEpochMillis = expiresAt, issuerId = ISSUER_ID, issuerKeyId = ISSUER_KEY_ID,
    signatureProfile = "P256-SHA256-v1", signature = ByteArray(0),
)

private fun storedFrom(credential: TrustedBusinessDeviceCredential): StoredTrustCredential = StoredTrustCredential(
    credentialVersion = credential.credentialVersion, credentialId = credential.credentialId, businessId = credential.businessId,
    actorId = credential.actorId, membershipId = credential.membershipId, deviceId = credential.deviceId, deviceKeyId = credential.deviceKeyId,
    deviceKeyVersion = credential.deviceKeyVersion, devicePublicKeyFingerprint = credential.devicePublicKeyFingerprint,
    authorityScope = credential.authorityScope.toList(), authorityEpoch = credential.authorityEpoch,
    issuedAtEpochMillis = credential.issuedAtEpochMillis, notBeforeEpochMillis = credential.notBeforeEpochMillis,
    expiresAtEpochMillis = credential.expiresAtEpochMillis, issuerId = credential.issuerId, issuerKeyId = credential.issuerKeyId,
    signatureBase64 = Base64.getEncoder().encodeToString(credential.signature),
)

private fun baseRequest(action: CommercialAction = CommercialAction.BuyerCreateOrder, expectedDeviceKeyVersion: Int = 1) = CommercialActionAuthorityRequest(
    action = action, viewerBusinessId = BUSINESS_ID, expectedActorId = ACTOR_ID, expectedDeviceId = DEVICE_ID,
    expectedDeviceKeyVersion = expectedDeviceKeyVersion, orderId = "", orderVersion = 0, inboxOrderId = "", inboxOrderVersion = 0,
    sellerBusinessId = SELLER_BUSINESS_ID, buyerBusinessId = BUSINESS_ID, nowEpochMillis = NOW,
)

private fun identity(keyVersion: Int = 1) = DeviceSigningIdentity(
    deviceId = DEVICE_ID, keyId = "device-1-key-1", keyVersion = keyVersion, publicKey = ByteArray(65) { 9 },
    publicKeyFingerprint = "fingerprint-1", createdAtEpochMillis = 0L, securityLevel = DeviceKeySecurityLevel.HardwareBacked,
)

class CommercialAuthorityWiringTest {
    private val keyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    private val issuerKey = CachedIssuerVerificationKey(ISSUER_ID, ISSUER_KEY_ID, "P256-SHA256-v1", keyPair.public.encoded, revoked = false)

    private fun resolverWith(
        stored: StoredTrustCredential?, issuerKeyCache: IssuerVerificationKeyCache = FixedIssuerKeyCache(issuerKey),
        epochCache: AuthorityEpochCache = FixedEpochCache(3L), deviceIdentity: DeviceSigningIdentity? = identity(),
    ): TrustVerifiedCommercialActionAuthorityResolver {
        val credentials = TrustBackedCommercialCredentialSource(FakeWiringCredentialStore(stored))
        val verifier = CachedTransportCredentialVerifier(issuerKeyCache, epochCache)
        return TrustVerifiedCommercialActionAuthorityResolver(credentials, verifier, FakeIdentityStore(deviceIdentity), epochCache)
    }

    @Test
    fun `1 -- a valid Trust-issued credential with matching Business and device is authorized`() = runTest {
        val signed = unsignedCredential().let { it.copy(signature = signCredential(keyPair.private, it)) }
        val outcome = resolverWith(storedFrom(signed)).resolve(baseRequest())
        assertTrue(outcome is CommercialActionAuthorityOutcome.Verified)
    }

    @Test
    fun `2 -- a missing credential fails closed`() = runTest {
        val outcome = resolverWith(stored = null).resolve(baseRequest())
        assertEquals(CommercialActionAuthorityOutcome.Unavailable, outcome)
    }

    @Test
    fun `3 -- an expired credential fails closed as Expired`() = runTest {
        val signed = unsignedCredential(expiresAt = NOW - 1).let { it.copy(signature = signCredential(keyPair.private, it)) }
        val outcome = resolverWith(storedFrom(signed)).resolve(baseRequest())
        assertEquals(CommercialActionAuthorityOutcome.Expired, outcome)
    }

    @Test
    fun `4 -- a credential stored for a different Business than the one being viewed fails closed`() = runTest {
        val signed = unsignedCredential().copy(businessId = "some-other-business").let { it.copy(signature = signCredential(keyPair.private, it)) }
        val outcome = resolverWith(storedFrom(signed)).resolve(baseRequest())
        // Caught at the credential-source layer (business mismatch) before ever reaching the
        // verifier -- see TrustBackedCommercialCredentialSourceTest for that boundary directly.
        assertEquals(CommercialActionAuthorityOutcome.Unavailable, outcome)
    }

    @Test
    fun `5 -- a credential whose deviceKeyVersion does not match the current device key fails closed as WrongDevice`() = runTest {
        val signed = unsignedCredential(deviceKeyVersion = 2).let { it.copy(signature = signCredential(keyPair.private, it)) }
        val outcome = resolverWith(storedFrom(signed)).resolve(baseRequest(expectedDeviceKeyVersion = 1))
        assertEquals(CommercialActionAuthorityOutcome.WrongDevice, outcome)
    }

    @Test
    fun `6 -- Trust issuer verification material being absent fails closed (TemporarilyUnverifiable to Unavailable)`() = runTest {
        val signed = unsignedCredential().let { it.copy(signature = signCredential(keyPair.private, it)) }
        val outcome = resolverWith(storedFrom(signed), issuerKeyCache = FixedIssuerKeyCache(null)).resolve(baseRequest())
        assertEquals(CommercialActionAuthorityOutcome.Unavailable, outcome)
    }

    @Test
    fun `7a -- malformed verification key material (not a real EC key) fails closed via signature rejection`() = runTest {
        val signed = unsignedCredential().let { it.copy(signature = signCredential(keyPair.private, it)) }
        val garbageKey = issuerKey.copy(publicKey = byteArrayOf(1, 2, 3))
        val outcome = resolverWith(storedFrom(signed), issuerKeyCache = FixedIssuerKeyCache(garbageKey)).resolve(baseRequest())
        assertEquals(CommercialActionAuthorityOutcome.Unavailable, outcome)
    }

    @Test
    fun `7b -- a tampered signature is rejected`() = runTest {
        val signed = unsignedCredential().let { it.copy(signature = signCredential(keyPair.private, it)) }
        val tampered = signed.copy(signature = signed.signature.also { it[0] = it[0].inc() })
        val outcome = resolverWith(storedFrom(tampered)).resolve(baseRequest())
        assertEquals(CommercialActionAuthorityOutcome.Unavailable, outcome)
    }

    @Test
    fun `a stale authority epoch (revoked or changed since issuance) fails closed before reaching signature verification`() = runTest {
        val signed = unsignedCredential(authorityEpoch = 3L).let { it.copy(signature = signCredential(keyPair.private, it)) }
        val outcome = resolverWith(storedFrom(signed), epochCache = FixedEpochCache(4L)).resolve(baseRequest())
        assertEquals(CommercialActionAuthorityOutcome.StaleEpoch, outcome)
    }

    @Test
    fun `revoked issuer key is rejected even with a mathematically valid signature`() = runTest {
        val signed = unsignedCredential().let { it.copy(signature = signCredential(keyPair.private, it)) }
        val revokedKey = issuerKey.copy(revoked = true)
        val outcome = resolverWith(storedFrom(signed), issuerKeyCache = FixedIssuerKeyCache(revokedKey)).resolve(baseRequest())
        assertEquals(CommercialActionAuthorityOutcome.Revoked, outcome)
    }
}
