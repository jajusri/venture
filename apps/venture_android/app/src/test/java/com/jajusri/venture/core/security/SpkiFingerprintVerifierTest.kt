package com.jajusri.venture.core.security

import okhttp3.tls.HeldCertificate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.concurrent.TimeUnit

private const val NOW_EPOCH_MILLIS = 1_800_000_000_000L

private fun heldCertificate(
    notBefore: Long = NOW_EPOCH_MILLIS - TimeUnit.DAYS.toMillis(1),
    notAfter: Long = NOW_EPOCH_MILLIS + TimeUnit.DAYS.toMillis(365),
    keyPair: HeldCertificate? = null,
): HeldCertificate {
    val builder = HeldCertificate.Builder()
        .commonName("Test Connector")
        .validityInterval(notBefore, notAfter)
    if (keyPair != null) builder.keyPair(keyPair.keyPair)
    return builder.build()
}

/** Flips the last DER byte (within an EC signature's BIT STRING payload) — keeps the certificate
 * parseable but makes it fail its own signature verification, simulating a tampered/malformed cert. */
private fun withCorruptedSignature(certificate: X509Certificate): X509Certificate {
    val der = certificate.encoded.copyOf()
    der[der.size - 1] = (der[der.size - 1].toInt() xor 0xFF).toByte()
    return CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(der)) as X509Certificate
}

class SpkiFingerprintVerifierTest {

    private val verifier: SpkiFingerprintVerifier = DefaultSpkiFingerprintVerifier()

    // 18. SPKI SHA-256 calculation matches a known fixture
    @Test
    fun `computeFingerprint matches a hand-computed SHA-256 of the SPKI DER encoding`() {
        val cert = heldCertificate()
        val expectedDigest = MessageDigest.getInstance("SHA-256").digest(cert.certificate.publicKey.encoded)
        val expected = "sha256/" + Base64.getEncoder().encodeToString(expectedDigest)

        assertEquals(expected, verifier.computeFingerprint(cert.certificate.publicKey))
    }

    // 19. same key across certificate renewal has the same fingerprint
    @Test
    fun `renewing a certificate with the same key pair produces the same fingerprint`() {
        val original = heldCertificate()
        val renewed = heldCertificate(
            notBefore = NOW_EPOCH_MILLIS,
            notAfter = NOW_EPOCH_MILLIS + TimeUnit.DAYS.toMillis(730),
            keyPair = original,
        )

        assertEquals(
            verifier.computeFingerprint(original.certificate.publicKey),
            verifier.computeFingerprint(renewed.certificate.publicKey),
        )
    }

    // 20. different key has a different fingerprint
    @Test
    fun `two independently generated keys produce different fingerprints`() {
        val certA = heldCertificate()
        val certB = heldCertificate()

        assertNotEquals(
            verifier.computeFingerprint(certA.certificate.publicKey),
            verifier.computeFingerprint(certB.certificate.publicKey),
        )
    }

    // 21. fingerprint comparison is exact
    @Test
    fun `verify matches only an exactly equal fingerprint, not a prefix or near-miss`() {
        val cert = heldCertificate()
        val expected = verifier.computeFingerprint(cert.certificate.publicKey)
        val almostRight = expected.dropLast(2) + "AA"

        val exact = verifier.verify(arrayOf(cert.certificate), expected, NOW_EPOCH_MILLIS)
        val nearMiss = verifier.verify(arrayOf(cert.certificate), almostRight, NOW_EPOCH_MILLIS)

        assertEquals(SpkiFingerprintVerificationResult.Match, exact)
        assertEquals(SpkiFingerprintVerificationResult.FingerprintMismatch, nearMiss)
    }

    // 22. mismatched certificate rejected
    @Test
    fun `a certificate presenting a different key than the pinned fingerprint is rejected`() {
        val pinned = heldCertificate()
        val impostor = heldCertificate()

        val result = verifier.verify(
            arrayOf(impostor.certificate),
            verifier.computeFingerprint(pinned.certificate.publicKey),
            NOW_EPOCH_MILLIS,
        )

        assertEquals(SpkiFingerprintVerificationResult.FingerprintMismatch, result)
    }

    // 23. malformed certificate rejected
    @Test
    fun `a certificate with a corrupted self-signature is rejected as malformed`() {
        val cert = heldCertificate()
        val corrupted = withCorruptedSignature(cert.certificate)

        val result = verifier.verify(
            arrayOf(corrupted),
            verifier.computeFingerprint(cert.certificate.publicKey),
            NOW_EPOCH_MILLIS,
        )

        assertEquals(SpkiFingerprintVerificationResult.MalformedCertificate, result)
    }

    // 24. expired certificate rejected
    @Test
    fun `an expired certificate is rejected`() {
        val cert = heldCertificate(
            notBefore = NOW_EPOCH_MILLIS - TimeUnit.DAYS.toMillis(30),
            notAfter = NOW_EPOCH_MILLIS - TimeUnit.DAYS.toMillis(1),
        )

        val result = verifier.verify(
            arrayOf(cert.certificate),
            verifier.computeFingerprint(cert.certificate.publicKey),
            NOW_EPOCH_MILLIS,
        )

        assertEquals(SpkiFingerprintVerificationResult.CertificateExpired, result)
    }

    @Test
    fun `a not-yet-valid certificate is rejected`() {
        val cert = heldCertificate(
            notBefore = NOW_EPOCH_MILLIS + TimeUnit.DAYS.toMillis(1),
            notAfter = NOW_EPOCH_MILLIS + TimeUnit.DAYS.toMillis(30),
        )

        val result = verifier.verify(
            arrayOf(cert.certificate),
            verifier.computeFingerprint(cert.certificate.publicKey),
            NOW_EPOCH_MILLIS,
        )

        assertEquals(SpkiFingerprintVerificationResult.CertificateNotYetValid, result)
    }

    // missing certificate (chain null/empty)
    @Test
    fun `a null certificate chain is rejected as missing`() {
        val result = verifier.verify(null, "sha256/AAAA", NOW_EPOCH_MILLIS)
        assertEquals(SpkiFingerprintVerificationResult.MissingCertificate, result)
    }

    @Test
    fun `an empty certificate chain is rejected as missing`() {
        val result = verifier.verify(emptyArray(), "sha256/AAAA", NOW_EPOCH_MILLIS)
        assertEquals(SpkiFingerprintVerificationResult.MissingCertificate, result)
    }

    @Test
    fun `a genuinely matching certificate and fingerprint succeeds end to end`() {
        val cert = heldCertificate()
        val fingerprint = verifier.computeFingerprint(cert.certificate.publicKey)

        val result = verifier.verify(arrayOf(cert.certificate), fingerprint, NOW_EPOCH_MILLIS)

        assertTrue(result is SpkiFingerprintVerificationResult.Match)
    }
}
