package com.budcom.android.feature.serverconfig.domain.port

import com.budcom.android.core.common.AppError
import com.budcom.android.core.common.AppResult
import com.budcom.android.feature.serverconfig.domain.model.ConnectorConnectionProbe

/**
 * TD-016: the transport-aware answer to "is the Connector reachable, and what endpoint is it
 * at" — replacing direct reliance on [ConnectorStatusPort] (which is always LEGACY-sourced,
 * `BuildConfig.CONNECTOR_BASE_URL`-seeded) wherever a securely paired device's real status must
 * be shown instead.
 *
 * [Legacy] preserves today's exact [ConnectorStatusPort] behavior unchanged — genuinely
 * legacy/manual-URL devices are unaffected by this port's existence.
 *
 * The three AUTHENTICATED-transport variants intentionally never fall back to the legacy probe:
 * a device with any secure-pairing credential record (see `ConnectorTransportSelectionGate`)
 * must always be represented by its authenticated state, honestly, even when that state is
 * "not ready yet" or "genuinely unavailable" — never by silently substituting the unrelated
 * legacy endpoint's probe result.
 */
sealed class ConnectorOperationalStatus {

    /** transport = LEGACY. Identical to calling [ConnectorStatusPort] directly. */
    data class Legacy(
        val baseUrl: String,
        val healthProbe: AppResult<ConnectorConnectionProbe>,
    ) : ConnectorOperationalStatus()

    /**
     * transport = AUTHENTICATED, trust is ACTIVE, and an authenticated request round-trip
     * (pinned TLS + bearer credential) succeeded — proof the device really did reach its
     * trusted Connector, not merely that some URL responds. [endpointDisplay] is sourced from
     * the trusted-pairing context, never from [ConnectorStatusPort]/`BuildConfig.CONNECTOR_BASE_URL`.
     *
     * `/health`/`/ready` are deliberately excluded from the authenticated operation catalog
     * (see `AuthenticatedConnectorOperation`'s own doc comment) — there is no authenticated
     * equivalent of [ConnectorConnectionProbe]'s detailed health/readiness breakdown by design,
     * not by omission.
     */
    data class AuthenticatedHealthy(
        val endpointDisplay: String,
        val checkedAtEpochMillis: Long,
    ) : ConnectorOperationalStatus()

    /**
     * transport = AUTHENTICATED, and the authenticated round-trip genuinely failed (rejected
     * credential, unreachable trusted endpoint, malformed response, etc.). [error] describes the
     * real authenticated failure — this is never populated from a legacy-probe error.
     */
    data class AuthenticatedUnavailable(val error: AppError) : ConnectorOperationalStatus()

    /**
     * transport = AUTHENTICATED, but trust is not yet in a request-ready state (redeemed but
     * unverified, or requires re-pairing). A bounded, neutral state — must never be presented as
     * either "connected" or as the legacy emulator default.
     */
    data class AuthenticatedPreparing(val message: String) : ConnectorOperationalStatus()

    companion object {
        /** Display placeholder for authenticated non-healthy states — never a raw IP/URL. */
        const val AUTHENTICATED_ENDPOINT_PLACEHOLDER: String = "Secure paired Connector"
    }
}

/**
 * Resolves [ConnectorOperationalStatus] fresh on every call (matches the established
 * never-cache-trust-state convention of `ConnectorTransportSelectionGate` and
 * `AuthenticatedConnectorContextProvider`) — a vault-state change is observed by the very next
 * call, exactly like those two ports.
 */
interface ConnectorOperationalStatusPort {
    suspend fun currentStatus(): ConnectorOperationalStatus
}
