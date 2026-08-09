package com.budcom.android.core.pairing.data.remote

import com.budcom.android.core.pairing.domain.model.TrustedConnectorEndpoint
import com.budcom.android.core.security.SpkiFingerprintVerificationResult
import com.budcom.android.core.security.SpkiFingerprintVerifier
import com.budcom.android.core.util.TimeProvider
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.TlsVersion
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Builds a fresh, single-purpose [OkHttpClient] pinned to exactly one [TrustedConnectorEndpoint]
 * — never the app's shared Retrofit/OkHttp stack (see `core/network/NetworkModule.kt`), which
 * stays completely untouched by this phase. Every client this factory returns:
 *  - trusts only a server certificate whose SPKI fingerprint matches the pinned endpoint (via
 *    [SpkiFingerprintVerifier]) — never the system CA store, never "any self-signed cert";
 *  - verifies the TLS hostname against the exact configured endpoint host, nothing else;
 *  - never falls back to cleartext (its `connectionSpecs` contain no [ConnectionSpec.CLEARTEXT]);
 *  - never follows redirects (a redirect could otherwise smuggle a request to an unpinned host);
 *  - never auto-retries a failed connection;
 *  - never logs certificate or credential material.
 *
 * A client returned for one endpoint has no path to silently start trusting another endpoint's
 * fingerprint/host — the trust manager and hostname verifier are captured over that endpoint's
 * values alone at construction time, and every [create] call returns an independent instance.
 */
interface PinnedHttpClientFactory {
    fun create(endpoint: TrustedConnectorEndpoint): OkHttpClient
}

/** Sanitized marker retained through TLS exception wrapping; contains no certificate material. */
internal class ConnectorCertificateVerificationException(
    val verificationResult: SpkiFingerprintVerificationResult,
) : CertificateException("Connector certificate verification failed: $verificationResult")

internal fun Throwable.connectorCertificateVerificationResult(): SpkiFingerprintVerificationResult? =
    generateSequence(this) { it.cause }
        .filterIsInstance<ConnectorCertificateVerificationException>()
        .firstOrNull()
        ?.verificationResult

@Singleton
class OkHttpPinnedHttpClientFactory @Inject constructor(
    private val fingerprintVerifier: SpkiFingerprintVerifier,
    private val timeProvider: TimeProvider,
) : PinnedHttpClientFactory {

    override fun create(endpoint: TrustedConnectorEndpoint): OkHttpClient {
        val trustManager = PinnedTrustManager(fingerprintVerifier, timeProvider, endpoint.transportFingerprint)

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())

        val hostnameVerifier = HostnameVerifier { hostname, _ -> hostname == endpoint.host }

        val tlsOnlySpec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
            .tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2)
            .build()

        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier(hostnameVerifier)
            .connectionSpecs(listOf(tlsOnlySpec))
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(WRITE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .callTimeout(CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000L
        const val READ_TIMEOUT_MS = 15_000L
        const val WRITE_TIMEOUT_MS = 15_000L
        const val CALL_TIMEOUT_MS = 30_000L
    }
}

/** Never used for client-certificate auth; only [checkServerTrusted] is meaningful here. */
private class PinnedTrustManager(
    private val fingerprintVerifier: SpkiFingerprintVerifier,
    private val timeProvider: TimeProvider,
    private val pinnedFingerprint: String,
) : X509TrustManager {

    override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {
        throw CertificateException("Client certificate authentication is not used for pairing connections.")
    }

    override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {
        val result = fingerprintVerifier.verify(chain, pinnedFingerprint, timeProvider.nowEpochMillis())
        if (result != SpkiFingerprintVerificationResult.Match) {
            throw ConnectorCertificateVerificationException(result)
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}
