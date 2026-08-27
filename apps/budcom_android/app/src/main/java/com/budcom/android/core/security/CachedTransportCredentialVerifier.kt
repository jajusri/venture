package com.budcom.android.core.security

import com.budcom.android.feature.transaction.domain.port.*
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

data class CachedIssuerVerificationKey(val issuerId: String, val issuerKeyId: String, val profile: String, val publicKey: ByteArray, val revoked: Boolean)
fun interface IssuerVerificationKeyCache { suspend fun get(issuerId: String, issuerKeyId: String): CachedIssuerVerificationKey? }
fun interface AuthorityEpochCache { suspend fun currentEpoch(businessId: String, membershipId: String, deviceId: String): Long? }

class CachedTransportCredentialVerifier(private val keys: IssuerVerificationKeyCache, private val epochs: AuthorityEpochCache) : TransportCredentialVerifier {
    override suspend fun verify(request: CredentialVerificationRequest): CredentialVerificationOutcome {
        val c = request.credential
        if (c.credentialVersion != 1 || c.signatureProfile != PROFILE) return CredentialVerificationOutcome.UnsupportedVersion
        if (request.nowEpochMillis < c.notBeforeEpochMillis || request.nowEpochMillis >= c.expiresAtEpochMillis) return CredentialVerificationOutcome.Expired
        if (c.businessId != request.expectedBusinessId) return CredentialVerificationOutcome.WrongBusiness
        if (request.expectedActorId != null && c.actorId != request.expectedActorId) return CredentialVerificationOutcome.WrongActor
        if (c.deviceId != request.expectedDeviceId || c.deviceKeyVersion != request.expectedDeviceKeyVersion) return CredentialVerificationOutcome.WrongDevice
        if (request.actualRecipient != request.expectedRecipient) return CredentialVerificationOutcome.WrongRecipient
        val key = keys.get(c.issuerId, c.issuerKeyId) ?: return CredentialVerificationOutcome.TemporarilyUnverifiable
        if (key.revoked) return CredentialVerificationOutcome.Revoked
        if (key.issuerId != c.issuerId || key.issuerKeyId != c.issuerKeyId || key.profile != PROFILE) return CredentialVerificationOutcome.InvalidSignature
        val validSignature = runCatching { Signature.getInstance(ALGORITHM).apply {
            initVerify(KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(key.publicKey))); update(c.signingBytes())
        }.verify(c.signature) }.getOrDefault(false)
        if (!validSignature) return CredentialVerificationOutcome.InvalidSignature
        val epoch = epochs.currentEpoch(c.businessId, c.membershipId, c.deviceId) ?: return CredentialVerificationOutcome.TemporarilyUnverifiable
        if (epoch != c.authorityEpoch) return CredentialVerificationOutcome.Revoked
        return CredentialVerificationOutcome.Valid(TransportAuthorityContext(c.businessId, c.actorId, c.deviceId, c.authorityScope, c.authorityEpoch))
    }
    private companion object { const val PROFILE = "P256-SHA256-v1"; const val ALGORITHM = "SHA256withECDSA" }
}
