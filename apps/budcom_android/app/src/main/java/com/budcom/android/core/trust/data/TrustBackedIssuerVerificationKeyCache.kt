package com.budcom.android.core.trust.data

import com.budcom.android.core.security.CachedIssuerVerificationKey
import com.budcom.android.core.security.IssuerVerificationKeyCache
import com.budcom.android.core.trust.data.remote.TrustApi
import java.io.IOException
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/** Strips PEM armor and decodes the base64 body to raw X.509 SPKI DER bytes -- the exact format
 * `CachedTransportCredentialVerifier` feeds to `X509EncodedKeySpec`. Trust's own signer
 * (`local-signer.ts`) exports its public key as PEM, never DER directly, so this conversion has to
 * happen on the reading side. */
fun decodePemPublicKey(pem: String): ByteArray {
    val body = pem.lineSequence().filterNot { it.startsWith("-----") }.joinToString("") { it.trim() }
    require(body.isNotBlank()) { "Empty PEM body" }
    return Base64.getDecoder().decode(body)
}

/**
 * Real [IssuerVerificationKeyCache] -- despite the port's own name (fixed by
 * `core/security/CachedTransportCredentialVerifier.kt`, not owned by this class), this
 * implementation deliberately holds NO positive cache of key acceptability.
 *
 * ROUND 5 SECURITY FIX (Codex BLOCKER 1): the previous implementation cached the fetched
 * `CachedIssuerVerificationKey` -- including `revoked` -- for a 5-minute TTL. That let an earlier
 * `revoked=false` answer keep authorizing signatures for up to 5 minutes after Trust actually
 * revoked or removed the key server-side: POSITIVE AUTHORIZATION STATE MUST NOT BE STALE-CACHED.
 * Every call now queries Trust's own public `GET /v1/trust/issuers/{issuerId}/verification-keys`
 * fresh -- no TTL, no stored map, nothing retained between calls. Trust being unreachable, the key
 * being absent, or the PEM being malformed all return `null` here, which
 * `CachedTransportCredentialVerifier` already treats as `TemporarilyUnverifiable` (fail closed) --
 * so "smallest safe implementation" is genuinely just: don't cache at all.
 */
@Singleton
class TrustBackedIssuerVerificationKeyCache @Inject constructor(
    private val api: TrustApi,
) : IssuerVerificationKeyCache {
    override suspend fun get(issuerId: String, issuerKeyId: String): CachedIssuerVerificationKey? {
        val response = try { api.getVerificationKeys(issuerId) } catch (e: IOException) { return null }
        if (!response.isSuccessful) return null
        val match = response.body()?.keys?.firstOrNull { it.issuerKeyId == issuerKeyId } ?: return null
        val derBytes = try { decodePemPublicKey(match.publicKey) } catch (e: IllegalArgumentException) { return null }
        return CachedIssuerVerificationKey(
            issuerId = match.issuerId, issuerKeyId = match.issuerKeyId, profile = match.profile,
            publicKey = derBytes, revoked = match.status == "revoked",
        )
    }
}
