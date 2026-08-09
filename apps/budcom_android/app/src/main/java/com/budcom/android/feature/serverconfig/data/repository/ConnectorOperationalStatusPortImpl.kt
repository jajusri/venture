package com.budcom.android.feature.serverconfig.data.repository

import com.budcom.android.core.common.AppError
import com.budcom.android.core.connectorauth.data.remote.AuthenticatedConnectorApiPort
import com.budcom.android.core.connectorauth.domain.AuthenticatedConnectorContextProvider
import com.budcom.android.core.connectorauth.domain.AuthenticatedConnectorContextResolution
import com.budcom.android.core.connectorauth.domain.ConnectorTransportSelection
import com.budcom.android.core.connectorauth.domain.ConnectorTransportSelectionGate
import com.budcom.android.core.connectorauth.domain.model.AuthenticatedConnectorOperation
import com.budcom.android.core.connectorauth.domain.model.AuthenticatedConnectorResult
import com.budcom.android.core.util.TimeProvider
import com.budcom.android.feature.serverconfig.domain.model.ConnectorConnectionProbe
import com.budcom.android.feature.serverconfig.domain.port.ConnectorOperationalStatus
import com.budcom.android.feature.serverconfig.domain.port.ConnectorOperationalStatusPort
import com.budcom.android.feature.serverconfig.domain.port.ConnectorStatusPort
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TD-016: selects LEGACY or AUTHENTICATED per [ConnectorTransportSelectionGate] and reports the
 * corresponding real status. Never writes the authenticated endpoint into any legacy store — see
 * [ConnectorStatusPort]'s own doc comment ("this store never silently reverts a user-saved value")
 * and the TD-016 registry entry's explicit "not an acceptable fix direction" note. Deliberately
 * does not touch [com.budcom.android.core.network.ConnectorBaseUrlProvider]'s shared in-memory
 * state, which the LEGACY transport's own requests continue to rely on unmodified.
 */
@Singleton
class ConnectorOperationalStatusPortImpl @Inject constructor(
    private val transportGate: ConnectorTransportSelectionGate,
    private val legacyStatus: ConnectorStatusPort,
    private val authenticatedApi: AuthenticatedConnectorApiPort,
    private val contextProvider: AuthenticatedConnectorContextProvider,
    private val timeProvider: TimeProvider,
) : ConnectorOperationalStatusPort {

    override suspend fun currentStatus(): ConnectorOperationalStatus =
        when (transportGate.resolve()) {
            ConnectorTransportSelection.LEGACY -> resolveLegacy()
            ConnectorTransportSelection.AUTHENTICATED -> resolveAuthenticated()
        }

    private suspend fun resolveLegacy(): ConnectorOperationalStatus.Legacy =
        ConnectorOperationalStatus.Legacy(
            baseUrl = legacyStatus.currentBaseUrl(),
            healthProbe = legacyStatus.probeConnection(),
        )

    /**
     * `/health`/`/ready` are deliberately excluded from [AuthenticatedConnectorOperation] (public,
     * pre-credential routes by design). [AuthenticatedConnectorOperation.DiagnosticsConnection] is
     * used instead as the authenticated reachability probe: a bearer-authenticated, pinned-TLS
     * round-trip to the trusted Connector. Its response body (Connector-to-Tally diagnostics) is
     * deliberately not parsed here — a [AuthenticatedConnectorResult.Success] on its own is already
     * sufficient proof the trusted endpoint is reachable and the credential is valid, which is what
     * this port exists to establish. Parsing/surfacing that body's detail is a separate, later
     * enhancement, not required to fix TD-016.
     */
    private suspend fun resolveAuthenticated(): ConnectorOperationalStatus =
        when (val result = authenticatedApi.execute(AuthenticatedConnectorOperation.DiagnosticsConnection)) {
            is AuthenticatedConnectorResult.Success -> ConnectorOperationalStatus.AuthenticatedHealthy(
                endpointDisplay = resolveEndpointDisplay(),
                checkedAtEpochMillis = timeProvider.nowEpochMillis(),
            )

            AuthenticatedConnectorResult.PendingVerification -> ConnectorOperationalStatus.AuthenticatedPreparing(
                "Verifying secure pairing…",
            )

            AuthenticatedConnectorResult.RePairRequired -> ConnectorOperationalStatus.AuthenticatedUnavailable(
                AppError.Message("This device needs to be re-paired with the Connector."),
            )

            AuthenticatedConnectorResult.CredentialUnavailable -> ConnectorOperationalStatus.AuthenticatedUnavailable(
                AppError.Message("The secure pairing credential could not be read on this device."),
            )

            AuthenticatedConnectorResult.Unpaired -> ConnectorOperationalStatus.AuthenticatedUnavailable(
                AppError.Message("No secure pairing is set up on this device."),
            )

            is AuthenticatedConnectorResult.Unauthorized -> ConnectorOperationalStatus.AuthenticatedUnavailable(
                AppError.Message("The Connector rejected this device's credential."),
            )

            AuthenticatedConnectorResult.Forbidden -> ConnectorOperationalStatus.AuthenticatedUnavailable(
                AppError.Message("Access to the Connector was denied."),
            )

            AuthenticatedConnectorResult.NotFound,
            AuthenticatedConnectorResult.Conflict,
            AuthenticatedConnectorResult.RateLimited,
            is AuthenticatedConnectorResult.ValidationFailure,
            is AuthenticatedConnectorResult.ServerFailure,
            AuthenticatedConnectorResult.MalformedResponse,
            -> ConnectorOperationalStatus.AuthenticatedUnavailable(
                AppError.Message("The Connector returned an unexpected response."),
            )

            AuthenticatedConnectorResult.TransportFailure -> ConnectorOperationalStatus.AuthenticatedUnavailable(
                AppError.Message("Could not reach the Connector."),
            )

            AuthenticatedConnectorResult.IdentityMismatch -> ConnectorOperationalStatus.AuthenticatedUnavailable(
                AppError.Message("Connector identity changed. Trust was not replaced; scan a fresh Desktop QR to re-pair explicitly."),
            )

            AuthenticatedConnectorResult.CertificateInvalid -> ConnectorOperationalStatus.AuthenticatedUnavailable(
                AppError.Message("The Connector certificate is invalid. Trust was not changed."),
            )

            AuthenticatedConnectorResult.Cancelled -> ConnectorOperationalStatus.AuthenticatedUnavailable(
                AppError.Message("The connection check was cancelled."),
            )
        }

    /**
     * Sourced only from the trusted-pairing context (never from [ConnectorStatusPort] /
     * `BuildConfig.CONNECTOR_DEFAULT_BASE_URL`) — this is the one place a raw endpoint may legitimately be
     * shown for a securely paired device, and only the real one.
     */
    private suspend fun resolveEndpointDisplay(): String =
        when (val resolution = contextProvider.resolve()) {
            is AuthenticatedConnectorContextResolution.Ready ->
                "https://${resolution.context.endpoint.host}:${resolution.context.endpoint.securePort}/"
            else -> ConnectorOperationalStatus.AUTHENTICATED_ENDPOINT_PLACEHOLDER
        }
}
