package com.jajusri.venture.core.pairing.data.remote

import com.jajusri.venture.core.pairing.domain.model.PairingDeviceIdentity
import com.jajusri.venture.core.pairing.domain.model.SecurePairingQrPayload
import com.jajusri.venture.core.pairing.domain.model.TrustedConnectorEndpoint

/**
 * Records every call it receives so use-case tests can assert exactly what was sent (e.g. that
 * short-code redemption never independently re-derives an endpoint, or that self-revoke is
 * called with the caller's own credential) without a real network.
 */
class FakeSecurePairingApiPort(
    private val redeemResult: PairingRedeemOutcome = PairingRedeemOutcome.TransportFailure,
    private val selfStatusResult: PairingSelfStatusOutcome = PairingSelfStatusOutcome.TransportFailure,
    private val revokeResult: PairingSelfRevokeOutcome = PairingSelfRevokeOutcome.TransportFailure,
) : SecurePairingApiPort {

    var redeemQrCallCount = 0
        private set
    var lastRedeemQrPayload: SecurePairingQrPayload? = null
    var lastRedeemDeviceIdentity: PairingDeviceIdentity? = null

    var redeemShortCodeCallCount = 0
        private set
    var lastShortCode: String? = null
    var lastShortCodeEndpoint: TrustedConnectorEndpoint? = null

    var getCredentialSelfCallCount = 0
        private set
    var lastSelfStatusBearer: String? = null
    var lastSelfStatusEndpoint: TrustedConnectorEndpoint? = null

    var revokeSelfCallCount = 0
        private set
    var lastRevokeBearer: String? = null
    var lastRevokeEndpoint: TrustedConnectorEndpoint? = null

    override suspend fun redeemQr(payload: SecurePairingQrPayload, deviceIdentity: PairingDeviceIdentity): PairingRedeemOutcome {
        redeemQrCallCount++
        lastRedeemQrPayload = payload
        lastRedeemDeviceIdentity = deviceIdentity
        return redeemResult
    }

    override suspend fun redeemShortCode(
        shortCode: String,
        endpoint: TrustedConnectorEndpoint,
        deviceIdentity: PairingDeviceIdentity,
    ): PairingRedeemOutcome {
        redeemShortCodeCallCount++
        lastShortCode = shortCode
        lastShortCodeEndpoint = endpoint
        lastRedeemDeviceIdentity = deviceIdentity
        return redeemResult
    }

    override suspend fun getCredentialSelf(endpoint: TrustedConnectorEndpoint, bearerCredential: String): PairingSelfStatusOutcome {
        getCredentialSelfCallCount++
        lastSelfStatusBearer = bearerCredential
        lastSelfStatusEndpoint = endpoint
        return selfStatusResult
    }

    override suspend fun revokeSelf(endpoint: TrustedConnectorEndpoint, bearerCredential: String): PairingSelfRevokeOutcome {
        revokeSelfCallCount++
        lastRevokeBearer = bearerCredential
        lastRevokeEndpoint = endpoint
        return revokeResult
    }
}
