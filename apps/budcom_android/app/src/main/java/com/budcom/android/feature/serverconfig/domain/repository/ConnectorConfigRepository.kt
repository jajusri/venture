package com.budcom.android.feature.serverconfig.domain.repository

import com.budcom.android.core.common.AppResult
import com.budcom.android.feature.serverconfig.domain.model.ConnectorConnectionProbe
import com.budcom.android.feature.serverconfig.domain.port.ConnectorStatusPort
import kotlinx.coroutines.flow.Flow

/**
 * Persists Connector base URL and probes connectivity via `/health` and `/ready`.
 *
 * Extends [ConnectorStatusPort] so cross-feature consumers can depend on the stable port.
 */
interface ConnectorConfigRepository : ConnectorStatusPort {
    /**
     * Validates, persists, and applies [rawUrl] as the Connector base URL.
     * Does not fall back to the default when a previous user value exists.
     */
    suspend fun saveBaseUrl(rawUrl: String): AppResult<String>

    /**
     * Alias for [probeConnection] retained for existing server-config call sites.
     */
    suspend fun testConnection(): AppResult<ConnectorConnectionProbe> = probeConnection()
}
