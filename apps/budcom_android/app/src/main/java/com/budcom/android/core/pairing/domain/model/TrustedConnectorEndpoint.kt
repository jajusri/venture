package com.budcom.android.core.pairing.domain.model

/**
 * The narrow, cryptographically-anchored endpoint context the pinned pairing client is allowed
 * to connect to. Deliberately constructable only from a source this file's factory functions
 * name explicitly — never from mDNS TXT records, an arbitrary user-entered URL, the legacy
 * `PairedConnectorEntity` host, or a live `/health` response: mDNS may still *locate* a
 * Connector, but the fingerprint that makes the connection trustworthy always comes from the QR
 * payload or a previously persisted secure trust record, never from the network itself.
 */
data class TrustedConnectorEndpoint(
    val connectorId: String,
    val connectorName: String,
    val host: String,
    val securePort: Int,
    val transportFingerprint: String,
    val fingerprintAlgorithm: String,
    val transportIdentityVersion: Int,
) {
    override fun toString(): String =
        "TrustedConnectorEndpoint(connectorId=$connectorId, connectorName=$connectorName, " +
            "transportFingerprint=$transportFingerprint, transportIdentityVersion=$transportIdentityVersion, host=<redacted>, securePort=<redacted>)"

    companion object {
        /** The only two approved construction sites: a freshly validated QR payload... */
        fun fromValidatedQrPayload(payload: SecurePairingQrPayload): TrustedConnectorEndpoint = TrustedConnectorEndpoint(
            connectorId = payload.connectorId,
            connectorName = payload.connectorName,
            host = payload.host,
            securePort = payload.securePort,
            transportFingerprint = payload.transportFingerprint,
            fingerprintAlgorithm = payload.fingerprintAlgorithm,
            transportIdentityVersion = payload.transportIdentityVersion,
        )

        /** ...or a previously persisted secure trust record (see `SecureCredentialVault`). */
        fun fromTrustedPublicMetadata(
            connectorId: String,
            connectorName: String,
            host: String,
            securePort: Int,
            transportFingerprint: String,
            fingerprintAlgorithm: String,
            transportIdentityVersion: Int,
        ): TrustedConnectorEndpoint = TrustedConnectorEndpoint(
            connectorId = connectorId,
            connectorName = connectorName,
            host = host,
            securePort = securePort,
            transportFingerprint = transportFingerprint,
            fingerprintAlgorithm = fingerprintAlgorithm,
            transportIdentityVersion = transportIdentityVersion,
        )
    }
}
