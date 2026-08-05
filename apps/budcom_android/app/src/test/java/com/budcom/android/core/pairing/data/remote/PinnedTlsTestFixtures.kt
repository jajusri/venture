package com.budcom.android.core.pairing.data.remote

import com.budcom.android.core.pairing.domain.model.TrustedConnectorEndpoint
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.TimeUnit

/** Shared MockWebServer + real self-signed-certificate test harness for the pinned-TLS layer. */
internal const val TLS_TEST_NOW_EPOCH_MILLIS = 1_800_000_000_000L

internal fun testHeldCertificate(
    commonName: String = "Test Connector",
    notBefore: Long = TLS_TEST_NOW_EPOCH_MILLIS - TimeUnit.DAYS.toMillis(1),
    notAfter: Long = TLS_TEST_NOW_EPOCH_MILLIS + TimeUnit.DAYS.toMillis(365),
): HeldCertificate = HeldCertificate.Builder()
    .commonName(commonName)
    .addSubjectAlternativeName("localhost")
    .addSubjectAlternativeName("127.0.0.1")
    .validityInterval(notBefore, notAfter)
    .build()

internal fun fingerprintOf(heldCertificate: HeldCertificate): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(heldCertificate.certificate.publicKey.encoded)
    return "sha256/" + Base64.getEncoder().encodeToString(digest)
}

/** Starts a real HTTPS [MockWebServer] presenting [heldCertificate]. Caller must shut it down. */
internal fun startTlsMockWebServer(heldCertificate: HeldCertificate): MockWebServer {
    val handshakeCertificates = HandshakeCertificates.Builder()
        .heldCertificate(heldCertificate)
        .build()
    val server = MockWebServer()
    server.useHttps(handshakeCertificates.sslSocketFactory(), false)
    server.start()
    return server
}

/** A [TrustedConnectorEndpoint] pointed at [server], pinned to [heldCertificate]'s own fingerprint. */
internal fun endpointFor(server: MockWebServer, heldCertificate: HeldCertificate, connectorId: String = "connector-abc"): TrustedConnectorEndpoint =
    TrustedConnectorEndpoint.fromTrustedPublicMetadata(
        connectorId = connectorId,
        connectorName = "Front Desk",
        host = server.hostName,
        securePort = server.port,
        transportFingerprint = fingerprintOf(heldCertificate),
        fingerprintAlgorithm = "sha256",
        transportIdentityVersion = 1,
    )
