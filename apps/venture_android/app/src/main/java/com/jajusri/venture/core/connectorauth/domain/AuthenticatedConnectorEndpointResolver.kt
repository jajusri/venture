package com.jajusri.venture.core.connectorauth.domain

import com.jajusri.venture.core.discovery.ConnectorDiscoveryPort
import com.jajusri.venture.core.pairing.data.remote.PinnedHttpClientFactory
import com.jajusri.venture.core.pairing.data.remote.connectorCertificateVerificationResult
import com.jajusri.venture.core.pairing.domain.model.TrustedConnectorEndpoint
import com.jajusri.venture.core.security.SpkiFingerprintVerificationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okhttp3.HttpUrl
import okhttp3.Request
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val TIMBER_TAG = "AuthenticatedEndpointResolver"

/** Outcome of one [AuthenticatedConnectorEndpointResolver.resolveVerifiedEndpoint] attempt. */
sealed class VerifiedEndpointResolution {
    /** A candidate's TLS certificate cryptographically matched [expected]'s pinned fingerprint. */
    data class Verified(val endpoint: TrustedConnectorEndpoint) : VerifiedEndpointResolution()

    /** No candidate was found, or none presented a certificate matching the pinned fingerprint. */
    data object Unavailable : VerifiedEndpointResolution()

    /** At least one matching-ID candidate presented a different cryptographic identity. */
    data object IdentityMismatch : VerifiedEndpointResolution()

    /** At least one matching-ID candidate presented an invalid pinned certificate. */
    data object CertificateInvalid : VerifiedEndpointResolution()
}

/**
 * TD-017: finds the trusted Connector's current network address after its previously-persisted
 * endpoint stops responding, without ever trusting network-supplied data on its own.
 *
 * mDNS/NSD (`_venture._tcp`, via [ConnectorDiscoveryPort]) is discovery-only here — a candidate's
 * advertised `connectorId`/host/port/TXT record is merely a hint of *where to try*, never proof of
 * *who it is*. The only trust decision this class makes is a real, pinned TLS handshake against
 * each candidate using [expected]'s already-established `transportFingerprint` (the same
 * [PinnedHttpClientFactory]/`SpkiFingerprintVerifier` machinery [AuthenticatedConnectorApiPort]
 * itself relies on) — a candidate is [VerifiedEndpointResolution.Verified] only if its certificate
 * satisfies that exact pinned fingerprint, identically to every other authenticated request in
 * this app. `connectorId`, `transportFingerprint`, `connectorName`, `fingerprintAlgorithm`, and
 * `transportIdentityVersion` are carried through from [expected] unchanged; only `host`/`port` are
 * ever replaced, and only with a candidate's own values, never anything mDNS supplied for identity.
 *
 * The verification probe is a plain, unauthenticated GET against the candidate's `/health` route
 * (mounted on the same HTTPS listener as every authenticated route — see
 * `connector/venture_connector/src/api/server.ts`) purely to complete a TLS handshake; no bearer
 * credential is presented and the response body/status is never inspected — [PinnedHttpClientFactory]'s
 * trust manager already throws before any response would be readable if the fingerprint does not
 * match, so a request that completes at all is already sufficient proof.
 *
 * Never issues a pairing session, never creates or replaces trust/credential state, never persists
 * anything — see [com.jajusri.venture.core.pairing.data.local.SecureCredentialVault.updateVerifiedEndpoint]
 * for the separate, caller-driven persistence step that follows a [VerifiedEndpointResolution.Verified]
 * result.
 */
interface AuthenticatedConnectorEndpointResolver {
    suspend fun resolveVerifiedEndpoint(expected: TrustedConnectorEndpoint): VerifiedEndpointResolution
}

@Singleton
class DefaultAuthenticatedConnectorEndpointResolver @Inject constructor(
    private val discoveryPort: ConnectorDiscoveryPort,
    private val pinnedHttpClientFactory: PinnedHttpClientFactory,
) : AuthenticatedConnectorEndpointResolver {

    override suspend fun resolveVerifiedEndpoint(expected: TrustedConnectorEndpoint): VerifiedEndpointResolution {
        val discovered = discoveryPort.discover(DISCOVERY_TIMEOUT_MS)
        // TD-017 (real root cause): a candidate whose connectorId matches but which never
        // advertised a secure port has nothing this HTTPS-only path can verify against — the
        // previous code used the plain HTTP `port` field here instead, so every pinned-TLS
        // handshake attempt was doomed before it started (a plain HTTP listener never completes a
        // TLS handshake). Filtering here rather than crashing/erroring keeps a mixed discovery
        // result (e.g. a genuinely secure-transport-disabled installation) a clean Unavailable.
        val candidates = discovered.filter { it.connectorId == expected.connectorId && it.securePort != null }
        // Never logs host/IP/fingerprint: counts and the connectorId prefix (already broadcast
        // in cleartext over mDNS, never secret) are enough to diagnose without exposing topology.
        Timber.tag(TIMBER_TAG).d(
            "resolve start expectedConnectorId=%s discovered=%d matchingId=%d withSecurePort=%d",
            expected.connectorId.take(8),
            discovered.size,
            discovered.count { it.connectorId == expected.connectorId },
            candidates.size,
        )

        var sawIdentityMismatch = false
        var sawInvalidCertificate = false
        for (candidate in candidates) {
            val candidateEndpoint = expected.copy(host = candidate.host, securePort = candidate.securePort!!)
            val verification = verifyPinnedHandshake(candidateEndpoint)
            Timber.tag(TIMBER_TAG).d("candidate verification=%s", verification)
            when (verification) {
                CandidateVerification.Verified -> return VerifiedEndpointResolution.Verified(candidateEndpoint)
                CandidateVerification.IdentityMismatch -> sawIdentityMismatch = true
                CandidateVerification.CertificateInvalid -> sawInvalidCertificate = true
                CandidateVerification.Unavailable -> Unit
            }
        }
        return when {
            sawIdentityMismatch -> VerifiedEndpointResolution.IdentityMismatch
            sawInvalidCertificate -> VerifiedEndpointResolution.CertificateInvalid
            else -> VerifiedEndpointResolution.Unavailable
        }
    }

    /**
     * True only if a real TLS handshake against [candidate].host:securePort completes with a
     * certificate satisfying [candidate].transportFingerprint — any [IOException] (including the
     * `CertificateException` [PinnedHttpClientFactory]'s trust manager throws on a pin mismatch)
     * means verification failed. The response itself is discarded unread; only handshake success
     * is meaningful.
     */
    private suspend fun verifyPinnedHandshake(candidate: TrustedConnectorEndpoint): CandidateVerification {
        val client = pinnedHttpClientFactory.create(candidate)
        val request = Request.Builder()
            .url(
                HttpUrl.Builder()
                    .scheme("https")
                    .host(candidate.host)
                    .port(candidate.securePort)
                    .addPathSegment(VERIFICATION_PROBE_PATH)
                    .build(),
            )
            .get()
            .build()
        return try {
            runInterruptible(Dispatchers.IO) { client.newCall(request).execute() }.use { CandidateVerification.Verified }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            when (e.connectorCertificateVerificationResult()) {
                SpkiFingerprintVerificationResult.FingerprintMismatch -> CandidateVerification.IdentityMismatch
                null -> CandidateVerification.Unavailable
                else -> CandidateVerification.CertificateInvalid
            }
        }
    }

    private enum class CandidateVerification { Verified, IdentityMismatch, CertificateInvalid, Unavailable }

    private companion object {
        const val DISCOVERY_TIMEOUT_MS = 5_000L
        const val VERIFICATION_PROBE_PATH = "health"
    }
}
