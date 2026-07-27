package com.budcom.android.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
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
 * This does not prove the BudCom Connector is reachable — only that a network
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

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}

private fun ConnectivityManager.isCurrentlyOnline(): Boolean {
    val network = activeNetwork ?: return false
    val capabilities = getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
