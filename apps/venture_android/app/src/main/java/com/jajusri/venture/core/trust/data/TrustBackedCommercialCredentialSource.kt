package com.jajusri.venture.core.trust.data

import com.jajusri.venture.core.trust.domain.TrustCredentialStore
import com.jajusri.venture.feature.transaction.domain.model.CommercialTrustCredentialSource
import com.jajusri.venture.feature.transaction.domain.port.TrustedBusinessDeviceCredential
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real [CommercialTrustCredentialSource] -- feeds the ALREADY-CERTIFIED, already-wired
 * `CachedTransportCredentialVerifier`/`TrustVerifiedCommercialActionAuthorityResolver`
 * (`feature/transaction`) with the real, fully-signed Trust credential this device holds.
 *
 * Deliberately does NOT itself check expiry (contrast with
 * [com.jajusri.venture.core.trust.data.TrustBackedRelayCredentialSource], which must: the wire-
 * transmission path it feeds, `AuthenticatedEnvelopeBinder.bind()`, never checks expiry itself).
 * Here, `CachedTransportCredentialVerifier.verify()` already classifies an expired credential as
 * the specific `CredentialVerificationOutcome.Expired` outcome -- short-circuiting to `null` here
 * would collapse that into the less-specific `Unavailable` instead, discarding information the
 * existing, certified verifier is designed to report precisely.
 */
@Singleton
class TrustBackedCommercialCredentialSource @Inject constructor(
    private val credentialStore: TrustCredentialStore,
) : CommercialTrustCredentialSource {
    override suspend fun trustedCredentialFor(businessId: String, deviceId: String): TrustedBusinessDeviceCredential? {
        val stored = credentialStore.current() ?: return null
        if (stored.businessId != businessId || stored.deviceId != deviceId) return null
        return TrustedBusinessDeviceCredential(
            credentialVersion = stored.credentialVersion, credentialId = stored.credentialId, businessId = stored.businessId,
            actorId = stored.actorId, membershipId = stored.membershipId, deviceId = stored.deviceId, deviceKeyId = stored.deviceKeyId,
            deviceKeyVersion = stored.deviceKeyVersion, devicePublicKeyFingerprint = stored.devicePublicKeyFingerprint,
            authorityScope = stored.authorityScope.toSet(), authorityEpoch = stored.authorityEpoch,
            issuedAtEpochMillis = stored.issuedAtEpochMillis, notBeforeEpochMillis = stored.notBeforeEpochMillis, expiresAtEpochMillis = stored.expiresAtEpochMillis,
            issuerId = stored.issuerId, issuerKeyId = stored.issuerKeyId, signatureProfile = SIGNATURE_PROFILE,
            signature = Base64.getDecoder().decode(stored.signatureBase64),
        )
    }

    private companion object { const val SIGNATURE_PROFILE = "P256-SHA256-v1" }
}
