package com.budcom.android.core.connection

import com.budcom.android.core.connectorauth.data.remote.AuthenticatedConnectorApiPort
import com.budcom.android.core.connectorauth.domain.ConnectorTransportSelection
import com.budcom.android.core.connectorauth.domain.ConnectorTransportSelectionGate
import com.budcom.android.core.connectorauth.domain.model.AuthenticatedConnectorOperation
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
 * Triggers exactly one bounded reconnect attempt per "device came back online" transition —
 * never a poll loop, never overlapping attempts. Transport-aware (TD-017): a LEGACY-paired
 * device gets [ConnectorConnectionOrchestrator.ensureConnected] unchanged; an AUTHENTICATED
 * (securely paired) device gets one bounded authenticated probe instead, via
 * [AuthenticatedConnectorApiPort.execute] — the probe operation itself already contains the
 * verified-endpoint-rediscovery-and-retry-once logic (see `OkHttpAuthenticatedConnectorApiClient`),
 * so this coordinator only needs to *trigger* that path, never duplicate its rediscovery logic.
 * This is what makes a customer's "Wi-Fi restored" scenario self-heal without requiring them to
 * open a screen or tap Retry — the same wiring [ConnectorConnectionOrchestrator] already relied
 * on for LEGACY devices, extended to cover AUTHENTICATED ones too.
 *
 * The initial emission from [NetworkConnectivityObserver.isOnline] is dropped: app-start
 * reconnection is already handled by whoever calls [ConnectorConnectionOrchestrator]/the
 * authenticated transport directly at startup. This coordinator only reacts to *changes* while
 * the app is running (Wi-Fi switch, reconnect after a drop, DHCP renewal that toggles
 * connectivity).
 */
interface ConnectorReconnectCoordinator {
    /** Call once (e.g. from Application.onCreate()) with a process-lifetime scope. */
    fun start(scope: CoroutineScope)
}

@Singleton
class DefaultConnectorReconnectCoordinator @Inject constructor(
    private val connectivityObserver: NetworkConnectivityObserver,
    private val orchestrator: ConnectorConnectionOrchestrator,
    private val transportGate: ConnectorTransportSelectionGate,
    private val authenticatedApi: AuthenticatedConnectorApiPort,
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
            when (transportGate.resolve()) {
                ConnectorTransportSelection.LEGACY -> orchestrator.ensureConnected()
                ConnectorTransportSelection.AUTHENTICATED -> authenticatedApi.execute(AuthenticatedConnectorOperation.DiagnosticsConnection)
            }
        } finally {
            reconnectGate.unlock()
        }
    }
}
