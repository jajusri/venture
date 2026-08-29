package com.budcom.android.core.trust.data

import com.budcom.android.core.security.CachedIssuerVerificationKey
import com.budcom.android.core.security.IssuerVerificationKeyCache
import com.budcom.android.core.trust.data.remote.TrustApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

private data class VerificationKeyCacheEntry(val key: CachedIssuerVerificationKey?, val fetchedAtEpochMillis: Long)

/** Real [IssuerVerificationKeyCache] -- fetches from Trust's own public, cacheable
 * `GET /v1/trust/issuers/{issuerId}/verification-keys` endpoint (already certified, unchanged here)
 * and caches for a bounded TTL matching that endpoint's own `cache-control: max-age=300`. A fetch
 * failure (network, malformed PEM, key not found) caches `null` too -- a short-lived negative cache
 * entry, not a permanent one, so a transient Trust outage does not need an app restart to recover
 * from, but also does not retry on every single verification call in a burst. */
@Singleton
class TrustBackedIssuerVerificationKeyCache(
    private val api: TrustApi,
    private val now: () -> Long = { System.currentTimeMillis() },
) : IssuerVerificationKeyCache {
    // See TrustBackedAuthorityEpochCache's identical secondary-constructor comment: Dagger only
    // ever sees this @Inject constructor (single real dependency), never the primary constructor's
    // defaulted `now` parameter.
    @Inject constructor(api: TrustApi) : this(api, { System.currentTimeMillis() })

    private val mutex = Mutex()
    private val cache = mutableMapOf<String, VerificationKeyCacheEntry>()

    override suspend fun get(issuerId: String, issuerKeyId: String): CachedIssuerVerificationKey? = mutex.withLock {
        val cacheKey = "$issuerId::$issuerKeyId"
        val cached = cache[cacheKey]
        if (cached != null && now() - cached.fetchedAtEpochMillis < CACHE_TTL_MILLIS) return@withLock cached.key
        val fetched = fetchFromTrust(issuerId, issuerKeyId)
        cache[cacheKey] = VerificationKeyCacheEntry(fetched, now())
        fetched
    }

    private suspend fun fetchFromTrust(issuerId: String, issuerKeyId: String): CachedIssuerVerificationKey? {
        val response = try { api.getVerificationKeys(issuerId) } catch (e: IOException) { return null }
        if (!response.isSuccessful) return null
        val match = response.body()?.keys?.firstOrNull { it.issuerKeyId == issuerKeyId } ?: return null
        val derBytes = try { decodePemPublicKey(match.publicKey) } catch (e: IllegalArgumentException) { return null }
        return CachedIssuerVerificationKey(
            issuerId = match.issuerId, issuerKeyId = match.issuerKeyId, profile = match.profile,
            publicKey = derBytes, revoked = match.status == "revoked",
        )
    }

    private companion object { const val CACHE_TTL_MILLIS = 5 * 60 * 1000L }
}
