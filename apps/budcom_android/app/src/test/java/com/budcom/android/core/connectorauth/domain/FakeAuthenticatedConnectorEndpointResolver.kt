package com.budcom.android.core.connectorauth.domain

import com.budcom.android.core.pairing.domain.model.TrustedConnectorEndpoint

/** Deterministic test double — never touches NsdManager, OkHttp, or a real network. */
class FakeAuthenticatedConnectorEndpointResolver(
    private var result: VerifiedEndpointResolution = VerifiedEndpointResolution.Unavailable,
) : AuthenticatedConnectorEndpointResolver {

    var callCount: Int = 0
        private set
    var lastExpected: TrustedConnectorEndpoint? = null
        private set

    fun setResult(result: VerifiedEndpointResolution) {
        this.result = result
    }

    override suspend fun resolveVerifiedEndpoint(expected: TrustedConnectorEndpoint): VerifiedEndpointResolution {
        callCount++
        lastExpected = expected
        return result
    }
}
