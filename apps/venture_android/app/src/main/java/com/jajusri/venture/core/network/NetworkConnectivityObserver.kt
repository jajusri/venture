package com.jajusri.venture.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Observes whether the device currently has a validated internet capability.
 *
 * This does not prove the Venture Connector is reachable — only that a network
 * route exists. Connector reachability is determined by API calls (e.g. `/health`).
 */
interface NetworkConnectivityObserver {
    /** Emits `true` when a validated internet-capable network is available. */
    val isOnline: Flow<Boolean>

    /** Synchronous snapshot of current connectivity. */
    fun current(): Boolean
}

/**
 * [ConnectivityManager]-backed [NetworkConnectivityObserver].
 *
 * Tracks the system's *default* network via [ConnectivityManager.registerDefaultNetworkCallback]
 * rather than a capability-filtered [ConnectivityManager.registerNetworkCallback]. Physical
 * testing showed the capability-filtered "listener" form reliably delivered `onAvailable` when
 * Wi-Fi reconnected but could miss or indefinitely delay `onLost` while the app was backgrounded
 * (screen locked) — the default-network callback is the same signal backing the OS's own
 * connectivity indicator, so platform/OEM power management treats its transitions as
 * higher-priority and less eligible for deferral. This does not change what "online" means
 * (still "is there a validated internet-capable network") — only which OS signal reports it.
 */
@Singleton
class DefaultNetworkConnectivityObserver @Inject constructor(
    @ApplicationContext context: Context,
) : NetworkConnectivityObserver {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override fun current(): Boolean = connectivityManager.isCurrentlyOnline()

    override val isOnline: Flow<Boolean> = callbackFlow {
        trySend(current())

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(connectivityManager.isCurrentlyOnline())
            }

            override fun onLost(network: Network) {
                trySend(connectivityManager.isCurrentlyOnline())
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                trySend(connectivityManager.isCurrentlyOnline())
            }

            override fun onUnavailable() {
                trySend(false)
            }
        }

        connectivityManager.registerDefaultNetworkCallback(callback)
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}

private fun ConnectivityManager.isCurrentlyOnline(): Boolean {
    val network = activeNetwork ?: return false
    val capabilities = getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
