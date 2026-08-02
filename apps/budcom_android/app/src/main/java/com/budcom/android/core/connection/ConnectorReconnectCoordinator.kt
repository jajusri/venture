package com.budcom.android.core.connection

import com.budcom.android.core.network.NetworkConnectivityObserver
import com.budcom.android.core.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Triggers exactly one bounded [ConnectorConnectionOrchestrator.ensureConnected] attempt per
 * "device came back online" transition — never a poll loop, never overlapping attempts.
 *
 * The initial emission from [NetworkConnectivityObserver.isOnline] is dropped: app-start
 * reconnection is already handled by whoever calls [ConnectorConnectionOrchestrator]
 * directly at startup. This coordinator only reacts to *changes* while the app is running
 * (Wi-Fi switch, reconnect after a drop, DHCP renewal that toggles connectivity).
 */
interface ConnectorReconnectCoordinator {
    /** Call once (e.g. from Application.onCreate()) with a process-lifetime scope. */
    fun start(scope: CoroutineScope)
}

@Singleton
class DefaultConnectorReconnectCoordinator @Inject constructor(
    private val connectivityObserver: NetworkConnectivityObserver,
    private val orchestrator: ConnectorConnectionOrchestrator,
    private val dispatchers: DispatcherProvider,
) : ConnectorReconnectCoordinator {

    private val reconnectGate = Mutex()

    override fun start(scope: CoroutineScope) {
        scope.launch(dispatchers.io) {
            connectivityObserver.isOnline
                .drop(1)
                .filter { isOnline -> isOnline }
                .collect { attemptReconnect() }
        }
    }

    private suspend fun attemptReconnect() {
        if (!reconnectGate.tryLock()) {
            // A reconnect attempt is already in flight — this transition rides along with it
            // instead of stacking a second concurrent resolution.
            return
        }
        try {
            orchestrator.ensureConnected()
        } finally {
            reconnectGate.unlock()
        }
    }
}
