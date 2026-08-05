package com.budcom.android.core.security

import java.security.MessageDigest
import java.security.PublicKey
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateNotYetValidException
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

sealed class SpkiFingerprintVerificationResult {
    data object Match : SpkiFingerprintVerificationResult()
    data object FingerprintMismatch : SpkiFingerprintVerificationResult()
    data object CertificateExpired : SpkiFingerprintVerificationResult()
    data object CertificateNotYetValid : SpkiFingerprintVerificationResult()
    data object MissingCertificate : SpkiFingerprintVerificationResult()
    data object MalformedCertificate : SpkiFingerprintVerificationResult()
}

/**
 * Pure SPKI-pinning verification, independent of TLS plumbing so it's directly unit-testable
 * (see [com.budcom.android.core.pairing.data.remote.PinnedHttpClientFactory] for where this is
 * wired into an actual `X509TrustManager`). Never trusts the system CA store as a substitute for
 * pinning, never treats "is self-signed" alone as sufficient trust, and never trusts a live
 * `/health` response's `connectorId` in place of this cryptographic check.
 */
interface SpkiFingerprintVerifier {
    /** `sha256/<base64 SHA-256 of the DER SubjectPublicKeyInfo>` — the same form the Connector emits. */
    fun computeFingerprint(publicKey: PublicKey): String

    /**
     * Full pinning check against the leaf of [chain] (as received by an
     * `X509TrustManager.checkServerTrusted` callback): validity window, self-consistency, and an
     * exact constant-time fingerprint comparison against [expectedFingerprint].
     */
    fun verify(
        chain: Array<X509Certificate>?,
        expectedFingerprint: String,
        atEpochMillis: Long,
    ): SpkiFingerprintVerificationResult
}

@Singleton
class DefaultSpkiFingerprintVerifier @Inject constructor() : SpkiFingerprintVerifier {

    override fun computeFingerprint(publicKey: PublicKey): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(publicKey.encoded)
        return FINGERPRINT_PREFIX + Base64.getEncoder().encodeToString(digest)
    }

    override fun verify(
        chain: Array<X509Certificate>?,
        expectedFingerprint: String,
        atEpochMillis: Long,
    ): SpkiFingerprintVerificationResult {
        val leaf = chain?.firstOrNull() ?: return SpkiFingerprintVerificationResult.MissingCertificate

        try {
            leaf.checkValidity(Date(atEpochMillis))
        } catch (e: CertificateExpiredException) {
            return SpkiFingerprintVerificationResult.CertificateExpired
        } catch (e: CertificateNotYetValidException) {
            return SpkiFingerprintVerificationResult.CertificateNotYetValid
        }

        try {
            // Confirms the certificate's own signature is consistent with its embedded public
            // key — i.e. it is genuinely self-signed, not merely self-labeled. Does NOT chain to
            // any external trust anchor; that is deliberate, see the class doc comment.
            leaf.verify(leaf.publicKey)
        } catch (e: Exception) {
            return SpkiFingerprintVerificationResult.MalformedCertificate
        }

        val actual = try {
            computeFingerprint(leaf.publicKey)
        } catch (e: Exception) {
            return SpkiFingerprintVerificationResult.MalformedCertificate
        }

        return if (constantTimeFingerprintEquals(actual, expectedFingerprint)) {
            SpkiFingerprintVerificationResult.Match
        } else {
            SpkiFingerprintVerificationResult.FingerprintMismatch
        }
    }

    private fun constantTimeFingerprintEquals(actual: String, expected: String): Boolean {
        if (!actual.startsWith(FINGERPRINT_PREFIX) || !expected.startsWith(FINGERPRINT_PREFIX)) return false
        val actualBytes = try {
            Base64.getDecoder().decode(actual.removePrefix(FINGERPRINT_PREFIX))
        } catch (e: IllegalArgumentException) {
            return false
        }
        val expectedBytes = try {
            Base64.getDecoder().decode(expected.removePrefix(FINGERPRINT_PREFIX))
        } catch (e: IllegalArgumentException) {
            return false
        }
        return MessageDigest.isEqual(actualBytes, expectedBytes)
    }

    private companion object {
        const val FINGERPRINT_PREFIX = "sha256/"
    }
}
