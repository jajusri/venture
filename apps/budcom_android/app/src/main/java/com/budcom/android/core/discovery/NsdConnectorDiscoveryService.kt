package com.budcom.android.core.discovery

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

private const val SERVICE_TYPE = "_budcom._tcp."
private const val POLL_INTERVAL_MS = 50L

/**
 * Real [android.net.nsd.NsdManager]-backed discovery of `_budcom._tcp` services.
 *
 * A single bounded pass: starts discovery, resolves whatever services are found within
 * [discover]'s timeout window, then always stops discovery — no background listener is
 * left registered between calls.
 */
@Singleton
class NsdConnectorDiscoveryService @Inject constructor(
    private val nsdManager: NsdManager,
) : ConnectorDiscoveryPort {

    override suspend fun discover(timeoutMs: Long): List<DiscoveredConnector> {
        val found = Channel<DiscoveredConnector>(capacity = Channel.UNLIMITED)

        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                serviceInfo.toDiscoveredConnectorOrNull()?.let { found.trySendBlocking(it) }
            }
        }

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                runCatching { nsdManager.resolveService(serviceInfo, resolveListener) }
            }
        }

        val startedDiscovery = runCatching {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        }.isSuccess
        if (!startedDiscovery) {
            found.close()
            return emptyList()
        }

        val results = mutableListOf<DiscoveredConnector>()
        withTimeoutOrNull(timeoutMs) {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                val next = found.tryReceive().getOrNull()
                if (next != null) {
                    results += next
                } else {
                    delay(POLL_INTERVAL_MS)
                }
            }
        }

        runCatching { nsdManager.stopServiceDiscovery(discoveryListener) }
        found.close()
        return results.distinctBy { it.connectorId }
    }
}

private fun NsdServiceInfo.toDiscoveredConnectorOrNull(): DiscoveredConnector? {
    val attrs = attributes ?: return null
    val connectorId = attrs["connectorId"]?.toString(Charsets.UTF_8) ?: return null
    val hostAddress = host?.hostAddress ?: return null
    val name = attrs["name"]?.toString(Charsets.UTF_8) ?: serviceName ?: connectorId
    val apiVersion = attrs["apiVersion"]?.toString(Charsets.UTF_8)
    val authRequired = attrs["authRequired"]?.toString(Charsets.UTF_8)?.toBooleanStrictOrNull() ?: false
    return DiscoveredConnector(
        connectorId = connectorId,
        name = name,
        host = hostAddress,
        port = port,
        apiVersion = apiVersion,
        authRequired = authRequired,
    )
}
