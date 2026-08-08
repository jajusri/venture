package com.budcom.android.core.connectorauth.domain

import com.budcom.android.core.discovery.ConnectorDiscoveryPort
import com.budcom.android.core.pairing.data.remote.PinnedHttpClientFactory
import com.budcom.android.core.pairing.domain.model.TrustedConnectorEndpoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okhttp3.HttpUrl
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of one [AuthenticatedConnectorEndpointResolver.resolveVerifiedEndpoint] attempt. */
sealed class VerifiedEndpointResolution {
    /** A candidate's TLS certificate cryptographically matched [expected]'s pinned fingerprint. */
    data class Verified(val endpoint: TrustedConnectorEndpoint) : VerifiedEndpointResolution()

    /** No candidate was found, or none presented a certificate matching the pinned fingerprint. */
    data object Unavailable : VerifiedEndpointResolution()
}

/**
 * TD-017: finds the trusted Connector's current network address after its previously-persisted
 * endpoint stops responding, without ever trusting network-supplied data on its own.
 *
 * mDNS/NSD (`_budcom._tcp`, via [ConnectorDiscoveryPort]) is discovery-only here — a candidate's
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
 * `connector/budcom_connector/src/api/server.ts`) purely to complete a TLS handshake; no bearer
 * credential is presented and the response body/status is never inspected — [PinnedHttpClientFactory]'s
 * trust manager already throws before any response would be readable if the fingerprint does not
 * match, so a request that completes at all is already sufficient proof.
 *
 * Never issues a pairing session, never creates or replaces trust/credential state, never persists
 * anything — see [com.budcom.android.core.pairing.data.local.SecureCredentialVault.updateVerifiedEndpoint]
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
        val candidates = discovered.filter { it.connectorId == expected.connectorId }

        for (candidate in candidates) {
            val candidateEndpoint = expected.copy(host = candidate.host, securePort = candidate.port)
            if (verifyPinnedHandshake(candidateEndpoint)) {
                return VerifiedEndpointResolution.Verified(candidateEndpoint)
            }
        }
        return VerifiedEndpointResolution.Unavailable
    }

    /**
     * True only if a real TLS handshake against [candidate].host:securePort completes with a
     * certificate satisfying [candidate].transportFingerprint — any [IOException] (including the
     * `CertificateException` [PinnedHttpClientFactory]'s trust manager throws on a pin mismatch)
     * means verification failed. The response itself is discarded unread; only handshake success
     * is meaningful.
     */
    private suspend fun verifyPinnedHandshake(candidate: TrustedConnectorEndpoint): Boolean {
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
            runInterruptible(Dispatchers.IO) { client.newCall(request).execute() }.use { true }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            false
        }
    }

    private companion object {
        const val DISCOVERY_TIMEOUT_MS = 5_000L
        const val VERIFICATION_PROBE_PATH = "health"
    }
}
