package com.jajusri.venture.core.trust.data

import com.jajusri.venture.core.security.AuthorityEpochCache
import com.jajusri.venture.core.trust.data.remote.TrustApi
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real [AuthorityEpochCache] -- despite the port's own name, holds NO positive cache of "current
 * epoch." Every call queries Trust's `GET /v1/trust/authority/epoch` fresh (that endpoint itself
 * responds `cache-control: no-store` for exactly this reason).
 *
 * ROUND 5 SECURITY FIX (Codex BLOCKER 2): the previous implementation cached the fetched epoch for
 * 10 seconds. That let a credential bearing an epoch Trust had already advanced past (revocation,
 * scope change, device deactivation) keep being accepted as fresh for up to 10 seconds after the
 * change: POSITIVE AUTHORIZATION STATE MUST NOT BE STALE-CACHED. A fetch failure (network, 404 "no
 * active authority") returns `null` on every call, which both callers
 * (`CachedTransportCredentialVerifier` and `TrustVerifiedCommercialActionAuthorityResolver`,
 * `feature/transaction`, both unmodified) already treat as fail-closed
 * (`TemporarilyUnverifiable`/`Unavailable`) -- so, as with the issuer-key cache, the smallest safe
 * implementation is simply: don't cache at all.
 *
 * KNOWN, ACCEPTED TRADE-OFF (documented per this round's own "same decision / duplicate fetch"
 * instruction, not overlooked): `TrustVerifiedCommercialActionAuthorityResolver.resolve()` calls
 * `epochs.currentEpoch(...)` once for its own pre-check, then `CachedTransportCredentialVerifier.verify()`
 * calls it AGAIN internally -- meaning one authorization decision now makes two live network calls
 * to the same endpoint instead of one. Collapsing that into a single fetch would require changing
 * `TransportCredentialVerifier`/`CredentialVerificationRequest`'s shape (add an epoch field, or drop
 * the resolver's own pre-check) inside `feature/transaction/domain/port` and
 * `feature/transaction/domain/model` -- both outside this round's DO-NOT-TOUCH boundary and neither
 * strictly necessary for correctness (both calls independently return the same fresh truth; the
 * only cost is network overhead, not staleness or inconsistency). Left as two fresh calls rather
 * than touching protected commercial-domain files for a pure efficiency gain.
 */
@Singleton
class TrustBackedAuthorityEpochCache @Inject constructor(
    private val api: TrustApi,
) : AuthorityEpochCache {
    override suspend fun currentEpoch(businessId: String, membershipId: String, deviceId: String): Long? {
        val response = try { api.getCurrentAuthorityEpoch(businessId, membershipId, deviceId) } catch (e: IOException) { return null }
        if (!response.isSuccessful) return null
        return response.body()?.authorityEpoch
    }
}
