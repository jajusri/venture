package com.budcom.android.core.connection

import com.budcom.android.core.connection.data.local.PairedConnectorEntity
import com.budcom.android.core.connection.data.local.PairedConnectorLocalDataSource
import com.budcom.android.core.discovery.ConnectorDiscoveryPort
import com.budcom.android.core.network.ConnectorBaseUrlProvider
import com.budcom.android.core.util.TimeProvider
import com.budcom.android.feature.serverconfig.data.local.ConnectorBaseUrlLocalStore
import com.budcom.android.feature.serverconfig.domain.validation.ConnectorUrlValidator
import javax.inject.Inject
import javax.inject.Singleton

/** Result of a single [ConnectorConnectionResolver.resolve] attempt. */
sealed class ConnectionResolution {
    data class Connected(
        val connectorId: String,
        val friendlyName: String,
        val host: String,
        val port: Int,
    ) : ConnectionResolution()

    /** No paired Connector reachable via last-known endpoint or LAN discovery. */
    data object Offline : ConnectionResolution()
}

/**
 * Resolves a working connection to the device's paired Connector, following the fixed
 * order: (1) probe the last-known endpoint, (2) fall back to bounded mDNS/NSD discovery
 * matched by stable `connectorId`, (3) on match, persist and reconnect, (4) otherwise
 * report [ConnectionResolution.Offline].
 *
 * A single bounded attempt per call — never busy-polls. Never auto-pairs with an
 * unmatched BUDCOM instance, and never touches [ConnectorBaseUrlProvider] or clears any
 * cache when nothing reachable is found.
 */
interface ConnectorConnectionResolver {
    suspend fun resolve(): ConnectionResolution
}

@Singleton
class DefaultConnectorConnectionResolver @Inject constructor(
    private val pairedConnectors: PairedConnectorLocalDataSource,
    private val healthProbe: ConnectorHealthProbe,
    private val discoveryPort: ConnectorDiscoveryPort,
    private val baseUrlProvider: ConnectorBaseUrlProvider,
    private val baseUrlLocalStore: ConnectorBaseUrlLocalStore,
    private val timeProvider: TimeProvider,
) : ConnectorConnectionResolver {

    override suspend fun resolve(): ConnectionResolution {
        val paired = pairedConnectors.getPrimary() ?: return ConnectionResolution.Offline

        val lastKnownProbe = healthProbe.probe(paired.lastKnownHost, paired.lastKnownPort, PROBE_TIMEOUT_MS)
        if (lastKnownProbe.reachable && lastKnownProbe.connectorId == paired.connectorId) {
            return reconnect(paired, paired.lastKnownHost, paired.lastKnownPort)
        }

        val discovered = discoveryPort.discover(DISCOVERY_TIMEOUT_MS)
        val match = discovered.firstOrNull { it.connectorId == paired.connectorId } ?: return ConnectionResolution.Offline

        return reconnect(paired, match.host, match.port)
    }

    private suspend fun reconnect(paired: PairedConnectorEntity, host: String, port: Int): ConnectionResolution {
        val normalizedUrl = ConnectorUrlValidator.normalizeOrNull("http://$host:$port")
        if (normalizedUrl == null) {
            return ConnectionResolution.Offline
        }
        pairedConnectors.updateLastKnownEndpoint(paired.connectorId, host, port, timeProvider.nowEpochMillis())
        baseUrlLocalStore.save(normalizedUrl)
        baseUrlProvider.updateInMemory(normalizedUrl)
        return ConnectionResolution.Connected(
            connectorId = paired.connectorId,
            friendlyName = paired.friendlyName,
            host = host,
            port = port,
        )
    }

    private companion object {
        const val PROBE_TIMEOUT_MS = 3_000L
        const val DISCOVERY_TIMEOUT_MS = 5_000L
    }
}
