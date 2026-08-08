package com.budcom.android.core.connection

import com.budcom.android.core.connectorauth.data.remote.AuthenticatedConnectorApiPort
import com.budcom.android.core.connectorauth.domain.ConnectorTransportSelection
import com.budcom.android.core.connectorauth.domain.ConnectorTransportSelectionGate
import com.budcom.android.core.connectorauth.domain.model.AuthenticatedConnectorOperation
import com.budcom.android.core.connectorauth.domain.model.AuthenticatedConnectorResult
import com.budcom.android.core.network.NetworkConnectivityObserver
import com.budcom.android.core.util.DispatcherProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.coroutines.coroutineContext

/**
 * [FakeConnectivity] uses a [MutableStateFlow], mirroring the real
 * [com.budcom.android.core.network.DefaultNetworkConnectivityObserver]'s conflated
 * "current value on subscribe, then changes" semantics — a plain [MutableSharedFlow] with no
 * replay silently drops emissions made before a collector subscribes, which doesn't match
 * production behavior.
 *
 * [ConnectorReconnectCoordinator.start] is called with the test's own scope (`this`), then
 * its background collector is stopped via `coroutineContext.cancelChildren()` at the end of
 * each test — `runTest`'s `backgroundScope` does not reliably advance under this project's
 * kotlinx-coroutines-test version, so this is the deterministic alternative.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConnectorReconnectCoordinatorTest {
    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = dispatcher
        override val io: CoroutineDispatcher = dispatcher
        override val default: CoroutineDispatcher = dispatcher
    }
    private val legacyGate = FakeTransportGate(ConnectorTransportSelection.LEGACY)
    private val unreachableAuthApi = UnreachableAuthenticatedApi

    @Test
    fun `initial connectivity state at subscribe time does not trigger a reconnect`() = runTest(dispatcher) {
        val connectivity = FakeConnectivity(initial = true)
        val orchestrator = CountingOrchestratorFake()
        val coordinator = DefaultConnectorReconnectCoordinator(connectivity, orchestrator, legacyGate, unreachableAuthApi, dispatchers)

        coordinator.start(this)
        advanceUntilIdle()

        assertEquals(0, orchestrator.callCount)
        coroutineContext.cancelChildren()
    }

    @Test
    fun `becoming online after subscribing triggers exactly one bounded reconnect`() = runTest(dispatcher) {
        val connectivity = FakeConnectivity(initial = false)
        val orchestrator = CountingOrchestratorFake()
        val coordinator = DefaultConnectorReconnectCoordinator(connectivity, orchestrator, legacyGate, unreachableAuthApi, dispatchers)

        coordinator.start(this)
        advanceUntilIdle() // subscribes; drop(1) consumes the initial `false`
        connectivity.emit(true) // real online transition
        advanceUntilIdle()

        assertEquals(1, orchestrator.callCount)
        coroutineContext.cancelChildren()
    }

    @Test
    fun `going offline never triggers a reconnect attempt`() = runTest(dispatcher) {
        val connectivity = FakeConnectivity(initial = true)
        val orchestrator = CountingOrchestratorFake()
        val coordinator = DefaultConnectorReconnectCoordinator(connectivity, orchestrator, legacyGate, unreachableAuthApi, dispatchers)

        coordinator.start(this)
        advanceUntilIdle() // drop(1) consumes the initial `true`
        connectivity.emit(false)
        advanceUntilIdle()

        assertEquals(0, orchestrator.callCount)
        coroutineContext.cancelChildren()
    }

    @Test
    fun `a reconnect already in flight is not joined by a second overlapping transition`() = runTest(dispatcher) {
        val connectivity = FakeConnectivity(initial = false)
        val gate = CompletableDeferred<Unit>()
        val orchestrator = CountingOrchestratorFake(beforeReturn = { gate.await() })
        val coordinator = DefaultConnectorReconnectCoordinator(connectivity, orchestrator, legacyGate, unreachableAuthApi, dispatchers)

        coordinator.start(this)
        advanceUntilIdle() // drop(1) consumes the initial `false`
        connectivity.emit(true) // triggers the first (blocked) attempt
        advanceUntilIdle()
        connectivity.emit(false)
        advanceUntilIdle()
        connectivity.emit(true) // a second transition arrives while the first is still in flight
        advanceUntilIdle()

        assertEquals(1, orchestrator.callCount)
        gate.complete(Unit)
        advanceUntilIdle()
        coroutineContext.cancelChildren()
    }

    // ============================== TD-017: AUTHENTICATED transport ==============================

    @Test
    fun `becoming online while AUTHENTICATED triggers exactly one authenticated diagnostics probe, never the legacy orchestrator`() = runTest(dispatcher) {
        val connectivity = FakeConnectivity(initial = false)
        val orchestrator = CountingOrchestratorFake()
        val authApi = CountingAuthenticatedApiFake()
        val coordinator = DefaultConnectorReconnectCoordinator(
            connectivity,
            orchestrator,
            FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authApi,
            dispatchers,
        )

        coordinator.start(this)
        advanceUntilIdle() // drop(1) consumes the initial `false`
        connectivity.emit(true)
        advanceUntilIdle()

        assertEquals(0, orchestrator.callCount)
        assertEquals(listOf(AuthenticatedConnectorOperation.DiagnosticsConnection), authApi.executedOperations)
        coroutineContext.cancelChildren()
    }

    @Test
    fun `an authenticated reconnect already in flight is not joined by a second overlapping transition`() = runTest(dispatcher) {
        val connectivity = FakeConnectivity(initial = false)
        val gate = CompletableDeferred<Unit>()
        val authApi = CountingAuthenticatedApiFake(beforeReturn = { gate.await() })
        val coordinator = DefaultConnectorReconnectCoordinator(
            connectivity,
            CountingOrchestratorFake(),
            FakeTransportGate(ConnectorTransportSelection.AUTHENTICATED),
            authApi,
            dispatchers,
        )

        coordinator.start(this)
        advanceUntilIdle() // drop(1) consumes the initial `false`
        connectivity.emit(true) // triggers the first (blocked) attempt
        advanceUntilIdle()
        connectivity.emit(false)
        advanceUntilIdle()
        connectivity.emit(true) // a second transition arrives while the first is still in flight
        advanceUntilIdle()

        assertEquals(1, authApi.executedOperations.size)
        gate.complete(Unit)
        advanceUntilIdle()
        coroutineContext.cancelChildren()
    }
}

private class FakeConnectivity(initial: Boolean) : NetworkConnectivityObserver {
    private val flow = MutableStateFlow(initial)
    override val isOnline: Flow<Boolean> = flow
    override fun current(): Boolean = flow.value

    fun emit(value: Boolean) {
        flow.value = value
    }
}

private class CountingOrchestratorFake(
    private val beforeReturn: suspend () -> Unit = {},
) : ConnectorConnectionOrchestrator {
    var callCount = 0
        private set

    override suspend fun ensureConnected(): ConnectionResolution {
        callCount++
        beforeReturn()
        return ConnectionResolution.Offline
    }
}

private class FakeTransportGate(private val selection: ConnectorTransportSelection) : ConnectorTransportSelectionGate {
    override suspend fun resolve(): ConnectorTransportSelection = selection
}

private class CountingAuthenticatedApiFake(
    private val beforeReturn: suspend () -> Unit = {},
) : AuthenticatedConnectorApiPort {
    val executedOperations = mutableListOf<AuthenticatedConnectorOperation>()

    override suspend fun execute(operation: AuthenticatedConnectorOperation): AuthenticatedConnectorResult {
        executedOperations += operation
        beforeReturn()
        return AuthenticatedConnectorResult.TransportFailure
    }
}

private object UnreachableAuthenticatedApi : AuthenticatedConnectorApiPort {
    override suspend fun execute(operation: AuthenticatedConnectorOperation): AuthenticatedConnectorResult =
        error("UnreachableAuthenticatedApi must never be called on the LEGACY path")
}
