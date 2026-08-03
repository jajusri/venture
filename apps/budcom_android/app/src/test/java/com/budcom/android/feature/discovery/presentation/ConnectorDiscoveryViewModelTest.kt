package com.budcom.android.feature.discovery.presentation

import com.budcom.android.core.connection.ConnectorEnrolmentService
import com.budcom.android.core.connection.EnrolmentResult
import com.budcom.android.core.discovery.DiscoveredConnector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectorDiscoveryViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val connectorA = DiscoveredConnector(
        connectorId = "cid-a",
        name = "Front Desk",
        host = "192.168.1.20",
        port = 8080,
        apiVersion = "1.0.0",
        authRequired = false,
    )
    private val connectorB = DiscoveredConnector(
        connectorId = "cid-b",
        name = "Back Office",
        host = "192.168.1.21",
        port = 8080,
        apiVersion = "1.0.0",
        authRequired = false,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `clean install starts one bounded discovery pass on init`() = runTest(dispatcher) {
        val service = FakeEnrolmentService(discoverResult = listOf(connectorA))
        val viewModel = ConnectorDiscoveryViewModel(service)

        advanceUntilIdle()

        assertEquals(1, service.discoverCallCount)
        assertTrue(service.lastRequestedTimeoutMs!! > 0)
    }

    @Test
    fun `a single discovered Connector is shown for confirmation, not auto-paired`() = runTest(dispatcher) {
        val service = FakeEnrolmentService(discoverResult = listOf(connectorA))
        val viewModel = ConnectorDiscoveryViewModel(service)

        advanceUntilIdle()

        assertEquals(ConnectorDiscoveryPhase.Found, viewModel.uiState.value.phase)
        assertEquals(listOf(connectorA), viewModel.uiState.value.discovered)
        assertEquals(0, service.pairCallCount)
    }

    @Test
    fun `multiple discovered Connectors require explicit selection, never auto-selected`() = runTest(dispatcher) {
        val service = FakeEnrolmentService(discoverResult = listOf(connectorA, connectorB))
        val viewModel = ConnectorDiscoveryViewModel(service)
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.discovered.size)
        assertNull(viewModel.uiState.value.selected)

        viewModel.onEvent(ConnectorDiscoveryEvent.Select(connectorB))

        assertEquals(ConnectorDiscoveryPhase.Confirming, viewModel.uiState.value.phase)
        assertEquals(connectorB, viewModel.uiState.value.selected)
        assertEquals(0, service.pairCallCount)
    }

    @Test
    fun `pairing only happens after explicit confirm, never from selection alone`() = runTest(dispatcher) {
        val service = FakeEnrolmentService(
            discoverResult = listOf(connectorA),
            pairResult = EnrolmentResult.Paired(connectorA.connectorId, "Front Desk"),
        )
        val viewModel = ConnectorDiscoveryViewModel(service)
        advanceUntilIdle()

        viewModel.onEvent(ConnectorDiscoveryEvent.Select(connectorA))
        assertEquals(0, service.pairCallCount)

        viewModel.onEvent(ConnectorDiscoveryEvent.ConfirmPairing)
        advanceUntilIdle()

        assertEquals(1, service.pairCallCount)
        assertEquals(connectorA, service.lastPairedCandidate)
    }

    @Test
    fun `no Connector found shows a Retry-and-manual-fallback state, never silently retries 10-0-2-2`() = runTest(dispatcher) {
        val service = FakeEnrolmentService(discoverResult = emptyList())
        val viewModel = ConnectorDiscoveryViewModel(service)

        advanceUntilIdle()

        assertEquals(ConnectorDiscoveryPhase.Empty, viewModel.uiState.value.phase)
        assertEquals(1, service.discoverCallCount)

        viewModel.onEvent(ConnectorDiscoveryEvent.Retry)
        advanceUntilIdle()

        assertEquals(2, service.discoverCallCount)
    }

    @Test
    fun `identity mismatch on confirm is surfaced and does not navigate onward`() = runTest(dispatcher) {
        val service = FakeEnrolmentService(
            discoverResult = listOf(connectorA),
            pairResult = EnrolmentResult.IdentityMismatch(connectorA.connectorId, "some-other-id"),
        )
        val viewModel = ConnectorDiscoveryViewModel(service)
        advanceUntilIdle()
        viewModel.onEvent(ConnectorDiscoveryEvent.Select(connectorA))
        viewModel.onEvent(ConnectorDiscoveryEvent.ConfirmPairing)
        advanceUntilIdle()

        assertEquals(ConnectorDiscoveryPhase.Found, viewModel.uiState.value.phase)
        assertTrue(viewModel.uiState.value.errorMessage != null)
    }
}

private class FakeEnrolmentService(
    private val discoverResult: List<DiscoveredConnector> = emptyList(),
    private val pairResult: EnrolmentResult = EnrolmentResult.Unreachable,
) : ConnectorEnrolmentService {
    var discoverCallCount = 0
        private set
    var lastRequestedTimeoutMs: Long? = null
        private set
    var pairCallCount = 0
        private set
    var lastPairedCandidate: DiscoveredConnector? = null
        private set

    override suspend fun discover(timeoutMs: Long): List<DiscoveredConnector> {
        discoverCallCount++
        lastRequestedTimeoutMs = timeoutMs
        return discoverResult
    }

    override suspend fun pair(candidate: DiscoveredConnector): EnrolmentResult {
        pairCallCount++
        lastPairedCandidate = candidate
        return pairResult
    }
}
