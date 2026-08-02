package com.budcom.android.core.discovery

/**
 * A BudCom Connector instance found via LAN service discovery (mDNS/NSD), advertising
 * `_budcom._tcp` with non-sensitive TXT metadata only (matches connector's mdns-advertiser).
 */
data class DiscoveredConnector(
    val connectorId: String,
    val name: String,
    val host: String,
    val port: Int,
    val apiVersion: String?,
    val authRequired: Boolean,
)

/**
 * Narrow seam over Android's [android.net.nsd.NsdManager] so connection-resolution logic
 * never depends directly on the platform API and can run against a deterministic fake in
 * unit tests (instrumented NsdManager behavior isn't runnable headlessly).
 */
interface ConnectorDiscoveryPort {
    /**
     * Performs a single bounded-timeout discovery pass and returns whatever Connectors were
     * found and resolved within [timeoutMs]. Never busy-polls; callers invoke this once per
     * resolution attempt (app start / explicit retry / reconnect trigger).
     */
    suspend fun discover(timeoutMs: Long): List<DiscoveredConnector>
}
