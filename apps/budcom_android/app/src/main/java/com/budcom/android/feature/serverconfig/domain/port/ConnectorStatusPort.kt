package com.budcom.android.feature.serverconfig.domain.port

import com.budcom.android.core.common.AppResult
import com.budcom.android.feature.serverconfig.domain.model.ConnectorConnectionProbe
import kotlinx.coroutines.flow.Flow

/**
 * Stable public read-only port for Connector URL + health/readiness probing.
 *
 * Cross-feature consumers (e.g. dashboard) must depend on this port, not on
 * server-config data implementations.
 */
interface ConnectorStatusPort {
    fun observeBaseUrl(): Flow<String>

    fun currentBaseUrl(): String

    /**
     * Probes `GET /health` and optionally `GET /ready` using the configured base URL.
     */
    suspend fun probeConnection(): AppResult<ConnectorConnectionProbe>
}
