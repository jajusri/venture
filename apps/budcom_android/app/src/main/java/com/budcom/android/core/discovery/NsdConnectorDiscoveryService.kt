package com.budcom.android.core.discovery

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private const val SERVICE_TYPE = "_budcom._tcp."
private const val POLL_INTERVAL_MS = 50L
private const val TIMBER_TAG = "NsdConnectorDiscovery"

/**
 * Real [android.net.nsd.NsdManager]-backed discovery of `_budcom._tcp` services.
 *
 * A single bounded pass: starts discovery, resolves whatever services are found within
 * [discover]'s timeout window, then always stops discovery — no background listener is
 * left registered between calls.
 *
 * Holds a [MulticastLockController] for the duration of the pass. Without it, several OEM Wi-Fi
 * stacks silently drop every mDNS multicast frame the device would otherwise receive — discovery
 * still reports success, it simply never sees any candidate, which is indistinguishable at this
 * API from a genuinely empty network. See [MulticastLockController]'s doc comment.
 */
@Singleton
class NsdConnectorDiscoveryService @Inject constructor(
    private val nsdManager: NsdManager,
    private val multicastLock: MulticastLockController,
) : ConnectorDiscoveryPort {

    override suspend fun discover(timeoutMs: Long): List<DiscoveredConnector> = multicastLock.withLock {
        val found = Channel<DiscoveredConnector>(capacity = Channel.UNLIMITED)

        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Timber.tag(TIMBER_TAG).d("resolve failed errorCode=%d", errorCode)
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val connector = serviceInfo.toDiscoveredConnectorOrNull()
                // Never logs host/IP: connectorId/securePort presence are enough to diagnose
                // without exposing any network-topology detail.
                Timber.tag(TIMBER_TAG).d(
                    "resolved connectorId=%s hasSecurePort=%b",
                    connector?.connectorId?.take(8),
                    connector?.securePort != null,
                )
                connector?.let { found.trySendBlocking(it) }
            }
        }

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Timber.tag(TIMBER_TAG).w("start discovery failed errorCode=%d", errorCode)
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Timber.tag(TIMBER_TAG).w("stop discovery failed errorCode=%d", errorCode)
            }
            override fun onDiscoveryStarted(serviceType: String) {
                Timber.tag(TIMBER_TAG).d("discovery started")
            }
            override fun onDiscoveryStopped(serviceType: String) {
                Timber.tag(TIMBER_TAG).d("discovery stopped")
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Timber.tag(TIMBER_TAG).d("service lost")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Timber.tag(TIMBER_TAG).d("service found, resolving")
                runCatching { nsdManager.resolveService(serviceInfo, resolveListener) }
                    .onFailure { Timber.tag(TIMBER_TAG).w(it, "resolveService threw") }
            }
        }

        val startedDiscovery = runCatching {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        }.onFailure { Timber.tag(TIMBER_TAG).w(it, "discoverServices threw") }.isSuccess
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
        val distinct = results.distinctBy { it.connectorId }
        Timber.tag(TIMBER_TAG).d("discover() pass complete, resolvedCount=%d", distinct.size)
        return distinct
    }
}

private fun NsdServiceInfo.toDiscoveredConnectorOrNull(): DiscoveredConnector? {
    val attrs = attributes ?: return null
    val connectorId = attrs["connectorId"]?.toString(Charsets.UTF_8) ?: return null
    val hostAddress = host?.hostAddress ?: return null
    val name = attrs["name"]?.toString(Charsets.UTF_8) ?: serviceName ?: connectorId
    val apiVersion = attrs["apiVersion"]?.toString(Charsets.UTF_8)
    val authRequired = attrs["authRequired"]?.toString(Charsets.UTF_8)?.toBooleanStrictOrNull() ?: false
    // TD-017 (real root cause): absent or unparseable means "no secure transport advertised" —
    // never falls back to the primary port, which would repeat the exact bug this fixes.
    val securePort = attrs["securePort"]?.toString(Charsets.UTF_8)?.toIntOrNull()
    return DiscoveredConnector(
        connectorId = connectorId,
        name = name,
        host = hostAddress,
        port = port,
        apiVersion = apiVersion,
        authRequired = authRequired,
        securePort = securePort,
    )
}
