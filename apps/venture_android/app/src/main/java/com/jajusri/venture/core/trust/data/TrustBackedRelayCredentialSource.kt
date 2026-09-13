package com.jajusri.venture.core.trust.data

import com.jajusri.venture.core.trust.domain.TrustCredentialStore
import com.jajusri.venture.feature.transaction.domain.port.RelayCredentialSource
import com.jajusri.venture.feature.transaction.domain.port.TrustedBusinessDeviceCredential
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real [RelayCredentialSource] -- bound to [com.jajusri.venture.feature.transaction.domain.port.RelayEnvelopeAuthenticator]'s
 * dependency in `core/trust/di/TrustModule.kt`. Returns the FULL Trust-issued signed credential
 * (claims + Trust signature, [TrustedBusinessDeviceCredential]) so the Relay wire envelope can carry
 * the exact proof the certified Relay verifier requires -- never a summarized/reconstructed
 * credential (relay-authority-repair, 2026-08-29: this was the transport wire contract gap).
 *
 * Deliberately DOES check expiry here (contrast with [com.jajusri.venture.core.trust.data.TrustBackedCommercialCredentialSource],
 * which does not): the wire-transmission path this feeds, `AuthenticatedEnvelopeBinder.bind()`/
 * `bindRequest()`, never checks expiry itself, so an expired credential must fail closed at the
 * source, not silently ride along into a signed envelope the certified verifier will reject anyway.
 */
@Singleton
class TrustBackedRelayCredentialSource @Inject constructor(
    private val credentialStore: TrustCredentialStore,
) : RelayCredentialSource {
    override suspend fun credentialFor(businessId: String, deviceId: String): TrustedBusinessDeviceCredential? {
        val stored = credentialStore.current() ?: return null
        // Business binding (Gate 3D): the comparison against the REQUESTED businessId/deviceId is
        // always explicit here, never inferred from Tally company name, Party name, or assumed
        // because only one credential happens to be on file.
        if (stored.businessId != businessId || stored.deviceId != deviceId) return null
        if (System.currentTimeMillis() >= stored.expiresAtEpochMillis) return null
        return TrustedBusinessDeviceCredential(
            credentialVersion = stored.credentialVersion, credentialId = stored.credentialId, businessId = stored.businessId,
            actorId = stored.actorId, membershipId = stored.membershipId, deviceId = stored.deviceId, deviceKeyId = stored.deviceKeyId,
            deviceKeyVersion = stored.deviceKeyVersion, devicePublicKeyFingerprint = stored.devicePublicKeyFingerprint,
            authorityScope = stored.authorityScope.toSet(), authorityEpoch = stored.authorityEpoch,
            issuedAtEpochMillis = stored.issuedAtEpochMillis, notBeforeEpochMillis = stored.notBeforeEpochMillis,
            expiresAtEpochMillis = stored.expiresAtEpochMillis, issuerId = stored.issuerId, issuerKeyId = stored.issuerKeyId,
            signatureProfile = SIGNATURE_PROFILE, signature = Base64.getDecoder().decode(stored.signatureBase64),
        )
    }

    private companion object { const val SIGNATURE_PROFILE = "P256-SHA256-v1" }
}
