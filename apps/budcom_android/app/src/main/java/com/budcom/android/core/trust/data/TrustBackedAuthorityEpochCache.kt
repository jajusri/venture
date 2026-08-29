package com.budcom.android.core.trust.data

import com.budcom.android.core.security.AuthorityEpochCache
import com.budcom.android.core.trust.data.remote.TrustApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private data class EpochCacheEntry(val epoch: Long?, val fetchedAtEpochMillis: Long)

/** Real [AuthorityEpochCache] -- fetches from Trust's `GET /v1/trust/authority/epoch` (which itself
 * responds `cache-control: no-store`, since staleness detection is the entire point). This cache's
 * own TTL is deliberately much shorter than [TrustBackedIssuerVerificationKeyCache]'s (10 seconds
 * vs. 5 minutes): a burst of local commercial actions in quick succession should not each trigger a
 * network round trip, but a revocation should still be noticed within a few seconds, not five
 * minutes. A fetch failure caches `null` for the same short window, matching `TemporarilyUnverifiable`
 * fail-closed semantics rather than either retrying every call or wedging a stale value in place. */
@Singleton
class TrustBackedAuthorityEpochCache(
    private val api: TrustApi,
    private val now: () -> Long = { System.currentTimeMillis() },
) : AuthorityEpochCache {
    // Kotlin default parameter values do NOT exempt a constructor parameter from Dagger's
    // dependency graph -- `@Inject constructor(api, now = {...})` still asks Dagger to provide a
    // `Function0<Long>` binding, which does not exist. This secondary constructor is the one Dagger
    // actually sees (single real dependency); the primary constructor above stays available for
    // tests that need to inject a fake clock.
    @Inject constructor(api: TrustApi) : this(api, { System.currentTimeMillis() })

    private val mutex = Mutex()
    private val cache = mutableMapOf<String, EpochCacheEntry>()

    override suspend fun currentEpoch(businessId: String, membershipId: String, deviceId: String): Long? = mutex.withLock {
        val cacheKey = "$businessId::$membershipId::$deviceId"
        val cached = cache[cacheKey]
        if (cached != null && now() - cached.fetchedAtEpochMillis < CACHE_TTL_MILLIS) return@withLock cached.epoch
        val fetched = fetchFromTrust(businessId, membershipId, deviceId)
        cache[cacheKey] = EpochCacheEntry(fetched, now())
        fetched
    }

    private suspend fun fetchFromTrust(businessId: String, membershipId: String, deviceId: String): Long? {
        val response = try { api.getCurrentAuthorityEpoch(businessId, membershipId, deviceId) } catch (e: IOException) { return null }
        if (!response.isSuccessful) return null
        return response.body()?.authorityEpoch
    }

    private companion object { const val CACHE_TTL_MILLIS = 10_000L }
}
