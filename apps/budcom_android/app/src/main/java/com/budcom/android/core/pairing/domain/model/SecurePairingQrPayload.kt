package com.budcom.android.core.pairing.domain.model

/**
 * Immutable, versioned domain model for a validated Desktop-generated secure-pairing QR
 * payload — the QR code is the first-trust channel (see [SecurePairingQrPayloadParser]): the
 * Connector's own transport public-key fingerprint is only ever pinned from here, or from a
 * previously persisted trust record, never from mDNS/`/health` alone.
 *
 * Only ever constructed by [SecurePairingQrPayloadParser] in production code after every
 * validation rule has passed — the public constructor exists so tests and other pairing-domain
 * code can build fixtures directly without re-encoding/re-parsing JSON.
 */
data class SecurePairingQrPayload(
    val schemaVersion: String,
    val pairingSessionId: String,
    val secret: String,
    val connectorId: String,
    val connectorName: String,
    val host: String,
    /**
     * The Connector's plain HTTP listener port. Not itself connected to over cleartext — the
     * pinned client only ever dials [securePort] over HTTPS — but required verbatim in the
     * QR-path redemption request body, since the Connector's redeem contract matches the
     * submitted `connectorId`/`host`/`port` against the values the pairing session was created
     * with (its own plain `config.host`/`config.port`) as an anti-tampering check independent of
     * the one-time secret. See `RedeemSecurePairingSession`/`SecurePairingApiClient.redeemQr`.
     */
    val port: Int,
    val securePort: Int,
    val transportProtocol: String,
    val transportFingerprint: String,
    val fingerprintAlgorithm: String,
    val transportIdentityVersion: Int,
    val expiresAtEpochMillis: Long,
) {
    /**
     * Fully redacted — this payload carries a one-time pairing secret and network-topology
     * details that must never reach logs, crash reports, or exception messages via an
     * accidental `Log.d(payload.toString())`/string-templated exception.
     */
    override fun toString(): String = "SecurePairingQrPayload(redacted)"
}
