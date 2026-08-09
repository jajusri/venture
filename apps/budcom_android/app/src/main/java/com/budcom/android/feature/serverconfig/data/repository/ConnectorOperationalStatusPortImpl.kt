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
import com.budcom.android.feature.serverconfig.domain.model.ConnectorHealth
import com.budcom.android.feature.serverconfig.domain.model.ConnectorReadiness
import com.budcom.android.feature.serverconfig.domain.port.ConnectorOperationalStatus
import com.budcom.android.feature.serverconfig.domain.port.ConnectorOperationalStatusPort
import com.budcom.android.feature.serverconfig.domain.port.ConnectorStatusPort
import com.budcom.android.feature.serverconfig.data.remote.HealthResponseDto
import com.budcom.android.feature.serverconfig.data.remote.ReadinessResponseDto
import com.budcom.android.feature.serverconfig.data.remote.toDomain
import kotlinx.serialization.json.Json
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
    private val json: Json,
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
     * [AuthenticatedConnectorOperation.DiagnosticsConnection] is the credential-authenticated,
     * pinned-TLS reachability proof. Only after it succeeds are the public health and readiness
     * routes read from the same pinned endpoint, without attaching the bearer credential.
     * The authenticated probe's response body (Connector-to-Tally diagnostics) is
     * deliberately not parsed here — a [AuthenticatedConnectorResult.Success] on its own is already
     * sufficient proof the trusted endpoint is reachable and the credential is valid, which is what
     * this port exists to establish.
     */
    private suspend fun resolveAuthenticated(): ConnectorOperationalStatus =
        when (val result = authenticatedApi.execute(AuthenticatedConnectorOperation.DiagnosticsConnection)) {
            is AuthenticatedConnectorResult.Success -> buildAuthenticatedHealthy()

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
            AuthenticatedConnectorResult.SessionExpired,
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

    private suspend fun buildAuthenticatedHealthy(): ConnectorOperationalStatus.AuthenticatedHealthy {
        val health = decodeHealth(authenticatedApi.execute(AuthenticatedConnectorOperation.PublicHealth))
        val readiness = decodeReadiness(authenticatedApi.execute(AuthenticatedConnectorOperation.PublicReadiness))
        return ConnectorOperationalStatus.AuthenticatedHealthy(
            endpointDisplay = resolveEndpointDisplay(),
            checkedAtEpochMillis = timeProvider.nowEpochMillis(),
            health = health.value,
            healthError = health.error,
            readiness = readiness.value,
            readinessError = readiness.error,
        )
    }

    private fun decodeHealth(result: AuthenticatedConnectorResult): PublicDetail<ConnectorHealth> =
        decodePublicDetail(result, "Secure Connector health could not be read.") { payload ->
            json.decodeFromString(HealthResponseDto.serializer(), payload).toDomain()
        }

    private fun decodeReadiness(result: AuthenticatedConnectorResult): PublicDetail<ConnectorReadiness> =
        decodePublicDetail(result, "Secure Connector readiness could not be read.") { payload ->
            val dto = json.decodeFromString(ReadinessResponseDto.serializer(), payload)
            dto.toDomain(httpStatus = if (dto.status == "ready") 200 else 503)
        }

    private fun <T> decodePublicDetail(
        result: AuthenticatedConnectorResult,
        failureMessage: String,
        decode: (String) -> T,
    ): PublicDetail<T> = when (result) {
        is AuthenticatedConnectorResult.Success -> runCatching { decode(result.payload.rawJson) }
            .fold(
                onSuccess = { PublicDetail(value = it) },
                onFailure = { PublicDetail(error = AppError.Serialization(failureMessage, it)) },
            )
        else -> PublicDetail(error = AppError.Message(failureMessage))
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

private data class PublicDetail<T>(
    val value: T? = null,
    val error: AppError? = null,
)
