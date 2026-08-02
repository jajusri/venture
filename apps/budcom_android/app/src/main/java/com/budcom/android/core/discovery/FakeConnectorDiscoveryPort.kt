package com.budcom.android.core.discovery

/** Deterministic test double — never touches NsdManager or a real network. */
class FakeConnectorDiscoveryPort(
    private var results: List<DiscoveredConnector> = emptyList(),
) : ConnectorDiscoveryPort {

    var lastRequestedTimeoutMs: Long? = null
        private set
    var callCount: Int = 0
        private set

    fun setResults(results: List<DiscoveredConnector>) {
        this.results = results
    }

    override suspend fun discover(timeoutMs: Long): List<DiscoveredConnector> {
        callCount++
        lastRequestedTimeoutMs = timeoutMs
        return results
    }
}
